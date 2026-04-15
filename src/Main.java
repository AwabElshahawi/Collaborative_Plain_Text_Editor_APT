//import crdt.*;
//
//public class Main {
//    public static void main(String[] args) {
//        CollaborativeDocumentController controller = new CollaborativeDocumentController(1);
//
//        BlockId block1 = controller.createFirstBlock();
//
//        controller.localInsertChar(block1, 0, 'H');
//        controller.localInsertChar(block1, 1, 'e');
//        controller.localInsertChar(block1, 2, 'l');
//        controller.localInsertChar(block1, 3, 'l');
//        controller.localInsertChar(block1, 4, 'o');
//
//        System.out.println("Original:");
//        System.out.println(controller.renderText());
//
//        controller.localSplitBlock(block1, 2, "00:02");
//        System.out.println("\nAfter split:");
//        System.out.println(controller.renderText());
//
//        BlockId block2 = new BlockId(1, "00:03");
//        controller.localInsertBlock(block2, controller.getDocument().getRootId());
//        controller.localInsertChar(block2, 0, 'X');
//
//        System.out.println("\nAfter new block:");
//        System.out.println(controller.renderText());
//
//        controller.localMergeBlocks(block1, new BlockId(1, "00:02"));
//        System.out.println("\nAfter merge:");
//        System.out.println(controller.renderText());
//
//        controller.localPasteBlock(controller.getDocument().getRootId(), "TEST", "00:04");
//        System.out.println("\nAfter paste:");
//        System.out.println(controller.renderText());
//    }
//}