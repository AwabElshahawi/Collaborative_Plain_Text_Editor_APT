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
//        if(charMap.containsKey(op.charId)){
//            return;
//        }
        if (charMap.containsKey(op.charId)) {
            CharacterNode existing = charMap.get(op.charId);

            if (existing.deleted) {
                existing.deleted = false;
            }

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
        if(Node.deleted) {
            System.out.println("Deleted character");
            return;
        }
        op.prevBold = Node.bold;
        op.prevItalic = Node.italic;
        
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

    public CharacterNode findNode(CharacterId id){
        return charMap.get(id);
    }

    public String getText() {
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        return sb.toString();
    }
    private void collectVisible(CharacterNode node, List<CharacterNode> result) {
        if (node != root && !node.deleted) {
            result.add(node);
        }
        for (CharacterNode child : node.children) {
            collectVisible(child, result);
        }
    }
    public List<CharacterNode> getVisibleCharacters() {
        List<CharacterNode> result = new ArrayList<>();
        collectVisible(root, result);
        return result;
    }
    public void tombstoneAll() {
        for (CharacterNode node : charMap.values()) {
            if (node != root) {
                node.deleted = true;
            }
        }
    }
    public CharacterId insertText(String text, LocalClock clock) {
        CharacterId parent = getRootId();
        CharacterId lastInserted = null;

        for (char c : text.toCharArray()) {
            CharacterId newId = clock.next();
            Operation op = Operation.insert(newId, parent, c);
            apply(op);
            parent = newId;
            lastInserted = newId;
        }

        return lastInserted;
    }
    public int getMaxCounterForUser(int userId) {
        int maxCounter = 0;

        for (CharacterId id : charMap.keySet()) {
            if (id.userId == userId) {
                String[] parts = id.clock.split(":");
                int value = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
                if (value > maxCounter) {
                    maxCounter = value;
                }
            }
        }

        return maxCounter;
    }
}