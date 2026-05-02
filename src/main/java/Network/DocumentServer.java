package Network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import crdt.BlockCRDT;
import Database.DatabaseManager;
import jakarta.annotation.Nonnull;
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
    public void afterConnectionEstablished(@Nonnull WebSocketSession session) {
        sessions.put(session.getId(), session);
        System.out.println("Connection established: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @Nonnull CloseStatus status) {
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
    public void handleTextMessage(@Nonnull WebSocketSession session, @Nonnull TextMessage message) {
        try {
            String msg = message.getPayload();
            System.out.println("Received message from " + session.getId() + ": " + msg);
            JsonObject wrapper = JsonParser.parseString(msg).getAsJsonObject();
            String kind = wrapper.get("kind").getAsString();
            JsonElement data = wrapper.get("data");

            if ("PRESENCE".equals(kind)) {
                JsonObject presence = data.getAsJsonObject();
                if ("JOIN".equals(presence.get("action").getAsString())) {
                    String code = presence.has("sessionId") ? presence.get("sessionId").getAsString() : "";
                    String joinType = presence.has("joinType") ? presence.get("joinType").getAsString() : "JOIN";

                    Map<String, String> dbInfo = dbManager.validateCode(code);

                    if (dbInfo == null && "CREATE".equalsIgnoreCase(joinType)) {
                        dbManager.saveDocument(code, "Untitled Document", new BlockCRDT());
                        dbManager.createSharingCode(code, code, "EDITOR");
                        dbManager.createSharingCode(code + "-V", code, "VIEWER");
                        dbInfo = dbManager.validateCode(code);
                    }

                        if (dbInfo == null) {
                        sendReject(session);
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

    private void sendReject(WebSocketSession session) throws Exception {
        JsonObject reject = new JsonObject();
        reject.addProperty("action", "REJECT");
        reject.addProperty("reason", "Invalid sharing code. Please check and try again.");
        session.sendMessage(new TextMessage(gson.toJson(new MessageWrapper("SESSION", reject, "", ""))));
    }
}