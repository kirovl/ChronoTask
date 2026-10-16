package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.Once
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OuterA {
    class NestedClass {
        @Once(id = "nested-a-task")
        fun task() {}
    }
}

class OuterB {
    class NestedClass {
        @Once(id = "nested-b-task")
        fun task() {}
    }
}

class GenericTarget<T>(val payload: T) {
    @Once(id = "generic-target-task")
    fun runTask() {}
}

class Flat {
    class Name {
        @Once(id = "flat-nested")
        fun run() {}
    }

    class _Name {
        @Once(id = "underscore-inner")
        fun run() {}
    }
}

class Flat_Name {
    @Once(id = "flat-top-level")
    fun run() {}
}

class Flat_ {
    class Name {
        @Once(id = "underscore-outer")
        fun run() {}
    }
}

class __9_Flat_Name {
    @Once(id = "encoded-looking-source")
    fun run() {}
}

class NamingCollisionTest {

    @Test
    fun `literal underscores do not collide with nested class separators`() {
        assertEquals("flat-nested", Flat_Name_ChronoTaskBinding.definitions(Flat.Name()).single().id)
        assertEquals("flat-top-level", __9_Flat_Name_ChronoTaskBinding.definitions(Flat_Name()).single().id)
    }

    @Test
    fun `underscores adjacent to nesting boundaries remain distinct`() {
        assertEquals("underscore-outer", __5_Flat__4_Name_ChronoTaskBinding.definitions(Flat_.Name()).single().id)
        assertEquals("underscore-inner", __4_Flat_5__Name_ChronoTaskBinding.definitions(Flat._Name()).single().id)
    }

    @Test
    fun `source names resembling encoded binding names remain distinct`() {
        assertEquals("encoded-looking-source",
            __13___9_Flat_Name_ChronoTaskBinding.definitions(__9_Flat_Name()).single().id)
    }


    @Test
    fun `nested classes with same simple name in different outer classes produce distinct bindings`() {
        val targetA = OuterA.NestedClass()
        val defsA = OuterA_NestedClass_ChronoTaskBinding.definitions(targetA)
        assertEquals(1, defsA.size)
        assertEquals("nested-a-task", defsA.single().id)

        val targetB = OuterB.NestedClass()
        val defsB = OuterB_NestedClass_ChronoTaskBinding.definitions(targetB)
        assertEquals(1, defsB.size)
        assertEquals("nested-b-task", defsB.single().id)
    }

    @Test
    fun `generic target classes generate typed generic definitions and register functions`() {
        val target = GenericTarget("test-payload")
        val defs = GenericTarget_ChronoTaskBinding.definitions(target)
        assertEquals(1, defs.size)
        assertEquals("generic-target-task", defs.single().id)
    }
}
