package crdt;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import crdt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CharCRDTTest {

    @Test
    void testSimpleInsert() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharacterId id1 = new CharacterId(1, "00:01");
        Operation op1 = Operation.insert(id1, crdt.getRootId(), 'A');

        crdt.apply(op1);

        assertEquals("A", crdt.getText());
    }

    @Test
    void testInsertMultipleCharacters() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharacterId id1 = new CharacterId(1, "00:01");
        CharacterId id2 = new CharacterId(1, "00:02");

        crdt.apply(Operation.insert(id1, crdt.getRootId(), 'A'));
        crdt.apply(Operation.insert(id2, id1, 'B'));

        assertEquals("AB", crdt.getText());
    }

    @Test
    void testDeleteCharacter() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharacterId id1 = new CharacterId(1, "00:01");

        crdt.apply(Operation.insert(id1, crdt.getRootId(), 'A'));
        crdt.apply(Operation.delete(id1));

        assertEquals("", crdt.getText());
    }

    @Test
    void testTombstoneNotRemoved() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharacterId id1 = new CharacterId(1, "00:01");

        crdt.apply(Operation.insert(id1, crdt.getRootId(), 'A'));
        crdt.apply(Operation.delete(id1));

        CharacterNode node = crdt.findNode(id1);

        assertTrue(node.deleted);
    }

    @Test
    void testDuplicateInsertIgnored() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharacterId id = new CharacterId(1, "00:01");

        Operation op = Operation.insert(id, crdt.getRootId(), 'A');

        crdt.apply(op);
        crdt.apply(op); 

        assertEquals("A", crdt.getText());
    }

    @Test
    void testConcurrentInsertOrdering() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharacterId id1 = new CharacterId(1, "00:01");
        CharacterId id2 = new CharacterId(2, "00:01");

        crdt.apply(Operation.insert(id1, crdt.getRootId(), 'A'));
        crdt.apply(Operation.insert(id2, crdt.getRootId(), 'B'));

        String result = crdt.getText();

        assertEquals(2, result.length());
        assertTrue(result.contains("A"));
        assertTrue(result.contains("B"));
    }

    @Test
    void testFormat() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharacterId id = new CharacterId(1, "00:01");

        crdt.apply(Operation.insert(id, crdt.getRootId(), 'A'));
        crdt.apply(Operation.format(id, true, false));

        CharacterNode node = crdt.findNode(id);

        assertTrue(node.bold);
        assertFalse(node.italic);
    }
}

