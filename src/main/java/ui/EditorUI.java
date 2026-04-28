package ui;

import crdt.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
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
    private int userId;

    // ── UI state ─────────────────────────────────────────────────────
    private boolean isBold   = false;
    private boolean isItalic = false;
    private int caretPos = 0;

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

    //Server Connection
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
            activeUsers.put(username, randomColor(userId));

            clientConnection = new Network.ClientConnection(controller);
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

        // ── TextFlow inside a white AnchorPane that fills space ──────
        textFlow = new TextFlow();
        textFlow.setPadding(new Insets(12));
        textFlow.setLineSpacing(2);
        textFlow.setFocusTraversable(true);
        textFlow.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSpecialKeyPress);
        textFlow.addEventFilter(KeyEvent.KEY_TYPED, this::handleTypedCharacter);
        textFlow.setOnMouseClicked(e -> textFlow.requestFocus());

        // AnchorPane makes TextFlow fill the full white area
        AnchorPane editorPane = new AnchorPane(textFlow);
        editorPane.setStyle("-fx-background-color: white;");
        AnchorPane.setTopAnchor(textFlow, 0.0);
        AnchorPane.setLeftAnchor(textFlow, 0.0);
        AnchorPane.setRightAnchor(textFlow, 0.0);
        AnchorPane.setBottomAnchor(textFlow, 0.0);

        // Focus border so user knows editor is active
        textFlow.focusedProperty().addListener((obs, oldVal, focused) -> {
            editorPane.setStyle(focused
                ? "-fx-background-color: white; -fx-border-color: #3498db; -fx-border-width: 2;"
                : "-fx-background-color: white; -fx-border-color: transparent; -fx-border-width: 2;"
            );
        });

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

        // ── Editor + Users side by side ──────────────────────────────
        HBox editorRow = new HBox(scroll, usersPanel);
        HBox.setHgrow(scroll, Priority.ALWAYS);
        VBox.setVgrow(editorRow, Priority.ALWAYS);

        // Make editorPane fill the scroll viewport height
        scroll.viewportBoundsProperty().addListener((obs, oldVal, newVal) ->
            editorPane.setMinHeight(newVal.getHeight())
        );

        // ── Status bar ───────────────────────────────────────────────
        Label statusBar = new Label("Connected as: " + username + "  |  Session: " + sessionId);
        statusBar.setStyle("-fx-font-size: 11px; -fx-text-fill: #888; -fx-padding: 4 12 4 12;");
        HBox bottomBar = new HBox(statusBar);
        bottomBar.setStyle("-fx-background-color: #f1f1f1; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");

        // ── Root layout ──────────────────────────────────────────────
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
        if (controller == null || currentBlockId == null) return;

        KeyCode code = event.getCode();

        if (code == KeyCode.ENTER) {

            int blockSize = controller.getDocument()
                    .findBlock(currentBlockId)
                    .CharacterCRDT
                    .getVisibleCharacters()
                    .size();

            String newClock = String.valueOf(System.currentTimeMillis());
            BlockId newBlockId = new BlockId(userId, newClock);

            BlockOperation op;

            if (caretPos >= blockSize) {
                // caret at end -> create empty new block
                op = controller.localInsertBlock(newBlockId, currentBlockId);
            } else {
                // caret in middle -> split current block
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
        if (code == KeyCode.BACK_SPACE) {
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
                if (blocks.get(i).id.equals(currentBlockId)) {
                    currentIndex = i;
                    break;
                }
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

        if (code == KeyCode.DELETE) {
            Operation op = controller.localDeleteChar(currentBlockId, caretPos + 1);

            if (op != null && clientConnection != null && clientConnection.isConnected()) {
                clientConnection.sendOperation(op, currentBlockId);
            }

            refreshEditor(caretPos);
            event.consume();
            return;
        }

        if (code == KeyCode.LEFT) {
            if (caretPos > 0) {
                caretPos--;
            } else {
                List<BlockNode> blocks = controller.getDocument().getVisibleBlocks();

                for (int i = 0; i < blocks.size(); i++) {
                    if (blocks.get(i).id.equals(currentBlockId)) {
                        if (i > 0) {
                            BlockNode previousBlock = blocks.get(i - 1);
                            currentBlockId = previousBlock.id;
                            caretPos = previousBlock.CharacterCRDT.getVisibleCharacters().size();
                        }
                        break;
                    }
                }
            }

            refreshEditor(caretPos);
            event.consume();
            return;
        }

        if (code == KeyCode.RIGHT) {
            int size = controller.getDocument()
                    .findBlock(currentBlockId)
                    .CharacterCRDT
                    .getVisibleCharacters()
                    .size();

            if (caretPos < size) {
                caretPos++;
            } else {
                List<BlockNode> blocks = controller.getDocument().getVisibleBlocks();

                for (int i = 0; i < blocks.size(); i++) {
                    if (blocks.get(i).id.equals(currentBlockId)) {
                        if (i < blocks.size() - 1) {
                            BlockNode nextBlock = blocks.get(i + 1);
                            currentBlockId = nextBlock.id;
                            caretPos = 0;
                        }
                        break;
                    }
                }
            }

            refreshEditor(caretPos);
            event.consume();
            return;
        }
    }

    private void handleTypedCharacter(KeyEvent event) {
        if (suppressListener || applyingRemote) return;
        if (controller == null || currentBlockId == null) return;

        String ch = event.getCharacter();
        if (ch == null || ch.isEmpty()) return;

        char c = ch.charAt(0);

        if (c == '\b' || c == 127 || c == '\r' || c == '\n') {
            event.consume();
            return;
        }

        Operation op = controller.localInsertChar(currentBlockId, caretPos, c, isBold, isItalic);

        if (op != null && clientConnection != null && clientConnection.isConnected()) {
            clientConnection.sendOperation(op, currentBlockId);
        }

        caretPos++;
        refreshEditor(caretPos);
        event.consume();
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
            BlockNode block = blocks.get(b);
            List<CharacterNode> visible = block.CharacterCRDT.getVisibleCharacters();

            boolean isCurrentBlock = block.id.equals(currentBlockId);

            for (int i = 0; i < visible.size(); i++) {
                if (isCurrentBlock && i == caretPos) {
                    textFlow.getChildren().add(makeCaret());
                }

                CharacterNode node = visible.get(i);
                Text t = new Text(String.valueOf(node.value));
                t.setFill(Color.web("#2d3436"));
                t.setFont(Font.font(
                        "Consolas",
                        node.bold ? FontWeight.BOLD : FontWeight.NORMAL,
                        node.italic ? FontPosture.ITALIC : FontPosture.REGULAR,
                        15
                ));
                textFlow.getChildren().add(t);
            }

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

        javafx.animation.FadeTransition blink = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(500), r
        );
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
    //  PUBLIC API — called by Person 2 (networking)
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

    public String getSessionId()  { return sessionId; }
    public String getUsername()   { return username; }
    public int    getUserId()     { return userId; }
    public BlockId getCurrentBlockId() { return currentBlockId; }
    public CollaborativeDocumentController getController() { return controller; }

    // ─────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────

    private void setStatus(String text, String hexColor) {
        if (statusLabel != null) {
            statusLabel.setText(text);
            statusLabel.setStyle("-fx-text-fill: " + hexColor + "; -fx-font-size: 12px;");
        }
    }

    private String toolbarBtnStyle(boolean active) {
        if (active) {
            return "-fx-background-color: #3498db; -fx-text-fill: white; " +
                   "-fx-font-size: 13px; -fx-min-width: 32px; -fx-min-height: 28px; " +
                   "-fx-background-radius: 4; -fx-cursor: hand;";
        } else {
            return "-fx-background-color: white; -fx-text-fill: #333; " +
                   "-fx-border-color: #ccc; -fx-border-radius: 4; " +
                   "-fx-font-size: 13px; -fx-min-width: 32px; -fx-min-height: 28px; " +
                   "-fx-background-radius: 4; -fx-cursor: hand;";
        }
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