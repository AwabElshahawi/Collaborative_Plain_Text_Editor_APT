package crdt;

import java.util.Objects;

public class CharacterId implements Comparable<CharacterId>{
    public final int userId;
    public final String clock;
    public CharacterId(int userId, String clock) {
        this.userId = userId;
        this.clock  = clock;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CharacterId)) return false;
        CharacterId other = (CharacterId) o;
        return  userId == other.userId && clock.equals(other.clock);
    }
    @Override
    public int hashCode() {
        return Objects.hash(userId, clock);
    }
    @Override
    public int compareTo(CharacterId other) {
        int clockComparison = other.clock.compareTo(this.clock);
        if (clockComparison != 0) {
            return clockComparison;
        }
        return Integer.compare(this.userId, other.userId);
    }
    @Override
    public String toString() {
        return "[" + userId + ", " + clock + "]";
    }
}
