//package Server;
//
//import org.glassfish.tyrus.server.Server;
//
//public class ServerMain {
//
//    public static void main(String[] args) {
//
//        Server server = new Server("localhost", 8080, "/", null, WebSocketServer.class);
//
//        try {
//            server.start();
//            System.out.println("Server started at ws://localhost:8080/ws");
//
//            // keep running
//            Thread.currentThread().join();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            server.stop();
//        }
//    }
//}

package Server;

import org.glassfish.tyrus.server.Server;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ServerMain {

    public static void main(String[] args) {
        // host: localhost, port: 8080, contextPath: "/", endpoint: WebSocketServer
        Server server = new Server("localhost", 8080, "/ws", null, WebSocketServer.class);

        try {
            server.start();
            // The final URL will be ws://localhost:8080/ws/ws
            System.out.println("--- Collaborative Server Started ---");
            System.out.println("WebSocket URL: ws://localhost:8080/ws/ws");
            System.out.println("Press Enter to shutdown the server...");

            // Wait for user input to stop
            new BufferedReader(new InputStreamReader(System.in)).readLine();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            server.stop();
            System.out.println("Server stopped.");
        }
    }
}