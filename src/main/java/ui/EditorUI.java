
package ui;

import crdt.*;
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
import javafx.stage.Stage;

import java.util.*;

public class EditorUI extends Application {

    // ── CRDT Controller ──────────────────────────────────────────────
    private CollaborativeDocumentController controller;
    private BlockId currentBlockId;

    // ── Session info ─────────────────────────────────────────────────
    private String username;
    private String sessionId;
    private String userColor;
    private int userId;

    // ── UI state ─────────────────────────────────────────────────────
    private boolean isBold   = false;
    private boolean isItalic = false;
    private int caretPos = 0;

    // ── Selection state ───────────────────────────────────────────────
    // Selection is always within a single block (cross-block selection not supported yet).
    // selectionAnchorPos / selectionAnchorBlockId = where the selection started
    // selectionStart < selectionEnd = the visual range (character indices, exclusive end)
    private int     selectionAnchorPos     = -1;
    private BlockId selectionAnchorBlockId = null;
    private int     selectionStart         = -1;   // -1 means no selection
    private int     selectionEnd           = -1;
    private BlockId selectionBlockId       = null; // block the selection lives in

    // Active users: username -> color
    private final Map<String, String> activeUsers = new LinkedHashMap<>();

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

    // Server Connection
    private Network.ClientConnection clientConnection;

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
        showJoinScreen();
    }

    @Override
    public void stop() {
        if (clientConnection != null) clientConnection.disconnect();
    }

    // ─────────────────────────────────────────────────────────────────
    //  SCREEN 1 — JOIN SESSION
    // ─────────────────────────────────────────────────────────────────

    private void showJoinScreen() {
        Label title = new Label("Collaborative Text Editor");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label subtitle = new Label("Join or start a session");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #7f8c8d;");

        Label userLabel = new Label("Username");
        userLabel.setStyle("-fx-font-weight: bold;");
        TextField userField = new TextField();
        userField.setPromptText("Enter your name...");
        userField.setStyle("-fx-pref-width: 300px; -fx-pref-height: 36px; -fx-font-size: 14px;");

        Label sessionLabel = new Label("Session / Document ID");
        sessionLabel.setStyle("-fx-font-weight: bold;");
        TextField sessionField = new TextField();
        sessionField.setPromptText("Enter session ID (e.g. doc1)...");
        sessionField.setStyle("-fx-pref-width: 300px; -fx-pref-height: 36px; -fx-font-size: 14px;");

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12px;");

        Button joinBtn = new Button("Join Session");
        joinBtn.setStyle(
                "-fx-background-color: #2ecc71; -fx-text-fill: white; " +
                        "-fx-font-size: 14px; -fx-font-weight: bold; " +
                        "-fx-pref-width: 300px; -fx-pref-height: 40px; " +
                        "-fx-cursor: hand; -fx-background-radius: 6;"
        );

        joinBtn.setOnAction(e -> {
            String uname   = userField.getText().trim();
            String session = sessionField.getText().trim();

            if (uname.isEmpty() || session.isEmpty()) {
                errorLabel.setText("Please fill in both fields.");
                return;
            }

            username       = uname;
            sessionId      = session;
            userId         = Math.abs(uname.hashCode()) % 10000;
            controller     = new CollaborativeDocumentController(userId);
            currentBlockId = controller.createFirstBlock();
            userColor = randomColor(userId);
            activeUsers.put(username, userColor);

            clientConnection = new Network.ClientConnection(controller,this,"ws://localhost:8080/ws");
            clientConnection.connect();

            showEditorScreen();
        });

        sessionField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) joinBtn.fire();
        });

        VBox form = new VBox(10,
                userLabel, userField,
                sessionLabel, sessionField,
                errorLabel, joinBtn
        );
        form.setAlignment(Pos.CENTER_LEFT);
        form.setPadding(new Insets(30));
        form.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 12, 0, 0, 4);"
        );
        form.setMaxWidth(380);

        VBox root = new VBox(16, title, subtitle, form);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: #ecf0f1;");

        primaryStage.setScene(new Scene(root, 520, 480));
        primaryStage.show();
    }

    // ─────────────────────────────────────────────────────────────────
    //  SCREEN 2 — EDITOR
    // ─────────────────────────────────────────────────────────────────

    private void showEditorScreen() {
        primaryStage.setTitle("Editor — " + sessionId + " | " + username);

        // ── Top bar ──────────────────────────────────────────────────
        Label sessionInfo = new Label("Session: " + sessionId);
        statusLabel = new Label("● Connected");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(12, sessionInfo, spacer, statusLabel);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8, 16, 8, 16));
        topBar.setStyle("-fx-background-color: #2c3e50;");
        sessionInfo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: white;");
        statusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 12px;");

        // ── Toolbar ──────────────────────────────────────────────────
        boldBtn = new Button("B");
        boldBtn.setStyle(toolbarBtnStyle(false) + "-fx-font-weight: bold;");
        boldBtn.setOnAction(e -> toggleBold());

        italicBtn = new Button("I");
        italicBtn.setStyle(toolbarBtnStyle(false) + "-fx-font-style: italic;");
        italicBtn.setOnAction(e -> toggleItalic());

        Label formatLabel = new Label("Format:");
        formatLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");

        HBox toolbar = new HBox(8, formatLabel, boldBtn, italicBtn);
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
        Label statusBar = new Label("Connected as: " + username + "  |  Session: " + sessionId);
        statusBar.setStyle("-fx-font-size: 11px; -fx-text-fill: #888; -fx-padding: 4 12 4 12;");
        HBox bottomBar = new HBox(statusBar);
        bottomBar.setStyle("-fx-background-color: #f1f1f1; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");

        VBox root = new VBox(topBar, toolbar, editorRow, bottomBar);
        VBox.setVgrow(editorRow, Priority.ALWAYS);

        Scene scene = new Scene(root, 900, 600);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);

        Platform.runLater(() -> {
            textFlow.requestFocus();
            refreshEditor(0);
        });
    }

    // ─────────────────────────────────────────────────────────────────
    //  KEY HANDLING
    // ─────────────────────────────────────────────────────────────────

    private void handleSpecialKeyPress(KeyEvent event) {
        if (suppressListener || applyingRemote) return;
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
        if (ctrl && code == KeyCode.Z) {
            Operation inverse = controller.undo(currentBlockId);
            if (inverse != null && clientConnection != null && clientConnection.isConnected()) {
                clientConnection.sendOperation(inverse, currentBlockId);
            }
            refreshEditor(caretPos);
            event.consume();
            return;
        }

        if (ctrl && code == KeyCode.Y) {
            Operation redoOp = controller.redo(currentBlockId);
            if (redoOp != null && clientConnection != null && clientConnection.isConnected()) {
                clientConnection.sendOperation(redoOp, currentBlockId);
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
        if (suppressListener || applyingRemote) return;
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

            if (b < blocks.size() - 1) {
                textFlow.getChildren().add(new Text("\n"));
            }
        }

        suppressListener = false;
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
            int size = controller.getDocument().findBlock(currentBlockId)
                    .CharacterCRDT.getVisibleCharacters().size();
            caretPos = Math.min(caretPos, size);
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
            refreshUsersList();
            setStatus("● " + leftUsername + " left", "#e67e22");
        });
    }

    public void onRemoteCursorUpdate(String cursorUsername, String color, int position) {
        Platform.runLater(this::refreshUsersList);
    }

    public void onDisconnected() {
        Platform.runLater(() -> setStatus("● Disconnected", "#e74c3c"));
    }

    public void onConnected() {
        Platform.runLater(() -> setStatus("● Connected", "#27ae60"));
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
