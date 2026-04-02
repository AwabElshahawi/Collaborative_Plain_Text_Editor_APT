package crdt;
import crdt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CharIdTest {
 

    @Test
    void testEquality() {
        CharacterId id1 = new CharacterId(1, "00:01");
        CharacterId id2 = new CharacterId(1, "00:01");

        assertEquals(id1, id2);
    }

    @Test
    void testHashCodeConsistency() {
        CharacterId id1 = new CharacterId(1, "00:01");
        CharacterId id2 = new CharacterId(1, "00:01");

        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void testCompareToDifferentClock() {
        CharacterId id1 = new CharacterId(1, "00:02");
        CharacterId id2 = new CharacterId(1, "00:01");

        assertTrue(id1.compareTo(id2) < 0);
    }

    @Test
    void testCompareToSameClockDifferentUser() {
        CharacterId id1 = new CharacterId(1, "00:01");
        CharacterId id2 = new CharacterId(2, "00:01");

        assertTrue(id1.compareTo(id2) < 0);
    }
}
    

