package main.java.crdt;

public class Operation {

    public enum Type {
        INSERT,
        DELETE,
        FORMAT
    }

    public final Type type;
    public final CharacterId charId;
    public final CharacterId parentId;
    public final char value;
    public boolean bold;
    public boolean italic;
    boolean prevBold;
    boolean prevItalic;

    public Operation(Type type, CharacterId charId, CharacterId parentId,
                      char value, boolean bold, boolean italic) {
        this.type     = type;
        this.charId   = charId;
        this.parentId = parentId;
        this.value    = value;
        this.bold     = bold;
        this.italic   = italic;
    }

    public static Operation insert(CharacterId charId, CharacterId parentId, char value) {
        return new Operation(Type.INSERT, charId, parentId, value, false, false);
    }

    public static Operation delete(CharacterId charId) {
        return new Operation(Type.DELETE, charId, null, '\0', false, false);
    }

    public static Operation format(CharacterId charId, boolean bold, boolean italic) {
        return new Operation(Type.FORMAT, charId, null, '\0', bold, italic);
    }

    @Override
    public String toString() {
        switch (type) {
            case INSERT:
                return "INSERT{id=" + charId + ", parent=" + parentId + ", val='" + value + "'}";
            case DELETE:
                return "DELETE{id=" + charId + "}";
            case FORMAT:
                return "FORMAT{id=" + charId + ", bold=" + bold + ", italic=" + italic + "}";
            default:
                return "UNKNOWN";
        }
    }

}