/*


import crdt.*;
import Network.ClientConnection;

import java.util.Scanner;

public class TestClient {
    public static void main(String[] args) {
        CollaborativeDocumentController controller = new CollaborativeDocumentController(1);

        BlockId firstBlockId = controller.createFirstBlock();

        ClientConnection client = new ClientConnection(controller, firstBlockId);
        client.connect();

        System.out.println("Client is active. Press Enter to disconnect...");
        new Scanner(System.in).nextLine();

        client.disconnect();
    }
}*/
