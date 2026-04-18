package main.java;

import main.java.Network.ClientConnection;
import main.java.crdt.BlockId;
import main.java.crdt.CollaborativeDocumentController;

public class TestClient {
    public static void main(String[] args) {
        CollaborativeDocumentController controller = new CollaborativeDocumentController(1);

        BlockId firstBlockId = controller.createFirstBlock();
        ClientConnection client = new ClientConnection(controller, firstBlockId);

        client.connect();
        System.out.println("Client is active. Press Enter in this console to disconnect...");
        new java.util.Scanner(System.in).nextLine();
        client.disconnect();
    }
}