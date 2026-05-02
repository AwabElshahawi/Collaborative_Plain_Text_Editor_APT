//package Network;
//
//import Database.DatabaseManager;
//import com.google.gson.Gson;
//import crdt.*;
//import com.google.gson.JsonObject;
//import com.google.gson.JsonParser;
//import com.google.gson.JsonElement;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.*;
//import org.springframework.web.socket.handler.TextWebSocketHandler;
//import java.util.Collections;
//import java.util.HashSet;
//import java.util.Map;
//import java.util.HashMap;
//import java.util.Set;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Component
//public class DocumentServer extends TextWebSocketHandler {
//    private static final Set<WebSocketSession> openSessions = Collections.synchronizedSet(new HashSet<>());
//    private static final Map<String, JsonObject> presenceBySessionId = Collections.synchronizedMap(new HashMap<>());
//    private static final Set<String> knownDocumentSessions = Collections.synchronizedSet(new HashSet<>());
//    private final Gson gson = new Gson();
//    private final DatabaseManager dbManager = new DatabaseManager();
//    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
//    private static final Map<String, JsonObject> sessionDetails = new ConcurrentHashMap<>();
//
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session){
//        openSessions.add(session);
//        System.out.println("Session opened: " + session.getId());
//        System.out.println("Total Clients: " + openSessions.size());
//    }
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status){
//        JsonObject departed = presenceBySessionId.remove(session.getId());
//        if (departed != null) {
//            JsonObject leavePayload = new JsonObject();
//            leavePayload.addProperty("action", "LEAVE");
//            leavePayload.addProperty("username", departed.get("username").getAsString());
//            leavePayload.addProperty("color", departed.get("color").getAsString());
//            String targetSessionId = departed.has("sessionId") ? departed.get("sessionId").getAsString() : "";
//            broadcast(new MessageWrapper("PRESENCE", leavePayload, "", targetSessionId), session.getId(), targetSessionId);
//        }
//        openSessions.remove(session);
//        System.out.println("Session closed: " + session.getId());
//        System.out.println("Total Clients: " + openSessions.size());
//    }
//    @Override
//    public void handleTransportError(WebSocketSession session , Throwable throwable){
//        openSessions.remove(session);
//        System.err.println("Transport Error: " + throwable.getMessage());
//        throwable.printStackTrace();
//    }
//    @Override
//    public void handleTextMessage(WebSocketSession session, TextMessage message){
//        try{
//            String msg = message.getPayload();
//            JsonObject wrapper = JsonParser.parseString(msg).getAsJsonObject();
//            String kind = wrapper.get("kind").getAsString();
//            JsonElement data = wrapper.get("data");
//            System.out.println("Received Message[ " + kind + " ]: " + data);
//
//            if ("PRESENCE".equals(kind)) {
//                JsonObject presence = data.getAsJsonObject();
//                String action = presence.get("action").getAsString();
//                if ("JOIN".equals(action)) {
//                    String targetSessionId = presence.has("sessionId") ? presence.get("sessionId").getAsString() : "";
//                    String joinType = presence.has("joinType") ? presence.get("joinType").getAsString() : "JOIN";
//                    if ("CREATE".equals(joinType)) {
//                        knownDocumentSessions.add(targetSessionId);
//                    } else if (!knownDocumentSessions.contains(targetSessionId)) {
//                        JsonObject reject = new JsonObject();
//                        reject.addProperty("action", "REJECT");
//                        reject.addProperty("reason", "Wrong session ID. Please paste a valid session ID.");
//                        session.sendMessage(new TextMessage(gson.toJson(new MessageWrapper("SESSION", reject, "", targetSessionId))));
//                        return;
//                    }
//                    JsonObject accept = new JsonObject();
//                    accept.addProperty("action", "ACCEPT");
//                    session.sendMessage(new TextMessage(gson.toJson(new MessageWrapper("SESSION", accept, "", targetSessionId))));
//
//                    presenceBySessionId.put(session.getId(), presence);
//
//                    synchronized (presenceBySessionId) {
//                        for (Map.Entry<String, JsonObject> entry : presenceBySessionId.entrySet()) {
//                            if (entry.getKey().equals(session.getId())) continue;
//                            JsonObject existing = entry.getValue();
//                            JsonObject existingJoin = new JsonObject();
//                            existingJoin.addProperty("action", "JOIN");
//                            existingJoin.addProperty("username", existing.get("username").getAsString());
//                            existingJoin.addProperty("color", existing.get("color").getAsString());
//                            String existingSessionId = existing.has("sessionId") ? existing.get("sessionId").getAsString() : "";
//                            if (!existingSessionId.equals(presence.get("sessionId").getAsString())) continue;
//                            session.sendMessage(new TextMessage(gson.toJson(new MessageWrapper("PRESENCE", existingJoin, "", existingSessionId))));
//                        }
//                    }
//                }
//            }
//
//            String targetSessionId = wrapper.has("sessionId") ? wrapper.get("sessionId").getAsString() : "";
//            broadcastRaw(msg, session.getId(), targetSessionId);
//        }
//        catch (Exception e){
//            System.err.println("Error Handling Message: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//    private void broadcast(MessageWrapper wrapper, String senderSessionId, String targetSessionId) {
//        broadcastRaw(gson.toJson(wrapper), senderSessionId, targetSessionId);
//    }
//
//    private void broadcastRaw(String payload, String senderSessionId, String targetSessionId) {
//        synchronized (openSessions){
//            for  (WebSocketSession s : openSessions){
//                if((!s.getId().equals(senderSessionId)) && s.isOpen()){
//                    JsonObject presence = presenceBySessionId.get(s.getId());
//                    if (targetSessionId != null && !targetSessionId.isEmpty()) {
//                        if (presence == null || !targetSessionId.equals(presence.get("sessionId").getAsString())) continue;
//                    }
//                    try {
//                        s.sendMessage(new TextMessage(payload));
//                    } catch (Exception ignored) {}
//                }
//            }
//        }
//    }
//
//}
package Network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import crdt.BlockCRDT;
import Database.DatabaseManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DocumentServer extends TextWebSocketHandler {

    private final Gson gson = new Gson();

    private final DatabaseManager dbManager = new DatabaseManager();

    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private static final Map<String, JsonObject> sessionDetails = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        System.out.println("Connection established: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        JsonObject details = sessionDetails.remove(session.getId());
        if (details != null) {
            String username = details.get("username").getAsString();
            String docId = details.get("docId").getAsString();

            JsonObject leavePayload = new JsonObject();
            leavePayload.addProperty("action", "LEAVE");
            leavePayload.addProperty("username", username);

            broadcastToDocument(gson.toJson(new MessageWrapper("PRESENCE", leavePayload, "", "")),
                    session.getId(), docId);
        }
        sessions.remove(session.getId());
        System.out.println("Connection closed: " + session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String msg = message.getPayload();
            JsonObject wrapper = JsonParser.parseString(msg).getAsJsonObject();
            String kind = wrapper.get("kind").getAsString();
            JsonElement data = wrapper.get("data");

            if ("PRESENCE".equals(kind)) {
                JsonObject presence = data.getAsJsonObject();
                if ("JOIN".equals(presence.get("action").getAsString())) {
                    String code = presence.has("sessionId") ? presence.get("sessionId").getAsString() : "";

                    Map<String, String> dbInfo = dbManager.validateCode(code);

                    if (dbInfo == null) {
                        sendReject(session, "Invalid sharing code. Please check and try again.");
                        return;
                    }

                    String docId = dbInfo.get("doc_id");
                    String role = dbInfo.get("role");

                    presence.addProperty("role", role);
                    presence.addProperty("docId", docId);
                    sessionDetails.put(session.getId(), presence);

                    JsonObject accept = new JsonObject();
                    accept.addProperty("action", "ACCEPT");
                    session.sendMessage(new TextMessage(gson.toJson(new MessageWrapper("SESSION", accept, "", code))));

                    broadcastToDocument(msg, session.getId(), docId);

                    sendExistingUsersToNewcomer(session, docId);
                    return;
                }
            }

            JsonObject details = sessionDetails.get(session.getId());
            if (details == null) return;

            String userRole = details.get("role").getAsString();
            String userDocId = details.get("docId").getAsString();

            if (("CHAR".equals(kind) || "BLOCK".equals(kind)) && "VIEWER".equals(userRole)) {
                System.out.println("BLOCKED: Viewer " + details.get("username") + " tried to edit.");
                return;
            }

            broadcastToDocument(msg, session.getId(), userDocId);

        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
        }
    }


    private void broadcastToDocument(String payload, String senderId, String docId) {
        sessions.forEach((id, session) -> {
            if (!id.equals(senderId) && session.isOpen()) {
                JsonObject details = sessionDetails.get(id);
                if (details != null && details.get("docId").getAsString().equals(docId)) {
                    try {
                        session.sendMessage(new TextMessage(payload));
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void sendExistingUsersToNewcomer(WebSocketSession session, String docId) throws Exception {
        for (Map.Entry<String, JsonObject> entry : sessionDetails.entrySet()) {
            if (entry.getKey().equals(session.getId())) continue;

            JsonObject otherUser = entry.getValue();
            if (otherUser.get("docId").getAsString().equals(docId)) {
                JsonObject joinMsg = new JsonObject();
                joinMsg.addProperty("action", "JOIN");
                joinMsg.addProperty("username", otherUser.get("username").getAsString());
                joinMsg.addProperty("color", otherUser.get("color").getAsString());

                session.sendMessage(new TextMessage(gson.toJson(new MessageWrapper("PRESENCE", joinMsg, "", ""))));
            }
        }
    }

    private void sendReject(WebSocketSession session, String reason) throws Exception {
        JsonObject reject = new JsonObject();
        reject.addProperty("action", "REJECT");
        reject.addProperty("reason", reason);
        session.sendMessage(new TextMessage(gson.toJson(new MessageWrapper("SESSION", reject, "", ""))));
    }
}