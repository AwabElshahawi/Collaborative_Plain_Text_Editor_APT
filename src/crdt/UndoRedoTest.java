package crdt;
import crdt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UndoRedoTest {
 
    @Test
    void testUndoInsert() {
        CharacterCRDT crdt = new CharacterCRDT();
        UndoRedoManager manager = new UndoRedoManager(crdt);

        CharacterId id = new CharacterId(1, "00:01");
        Operation op = Operation.insert(id, crdt.getRootId(), 'A');

        crdt.apply(op);
        manager.record(op);

        manager.undo();

        assertEquals("", crdt.getText());
    }

    @Test
    void testRedoInsert() {
        CharacterCRDT crdt = new CharacterCRDT();
        UndoRedoManager manager = new UndoRedoManager(crdt);

        CharacterId id = new CharacterId(1, "00:01");
        Operation op = Operation.insert(id, crdt.getRootId(), 'A');

        crdt.apply(op);
        manager.record(op);

        manager.undo();
        manager.redo();

        assertEquals("A", crdt.getText());
    }

    @Test
    void testUndoDelete() {
        CharacterCRDT crdt = new CharacterCRDT();
        UndoRedoManager manager = new UndoRedoManager(crdt);

        CharacterId id = new CharacterId(1, "00:01");

        Operation insert = Operation.insert(id, crdt.getRootId(), 'A');
        Operation delete = Operation.delete(id);

        crdt.apply(insert);
        manager.record(insert);

        crdt.apply(delete);
        manager.record(delete);

        manager.undo(); 

        assertEquals("A", crdt.getText());
    }
}

