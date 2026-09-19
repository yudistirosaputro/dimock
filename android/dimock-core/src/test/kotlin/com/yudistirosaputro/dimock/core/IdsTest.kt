package com.yudistirosaputro.dimock.core

import com.yudistirosaputro.dimock.core.engine.Ids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdsTest {
    @Test
    fun `ids are unique and sort by time`() {
        val a = Ids.transaction(1_000)
        val b = Ids.transaction(2_000)
        assertEquals(16, a.length)
        assertTrue(a < b)
        assertEquals(1000, (1..1000).map { Ids.transaction(5_000) }.toSet().size)
    }
}
