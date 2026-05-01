package Network;

import com.google.gson.Gson;
import crdt.*;

import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;


public class ClientConnection extends TextWebSocketHandler {

    private WebSocketSession session;
    private final Gson gson = new Gson();
    private final CollaborativeDocumentController controller;
    private final ui.EditorUI editorUI;
    private final String serverUrl;
    private final String sessionId;

    public ClientConnection(CollaborativeDocumentController controller,
                            ui.EditorUI editorUI,
                            String serverUrl,
                            String sessionId) {
        this.controller = controller;
        this.editorUI   = editorUI;
        this.serverUrl  = serverUrl;
        this.sessionId = sessionId;
    }

    public void connect() {
        try {
            WebSocketClient client = new StandardWebSocketClient();
            client.execute(this, serverUrl).get();
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
                System.out.println("Disconnected cleanly");
            } catch (IOException e) {
                System.err.println("Error closing session: " + e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.session = session;
        System.out.println("Connected to server via Spring WebSocket");
        editorUI.onConnected();
        sendPresence("JOIN");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            System.out.println("CLIENT RECEIVED: " + payload);

            MessageWrapper wrapper = gson.fromJson(payload, MessageWrapper.class);

            if ("CHAR".equals(wrapper.kind)) {
                Operation op = gson.fromJson(gson.toJson(wrapper.data), Operation.class);
                BlockId targetBlock = BlockId.fromString(wrapper.blockId);
                editorUI.onRemoteOperationReceived(targetBlock, op);
            } else if ("BLOCK".equals(wrapper.kind)) {
                BlockOperation op = gson.fromJson(gson.toJson(wrapper.data), BlockOperation.class);
                editorUI.onRemoteBlockOperationReceived(op);
            }
            else if ("PRESENCE".equals(wrapper.kind)) {
                Map<?, ?> event = gson.fromJson(gson.toJson(wrapper.data), Map.class);
                String action = String.valueOf(event.get("action"));
                String username = String.valueOf(event.get("username"));
                String color = String.valueOf(event.get("color"));

                if ("JOIN".equals(action)) {
                    editorUI.onUserJoined(username, color);
                } else if ("LEAVE".equals(action)) {
                    editorUI.onUserLeft(username);
                }
            }

        } catch (Exception e) {
            System.err.println("Error parsing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("Disconnected from server. Status: " + status);
        this.session = null;
        editorUI.onDisconnected();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable throwable) {
        System.err.println("WebSocket Error: " + throwable.getMessage());
        throwable.printStackTrace();
        editorUI.onDisconnected();
    }

    public void sendOperation(Operation op, BlockId blockId) {
        if (session != null && session.isOpen()) {
            try {
                MessageWrapper wrapper = new MessageWrapper("CHAR", op, blockId.toString(), sessionId);
                String json = gson.toJson(wrapper);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                System.err.println("Failed to send CHAR operation: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Cannot send — not connected");
        }
    }

    public void sendBlockOperation(BlockOperation op, BlockId blockId) {
        if (session != null && session.isOpen()) {
            try {
                MessageWrapper wrapper = new MessageWrapper("BLOCK", op, blockId.toString(), sessionId);
                String json = gson.toJson(wrapper);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                System.err.println("Failed to send BLOCK operation: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Cannot send — not connected");
        }
    }

    public boolean isConnected() {
        return session != null && session.isOpen();
    }
    private void sendPresence(String action) {
        if (session == null || !session.isOpen()) return;
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("action", action);
            payload.put("username", editorUI.getUsername());
            payload.put("color", editorUI.getUserColor());
            payload.put("sessionId", sessionId);
            MessageWrapper wrapper = new MessageWrapper("PRESENCE", payload, "", sessionId);
            session.sendMessage(new TextMessage(gson.toJson(wrapper)));
        } catch (IOException e) {
            System.err.println("Failed to send presence update: " + e.getMessage());
        }
    }
}
