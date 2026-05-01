package crdt;
import java.util.*;

public class BlockCRDT {
    public static final int MIN_BLOCK_LINES = 1;
    public static final int MAX_BLOCK_LINES = 10;
    private final BlockNode root;
    private final Map<BlockId, BlockNode> blockMap;

    public BlockCRDT() {
        root = BlockNode.createRoot();
        blockMap = new HashMap<>();
        blockMap.put(root.id, root);
    }

    public BlockId getRootId() {
        return root.id;
    }

    public BlockNode findBlock(BlockId id) {
        return blockMap.get(id);
    }

    public void insertBlock(BlockOperation op) {
        if (blockMap.containsKey(op.blockId)) {
            return;
        }

        BlockNode parent = (op.parentId == null) ? root : blockMap.get(op.parentId);
        if (parent == null) {
            System.out.println("Parent block not found");
            return;
        }

        BlockNode block = new BlockNode(op.blockId, op.parentId);
        blockMap.put(block.id, block);
        parent.children.add(block);
        parent.children.sort(Comparator.comparing(b -> b.id));
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) return 1;
        return text.split("\n", -1).length;
    }

    private boolean isValidBlockText(String text) {
        int lines = countLines(text);
        return lines <= MAX_BLOCK_LINES;
    }

    private List<String> splitIntoMaxLineChunks(String text) {
        List<String> chunks = new ArrayList<>();
        String[] lines = (text == null ? "" : text).split("\n", -1);
        StringBuilder current = new StringBuilder();
        int currentLines = 0;
        for (int i = 0; i < lines.length; i++) {
            if (currentLines == MAX_BLOCK_LINES) {
                chunks.add(current.toString());
                current = new StringBuilder();
                currentLines = 0;
            }
            if (currentLines > 0) current.append("\n");
            current.append(lines[i]);
            currentLines++;
        }
        if (chunks.isEmpty() || currentLines > 0) chunks.add(current.toString());
        return chunks;
    }

    public void deleteBlock(BlockOperation op) {
        BlockNode block = blockMap.get(op.blockId);
        if (block == null) {
            System.out.println("Block not found");
            return;
        }
        block.deleted = true;
    }

    public void apply(BlockOperation op) {
        switch (op.type) {
            case INSERT_BLOCK -> insertBlock(op);
            case DELETE_BLOCK -> deleteBlock(op);

            case SPLIT -> splitBlock(
                    op.blockId,
                    op.splitIndex,
                    op.blockId.userId,
                    op.newBlockClock
            );

            case MERGE -> mergeBlocks(
                    op.blockId,
                    op.secondBlockId,
                    op.blockId.userId
            );

            case PASTE -> pasteBlock(
                    op.parentId,
                    op.text,
                    op.blockId.userId,
                    op.blockId.clock
            );
        }
    }
    private void collectBlocks(BlockNode node, List<BlockNode> visibleBlocks) {
        if (node != root && !node.deleted) {
            visibleBlocks.add(node);
        }
        for (BlockNode child : node.children) {
            collectBlocks(child, visibleBlocks);
        }
    }

    public List<BlockNode> getVisibleBlocks() {
        List<BlockNode> result = new ArrayList<>();
        collectBlocks(root, result);
        return result;
    }

    public String getDocumentText() {
        StringBuilder sb = new StringBuilder();
        List<BlockNode> blocks = getVisibleBlocks();

        for (int i = 0; i < blocks.size(); i++) {
            sb.append(blocks.get(i).CharacterCRDT.getText());
            if (i < blocks.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
    public String copyBlock(BlockId blockId) {
        BlockNode block = blockMap.get(blockId);
        if (block == null || block.deleted) {
            System.out.println("Block not found");
            return "";
        }
        return block.CharacterCRDT.getText();
    }
    public BlockId pasteBlock(BlockId parentBlockId, String text, int userId, String blockClockValue) {
        List<String> chunks = splitIntoMaxLineChunks(text);
        BlockId newBlockId = new BlockId(userId, blockClockValue);

        BlockOperation blockOp = BlockOperation.insert(newBlockId, parentBlockId);
        apply(blockOp);

        BlockNode newBlock = blockMap.get(newBlockId);
        if (newBlock == null) {
            System.out.println("Failed to create new block");
            return null;
        }

        LocalClock charClock = new LocalClock(userId);
        newBlock.CharacterCRDT.insertText(chunks.get(0), charClock);

        BlockId prevId = newBlockId;
        for (int i = 1; i < chunks.size(); i++) {
            BlockId extraId = new BlockId(userId, blockClockValue + ":chunk:" + i);
            apply(BlockOperation.insert(extraId, prevId));
            BlockNode extra = blockMap.get(extraId);
            if (extra != null) {
                extra.CharacterCRDT.insertText(chunks.get(i), new LocalClock(userId));
                prevId = extraId;
            }
        }

        return newBlockId;
    }
    public BlockId splitBlock(BlockId blockId, int splitIndex, int userId, String newBlockClock) {
        BlockNode block = blockMap.get(blockId);

        if (block == null || block.deleted) {
            System.out.println("Block not found");
            return null;
        }

        String text = block.CharacterCRDT.getText();

        if (splitIndex < 0 || splitIndex > text.length()) {
            System.out.println("Invalid split index");
            return null;
        }

        String left = text.substring(0, splitIndex);
        String right = text.substring(splitIndex);
        List<String> rightChunks = splitIntoMaxLineChunks(right);

        block.CharacterCRDT.tombstoneAll();

        int startLeft = block.CharacterCRDT.getMaxCounterForUser(userId);
        LocalClock leftClock = new LocalClock(userId, startLeft);
        block.CharacterCRDT.insertText(left, leftClock);

        BlockId newBlockId = new BlockId(userId, newBlockClock);
        BlockOperation blockOp = BlockOperation.insert(newBlockId, block.parentId);
        apply(blockOp);

        BlockNode newBlock = blockMap.get(newBlockId);
        if (newBlock == null) {
            System.out.println("Failed to create split block");
            return null;
        }

        LocalClock rightClock = new LocalClock(userId);
        newBlock.CharacterCRDT.insertText(rightChunks.get(0), rightClock);

        BlockId prevId = newBlockId;
        for (int i = 1; i < rightChunks.size(); i++) {
            BlockId extraId = new BlockId(userId, newBlockClock + ":chunk:" + i);
            apply(BlockOperation.insert(extraId, prevId));
            BlockNode extra = blockMap.get(extraId);
            if (extra != null) {
                extra.CharacterCRDT.insertText(rightChunks.get(i), new LocalClock(userId));
                prevId = extraId;
            }
        }

        return newBlockId;
    }
    public void mergeBlocks(BlockId firstBlockId, BlockId secondBlockId, int userId) {
        BlockNode first = blockMap.get(firstBlockId);
        BlockNode second = blockMap.get(secondBlockId);

        if (first == null || second == null || first.deleted || second.deleted) {
            System.out.println("One or both blocks not found");
            return;
        }

        String mergedText = first.CharacterCRDT.getText() + second.CharacterCRDT.getText();
        List<String> chunks = splitIntoMaxLineChunks(mergedText);

        first.CharacterCRDT.tombstoneAll();

        int startMerge = first.CharacterCRDT.getMaxCounterForUser(userId);
        LocalClock mergeClock = new LocalClock(userId, startMerge);
        first.CharacterCRDT.insertText(chunks.get(0), mergeClock);

        second.CharacterCRDT.tombstoneAll();
        if (chunks.size() > 1) {
            second.CharacterCRDT.insertText(chunks.get(1), new LocalClock(userId));
            second.deleted = false;
        } else {
            second.deleted = true;
        }

        BlockId prevId = secondBlockId;
        for (int i = 2; i < chunks.size(); i++) {
            BlockId extraId = new BlockId(userId, secondBlockId.clock + ":chunk:" + i);
            apply(BlockOperation.insert(extraId, prevId));
            BlockNode extra = blockMap.get(extraId);
            if (extra != null) {
                extra.CharacterCRDT.insertText(chunks.get(i), new LocalClock(userId));
                prevId = extraId;
            }
        }
    }

}
