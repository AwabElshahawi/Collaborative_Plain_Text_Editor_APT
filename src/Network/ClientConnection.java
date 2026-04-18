//package Network;
//
//import com.google.gson.Gson;
//import crdt.*;
//
//import javax.websocket.*;
//import java.net.URI;
//
//@ClientEndpoint
//
//public class ClientConnection {
//
//
//    private Session session;
//    private final Gson gson = new Gson();
//
//    private final CollaborativeDocumentController controller;
//    private final BlockId currentBlockId;
//
//    public ClientConnection(CollaborativeDocumentController controller, BlockId blockId) {
//        this.controller = controller;
//        this.currentBlockId = blockId;
//    }
//
//    // =========================
//    // CONNECT
//    // =========================
//    public void connect() {
//        try {
//            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
//            container.connectToServer(this, URI.create("ws://localhost:8080/ws"));
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    @OnOpen
//    public void onOpen(Session session) {
//        this.session = session;
//        System.out.println("Connected to server");
//    }
//
//    // =========================
//    // RECEIVE
//    // =========================
//    @OnMessage
//    public void onMessage(String message) {
//        try {
//            MessageWrapper wrapper = gson.fromJson(message, MessageWrapper.class);
//
//            if ("CHAR".equals(wrapper.kind)) {
//                Operation op = gson.fromJson(gson.toJson(wrapper.data), Operation.class);
//                controller.applyRemoteCharOperation(currentBlockId, op);
//            }
//
//            else if ("BLOCK".equals(wrapper.kind)) {
//                BlockOperation op = gson.fromJson(gson.toJson(wrapper.data), BlockOperation.class);
//                controller.applyRemoteBlockOperation(op);
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    @OnClose
//    public void onClose(Session session) {
//        System.out.println("Disconnected");
//    }
//
//    @OnError
//    public void onError(Session session, Throwable throwable) {
//        throwable.printStackTrace();
//    }
//
//    // =========================
//    // SEND CHAR OP
//    // =========================
//    public void sendOperation(Operation op) {
//        try {
//            MessageWrapper wrapper = new MessageWrapper("CHAR", op);
//            String json = gson.toJson(wrapper);
//            session.getAsyncRemote().sendText(json);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    // =========================
//    // SEND BLOCK OP
//    // =========================
//    public void sendBlockOperation(BlockOperation op) {
//        try {
//            MessageWrapper wrapper = new MessageWrapper("BLOCK", op);
//            String json = gson.toJson(wrapper);
//            session.getAsyncRemote().sendText(json);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//}
package Network;

import com.google.gson.Gson;
import crdt.*;

import jakarta.websocket.*; // Switched from javax to jakarta
import java.net.URI;

@ClientEndpoint
public class ClientConnection {

    private Session session;
    private final Gson gson = new Gson();

    private final CollaborativeDocumentController controller;
    private final BlockId currentBlockId;

    public ClientConnection(CollaborativeDocumentController controller, BlockId blockId) {
        this.controller = controller;
        this.currentBlockId = blockId;
    }

    public void connect() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            // Note: If using the DummyServer below, the URI is /ws/ws
            container.connectToServer(this, URI.create("ws://localhost:8080/ws/ws"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println("Connected to server via Jakarta WebSocket");


    }


    @OnMessage
    public void onMessage(String message) {
        try {
            System.out.println("CLIENT RECEIVED DATA FROM SERVER: " + message);
            MessageWrapper wrapper = gson.fromJson(message, MessageWrapper.class);

            if ("CHAR".equals(wrapper.kind)) {
                Operation op = gson.fromJson(gson.toJson(wrapper.data), Operation.class);
                controller.applyRemoteCharOperation(currentBlockId, op);
            } else if ("BLOCK".equals(wrapper.kind)) {
                BlockOperation op = gson.fromJson(gson.toJson(wrapper.data), BlockOperation.class);
                controller.applyRemoteBlockOperation(op);
            }

        } catch (Exception e) {
            System.err.println("Error parsing message: " + e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session) {
        System.out.println("Disconnected from server");
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("WebSocket Error: " + throwable.getMessage());
        throwable.printStackTrace();
    }


    public void sendOperation(Operation op) {
        if (session != null && session.isOpen()) {
            MessageWrapper wrapper = new MessageWrapper("CHAR", op);
            session.getAsyncRemote().sendText(gson.toJson(wrapper));
        }
    }


    public void sendBlockOperation(BlockOperation op) {
        try {
            MessageWrapper wrapper = new MessageWrapper("BLOCK", op);
            String json = gson.toJson(wrapper);
            session.getAsyncRemote().sendText(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
