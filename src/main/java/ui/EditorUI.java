
package ui;

import Database.DatabaseManager;
import crdt.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class EditorUI extends Application {

    // ── CRDT Controller ──────────────────────────────────────────────
    private CollaborativeDocumentController controller;
    private BlockId currentBlockId;

    // ── Session info ─────────────────────────────────────────────────
    private String username;
    private String sessionId;
    private String editorSessionId;
    private String viewerSessionId;
    private boolean readOnlyMode;
    private int userId;
    private String userColor;

    // ── UI state ─────────────────────────────────────────────────────
    private boolean isBold   = false;
    private boolean isItalic = false;
    private int caretPos = 0;

    // ── Selection state ───────────────────────────────────────────────
    private int     selectionAnchorPos     = -1;
    private BlockId selectionAnchorBlockId = null;
    private int     selectionStart         = -1;   // -1 means no selection
    private int     selectionEnd           = -1;
    private BlockId selectionBlockId       = null; // block the selection lives in

    // Active users: username -> color
    private final Map<String, String> activeUsers = new LinkedHashMap<>();
    private final Map<String, RemoteCursor> remoteCursors = new HashMap<>();

    private static class RemoteCursor {
        private final String username;
        private final String color;
        private final BlockId blockId;
        private final int caretPos;

        private RemoteCursor(String username, String color, BlockId blockId, int caretPos) {
            this.username = username;
            this.color = color;
            this.blockId = blockId;
            this.caretPos = Math.max(caretPos, 0);
        }
    }

    // ── UI nodes ─────────────────────────────────────────────────────
    private Stage primaryStage;
    private TextFlow textFlow;
    private VBox usersListBox;
    private Label statusLabel;
    private Button boldBtn;
    private Button italicBtn;

    // ── Flags ─────────────────────────────────────────────────────────
    private boolean suppressListener = false;
    private boolean applyingRemote   = false;
    private boolean pendingJoinFlow  = false;
    private boolean suppressCursorAnnouncement = false;
    private String lastJoinAttemptedId = "";

    // Server Connection
    private Network.ClientConnection clientConnection;
    private final DatabaseManager databaseManager = new DatabaseManager();
    private Timeline autoSaveTimeline;
    private static final int AUTO_SAVE_SECONDS = 10;

    // ─────────────────────────────────────────────────────────────────
    //  ENTRY POINT
    // ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("Collaborative Text Editor");
        showEntryChoiceScreen();
    }

    @Override
    public void stop() {
        persistDocumentToDatabase();
        stopAutoSave();
        if (clientConnection != null) clientConnection.disconnect();
    }

    // ─────────────────────────────────────────────────────────────────
    //  SCREEN 1 — ENTRY CHOICE
    // ─────────────────────────────────────────────────────────────────

    private void showEntryChoiceScreen() {
        Label title = new Label("Collaborative Text Editor");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Button createBtn = new Button("Create New Session");
        createBtn.setStyle("-fx-pref-width: 260px; -fx-pref-height: 40px;");
        createBtn.setOnAction(e -> showCreateSessionScreen());

        Button joinBtn = new Button("Join Existing Session");
        joinBtn.setStyle("-fx-pref-width: 260px; -fx-pref-height: 40px;");
        joinBtn.setOnAction(e -> showJoinSessionScreen());

        VBox root = new VBox(16, title, createBtn, joinBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: #ecf0f1;");
        primaryStage.setScene(new Scene(root, 520, 360));
        primaryStage.show();
    }

    private void showCreateSessionScreen() {
        TextField userField = new TextField();
        userField.setPromptText("Enter your name...");
        Label errorLabel = new Label();
        Button createBtn = new Button("Create");
        createBtn.setOnAction(e -> {
            String uname = userField.getText().trim();
            if (uname.isEmpty()) { errorLabel.setText("Please enter your name."); return; }
            initializeSession(uname, generateSessionId(), false, true);
        });
        VBox root = new VBox(10, new Label("Create Session"), new Label("Username"), userField, errorLabel, createBtn);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER_LEFT);
        primaryStage.setScene(new Scene(new VBox(root), 520, 320));
    }

    private void showJoinSessionScreen() {
        showJoinSessionScreen("", "", "");
    }

    private void showJoinSessionScreen(String presetUser, String presetSessionId, String errorMessage) {
        TextField userField = new TextField(presetUser);
        TextField sessionField = new TextField(presetSessionId);
        sessionField.setPromptText("Editor or viewer session ID...");
        Label errorLabel = new Label(errorMessage);
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
        Button joinBtn = new Button("Join");
        joinBtn.setOnAction(e -> {
            String uname = userField.getText().trim();
            String sid = sessionField.getText().trim();
            if (uname.isEmpty() || sid.isEmpty()) { errorLabel.setText("Please fill in both fields."); return; }
            lastJoinAttemptedId = sid;
            initializeSession(uname, sid, false, false);
        });
        VBox root = new VBox(10, new Label("Join Session"), new Label("Username"), userField, new Label("Session ID"), sessionField, errorLabel, joinBtn);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER_LEFT);
        primaryStage.setScene(new Scene(new VBox(root), 520, 360));
    }

    private void initializeSession(String uname, String baseSessionId, boolean readOnly, boolean isCreateFlow) {
        username = uname;
        sessionId = baseSessionId;
        editorSessionId = baseSessionId;
        viewerSessionId = isCreateFlow ? generateDistinctSessionId(editorSessionId) : "";
        readOnlyMode = readOnly;
        userId = Math.abs(uname.hashCode()) % 10000;
        controller = new CollaborativeDocumentController(userId);
        currentBlockId = controller.createFirstBlock();
        persistDocumentToDatabase();
        userColor = randomColor(userId);
        activeUsers.put(username, userColor);

        pendingJoinFlow = !isCreateFlow;
        clientConnection = new Network.ClientConnection(controller,this,"ws://localhost:8080/ws", sessionId, viewerSessionId, readOnlyMode, isCreateFlow);
        clientConnection.connect();
        if (isCreateFlow) showEditorScreen();
    }

    // ─────────────────────────────────────────────────────────────────
    //  SCREEN 2 — EDITOR
    // ─────────────────────────────────────────────────────────────────

    private void showEditorScreen() {
        primaryStage.setTitle("Editor — " + sessionId + " | " + username);

        // ── Top bar ──────────────────────────────────────────────────
        String headerText = readOnlyMode
                ? "Collaborative Editor (Viewer Mode)"
                : "Editor Session: " + editorSessionId + " | Viewer Session: " + viewerSessionId;

        Label sessionInfo = new Label(headerText);
        sessionInfo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: white;");

        Button copyEditorBtn = new Button("Copy Editor ID");
        copyEditorBtn.setStyle(toolbarBtnStyle(false));
        copyEditorBtn.setOnAction(e -> copyToClipboard(editorSessionId));
        copyEditorBtn.setVisible(!readOnlyMode); // Only editors see this

        Button copyViewerBtn = new Button("Copy Viewer ID");
        copyViewerBtn.setStyle(toolbarBtnStyle(false));
        copyViewerBtn.setOnAction(e -> copyToClipboard(viewerSessionId));
        copyViewerBtn.setVisible(!readOnlyMode); // Only editors see this

        statusLabel = new Label("● Connected");
        statusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(12, sessionInfo, copyEditorBtn, copyViewerBtn, spacer, statusLabel);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8, 16, 8, 16));
        topBar.setStyle("-fx-background-color: #2c3e50;");

        // ── Toolbar ──────────────────────────────────────────────────
        boldBtn = new Button("B");
        boldBtn.setStyle(toolbarBtnStyle(false) + "-fx-font-weight: bold;");
        boldBtn.setOnAction(e -> toggleBold());

        italicBtn = new Button("I");
        italicBtn.setStyle(toolbarBtnStyle(false) + "-fx-font-style: italic;");
        italicBtn.setOnAction(e -> toggleItalic());

        Label formatLabel = new Label("Format:");
        formatLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");

        Button importBtn = new Button("Import");
        importBtn.setStyle(toolbarBtnStyle(false));
        importBtn.setOnAction(e -> importFromTxtFile());

        Button exportBtn = new Button("Export");
        exportBtn.setStyle(toolbarBtnStyle(false));
        exportBtn.setOnAction(e -> exportToTxtFile());

        Button browseDbBtn = new Button("Browse DB");
        browseDbBtn.setStyle(toolbarBtnStyle(false));
        browseDbBtn.setOnAction(e -> browseImportedFilesFromDatabase());

        Button renameBtn = new Button("Rename");
        renameBtn.setStyle(toolbarBtnStyle(false));
        renameBtn.setDisable(readOnlyMode); // Viewers can't rename files
        renameBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog(sessionId);
            dialog.setTitle("Rename Document");
            dialog.setHeaderText("Enter a new name for this document:");
            dialog.showAndWait().ifPresent(newName -> {
                databaseManager.renameDocument(sessionId, newName);
                primaryStage.setTitle("Editor — " + newName + " | " + username);
                setStatus("● Renamed to: " + newName, "#3498db");
            });
        });

        Button deleteBtn = new Button("Delete");
        deleteBtn.setStyle(toolbarBtnStyle(false) + "-fx-text-fill: #e74c3c;");
        deleteBtn.setDisable(readOnlyMode); // Viewers can't delete files
        deleteBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete this file permanently?", ButtonType.YES, ButtonType.NO);
            alert.showAndWait().ifPresent(type -> {
                if (type == ButtonType.YES) {
                    databaseManager.deleteDocument(sessionId);
                    if (clientConnection != null) clientConnection.disconnect();
                    showEntryChoiceScreen();
                }
            });
        });

        HBox toolbar = new HBox(8, formatLabel, boldBtn, italicBtn, importBtn, exportBtn, browseDbBtn, renameBtn, deleteBtn);

        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(6, 16, 6, 16));
        toolbar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        // ── TextFlow ─────────────────────────────────────────────────
        textFlow = new TextFlow();
        textFlow.setPadding(new Insets(12));
        textFlow.setLineSpacing(2);
        textFlow.setFocusTraversable(true);
        textFlow.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSpecialKeyPress);
        textFlow.addEventFilter(KeyEvent.KEY_TYPED,   this::handleTypedCharacter);
        textFlow.setOnMouseClicked(e -> {
            clearSelection();
            textFlow.requestFocus();
            refreshEditor(caretPos);
        });

        AnchorPane editorPane = new AnchorPane(textFlow);
        editorPane.setStyle("-fx-background-color: white;");
        AnchorPane.setTopAnchor(textFlow, 0.0);
        AnchorPane.setLeftAnchor(textFlow, 0.0);
        AnchorPane.setRightAnchor(textFlow, 0.0);
        AnchorPane.setBottomAnchor(textFlow, 0.0);

        textFlow.focusedProperty().addListener((obs, oldVal, focused) ->
                editorPane.setStyle(focused
                        ? "-fx-background-color: white; -fx-border-color: #3498db; -fx-border-width: 2;"
                        : "-fx-background-color: white; -fx-border-color: transparent; -fx-border-width: 2;"
                )
        );

        ScrollPane scroll = new ScrollPane(editorPane);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setStyle(
                "-fx-background: white; " +
                        "-fx-background-color: white; " +
                        "-fx-border-color: transparent;"
        );
        HBox.setHgrow(scroll, Priority.ALWAYS);

        // ── Right panel — Active Users ───────────────────────────────
        Label usersTitle = new Label("Active Users");
        usersTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #2c3e50;");

        usersListBox = new VBox(6);
        usersListBox.setPadding(new Insets(4, 0, 4, 0));

        ScrollPane usersScroll = new ScrollPane(usersListBox);
        usersScroll.setFitToWidth(true);
        usersScroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(usersScroll, Priority.ALWAYS);

        VBox usersPanel = new VBox(10, usersTitle, usersScroll);
        usersPanel.setPadding(new Insets(12));
        usersPanel.setPrefWidth(180);
        usersPanel.setMinWidth(180);
        usersPanel.setMaxWidth(180);
        usersPanel.setStyle(
                "-fx-background-color: #f8f9fa; " +
                        "-fx-border-color: #dee2e6; " +
                        "-fx-border-width: 0 0 0 1;"
        );

        refreshUsersList();

        HBox editorRow = new HBox(scroll, usersPanel);
        HBox.setHgrow(scroll, Priority.ALWAYS);
        VBox.setVgrow(editorRow, Priority.ALWAYS);

        scroll.viewportBoundsProperty().addListener((obs, oldVal, newVal) ->
                editorPane.setMinHeight(newVal.getHeight())
        );

        // ── Status bar ───────────────────────────────────────────────
        Label statusBar = new Label("Connected as: " + username + "  |  Editor ID: " + editorSessionId + (viewerSessionId.isBlank() ? "" : "  |  Viewer ID: " + viewerSessionId));
        statusBar.setStyle("-fx-font-size: 11px; -fx-text-fill: #888; -fx-padding: 4 12 4 12;");
        HBox bottomBar = new HBox(statusBar);
        bottomBar.setStyle("-fx-background-color: #f1f1f1; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");

        VBox root = new VBox(topBar, toolbar, editorRow, bottomBar);
        VBox.setVgrow(editorRow, Priority.ALWAYS);

        Scene scene = new Scene(root, 900, 600);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);

        startAutoSave();

        Platform.runLater(() -> {
            textFlow.requestFocus();
            refreshEditor(0);
        });
    }

    // ─────────────────────────────────────────────────────────────────
    //  KEY HANDLING
    // ─────────────────────────────────────────────────────────────────

    private void handleSpecialKeyPress(KeyEvent event) {
        if (suppressListener || applyingRemote || readOnlyMode) return;
        if (controller == null) return;
        if (!ensureCurrentBlockAvailable()) return;

        KeyCode code    = event.getCode();
        boolean shift   = event.isShiftDown();
        boolean ctrl    = event.isControlDown() || event.isMetaDown();

        // ── Ctrl+A — Select all in current block ─────────────────────
        if (ctrl && code == KeyCode.A) {
            selectAll();
            event.consume();
            return;
        }

        // ── Ctrl+C — Copy ────────────────────────────────────────────
        if (ctrl && code == KeyCode.C) {
            copySelection();
            event.consume();
            return;
        }

        // ── Ctrl+X — Cut ─────────────────────────────────────────────
        if (ctrl && code == KeyCode.X) {
            copySelection();
            deleteSelection();
            event.consume();
            return;
        }

        // ── Ctrl+V — Paste ───────────────────────────────────────────
        if (ctrl && code == KeyCode.V) {
            pasteFromClipboard();
            event.consume();
            return;
        }


    //  UNDO (Ctrl+Z)
        if (ctrl && code == KeyCode.Z) {
                Object result = controller.undo();

                if (result instanceof Operation) {
                    Operation op = (Operation) result;

                    if (op.type == Operation.Type.INSERT) {
                        caretPos++;
                    }
                    else if (op.type == Operation.Type.DELETE) {
                        caretPos = Math.max(0, caretPos - 1);
                    }
                }

                refreshEditor(caretPos);
                event.consume();
                return;
            }


    // ─────────────────────────────────────────────────────────────────
    //  REDO (Ctrl+Y)
    // ─────────────────────────────────────────────────────────────────
        if (ctrl && code == KeyCode.Y) {
            Object result = controller.redo();

            if (result instanceof Operation) {
                Operation op = (Operation) result;

                if (op.type == Operation.Type.INSERT) {
                    caretPos++;
                }
                else if (op.type == Operation.Type.DELETE) {
                    caretPos = Math.max(0, caretPos - 1);
                }
            }

            refreshEditor(caretPos);
            event.consume();
            return;
        }
        // ── ENTER ────────────────────────────────────────────────────
        if (code == KeyCode.ENTER) {
            if (hasSelection()) deleteSelection();

            int blockSize = controller.getDocument()
                    .findBlock(currentBlockId)
                    .CharacterCRDT
                    .getVisibleCharacters()
                    .size();

            String  newClock   = String.valueOf(System.currentTimeMillis());
            BlockId newBlockId = new BlockId(userId, newClock);

            BlockOperation op;
            if (caretPos >= blockSize) {
                op = controller.localInsertBlock(newBlockId, currentBlockId);
            } else {
                op = controller.localSplitBlock(currentBlockId, caretPos, newClock);
            }

            if (op != null && clientConnection != null && clientConnection.isConnected()) {
                clientConnection.sendBlockOperation(op, currentBlockId);
            }

            currentBlockId = newBlockId;
            caretPos = 0;
            refreshEditor(caretPos);
            event.consume();
            return;
        }

        // ── BACKSPACE ────────────────────────────────────────────────
        if (code == KeyCode.BACK_SPACE) {
            if (hasSelection()) {
                deleteSelection();
                event.consume();
                return;
            }

            if (caretPos > 0) {
                Operation op = controller.localDeleteChar(currentBlockId, caretPos);
                if (op != null) {
                    caretPos--;
                    if (clientConnection != null && clientConnection.isConnected()) {
                        clientConnection.sendOperation(op, currentBlockId);
                    }
                }
                refreshEditor(caretPos);
                event.consume();
                return;
            }

            // caretPos == 0: merge with previous block
            List<BlockNode> blocks = controller.getDocument().getVisibleBlocks();
            int currentIndex = -1;
            for (int i = 0; i < blocks.size(); i++) {
                if (blocks.get(i).id.equals(currentBlockId)) { currentIndex = i; break; }
            }
            if (currentIndex > 0) {
                BlockId previousBlockId = blocks.get(currentIndex - 1).id;
                int previousSize = blocks.get(currentIndex - 1)
                        .CharacterCRDT.getVisibleCharacters().size();

                BlockOperation op = controller.localMergeBlocks(previousBlockId, currentBlockId);
                if (op != null && clientConnection != null && clientConnection.isConnected()) {
                    clientConnection.sendBlockOperation(op, previousBlockId);
                }
                currentBlockId = previousBlockId;
                caretPos = previousSize;
                refreshEditor(caretPos);
            }
            event.consume();
            return;
        }

        // ── DELETE ───────────────────────────────────────────────────
        if (code == KeyCode.DELETE) {
            if (hasSelection()) {
                deleteSelection();
                event.consume();
                return;
            }
            Operation op = controller.localDeleteChar(currentBlockId, caretPos + 1);
            if (op != null && clientConnection != null && clientConnection.isConnected()) {
                clientConnection.sendOperation(op, currentBlockId);
            }
            refreshEditor(caretPos);
            event.consume();
            return;
        }

        // ── LEFT arrow ───────────────────────────────────────────────
        if (code == KeyCode.LEFT) {
            if (!shift) clearSelection();

            if (caretPos > 0) {
                if (shift) extendSelection(caretPos - 1, currentBlockId);
                caretPos--;
            } else {
                List<BlockNode> blocks = controller.getDocument().getVisibleBlocks();
                for (int i = 0; i < blocks.size(); i++) {
                    if (blocks.get(i).id.equals(currentBlockId) && i > 0) {
                        BlockNode prev = blocks.get(i - 1);
                        int newPos = prev.CharacterCRDT.getVisibleCharacters().size();
                        if (shift) extendSelection(newPos, prev.id);
                        currentBlockId = prev.id;
                        caretPos = newPos;
                        break;
                    }
                }
            }
            refreshEditor(caretPos);
            event.consume();
            return;
        }

        // ── RIGHT arrow ──────────────────────────────────────────────
        if (code == KeyCode.RIGHT) {
            int size = controller.getDocument()
                    .findBlock(currentBlockId)
                    .CharacterCRDT.getVisibleCharacters().size();

            if (!shift) clearSelection();

            if (caretPos < size) {
                if (shift) extendSelection(caretPos + 1, currentBlockId);
                caretPos++;
            } else {
                List<BlockNode> blocks = controller.getDocument().getVisibleBlocks();
                for (int i = 0; i < blocks.size(); i++) {
                    if (blocks.get(i).id.equals(currentBlockId) && i < blocks.size() - 1) {
                        BlockNode next = blocks.get(i + 1);
                        if (shift) extendSelection(0, next.id);
                        currentBlockId = next.id;
                        caretPos = 0;
                        break;
                    }
                }
            }
            refreshEditor(caretPos);
            event.consume();
            return;
        }

        // ── HOME — jump to start of block ────────────────────────────
        if (code == KeyCode.HOME) {
            if (!shift) clearSelection();
            else extendSelection(0, currentBlockId);
            caretPos = 0;
            refreshEditor(caretPos);
            event.consume();
            return;
        }

        // ── END — jump to end of block ───────────────────────────────
        if (code == KeyCode.END) {
            int size = controller.getDocument()
                    .findBlock(currentBlockId)
                    .CharacterCRDT.getVisibleCharacters().size();
            if (!shift) clearSelection();
            else extendSelection(size, currentBlockId);
            caretPos = size;
            refreshEditor(caretPos);
            event.consume();
        }

    }

    private void handleTypedCharacter(KeyEvent event) {
        if (suppressListener || applyingRemote || readOnlyMode) return;
        if (controller == null) return;
        if (!ensureCurrentBlockAvailable()) return;

        // ── Block Ctrl/Meta combos — handled in handleSpecialKeyPress ──
        if (event.isControlDown() || event.isMetaDown()) {
            event.consume();
            return;
        }

        String ch = event.getCharacter();
        if (ch == null || ch.isEmpty()) return;

        char c = ch.charAt(0);
        if (c == '\b' || c == 127 || c == '\r' || c == '\n') {
            event.consume();
            return;
        }

        if (hasSelection()) deleteSelection();

        Operation op = controller.localInsertChar(currentBlockId, caretPos, c, isBold, isItalic);
        if (op != null && clientConnection != null && clientConnection.isConnected()) {
            clientConnection.sendOperation(op, currentBlockId);
        }

        caretPos++;
        refreshEditor(caretPos);
        event.consume();
    }

    // ─────────────────────────────────────────────────────────────────
    //  SELECTION HELPERS
    // ─────────────────────────────────────────────────────────────────

    /** Returns true if a valid selection exists. */
    private boolean hasSelection() {
        return selectionStart >= 0 && selectionEnd > selectionStart
                && selectionBlockId != null;
    }

    /** Clear any active selection. */
    private void clearSelection() {
        selectionStart         = -1;
        selectionEnd           = -1;
        selectionBlockId       = null;
        selectionAnchorPos     = -1;
        selectionAnchorBlockId = null;
    }

    /**
     * Called when the caret moves with Shift held.
     * Starts a selection if none exists, or extends the existing one.
     * Only same-block selection is supported.
     */
    private void extendSelection(int newCaretPos, BlockId newBlockId) {
        // Only support same-block for now
        if (!newBlockId.equals(currentBlockId)) return;

        if (selectionAnchorBlockId == null) {
            // First extension: anchor is where we were
            selectionAnchorPos     = caretPos;
            selectionAnchorBlockId = currentBlockId;
        }

        selectionBlockId = currentBlockId;

        if (newCaretPos >= selectionAnchorPos) {
            selectionStart = selectionAnchorPos;
            selectionEnd   = newCaretPos;
        } else {
            selectionStart = newCaretPos;
            selectionEnd   = selectionAnchorPos;
        }
    }

    /** Select every character in the current block. */
    private void selectAll() {
        int size = controller.getDocument()
                .findBlock(currentBlockId)
                .CharacterCRDT.getVisibleCharacters().size();
        selectionBlockId       = currentBlockId;
        selectionAnchorBlockId = currentBlockId;
        selectionAnchorPos     = 0;
        selectionStart         = 0;
        selectionEnd           = size;
        caretPos               = size;
        refreshEditor(caretPos);
    }

    /**
     * Copy the selected text to the system clipboard.
     */
    private void copySelection() {
        if (!hasSelection()) return;

        // Use the existing copyBlock from BlockCRDT
        String text = controller.getDocument().copyBlock(selectionBlockId);

        // Trim to only the selected range
        List<CharacterNode> visible = controller.getDocument()
                .findBlock(selectionBlockId)
                .CharacterCRDT.getVisibleCharacters();

        StringBuilder sb = new StringBuilder();
        for (int i = selectionStart; i < selectionEnd && i < visible.size(); i++) {
            sb.append(visible.get(i).value);
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }
    /**
     * Delete all characters in the current selection via CRDT operations,
     * then clear the selection and move the caret to selectionStart.
     */
    private void deleteSelection() {
        if (!hasSelection()) return;

        // Delete from end to start so indices stay valid
        for (int i = selectionEnd; i > selectionStart; i--) {
            Operation op = controller.localDeleteChar(selectionBlockId, i);
            if (op != null && clientConnection != null && clientConnection.isConnected()) {
                clientConnection.sendOperation(op, selectionBlockId);
            }
        }

        caretPos       = selectionStart;
        currentBlockId = selectionBlockId;
        clearSelection();
        refreshEditor(caretPos);
    }

    /**
     * Paste plain text from the system clipboard at the current caret position.
     * If there is a selection it is deleted first.
     */
    private void pasteFromClipboard() {
        String text = Clipboard.getSystemClipboard().getString();
        if (text == null || text.isEmpty()) return;

        if (hasSelection()) deleteSelection();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\r') continue; // skip \r in \r\n

            if (c == '\n') {
                // Only create a new block on newline
                String  newClock   = String.valueOf(System.currentTimeMillis());
                BlockId newBlockId = new BlockId(userId, newClock);
                BlockOperation op  = controller.localInsertBlock(newBlockId, currentBlockId);
                if (op != null && clientConnection != null && clientConnection.isConnected()) {
                    clientConnection.sendBlockOperation(op, currentBlockId);
                }
                currentBlockId = newBlockId;
                caretPos       = 0;

            } else if (c >= 32 && c <= 126) {
                // Insert directly into current block at caret position
                Operation op = controller.localInsertChar(currentBlockId, caretPos, c, isBold, isItalic);
                if (op != null && clientConnection != null && clientConnection.isConnected()) {
                    clientConnection.sendOperation(op, currentBlockId);
                }
                caretPos++;
            }
        }

        refreshEditor(caretPos);
    }


    private void startAutoSave() {
        stopAutoSave();
        autoSaveTimeline = new Timeline(new KeyFrame(Duration.seconds(AUTO_SAVE_SECONDS), e -> persistDocumentToDatabase()));
        autoSaveTimeline.setCycleCount(Timeline.INDEFINITE);
        autoSaveTimeline.play();
    }

    private void stopAutoSave() {
        if (autoSaveTimeline != null) {
            autoSaveTimeline.stop();
            autoSaveTimeline = null;
        }
    }

    private void persistDocumentToDatabase() {
        if (controller == null || sessionId == null || sessionId.isBlank()) return;
        databaseManager.saveDocument(sessionId, "Session " + sessionId, controller.getDocument());
    }

    private void exportToTxtFile() {
        if (controller == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Document");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        chooser.setInitialFileName("document.txt");

        java.io.File file = chooser.showSaveDialog(primaryStage);
        if (file == null) return;

        String serialized = serializeDocumentWithFormatting();
        try {
            Files.writeString(file.toPath(), serialized, StandardCharsets.UTF_8);
            setStatus("● Exported " + file.getName() + " locally", "#27ae60");
        } catch (IOException ex) {
            setStatus("● Export failed", "#e74c3c");
        }
    }

    private void importFromTxtFile() {
        if (controller == null || readOnlyMode) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Document");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        java.io.File file = chooser.showOpenDialog(primaryStage);
        if (file == null) return;

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            applyImportedDocument(content);
            refreshEditor(0);
            setStatus("● Imported " + file.getName(), "#27ae60");
        } catch (IOException ex) {
            setStatus("● Import failed", "#e74c3c");
        }
    }

    private void browseImportedFilesFromDatabase() {
        if (controller == null || readOnlyMode) return;

        List<Map<String, String>> exportedFiles = databaseManager.getExportedFiles();
        if (exportedFiles.isEmpty()) {
            setStatus("● No exported files found in DB", "#e67e22");
            return;
        }

        Map<String, Map<String, String>> displayToFile = new LinkedHashMap<>();
        for (Map<String, String> file : exportedFiles) {
            String display = file.getOrDefault("name", "unnamed")
                    + " | doc: " + file.getOrDefault("docId", "")
                    + " | saved: " + file.getOrDefault("createdAt", "");
            displayToFile.put(display, file);
        }

        List<String> choices = new ArrayList<>(displayToFile.keySet());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle("Browse Database Files");
        dialog.setHeaderText("Import previously exported file");
        dialog.setContentText("Choose a file:");
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(560);

        Optional<String> selected = dialog.showAndWait();
        if (selected.isPresent()) {
            Map<String, String> selectedFile = displayToFile.get(selected.get());
            if (selectedFile == null) return;
            String content = selectedFile.get("content");
            applyImportedDocument(content == null ? "" : content);
            refreshEditor(0);
            setStatus("● Imported from DB: " + selectedFile.getOrDefault("name", "file"), "#27ae60");
        }
    }

    private String serializeDocumentWithFormatting() {
        StringBuilder out = new StringBuilder();
        List<BlockNode> blocks = controller.getDocument().getVisibleBlocks();
        for (int b = 0; b < blocks.size(); b++) {
            boolean bold = false;
            boolean italic = false;
            List<CharacterNode> chars = blocks.get(b).CharacterCRDT.getVisibleCharacters();
            for (CharacterNode node : chars) {
                if (node.bold != bold) { out.append(node.bold ? "<b>" : "</b>"); bold = node.bold; }
                if (node.italic != italic) { out.append(node.italic ? "<i>" : "</i>"); italic = node.italic; }
                out.append(escapeTextChar(node.value));
            }
            if (italic) out.append("</i>");
            if (bold) out.append("</b>");
            if (b < blocks.size() - 1) out.append('\n');
        }
        return out.toString();
    }

    private void applyImportedDocument(String serialized) {
        // Always clear the current working area before import.
        clearAllBlocks();
        currentBlockId = controller.createFirstBlock();
        caretPos = 0;
        clearSelection();
        boolean bold = false;
        boolean italic = false;

        for (int i = 0; i < serialized.length();) {
            if (serialized.startsWith("<b>", i)) { bold = true; i += 3; continue; }
            if (serialized.startsWith("</b>", i)) { bold = false; i += 4; continue; }
            if (serialized.startsWith("<i>", i)) { italic = true; i += 3; continue; }
            if (serialized.startsWith("</i>", i)) { italic = false; i += 4; continue; }
            if (serialized.startsWith("&lt;", i)) { insertImportedChar('<', bold, italic); i += 4; continue; }
            if (serialized.startsWith("&gt;", i)) { insertImportedChar('>', bold, italic); i += 4; continue; }
            if (serialized.startsWith("&amp;", i)) { insertImportedChar('&', bold, italic); i += 5; continue; }

            char c = serialized.charAt(i++);
            if (c == '\r') continue;
            if (c == '\n') {
                String clock = String.valueOf(System.currentTimeMillis() + i);
                BlockId newBlockId = new BlockId(userId, clock);
                BlockOperation blockOp = controller.localInsertBlock(newBlockId, currentBlockId);
                if (blockOp != null && clientConnection != null && clientConnection.isConnected()) {
                    clientConnection.sendBlockOperation(blockOp, currentBlockId);
                }
                currentBlockId = newBlockId;
                caretPos = 0;
                continue;
            }
            insertImportedChar(c, bold, italic);
        }
    }

    private void insertImportedChar(char c, boolean bold, boolean italic) {
        Operation op = controller.localInsertChar(currentBlockId, caretPos, c, bold, italic);
        if (op != null && clientConnection != null && clientConnection.isConnected()) {
            clientConnection.sendOperation(op, currentBlockId);
        }
        caretPos++;
    }

    private void clearAllBlocks() {
        List<BlockNode> blocks = new ArrayList<>(controller.getDocument().getVisibleBlocks());
        for (BlockNode block : blocks) {
            BlockOperation delete = controller.localDeleteBlock(block.id);
            if (delete != null && clientConnection != null && clientConnection.isConnected()) {
                clientConnection.sendBlockOperation(delete, block.id);
            }
        }
    }

    private String escapeTextChar(char c) {
        if (c == '<') return "&lt;";
        if (c == '>') return "&gt;";
        if (c == '&') return "&amp;";
        return String.valueOf(c);
    }

    // ─────────────────────────────────────────────────────────────────
    //  REFRESH EDITOR
    // ─────────────────────────────────────────────────────────────────

    private void refreshEditor(int pos) {
        suppressListener = true;
        caretPos = pos;

        textFlow.getChildren().clear();

        List<BlockNode> blocks = controller.getDocument().getVisibleBlocks();

        for (int b = 0; b < blocks.size(); b++) {
            BlockNode block   = blocks.get(b);
            List<CharacterNode> visible = block.CharacterCRDT.getVisibleCharacters();
            boolean isCurrentBlock = block.id.equals(currentBlockId);
            boolean isSelBlock     = block.id.equals(selectionBlockId);

            for (int i = 0; i < visible.size(); i++) {
                // Draw caret before this character if caret is here
                if (isCurrentBlock && i == caretPos) {
                    textFlow.getChildren().add(makeCaret());
                }
                renderRemoteCursorTags(block.id, i);

                CharacterNode node     = visible.get(i);
                boolean       selected = isSelBlock && hasSelection()
                        && i >= selectionStart && i < selectionEnd;

                Text t = new Text(String.valueOf(node.value));
                t.setFont(Font.font(
                        "Consolas",
                        node.bold   ? FontWeight.BOLD   : FontWeight.NORMAL,
                        node.italic ? FontPosture.ITALIC : FontPosture.REGULAR,
                        15
                ));

                if (selected) {
                    // Highlighted selection: blue background via fill trick
                    t.setFill(Color.WHITE);
                    t.setStyle("-fx-background-color: #3498db;");
                    // JavaFX TextFlow doesn't support background on Text directly,
                    // so we use a StackPane wrapper to simulate the highlight.
                    javafx.scene.layout.StackPane highlight = new javafx.scene.layout.StackPane(t);
                    highlight.setStyle("-fx-background-color: #3498db; -fx-background-radius: 2;");
                    textFlow.getChildren().add(highlight);
                } else {
                    t.setFill(Color.web("#2d3436"));
                    textFlow.getChildren().add(t);
                }
            }

            // Caret at end of block
            if (isCurrentBlock && caretPos >= visible.size()) {
                textFlow.getChildren().add(makeCaret());
            }
            renderRemoteCursorTags(block.id, visible.size());

            if (b < blocks.size() - 1) {
                textFlow.getChildren().add(new Text("\n"));
            }
        }

        suppressListener = false;
        if (!suppressCursorAnnouncement) {
            announceCursorPosition();
        }
    }


    private void renderRemoteCursorTags(BlockId blockId, int position) {
        List<RemoteCursor> markers = new ArrayList<>();
        for (RemoteCursor cursor : remoteCursors.values()) {
            if (cursor.blockId != null && cursor.blockId.equals(blockId) && cursor.caretPos == position) {
                markers.add(cursor);
            }
        }
        markers.sort(Comparator.comparing(c -> c.username.toLowerCase(Locale.ROOT)));
        for (RemoteCursor cursor : markers) {
            textFlow.getChildren().add(makeRemoteCursorTag(cursor));
        }
    }

    private HBox makeRemoteCursorTag(RemoteCursor cursor) {
        javafx.scene.shape.Rectangle line = new javafx.scene.shape.Rectangle(2, 18);
        line.setFill(Color.web(cursor.color));
        Text label = new Text(cursor.username + " ");
        label.setFill(Color.web(cursor.color));
        label.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        HBox box = new HBox(2, line, label);
        box.setAlignment(Pos.BOTTOM_LEFT);
        return box;
    }

    private void refreshEditorWithoutBroadcast() {
        boolean prev = suppressCursorAnnouncement;
        suppressCursorAnnouncement = true;
        try {
            refreshEditor(caretPos);
        } finally {
            suppressCursorAnnouncement = prev;
        }
    }

    private void announceCursorPosition() {
        if (clientConnection == null || !clientConnection.isConnected() || currentBlockId == null) return;
        clientConnection.sendCursor(currentBlockId.toString(), caretPos);
    }

    private void refreshEditor() {
        refreshEditor(caretPos);
    }

    private javafx.scene.shape.Rectangle makeCaret() {
        javafx.scene.shape.Rectangle r = new javafx.scene.shape.Rectangle(2, 18);
        r.setFill(Color.web("#0984e3"));

        javafx.animation.FadeTransition blink =
                new javafx.animation.FadeTransition(javafx.util.Duration.millis(500), r);
        blink.setFromValue(1.0);
        blink.setToValue(0.0);
        blink.setCycleCount(javafx.animation.Animation.INDEFINITE);
        blink.setAutoReverse(true);
        blink.play();

        return r;
    }

    // ─────────────────────────────────────────────────────────────────
    //  BOLD / ITALIC TOGGLE
    // ─────────────────────────────────────────────────────────────────

    private void toggleBold() {
        isBold = !isBold;
        boldBtn.setStyle(toolbarBtnStyle(isBold) + "-fx-font-weight: bold;");
    }

    private void toggleItalic() {
        isItalic = !isItalic;
        italicBtn.setStyle(toolbarBtnStyle(isItalic) + "-fx-font-style: italic;");
    }

    // ─────────────────────────────────────────────────────────────────
    //  ACTIVE USERS PANEL
    // ─────────────────────────────────────────────────────────────────

    private void refreshUsersList() {
        if (usersListBox == null) return;
        usersListBox.getChildren().clear();
        for (Map.Entry<String, String> entry : activeUsers.entrySet()) {
            usersListBox.getChildren().add(makeUserRow(entry.getKey(), entry.getValue()));
        }
    }

    private HBox makeUserRow(String name, String color) {
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(7);
        dot.setFill(Color.web(color));

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 13px;");

        if (name.equals(username)) {
            nameLabel.setText(name + " (you)");
            nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        }

        HBox row = new HBox(8, dot, nameLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 8, 4, 8));
        row.setStyle("-fx-background-color: white; -fx-background-radius: 6;");
        return row;
    }

    // ─────────────────────────────────────────────────────────────────
    //  PUBLIC API — called by networking layer
    // ─────────────────────────────────────────────────────────────────

    public void onRemoteOperationReceived(BlockId blockId, Operation op) {
        Platform.runLater(() -> {
            applyingRemote = true;
            controller.applyRemoteCharOperation(blockId, op);

            BlockNode caretBlock = controller.getDocument().findBlock(currentBlockId);
            if (caretBlock == null || caretBlock.deleted) {
                currentBlockId = blockId;
                caretPos = controller.getDocument().findBlock(currentBlockId)
                        .CharacterCRDT.getVisibleCharacters().size();
            } else {
                int size = caretBlock.CharacterCRDT.getVisibleCharacters().size();
                caretPos = Math.min(caretPos, size);
            }

            refreshEditor(caretPos);
            applyingRemote = false;
        });
    }

    public void onRemoteBlockOperationReceived(BlockOperation op) {
        Platform.runLater(() -> {
            controller.applyRemoteBlockOperation(op);
            ensureCurrentBlockAvailable();
            refreshEditor();
        });
    }

    public void onUserJoined(String joinedUsername, String color) {
        Platform.runLater(() -> {
            activeUsers.put(joinedUsername, color);
            refreshUsersList();
            setStatus("● " + joinedUsername + " joined", "#27ae60");
        });
    }

    public void onUserLeft(String leftUsername) {
        Platform.runLater(() -> {
            activeUsers.remove(leftUsername);
            remoteCursors.remove(leftUsername);
            refreshUsersList();
            refreshEditorWithoutBroadcast();
            setStatus("● " + leftUsername + " left", "#e67e22");
        });
    }


    public void onDisconnected() {
        Platform.runLater(() -> setStatus("● Disconnected", "#e74c3c"));
    }

    public void onConnected() {
        Platform.runLater(() -> setStatus("● Connected", "#27ae60"));
    }

    public void onSessionAccepted() {
        Platform.runLater(() -> {
            if (pendingJoinFlow) {
                pendingJoinFlow = false;
                showEditorScreen();
            }
        });
    }

    public void onRemoteCursorUpdated(String uname, String color, String blockIdValue, int remoteCaretPos) {
        if (uname == null || uname.isBlank() || uname.equals(username)) return;
        BlockId parsed;
        try {
            parsed = BlockId.fromString(blockIdValue);
        } catch (Exception e) {
            return;
        }
        Platform.runLater(() -> {
            remoteCursors.put(uname, new RemoteCursor(uname, color, parsed, remoteCaretPos));
            refreshEditorWithoutBroadcast();
        });
    }

    public void onSessionJoinRejected(String reason) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Session not found");
            alert.setHeaderText("Unable to join session");
            alert.setContentText(reason == null || reason.isBlank()
                    ? "There is no session with this ID."
                    : reason);
            alert.showAndWait();
            if (clientConnection != null) clientConnection.disconnect();
            activeUsers.clear();
            String rememberedUser = username == null ? "" : username;
            showJoinSessionScreen(rememberedUser, lastJoinAttemptedId, "Wrong session ID. Please re-enter a valid copied ID.");
        });
    }

    // ─────────────────────────────────────────────────────────────────
    //  GETTERS
    // ─────────────────────────────────────────────────────────────────

    public String  getSessionId()      { return sessionId; }
    public String  getUsername()       { return username; }
    public String  getUserColor()      { return userColor; }
    public int     getUserId()         { return userId; }
    public BlockId getCurrentBlockId() { return currentBlockId; }
    public CollaborativeDocumentController getController() { return controller; }
    public void setReadOnlyMode(boolean readOnlyMode) { this.readOnlyMode = readOnlyMode; }

    // ─────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────


    private boolean ensureCurrentBlockAvailable() {
        if (controller == null) return false;

        BlockNode current = (currentBlockId == null) ? null : controller.getDocument().findBlock(currentBlockId);
        if (current != null && !current.deleted) {
            int size = current.CharacterCRDT.getVisibleCharacters().size();
            caretPos = Math.max(0, Math.min(caretPos, size));
            return true;
        }

        List<BlockNode> blocks = controller.getDocument().getVisibleBlocks();
        if (!blocks.isEmpty()) {
            currentBlockId = blocks.get(0).id;
            caretPos = Math.min(caretPos, blocks.get(0).CharacterCRDT.getVisibleCharacters().size());
            clearSelection();
            return true;
        }

        currentBlockId = controller.createFirstBlock();
        caretPos = 0;
        clearSelection();
        return currentBlockId != null;
    }

    private void setStatus(String text, String hexColor) {
        if (statusLabel != null) {
            statusLabel.setText(text);
            statusLabel.setStyle("-fx-text-fill: " + hexColor + "; -fx-font-size: 12px;");
        }
    }

    private String toolbarBtnStyle(boolean active) {
        return active
                ? "-fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-font-size: 13px; -fx-min-width: 32px; -fx-min-height: 28px; " +
                "-fx-background-radius: 4; -fx-cursor: hand;"
                : "-fx-background-color: white; -fx-text-fill: #333; " +
                "-fx-border-color: #ccc; -fx-border-radius: 4; " +
                "-fx-font-size: 13px; -fx-min-width: 32px; -fx-min-height: 28px; " +
                "-fx-background-radius: 4; -fx-cursor: hand;";
    }


    private void copyToClipboard(String value) {
        if (value == null || value.isBlank()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private String generateSessionId() {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int idx = ThreadLocalRandom.current().nextInt(chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }

    private String generateDistinctSessionId(String existingId) {
        String id;
        do {
            id = generateSessionId();
        } while (id.equals(existingId));
        return id;
    }
    private String randomColor(int seed) {
        String[] colors = {
                "#e74c3c", "#3498db", "#2ecc71", "#9b59b6",
                "#f39c12", "#1abc9c", "#e67e22", "#34495e"
        };
        return colors[Math.abs(seed) % colors.length];
    }

    private boolean isNonPrintable(KeyCode code) {
        return code == KeyCode.SHIFT   || code == KeyCode.CONTROL ||
                code == KeyCode.ALT    || code == KeyCode.META     ||
                code == KeyCode.CAPS   || code == KeyCode.TAB      ||
                code == KeyCode.ESCAPE || code == KeyCode.UP       ||
                code == KeyCode.DOWN   || code == KeyCode.LEFT     ||
                code == KeyCode.RIGHT  || code == KeyCode.HOME     ||
                code == KeyCode.END    || code == KeyCode.PAGE_UP  ||
                code == KeyCode.PAGE_DOWN || code.isFunctionKey();
    }
}
