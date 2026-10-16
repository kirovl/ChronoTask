package io.github.chronotask.interop;

import io.github.chronotask.clock.ChronoClock;
import io.github.chronotask.clock.ChronoTimer;
import io.github.chronotask.clock.DefaultChronoClock;
import io.github.chronotask.clock.DefaultChronoTimer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Java Clock and Timer Interoperability Tests")
class JavaClockInteropTest {

    @Test
    @DisplayName("ChronoClock.SYSTEM and DefaultChronoClock are accessible and functional from Java")
    void testSystemClockAccess() {
        ChronoClock clock = ChronoClock.SYSTEM;
        assertNotNull(clock);
        assertSame(DefaultChronoClock.INSTANCE, clock);

        Instant now = clock.now();
        assertNotNull(now);
        long millis = clock.currentTimeMillis();
        assertTrue(millis > 0);
    }

    @Test
    @DisplayName("ChronoTimer.SYSTEM and DefaultChronoTimer are accessible from Java")
    void testSystemTimerAccess() {
        ChronoTimer timer = ChronoTimer.SYSTEM;
        assertNotNull(timer);
        assertSame(DefaultChronoTimer.INSTANCE, timer);
    }

    @Test
    @DisplayName("ChronoClock is a FunctionalInterface (SAM) in Java")
    void testClockSamConversion() {
        Instant fixed = Instant.parse("2026-09-18T10:00:00Z");
        ChronoClock customClock = () -> fixed;

        assertEquals(fixed, customClock.now());
        assertEquals(fixed.toEpochMilli(), customClock.currentTimeMillis());
    }
}
