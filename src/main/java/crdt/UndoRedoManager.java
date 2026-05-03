package crdt;
import java.util.*;

public class UndoRedoManager {
    // to store either a Char Op or a Block Op in one stack
    public static class HistoryItem {
        public Operation charOp;
        public BlockOperation blockOp;
        public boolean isBlock;

        public HistoryItem(Operation op) { this.charOp = op; this.isBlock = false; }
        public HistoryItem(BlockOperation op) { this.blockOp = op; this.isBlock = true; }
    }

    private Stack<HistoryItem> undoStack = new Stack<>();
    private Stack<HistoryItem> redoStack = new Stack<>();
    private final BlockCRDT document;

    public UndoRedoManager(BlockCRDT document) {
        this.document = document;
    }

    public void record(Operation op) {
        if (undoStack.size() >= 10) undoStack.remove(0);
        undoStack.push(new HistoryItem(op));
        redoStack.clear();
    }

    public void record(BlockOperation op) {
        if (undoStack.size() >= 10) undoStack.remove(0);
        undoStack.push(new HistoryItem(op));
        redoStack.clear();
    }

    public Object undo() {
        if (undoStack.isEmpty()) return null;
        HistoryItem item = undoStack.pop();
        redoStack.push(item);
        return getInverse(item);
    }

    public Object redo() {
        if (redoStack.isEmpty()) return null;
        HistoryItem item = redoStack.pop();
        undoStack.push(item);
        return item.isBlock ? item.blockOp : item.charOp;
    }

    private Object getInverse(HistoryItem item) {
        if (item.isBlock) {
            BlockOperation original = item.blockOp;
            BlockOperation inverse = null;
            switch (original.type) {
                case INSERT_BLOCK:
                    inverse = BlockOperation.delete(original.blockId);
                    break;
                case DELETE_BLOCK:
                    inverse = BlockOperation.insert(original.blockId, original.parentId);
                    break;
                case SPLIT:
                    BlockId secondBlockId = new BlockId(original.blockId.userId, original.newBlockClock);
                    inverse = BlockOperation.merge(original.blockId, secondBlockId);
                    break;
                case MERGE:
                    inverse = null;
                    break;
                case PASTE:
                    inverse = BlockOperation.delete(original.blockId);
                    break;
            }
            return inverse;
        } else {
            Operation original = item.charOp;
            Operation inverse = null;
            switch (original.type) {
                case INSERT:
                    inverse = Operation.delete(original.charId);
                    break;
                case DELETE:
                    inverse = Operation.insert(
                            original.charId,
                            original.parentId,
                            original.value,
                            original.bold,
                            original.italic
                    );
                    break;
                case FORMAT:
                    inverse = Operation.format(original.charId, original.prevBold, original.prevItalic);
                    break;
            }
            if (inverse != null) {
                inverse.blockIdHint = original.blockIdHint;
            }
            return inverse;
        }
    }
}
