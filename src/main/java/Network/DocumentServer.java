package Network;

import com.google.gson.Gson;
import crdt.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class DocumentServer extends TextWebSocketHandler {
    private static final Set<WebSocketSession> openSessions = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void afterConnectionEstablished(WebSocketSession session){
            openSessions.add(session);
            System.out.println("Session opened: " + session.getId());
            System.out.println("Total Clients: " + openSessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status){
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
            String kind = wrapper.get("Kind").getAsString();
            Object data = wrapper.get("Data");
            System.out.println("Received Message[ " + kind + " ]: " + data);

            synchronized (openSessions){
                for  (WebSocketSession s : openSessions){
                    if((!s.getId().equals(session.getId())) && s.isOpen()){
                        s.sendMessage(new TextMessage(msg));
                    }
                }
            }
        }
        catch (Exception e){
            System.err.println("Error Handling Message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}