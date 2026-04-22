package crdt;
import java.util.*;

public class UndoRedoManager {

    private Stack<Operation> undoStack = new Stack<>();
    private Stack<Operation> redoStack = new Stack<>();

    private CharacterCRDT crdt;

    public UndoRedoManager(CharacterCRDT crdt) {
        this.crdt = crdt;
    }

    public void record(Operation op) {
        undoStack.push(op);

        if (undoStack.size() > 10) {
            undoStack.remove(0);
        }

        redoStack.clear();
    }

    private Operation getInverse(Operation op){
        CharacterNode node;

        switch (op.type) {

            case INSERT:
                return Operation.delete(op.charId);
            
            case DELETE:
                node = crdt.findNode(op.charId);
                return Operation.insert(node.id, node.parentId, node.value);

            case FORMAT:
                return Operation.format(op.charId, op.prevBold, op.prevItalic);
        }

        throw new IllegalArgumentException("Unsupported operation type");

    }

    public void undo() {
        
        if (undoStack.isEmpty()) {
            System.out.println("Nothing to undo");
            return;
        }
        
        Operation op = undoStack.pop();
        Operation inverseOp = getInverse(op);
        crdt.apply(inverseOp);
        redoStack.push(op);
    }

    public void redo() {

        if (redoStack.isEmpty()) {
            System.out.println("Nothing to redo");
            return;
        }

        Operation op = redoStack.pop();
        crdt.apply(op);
        undoStack.push(op);
    }

}

