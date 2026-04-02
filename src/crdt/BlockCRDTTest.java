package crdt;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
public class BlockCRDTTest {

    @Test
    void testInsertBlock() {
        BlockCRDT crdt = new BlockCRDT();

        BlockId id = new BlockId(1, "00:01");
        BlockOperation op = BlockOperation.insert(id, crdt.getRootId());

        crdt.apply(op);

        assertNotNull(crdt.findBlock(id));
    }


    @Test
    void testDeleteBlock() {
        BlockCRDT crdt = new BlockCRDT();

        BlockId id = new BlockId(1, "00:01");
        crdt.apply(BlockOperation.insert(id, crdt.getRootId()));
        crdt.apply(BlockOperation.delete(id));

        BlockNode node = crdt.findBlock(id);

        assertTrue(node.deleted);
    }

    @Test
    void testGetVisibleBlocks() {
        BlockCRDT crdt = new BlockCRDT();

        BlockId id1 = new BlockId(1, "00:01");
        BlockId id2 = new BlockId(1, "00:02");

        crdt.apply(BlockOperation.insert(id1, crdt.getRootId()));
        crdt.apply(BlockOperation.insert(id2, crdt.getRootId()));

        crdt.apply(BlockOperation.delete(id1));

        List<BlockNode> visible = crdt.getVisibleBlocks();

        assertEquals(1, visible.size());
        assertEquals(id2, visible.get(0).id);
    }

    @Test
    void testGetDocumentText() {
        BlockCRDT crdt = new BlockCRDT();

        BlockId id = new BlockId(1, "00:01");
        crdt.apply(BlockOperation.insert(id, crdt.getRootId()));

        BlockNode block = crdt.findBlock(id);

        LocalClock clock = new LocalClock(1);
        block.CharacterCRDT.insertText("Hello", clock);

        assertEquals("Hello", crdt.getDocumentText());
    }



    @Test
    void testCopyBlock() {
        BlockCRDT crdt = new BlockCRDT();

        BlockId id = new BlockId(1, "00:01");
        crdt.apply(BlockOperation.insert(id, crdt.getRootId()));

        BlockNode block = crdt.findBlock(id);

        LocalClock clock = new LocalClock(1);
        block.CharacterCRDT.insertText("ABC", clock);

        String copied = crdt.copyBlock(id);

        assertEquals("ABC", copied);
    }

    @Test
    void testPasteBlock() {
        BlockCRDT crdt = new BlockCRDT();

        BlockId id = crdt.pasteBlock(crdt.getRootId(), "XYZ", 1, "00:02");

        BlockNode block = crdt.findBlock(id);

        assertNotNull(block);
        assertEquals("XYZ", block.CharacterCRDT.getText());
    }

    @Test
    void testSplitBlock() {
        BlockCRDT crdt = new BlockCRDT();

        BlockId id = new BlockId(1, "00:01");
        crdt.apply(BlockOperation.insert(id, crdt.getRootId()));

        BlockNode block = crdt.findBlock(id);

        LocalClock clock = new LocalClock(1);
        block.CharacterCRDT.insertText("HELLO", clock);

        BlockId newBlockId = crdt.splitBlock(id, 2, 1, "00:02");

        BlockNode first = crdt.findBlock(id);
        BlockNode second = crdt.findBlock(newBlockId);

        assertEquals("HE", first.CharacterCRDT.getText());
        assertEquals("LLO", second.CharacterCRDT.getText());
    }

    @Test
    void testMergeBlocks() {
        BlockCRDT crdt = new BlockCRDT();

        BlockId id1 = new BlockId(1, "00:01");
        BlockId id2 = new BlockId(1, "00:02");

        crdt.apply(BlockOperation.insert(id1, crdt.getRootId()));
        crdt.apply(BlockOperation.insert(id2, crdt.getRootId()));

        BlockNode b1 = crdt.findBlock(id1);
        BlockNode b2 = crdt.findBlock(id2);

        LocalClock clock = new LocalClock(1);
        b1.CharacterCRDT.insertText("Hello", clock);
        b2.CharacterCRDT.insertText("World", clock);

        crdt.mergeBlocks(id1, id2, 1);

        assertEquals("HelloWorld", b1.CharacterCRDT.getText());
        assertTrue(b2.deleted);
    }
}

