package main.java.crdt;

import java.util.ArrayList;
import java.util.List;

public class CharacterNode {

    public final CharacterId id;
    public final CharacterId parentId;
    public final char value;
    public boolean deleted;
    public boolean bold;
    public boolean italic;
    public final List<CharacterNode> children;

    public CharacterNode(CharacterId id, CharacterId parentId,
                         char value, boolean bold, boolean italic) {
        this.id       = id;
        this.parentId = parentId;
        this.value    = value;
        this.deleted  = false;
        this.bold     = bold;
        this.italic   = italic;
        this.children = new ArrayList<>();
    }

    public static CharacterNode createRoot() {
        CharacterId rootId = new CharacterId(0, "00:00");
        CharacterNode root = new CharacterNode(rootId, null, '\0', false, false);
        return root;
    }

    public boolean isRoot() {
        return parentId == null;
    }

    @Override
    public String toString() {
        return "Node{id=" + id + ", val='" + value + "', del=" + deleted + "}";
    }

}