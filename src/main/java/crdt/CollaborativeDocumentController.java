package crdt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollaborativeDocumentController {

    private final BlockCRDT document;
    private final LocalClock localClock;
    private final int localUserId;
    private boolean applyingRemote;

    // One UndoRedoManager per block
    private final Map<BlockId, UndoRedoManager> undoRedoManagers = new HashMap<>();

    public CollaborativeDocumentController(int localUserId) {
        this.document = new BlockCRDT();
        this.localUserId = localUserId;
        this.localClock = new LocalClock(localUserId);
        this.applyingRemote = false;
    }

    // ─────────────────────────────────────────────────────────────────
    //  UNDO / REDO
    // ─────────────────────────────────────────────────────────────────

    private UndoRedoManager getUndoManager(BlockId blockId) {
        return undoRedoManagers.computeIfAbsent(blockId, id ->
                new UndoRedoManager(document.findBlock(id).CharacterCRDT));
    }

    /** Undo last character operation in the given block. Returns inverse op to broadcast. */
    public Operation undo(BlockId blockId) {
        return getUndoManager(blockId).undo();
    }

    /** Redo last undone character operation in the given block. Returns op to broadcast. */
    public Operation redo(BlockId blockId) {
        return getUndoManager(blockId).redo();
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

    // ─────────────────────────────────────────────────────────────────
    //  CHARACTER OPERATIONS
    // ─────────────────────────────────────────────────────────────────

    public Operation localInsertChar(BlockId blockId, int visibleIndex, char value, boolean bold, boolean italic) {
        BlockNode block = requireBlock(blockId);

        CharacterId parentId = resolveInsertParentId(blockId, visibleIndex);
        CharacterId newId    = localClock.next();

        Operation op = Operation.insert(newId, parentId, value, bold, italic);
        block.CharacterCRDT.apply(op);

        // Record for undo
        getUndoManager(blockId).record(op);

        return op;
    }

    public Operation localInsertChar(BlockId blockId, int visibleIndex, char value) {
        return localInsertChar(blockId, visibleIndex, value, false, false);
    }

    public Operation localDeleteChar(BlockId blockId, int visibleIndex) {
        BlockNode block = requireBlock(blockId);
        CharacterNode target = resolveDeleteTarget(blockId, visibleIndex);

        if (target == null) return null;

        Operation op = Operation.delete(target.id);
        block.CharacterCRDT.apply(op);

        // Record for undo
        getUndoManager(blockId).record(op);

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
        getUndoManager(blockId).record(op);

        return op;
    }

    public void applyRemoteCharOperation(BlockId blockId, Operation op) {
        BlockNode block = document.findBlock(blockId);
        if (block == null || block.deleted) {
            // Be resilient to out-of-order delivery / older clients:
            // ensure the target block exists before applying remote char ops.
            document.apply(BlockOperation.insert(blockId, document.getRootId()));
            block = requireBlock(blockId);
        }

        applyingRemote = true;
        block.CharacterCRDT.apply(op);
        applyingRemote = false;
        // Remote ops are NOT recorded in undo stack
    }

    // ─────────────────────────────────────────────────────────────────
    //  BLOCK OPERATIONS
    // ─────────────────────────────────────────────────────────────────

    public BlockOperation localInsertBlock(BlockId newBlockId, BlockId parentId) {
        BlockOperation op = BlockOperation.insert(newBlockId, parentId);
        document.apply(op);
        return op;
    }

    public BlockOperation localDeleteBlock(BlockId blockId) {
        BlockOperation op = BlockOperation.delete(blockId);
        document.apply(op);
        return op;
    }

    public BlockOperation localSplitBlock(BlockId blockId, int splitIndex, String newBlockClock) {
        BlockOperation op = BlockOperation.split(blockId, splitIndex, newBlockClock);
        document.apply(op);
        return op;
    }

    public BlockOperation localMergeBlocks(BlockId firstBlockId, BlockId secondBlockId) {
        BlockOperation op = BlockOperation.merge(firstBlockId, secondBlockId);
        document.apply(op);
        return op;
    }

    public BlockOperation localPasteBlock(BlockId parentId, String text, String newBlockClock) {
        BlockOperation op = BlockOperation.paste(parentId, text, newBlockClock, localUserId);
        document.apply(op);
        return op;
    }

    public void applyRemoteBlockOperation(BlockOperation op) {
        applyingRemote = true;
        document.apply(op);
        applyingRemote = false;
    }

    public BlockId createFirstBlock() {
        // Shared deterministic first block so every client can apply remote ops
        // to the same initial block before any additional block operations arrive.
        BlockId blockId = new BlockId(0, "00:01");
        BlockOperation op = BlockOperation.insert(blockId, document.getRootId());
        document.apply(op);
        return blockId;
    }
}