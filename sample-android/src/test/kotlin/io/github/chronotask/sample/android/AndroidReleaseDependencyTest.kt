package io.github.chronotask.sample.android

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AndroidReleaseDependencyTest {

    @Test
    fun `test ksp symbol processor not leaked to android runtime classpath`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("io.github.chronotask.ksp.ChronoTaskSymbolProcessor")
        }
    }
}
