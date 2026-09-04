package com.vacster.problip.audio

import com.vacster.problip.core.KotlinRandomSource
import com.vacster.problip.core.RandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class PoolScript(vararg script: Long) : RandomSource {
    private val values = script
    private var i = 0
    override fun nextLong(fromInclusive: Long, toInclusive: Long): Long = values[i++ % values.size]
}

class RandomPoolTest {

    @Test
    fun emptyPoolReturnsNull() {
        assertNull(RandomPool(PoolScript(0)).next(emptyList()))
    }

    @Test
    fun singleSoundIsAlwaysReturned() {
        val pool = RandomPool(PoolScript(0, 1, 5, 100))
        repeat(10) {
            assertEquals("only", pool.next(listOf("only")))
        }
    }

    @Test
    fun pickFollowsInjectedRandomUniformly() {
        val pool = RandomPool(PoolScript(0, 1, 2, 0))
        val sounds = listOf("a", "b", "c")
        assertEquals("a", pool.next(sounds))
        assertEquals("b", pool.next(sounds))
        assertEquals("c", pool.next(sounds))
        assertEquals("a", pool.next(sounds))
    }

    @Test
    fun productionRandomStaysInsideThePool() {
        val pool = RandomPool(KotlinRandomSource)
        val sounds = listOf("a", "b", "c", "d")
        repeat(10_000) {
            val pick = pool.next(sounds)
            assertEquals(true, sounds.contains(pick))
        }
    }
}
