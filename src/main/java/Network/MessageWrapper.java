package Network;

import com.google.gson.JsonElement;

public class MessageWrapper {
    public String      kind;    // "CHAR" or "BLOCK"
    public JsonElement data;    // was Object — caused char corruption on double-conversion
    public String      blockId;

    public MessageWrapper() {}

    public MessageWrapper(String kind, Object data, String blockId) {
        this.kind    = kind;
        this.data    = com.google.gson.JsonParser.parseString(
                new com.google.gson.Gson().toJson(data));
        this.blockId = blockId;
    }
}