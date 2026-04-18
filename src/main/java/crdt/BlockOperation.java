package main.java.crdt;

public class BlockOperation {

    public enum Type {
        INSERT_BLOCK,
        DELETE_BLOCK,
        SPLIT,
        MERGE,
        PASTE
    }

    public final Type type;
    public final BlockId blockId;
    public final BlockId parentId;
    public final BlockId secondBlockId;
    public final int splitIndex;
    public final String text;
    public final String newBlockClock;

    public BlockOperation(Type type,
                           BlockId blockId,
                           BlockId parentId,
                           BlockId secondBlockId,
                           int splitIndex,
                           String text,
                           String newBlockClock) {
        this.type = type;
        this.blockId = blockId;
        this.parentId = parentId;
        this.secondBlockId = secondBlockId;
        this.splitIndex = splitIndex;
        this.text = text;
        this.newBlockClock = newBlockClock;
    }

    public static BlockOperation insert(BlockId blockId, BlockId parentId) {
        return new BlockOperation(Type.INSERT_BLOCK, blockId, parentId, null, -1, null, null);
    }

    public static BlockOperation delete(BlockId blockId) {
        return new BlockOperation(Type.DELETE_BLOCK, blockId, null, null, -1, null, null);
    }

    public static BlockOperation split(BlockId blockId, int splitIndex, String newBlockClock) {
        return new BlockOperation(Type.SPLIT, blockId, null, null, splitIndex, null, newBlockClock);
    }

    public static BlockOperation merge(BlockId firstBlockId, BlockId secondBlockId) {
        return new BlockOperation(Type.MERGE, firstBlockId, null, secondBlockId, -1, null, null);
    }

    public static BlockOperation paste(BlockId parentId, String text, String newBlockClock, int userId) {
        BlockId newBlockId = new BlockId(userId, newBlockClock);
        return new BlockOperation(Type.PASTE, newBlockId, parentId, null, -1, text, newBlockClock);
    }
}