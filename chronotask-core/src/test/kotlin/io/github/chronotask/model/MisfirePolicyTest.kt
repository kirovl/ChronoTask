package io.github.chronotask.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MisfirePolicyTest {

    @Test
    fun `test MisfirePolicy singletons`() {
        assertSame(MisfirePolicy.Skip, MisfirePolicy.SKIP)
        assertSame(MisfirePolicy.FireOnce, MisfirePolicy.FIRE_ONCE)
    }

    @Test
    fun `test CatchUp validation`() {
        assertThrows<IllegalArgumentException> { MisfirePolicy.CatchUp(0) }
        assertThrows<IllegalArgumentException> { MisfirePolicy.CatchUp(-1) }

        val catchUpDefault = MisfirePolicy.CatchUp()
        assertEquals(100, catchUpDefault.maxExecutions)

        val catchUpCustom = MisfirePolicy.CatchUp(20)
        assertEquals(20, catchUpCustom.maxExecutions)

        val catchUpStatic = MisfirePolicy.catchUp(50)
        assertEquals(50, catchUpStatic.maxExecutions)

        assertEquals(catchUpCustom, MisfirePolicy.CatchUp(20))
    }
}
