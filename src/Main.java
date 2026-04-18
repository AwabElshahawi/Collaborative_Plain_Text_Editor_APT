import crdt.*;

import Network.ClientConnection;
import crdt.BlockId;
import crdt.CollaborativeDocumentController;

public class Main {
    public static void main(String[] args) {
        CollaborativeDocumentController controller = new CollaborativeDocumentController(1);

        BlockId firstBlockId = controller.createFirstBlock();

        ClientConnection client = new ClientConnection(controller, firstBlockId);

        client.connect();
        System.out.println("Client is active. Press Enter in this console to disconnect...");
        new java.util.Scanner(System.in).nextLine();
    }
}