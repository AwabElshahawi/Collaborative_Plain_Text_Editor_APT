package crdt;

import java.util.*;


public class CharacterCRDT {
    private final CharacterNode root;
    private final Map<CharacterId, CharacterNode> charMap;

    private void collectText(CharacterNode node, StringBuilder sb) {
        if (node != root && !node.deleted) {
            sb.append(node.value);
        }
        for (CharacterNode child : node.children) {
            collectText(child, sb);
        }
    }

    public CharacterCRDT(){
        root = CharacterNode.createRoot();
        charMap = new HashMap<>();
        charMap.put(root.id, root);
    }

    public CharacterId getRootId() {
        return root.id;
    }

    public void insert(Operation op){
        if(charMap.containsKey(op.charId)){
            return;
        }
        CharacterNode parent = (op.parentId == null) ? root : charMap.get(op.parentId);

        if (parent == null){
            System.out.println("Parent not found");
            return;
        }
        CharacterNode Node = new CharacterNode(op.charId,op.parentId, op.value, op.bold, op.italic);

        charMap.put(Node.id, Node);
        parent.children.add(Node);
        parent.children.sort((a, b) -> {
            int c = b.id.clock.compareTo(a.id.clock);
            if (c != 0) return c;
            return Integer.compare(a.id.userId, b.id.userId);
        });

    }

    public void delete(Operation op){
        CharacterNode Node = charMap.get(op.charId);
        if (Node == null){
            System.out.println("Node not found");
            return;
        }

        Node.deleted = true;

    }

    public void format(Operation op){
        CharacterNode Node = charMap.get(op.charId);
        if (Node == null){
            System.out.println("Node not found");
            return;
        }
        Node.bold = op.bold;
        Node.italic = op.italic;
    }

    public void apply(Operation op){
        switch(op.type){
            case INSERT:
                insert(op);
                break;
            case DELETE:
                delete(op);
                break;
            case FORMAT:
                format(op);
                break;
        }
    }
    public String getText() {
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        return sb.toString();
    }

}