package crdt;

public class BlockOperation {

    public enum Type {
        INSERT_BLOCK,
        DELETE_BLOCK ,
        SPLIT,
        MERGE,
        PASTE
    }

    public final Type type;
    public final BlockId blockId;
    public final BlockId parentId;

    // For SPLIT
    public int splitIndex;

    // For MERGE
    public BlockId secondBlockId;

    // For PASTE
    public String text;

    private BlockOperation(Type type, BlockId blockId, BlockId parentId) {
        this.type = type;
        this.blockId = blockId;
        this.parentId = parentId;
    }

    // -------- BASIC --------

    public static BlockOperation insert(BlockId blockId, BlockId parentId) {
        return new BlockOperation(Type.INSERT_BLOCK, blockId, parentId);
    }

    public static BlockOperation delete(BlockId blockId) {
        return new BlockOperation(Type.INSERT_BLOCK, blockId, null);
    }

    // -------- SPLIT --------

    public static BlockOperation split(BlockId blockId, int splitIndex) {
        BlockOperation op = new BlockOperation(Type.SPLIT, blockId, null);
        op.splitIndex = splitIndex;
        return op;
    }

    // -------- MERGE --------

    public static BlockOperation merge(BlockId firstBlockId, BlockId secondBlockId) {
        BlockOperation op = new BlockOperation(Type.MERGE, firstBlockId, null);
        op.secondBlockId = secondBlockId;
        return op;
    }



    public static BlockOperation paste(BlockId parentId, String text) {
        BlockOperation op = new BlockOperation(Type.PASTE, null, parentId);
        op.text = text;
        return op;
    }

    @Override
    public String toString() {
        switch (type) {
            case INSERT_BLOCK:
                return "INSERT_BLOCK{id=" + blockId + ", parent=" + parentId + "}";

            case DELETE_BLOCK:
                return "DELETE_BLOCK{id=" + blockId + "}";

            case SPLIT:
                return "SPLIT_BLOCK{id=" + blockId + ", index=" + splitIndex + "}";

            case MERGE:
                return "MERGE_BLOCK{first=" + blockId + ", second=" + secondBlockId + "}";

            case PASTE:
                return "PASTE_BLOCK{parent=" + parentId + ", text=\"" + text + "\"}";

            default:
                return "UNKNOWN_BLOCK_OP";
        }
    }
}