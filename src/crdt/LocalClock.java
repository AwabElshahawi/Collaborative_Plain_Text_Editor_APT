package crdt;

public class LocalClock {
    private int counter;
    private final int userId;

    public LocalClock(int userId) {
        this.userId  = userId;
        this.counter = 0;
    }

    public CharacterId next() {
        counter++;
        String clockString = String.format("%02d:%02d", counter / 60, counter % 60);
        return new CharacterId(userId, clockString);
    }

    public int getUserId() {
        return userId;
    }

}
