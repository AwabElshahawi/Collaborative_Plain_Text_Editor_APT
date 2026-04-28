package crdt;
import java.util.Objects;

public class BlockId implements Comparable<BlockId> {
    public final int userId;
    public final String clock;

    public BlockId(int userId, String clock) {
        this.userId = userId;
        this.clock = clock;
    }

    public static BlockId fromString(String s) {
        // strips the surrounding brackets
        String trimmed = s.substring(1, s.length() - 1);

        // split on the first ", " only, in case clock contains commas
        int sep = trimmed.indexOf(", ");
        int userId = Integer.parseInt(trimmed.substring(0, sep));
        String clock = trimmed.substring(sep + 2);

        return new BlockId(userId, clock);
    }
    @Override
    public int compareTo(BlockId other) {
        int c = this.clock.compareTo(other.clock);
        if (c != 0) return c;
        return Integer.compare(this.userId, other.userId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockId)) return false;
        BlockId other = (BlockId) o;
        return userId == other.userId && clock.equals(other.clock);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, clock);
    }

    @Override
    public String toString() {
        return "[" + userId + ", " + clock + "]";
    }
}