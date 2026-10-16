package io.github.chronotask.sample.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JavaSampleStartupTest {

    @Test
    void testSampleMainFiniteDurationExecution() {
        // Runs main with duration argument and ensures zero uncaught exceptions
        assertDoesNotThrow(() -> {
            SampleJavaApplication.main(new String[]{"--duration=1s"});
        });
    }
}
