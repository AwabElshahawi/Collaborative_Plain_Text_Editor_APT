package main.java.Network;

public class MessageWrapper {
    public String kind;   // "CHAR" or "BLOCK"
    public Object data;

    public MessageWrapper() {}

    public MessageWrapper(String kind, Object data) {
        this.kind = kind;
        this.data = data;
    }
}
