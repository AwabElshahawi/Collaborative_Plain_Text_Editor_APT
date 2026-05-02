import Database.DatabaseManager;
import crdt.*;

public class TestDatabase {
    public static void main(String[] args) {
        DatabaseManager db = new DatabaseManager();

        BlockCRDT originalCrdt = new BlockCRDT();
        BlockId firstBlockId = new BlockId(1, "00:01");
        originalCrdt.insertBlock(BlockOperation.insert(firstBlockId, originalCrdt.getRootId()));

        BlockNode node = originalCrdt.findBlock(firstBlockId);
        node.CharacterCRDT.insertText("Hello Database!", new LocalClock(1));

        System.out.println("Original Text: " + originalCrdt.getDocumentText());

        System.out.println("Saving to database...");
        db.saveDocument("doc_123", "Test Project", originalCrdt);
        db.createSharingCode("EDIT-ME", "doc_123", "EDITOR");

        System.out.println("Loading from database...");
        BlockCRDT loadedCrdt = db.loadDocument("doc_123");

        if (loadedCrdt != null) {
            System.out.println("Loaded Text: " + loadedCrdt.getDocumentText());

            if (loadedCrdt.getDocumentText().equals(originalCrdt.getDocumentText())) {
                System.out.println("SUCCESS: CRDT Data persisted correctly!");
            } else {
                System.out.println("FAILURE: Data mismatch!");
            }
        } else {
            System.out.println("FAILURE: Could not load document!");
        }

        var session = db.validateCode("EDIT-ME");
        if (session != null && session.get("role").equals("EDITOR")) {
            System.out.println("SUCCESS: Sharing code validated!");
        } else {
            System.out.println("FAILURE: Sharing code invalid!");
        }
    }
}
