
package Network;


public class MessageWrapper {
    public String kind;   // "CHAR" or "BLOCK"
    public Object data;
    public String blockId;

    public MessageWrapper() {}

    public MessageWrapper(String kind, Object data,  String blockId) {
        this.kind = kind;
        this.data = data;
        this.blockId = blockId;
    }
}
