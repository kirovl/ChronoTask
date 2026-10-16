package io.github.chronotask.annotation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.annotation.ElementType

class AnnotationContractTest {

    @Test
    fun `annotations have RUNTIME retention and METHOD target`() {
        val annotations = listOf(
            Scheduled::class.java,
            Cron::class.java,
            Once::class.java,
            TickTask::class.java
        )

        for (ann in annotations) {
            val retention = ann.getAnnotation(Retention::class.java)
            assertEquals(RetentionPolicy.RUNTIME, retention.value, "Annotation ${ann.simpleName} must have RUNTIME retention")

            val target = ann.getAnnotation(Target::class.java)
            assertTrue(target.value.contains(ElementType.METHOD), "Annotation ${ann.simpleName} must target METHOD")
        }
    }
}
