package crdt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollaborativeDocumentController {

    private final BlockCRDT document;
    private final LocalClock localClock;
    private final int localUserId;
    private boolean applyingRemote;


    private final UndoRedoManager globalUndoManager; // to undo/redo for other users

    public CollaborativeDocumentController(int localUserId) {
        this.document = new BlockCRDT();
        this.localUserId = localUserId;
        this.localClock = new LocalClock(localUserId);
        this.globalUndoManager = new UndoRedoManager(this.document); // Initialize
    }

    // ─────────────────────────────────────────────────────────────────
    //  UNDO / REDO
    // ─────────────────────────────────────────────────────────────────

    public UndoRedoManager getUndoManager() {
        return globalUndoManager;
    }


    public Object undo() {
        Object inverse = globalUndoManager.undo();

        if (inverse instanceof Operation) {
            Operation op = (Operation) inverse;
            BlockNode block = document.findBlock(BlockId.fromString(op.blockIdHint));
            if (block != null) {
                block.CharacterCRDT.apply(op);
            }
        } else if (inverse instanceof BlockOperation) {
            document.apply((BlockOperation) inverse);
        }
        return inverse;
    }


    public Object redo() {
        Object toRedo = globalUndoManager.redo();

        if (toRedo instanceof Operation) {
            Operation op = (Operation) toRedo;
            BlockNode block = document.findBlock(BlockId.fromString(op.blockIdHint));
            if (block != null) {
                block.CharacterCRDT.apply(op);
            }
        } else if (toRedo instanceof BlockOperation) {
            document.apply((BlockOperation) toRedo);
        }
        return toRedo;
    }
    // ─────────────────────────────────────────────────────────────────
    //  GETTERS
    // ─────────────────────────────────────────────────────────────────

    public BlockCRDT getDocument() {
        return document;
    }

    public int getLocalUserId() {
        return localUserId;
    }

    public boolean isApplyingRemote() {
        return applyingRemote;
    }

    public String renderText() {
        return document.getDocumentText();
    }

    // ─────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────

    private BlockNode requireBlock(BlockId blockId) {
        BlockNode block = document.findBlock(blockId);
        if (block == null || block.deleted) {
            throw new IllegalArgumentException("Block not found: " + blockId);
        }
        return block;
    }

    private CharacterId resolveInsertParentId(BlockId blockId, int visibleIndex) {
        BlockNode block = requireBlock(blockId);
        List<CharacterNode> visible = block.CharacterCRDT.getVisibleCharacters();

        if (visibleIndex <= 0) {
            return block.CharacterCRDT.getRootId();
        }
        if (visibleIndex > visible.size()) {
            visibleIndex = visible.size();
        }
        return visible.get(visibleIndex - 1).id;
    }

    private CharacterNode resolveDeleteTarget(BlockId blockId, int visibleIndex) {
        BlockNode block = requireBlock(blockId);
        List<CharacterNode> visible = block.CharacterCRDT.getVisibleCharacters();

        if (visibleIndex <= 0 || visibleIndex > visible.size()) {
            return null;
        }
        return visible.get(visibleIndex - 1);
    }

    private int lineCount(String text) {
        if (text == null || text.isEmpty()) return 1;
        return text.split("\n", -1).length;
    }

    // ─────────────────────────────────────────────────────────────────
    //  CHARACTER OPERATIONS
    // ─────────────────────────────────────────────────────────────────

    public Operation localInsertChar(BlockId blockId, int visibleIndex, char value, boolean bold, boolean italic) {
        BlockNode block = requireBlock(blockId);
        String currentText = block.CharacterCRDT.getText();
        String nextText = currentText.substring(0, Math.max(0, Math.min(visibleIndex, currentText.length())))
                + value
                + currentText.substring(Math.max(0, Math.min(visibleIndex, currentText.length())));
        int nextLines = lineCount(nextText);
        if (nextLines > BlockCRDT.MAX_BLOCK_LINES) return null;

        CharacterId parentId = resolveInsertParentId(blockId, visibleIndex);
        CharacterId newId    = localClock.next();

        Operation op = Operation.insert(newId, parentId, value, bold, italic);
        block.CharacterCRDT.apply(op);

        // Record for undo

        op.blockIdHint = blockId.toString();
        globalUndoManager.record(op);

        return op;
    }

    public Operation localInsertChar(BlockId blockId, int visibleIndex, char value) {
        return localInsertChar(blockId, visibleIndex, value, false, false);

    }

    public Operation localDeleteChar(BlockId blockId, int visibleIndex) {
        BlockNode block = requireBlock(blockId);
        CharacterNode target = resolveDeleteTarget(blockId, visibleIndex);

        if (target == null) return null;
        String currentText = block.CharacterCRDT.getText();
        int idx = Math.max(0, Math.min(visibleIndex - 1, currentText.length() - 1));
        String nextText = currentText.substring(0, idx) + currentText.substring(idx + 1);
        Operation op = Operation.delete(target.id);
        block.CharacterCRDT.apply(op);

        // Record for undo
        op.blockIdHint = blockId.toString();
        globalUndoManager.record(op);
        return op;
    }

    public Operation localFormatChar(BlockId blockId, int visibleIndex, boolean bold, boolean italic) {
        BlockNode block = requireBlock(blockId);
        List<CharacterNode> visible = block.CharacterCRDT.getVisibleCharacters();

        if (visibleIndex < 0 || visibleIndex >= visible.size()) return null;

        CharacterNode target = visible.get(visibleIndex);
        Operation op = Operation.format(target.id, bold, italic);
        block.CharacterCRDT.apply(op);

        // Record for undo
        op.blockIdHint = blockId.toString();
        globalUndoManager.record(op);
        return op;
    }

    public void applyRemoteCharOperation(BlockId blockId, Operation op) {
        BlockNode block = document.findBlock(blockId);
        if (block == null || block.deleted) {
            document.apply(BlockOperation.insert(blockId, document.getRootId()));
            block = requireBlock(blockId);
        }
        applyingRemote = true;
        block.CharacterCRDT.apply(op);
        op.blockIdHint = blockId.toString();
        applyingRemote = false;
    }

    // ─────────────────────────────────────────────────────────────────
    //  BLOCK OPERATIONS
    // ─────────────────────────────────────────────────────────────────

    public BlockOperation localInsertBlock(BlockId newBlockId, BlockId parentId) {
        BlockOperation op = BlockOperation.insert(newBlockId, parentId);
        document.apply(op);
        globalUndoManager.record(op);

        return op;
    }

    public BlockOperation localDeleteBlock(BlockId blockId) {
        BlockOperation op = BlockOperation.delete(blockId);
        document.apply(op);
        globalUndoManager.record(op);

        return op;
    }

    public BlockOperation localSplitBlock(BlockId blockId, int splitIndex, String newBlockClock) {
        BlockOperation op = BlockOperation.split(blockId, splitIndex, newBlockClock);
        document.apply(op);
        globalUndoManager.record(op);

        return op;
    }

    public BlockOperation localMergeBlocks(BlockId firstBlockId, BlockId secondBlockId) {
        BlockOperation op = BlockOperation.merge(firstBlockId, secondBlockId);
        document.apply(op);
        globalUndoManager.record(op);

        return op;
    }

    public BlockOperation localPasteBlock(BlockId parentId, String text, String newBlockClock) {
        BlockOperation op = BlockOperation.paste(parentId, text, newBlockClock, localUserId);
        document.apply(op);
        globalUndoManager.record(op);

        return op;
    }

    public void applyRemoteBlockOperation(BlockOperation op) {
        applyingRemote = true;
        document.apply(op);
        applyingRemote = false;

    }

    public BlockId createFirstBlock() {
        BlockId blockId = new BlockId(0, "00:01");
        BlockNode existing = document.findBlock(blockId);
        if (existing != null) {
            existing.deleted = false;
            return blockId;
        }

        BlockOperation op = BlockOperation.insert(blockId, document.getRootId());
        document.apply(op);
        return blockId;
    }
}
