package crdt;

import java.util.ArrayList;
import java.util.List;

public class BlockNode {
    public final BlockId id;
    public final BlockId parentId;
    public boolean deleted;
    public final CharacterCRDT CharacterCRDT;
    public final List<BlockNode> children;

    public BlockNode(BlockId id, BlockId parentId) {
        this.id = id;
        this.parentId = parentId;
        this.deleted = false;
        this.CharacterCRDT = new CharacterCRDT();
        this.children = new ArrayList<>();
    }

    public static BlockNode createRoot() {
        return new BlockNode(new BlockId(0, "00:00"), null);
    }

    public boolean isRoot() {
        return parentId == null;
    }

    @Override
    public String toString() {
        return "Block{id=" + id + ", deleted=" + deleted + "}";
    }
}