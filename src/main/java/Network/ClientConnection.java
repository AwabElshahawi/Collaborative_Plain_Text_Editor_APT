package Network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import crdt.*;

import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ExecutionException;


public class ClientConnection extends TextWebSocketHandler {

    private WebSocketSession session;
    private final Gson gson = new Gson();

    private final CollaborativeDocumentController controller;

    public ClientConnection(CollaborativeDocumentController controller) {
        this.controller = controller;
    }

    public void connect() {
        try {
            WebSocketClient client = new StandardWebSocketClient();
            client.execute(this, "ws://localhost:8080/ws").get();
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
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            System.out.println("CLIENT RECEIVED DATA FROM SERVER: " + payload);

            // Parse the raw JSON directly — no double-conversion via Object/LinkedTreeMap
            JsonObject wrapper = JsonParser.parseString(payload).getAsJsonObject();
            String kind        = wrapper.get("kind").getAsString();
            JsonElement data   = wrapper.get("data");
            String blockId     = wrapper.get("blockId").getAsString();

            if ("CHAR".equals(kind)) {
                // Direct deserialization from JsonElement preserves char correctly
                Operation op = gson.fromJson(data, Operation.class);
                BlockId id   = BlockId.fromString(blockId);
                controller.applyRemoteCharOperation(id, op);

            } else if ("BLOCK".equals(kind)) {
                BlockOperation op = gson.fromJson(data, BlockOperation.class);
                controller.applyRemoteBlockOperation(op);
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
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable throwable) {
        System.err.println("WebSocket Error: " + throwable.getMessage());
        throwable.printStackTrace();
    }

    public void sendOperation(Operation op, BlockId blockId) {
        if (session != null && session.isOpen()) {
            try {
                // Serialize op directly to JsonElement — no Object wrapper needed
                JsonObject wrapper = new JsonObject();
                wrapper.addProperty("kind", "CHAR");
                wrapper.add("data", gson.toJsonTree(op));
                wrapper.addProperty("blockId", blockId.toString());

                session.sendMessage(new TextMessage(wrapper.toString()));
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
                JsonObject wrapper = new JsonObject();
                wrapper.addProperty("kind", "BLOCK");
                wrapper.add("data", gson.toJsonTree(op));
                wrapper.addProperty("blockId", blockId.toString());

                session.sendMessage(new TextMessage(wrapper.toString()));
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
}