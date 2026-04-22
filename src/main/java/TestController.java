package main.java;

import main.java.crdt.*;

public class TestController {

    public static void main(String[] args) {
        testInsertDelete();
        testFormat();
        testSplit();
        testMerge();
        testPaste();
        testRemoteCharOperation();
        testMixedFlow();
    }

    private static void printSection(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

    private static void testInsertDelete() {
        printSection("TEST 1 - INSERT + DELETE");

        CollaborativeDocumentController controller = new CollaborativeDocumentController(1);
        BlockId block = controller.createFirstBlock();

        controller.localInsertChar(block, 0, 'H');
        controller.localInsertChar(block, 1, 'e');
        controller.localInsertChar(block, 2, 'l');
        controller.localInsertChar(block, 3, 'l');
        controller.localInsertChar(block, 4, 'o');

        System.out.println("Expected: Hello");
        System.out.println("Actual  : " + controller.renderText());

        controller.localDeleteChar(block, 5);

        System.out.println("Expected after delete: Hell");
        System.out.println("Actual after delete  : " + controller.renderText());
    }

    private static void testFormat() {
        printSection("TEST 2 - FORMAT");

        CollaborativeDocumentController controller = new CollaborativeDocumentController(1);
        BlockId block = controller.createFirstBlock();

        controller.localInsertChar(block, 0, 'A');
        controller.localInsertChar(block, 1, 'B');
        controller.localInsertChar(block, 2, 'C');

        Operation op = controller.localFormatChar(block, 1, true, false);

        CharacterNode node = controller.getDocument()
                .findBlock(block)
                .CharacterCRDT
                .findNode(op.charId);

        System.out.println("Expected text : ABC");
        System.out.println("Actual text   : " + controller.renderText());

        System.out.println("Expected B bold   : true");
        System.out.println("Actual B bold     : " + node.bold);

        System.out.println("Expected B italic : false");
        System.out.println("Actual B italic   : " + node.italic);
    }

    private static void testSplit() {
        printSection("TEST 3 - SPLIT");

        CollaborativeDocumentController controller = new CollaborativeDocumentController(1);
        BlockId block = controller.createFirstBlock();

        controller.localInsertChar(block, 0, 'H');
        controller.localInsertChar(block, 1, 'e');
        controller.localInsertChar(block, 2, 'l');
        controller.localInsertChar(block, 3, 'l');
        controller.localInsertChar(block, 4, 'o');

        controller.localSplitBlock(block, 2, "00:02");

        System.out.println("Expected:");
        System.out.println("He");
        System.out.println("llo");

        System.out.println("Actual:");
        System.out.println(controller.renderText());
    }

    private static void testMerge() {
        printSection("TEST 4 - MERGE");

        CollaborativeDocumentController controller = new CollaborativeDocumentController(1);
        BlockId block = controller.createFirstBlock();

        controller.localInsertChar(block, 0, 'H');
        controller.localInsertChar(block, 1, 'e');
        controller.localInsertChar(block, 2, 'l');
        controller.localInsertChar(block, 3, 'l');
        controller.localInsertChar(block, 4, 'o');

        controller.localSplitBlock(block, 2, "00:02");

        BlockId secondBlock = new BlockId(1, "00:02");
        controller.localMergeBlocks(block, secondBlock);

        System.out.println("Expected:");
        System.out.println("Hello");

        System.out.println("Actual:");
        System.out.println(controller.renderText());
    }

    private static void testPaste() {
        printSection("TEST 5 - PASTE");

        CollaborativeDocumentController controller = new CollaborativeDocumentController(1);
        controller.createFirstBlock();

        controller.localPasteBlock(controller.getDocument().getRootId(), "TEST", "00:02");

        System.out.println("Expected:");
        System.out.println("TEST");

        System.out.println("Actual:");
        System.out.println(controller.renderText());
    }

    private static void testRemoteCharOperation() {
        printSection("TEST 6 - REMOTE CHARACTER OPERATION");

        CollaborativeDocumentController controller = new CollaborativeDocumentController(1);
        BlockId block = controller.createFirstBlock();

        controller.localInsertChar(block, 0, 'A');
        controller.localInsertChar(block, 1, 'B');

        CharacterId remoteId = new CharacterId(2, "00:10");
        CharacterId parentId = controller.getDocument()
                .findBlock(block)
                .CharacterCRDT
                .getRootId();

        Operation remoteInsert = Operation.insert(remoteId, parentId, 'X');
        controller.applyRemoteCharOperation(block, remoteInsert);

        System.out.println("Expected: text updated with remote insert");
        System.out.println("Actual  : " + controller.renderText());

        System.out.println("Expected applyingRemote: false");
        System.out.println("Actual applyingRemote  : " + controller.isApplyingRemote());
    }

    private static void testMixedFlow() {
        printSection("TEST 7 - MIXED FLOW");

        CollaborativeDocumentController controller = new CollaborativeDocumentController(1);
        BlockId block1 = controller.createFirstBlock();

        controller.localInsertChar(block1, 0, 'H');
        controller.localInsertChar(block1, 1, 'e');
        controller.localInsertChar(block1, 2, 'l');
        controller.localInsertChar(block1, 3, 'l');
        controller.localInsertChar(block1, 4, 'o');

        controller.localSplitBlock(block1, 2, "00:02");

        BlockId block2 = new BlockId(1, "00:02");
        controller.localInsertChar(block2, 3, '!');

        controller.localPasteBlock(controller.getDocument().getRootId(), "TEST", "00:03");

        controller.localMergeBlocks(block1, block2);

        System.out.println("Expected structure close to:");
        System.out.println("Hello!");
        System.out.println("TEST");

        System.out.println("Actual:");
        System.out.println(controller.renderText());
    }
}