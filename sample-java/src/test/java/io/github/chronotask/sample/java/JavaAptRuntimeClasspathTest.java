package io.github.chronotask.sample.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaAptRuntimeClasspathTest {

    @Test
    void testAnnotationProcessorNotOnRuntimeClasspath() {
        // Verify that chronotask-processor classes are NOT present at runtime in sample-java
        assertThrows(ClassNotFoundException.class, () -> {
            Class.forName("io.github.chronotask.processor.ChronoTaskProcessor");
        });
    }
}
