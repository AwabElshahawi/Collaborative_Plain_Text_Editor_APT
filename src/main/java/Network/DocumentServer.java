package Network;

import com.google.gson.Gson;
import crdt.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

@Component
public class DocumentServer extends TextWebSocketHandler {
    private static final Set<WebSocketSession> openSessions = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, JsonObject> presenceBySessionId = Collections.synchronizedMap(new HashMap<>());
    private final Gson gson = new Gson();
    @Override
    public void afterConnectionEstablished(WebSocketSession session){
        openSessions.add(session);
        System.out.println("Session opened: " + session.getId());
        System.out.println("Total Clients: " + openSessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status){
        JsonObject departed = presenceBySessionId.remove(session.getId());
        if (departed != null) {
            JsonObject leavePayload = new JsonObject();
            leavePayload.addProperty("action", "LEAVE");
            leavePayload.addProperty("username", departed.get("username").getAsString());
            leavePayload.addProperty("color", departed.get("color").getAsString());
            String targetSessionId = departed.has("sessionId") ? departed.get("sessionId").getAsString() : "";
            broadcast(new MessageWrapper("PRESENCE", leavePayload, "", targetSessionId), session.getId(), targetSessionId);
        }
        openSessions.remove(session);
        System.out.println("Session closed: " + session.getId());
        System.out.println("Total Clients: " + openSessions.size());
    }
    @Override
    public void handleTransportError(WebSocketSession session , Throwable throwable){
        openSessions.remove(session);
        System.err.println("Transport Error: " + throwable.getMessage());
        throwable.printStackTrace();
    }
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message){
        try{
            String msg = message.getPayload();
            JsonObject wrapper = JsonParser.parseString(msg).getAsJsonObject();
            String kind = wrapper.get("kind").getAsString();
            JsonElement data = wrapper.get("data");
            System.out.println("Received Message[ " + kind + " ]: " + data);

            if ("PRESENCE".equals(kind)) {
                JsonObject presence = data.getAsJsonObject();
                String action = presence.get("action").getAsString();
                if ("JOIN".equals(action)) {
                    presenceBySessionId.put(session.getId(), presence);

                    synchronized (presenceBySessionId) {
                        for (Map.Entry<String, JsonObject> entry : presenceBySessionId.entrySet()) {
                            if (entry.getKey().equals(session.getId())) continue;
                            JsonObject existing = entry.getValue();
                            JsonObject existingJoin = new JsonObject();
                            existingJoin.addProperty("action", "JOIN");
                            existingJoin.addProperty("username", existing.get("username").getAsString());
                            existingJoin.addProperty("color", existing.get("color").getAsString());
                            String targetSessionId = existing.has("sessionId") ? existing.get("sessionId").getAsString() : "";
                            if (!targetSessionId.equals(presence.get("sessionId").getAsString())) continue;
                            session.sendMessage(new TextMessage(gson.toJson(new MessageWrapper("PRESENCE", existingJoin, "", targetSessionId))));
                        }
                    }
                }
            }

            String targetSessionId = wrapper.has("sessionId") ? wrapper.get("sessionId").getAsString() : "";
            broadcastRaw(msg, session.getId(), targetSessionId);
        }
        catch (Exception e){
            System.err.println("Error Handling Message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void broadcast(MessageWrapper wrapper, String senderSessionId, String targetSessionId) {
        broadcastRaw(gson.toJson(wrapper), senderSessionId, targetSessionId);
    }

    private void broadcastRaw(String payload, String senderSessionId, String targetSessionId) {
        synchronized (openSessions){
            for  (WebSocketSession s : openSessions){
                if((!s.getId().equals(senderSessionId)) && s.isOpen()){
                    JsonObject presence = presenceBySessionId.get(s.getId());
                    if (targetSessionId != null && !targetSessionId.isEmpty()) {
                        if (presence == null || !targetSessionId.equals(presence.get("sessionId").getAsString())) continue;
                    }
                    try {
                        s.sendMessage(new TextMessage(payload));
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
