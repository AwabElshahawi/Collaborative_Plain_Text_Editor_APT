package Network;

import com.google.gson.Gson;
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
    private final BlockId currentBlockId;

    public ClientConnection(CollaborativeDocumentController controller, BlockId blockId) {
        this.controller     = controller;
        this.currentBlockId = blockId;
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

            MessageWrapper wrapper = gson.fromJson(payload, MessageWrapper.class);

            if ("CHAR".equals(wrapper.kind)) {
                Operation op = gson.fromJson(gson.toJson(wrapper.data), Operation.class);
                controller.applyRemoteCharOperation(currentBlockId, op);
            } else if ("BLOCK".equals(wrapper.kind)) {
                BlockOperation op = gson.fromJson(gson.toJson(wrapper.data), BlockOperation.class);
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

    public void sendOperation(Operation op) {
        if (session != null && session.isOpen()) {
            try {
                MessageWrapper wrapper = new MessageWrapper("CHAR", op);
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


    public void sendBlockOperation(BlockOperation op) {
        if (session != null && session.isOpen()) {
            try {
                MessageWrapper wrapper = new MessageWrapper("BLOCK", op);
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
}