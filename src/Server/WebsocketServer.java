//package Server;
//
//import javax.websocket.*;
//import javax.websocket.server.ServerEndpoint;
//import java.util.Set;
//import java.util.concurrent.ConcurrentHashMap;
//
//@ServerEndpoint("/ws")
//public class WebSocketServer {
//
//    private static Set<Session> clients = ConcurrentHashMap.newKeySet();
//
//    @OnOpen
//    public void onOpen(Session session) {
//        clients.add(session);
//        System.out.println("Client connected: " + session.getId());
//    }
//
//    @OnMessage
//    public void onMessage(String message, Session sender) {
//        System.out.println("Received: " + message);
//
//        // broadcast to all clients
//        for (Session s : clients) {
//            s.getAsyncRemote().sendText(message);
//        }
//    }
//
//    @OnClose
//    public void onClose(Session session) {
//        clients.remove(session);
//        System.out.println("Client disconnected: " + session.getId());
//    }
//
//    @OnError
//    public void onError(Session session, Throwable error) {
//        error.printStackTrace();
//    }
//}

package Server;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/ws")
public class WebSocketServer {

    // Using a Concurrent Set to store active sessions
    private static final Set<Session> clients = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(Session session) {
        clients.add(session);
        System.out.println("SERVER: Client connected: " + session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session sender) {
        System.out.println("SERVER RECEIVED: " + message);

        // Broadcast the CRDT operation to all clients (including the sender)
        // Note: For Phase 2, relaying to everyone is correct so all editors sync.
        for (Session s : clients) {
            if (s.isOpen()) {
                s.getAsyncRemote().sendText(message);
            }
        }
    }

    @OnClose
    public void onClose(Session session) {
        clients.remove(session);
        System.out.println("SERVER: Client disconnected: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        System.err.println("SERVER ERROR on session " + session.getId());
        error.printStackTrace();
    }
}