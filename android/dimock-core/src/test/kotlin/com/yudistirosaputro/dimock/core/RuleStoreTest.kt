package com.yudistirosaputro.dimock.core

import com.yudistirosaputro.dimock.core.engine.RuleStore
import com.yudistirosaputro.dimock.core.engine.RuleStorage
import com.yudistirosaputro.dimock.core.model.Fail
import com.yudistirosaputro.dimock.core.model.FailType
import com.yudistirosaputro.dimock.core.model.Match
import com.yudistirosaputro.dimock.core.model.MockRule
import com.yudistirosaputro.dimock.core.model.Outcome
import com.yudistirosaputro.dimock.core.model.RequestSnapshot
import com.yudistirosaputro.dimock.core.model.Respond
import com.yudistirosaputro.dimock.core.model.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleStoreTest {

    private val login = RequestSnapshot("POST", "https://api.example.com/v1/auth/login", "api.example.com", "/v1/auth/login", emptyMap(), emptyMap(), null)
    private val orders = RequestSnapshot("GET", "https://api.example.com/v1/orders", "api.example.com", "/v1/orders", emptyMap(), emptyMap(), null)

    private fun rule(id: String, priority: Int = 0, status: Int = 500, times: Int? = null, path: String = "/v1/auth/login", enabled: Boolean = true) =
        MockRule(id = id, enabled = enabled, priority = priority, match = Match(method = "POST", path = path), times = times, respond = Respond(status = status, body = """{"error":"internal"}"""))

    private fun store(vararg rules: MockRule): RuleStore = RuleStore(InMemoryStorage()).also { it.replaceAll(rules.toList()) }

    private fun mockStatus(outcome: Outcome) = (outcome as Outcome.Mock).respond.status

    @Test
    fun `stored rule mocks a matching request and pass-through otherwise`() {
        val s = store(rule("login-error"))
        assertEquals(500, mockStatus(s.resolve(login)))
        assertEquals(Outcome.PassThrough, s.resolve(orders))
    }

    @Test
    fun `higher priority wins`() {
        val s = store(rule("low", priority = 0, status = 500), rule("high", priority = 10, status = 503))
        assertEquals(503, mockStatus(s.resolve(login)))
    }

    @Test
    fun `equal priority - most recently added wins`() {
        val s = store(rule("first", status = 500))
        s.upsert(rule("second", status = 418))
        assertEquals(418, mockStatus(s.resolve(login)))
    }

    @Test
    fun `disabled rules are skipped`() {
        val s = store(rule("off", priority = 99, status = 503, enabled = false), rule("on", status = 500))
        assertEquals(500, mockStatus(s.resolve(login)))
    }

    @Test
    fun `times 1 - first request mocked, second passes through, rule shown spent and disabled`() {
        val s = store(rule("once", times = 1))
        assertEquals(500, mockStatus(s.resolve(login)))
        assertEquals(Outcome.PassThrough, s.resolve(login))
        val entry = s.entries().single()
        assertFalse(entry.rule.enabled)
        assertEquals(0, entry.state.remaining)
        assertEquals(1, entry.state.hits)
        assertTrue(entry.state.spent)
    }

    @Test
    fun `sequence - timeout then 200 then clamps at last step`() {
        val seq = MockRule(
            id = "orders-seq",
            match = Match(method = "GET", path = "/v1/orders**"),
            sequence = listOf(
                Step.FailStep(Fail(FailType.TIMEOUT)),
                Step.RespondStep(Respond(status = 200, body = "[]")),
            ),
        )
        val s = store(seq)
        val first = s.resolve(orders)
        assertTrue(first is Outcome.Failure && first.fail.type == FailType.TIMEOUT)
        assertEquals(200, mockStatus(s.resolve(orders)))
        assertEquals(200, mockStatus(s.resolve(orders)))
        assertEquals(1, s.entries().single().state.sequenceIndex)
        assertEquals(3, s.entries().single().state.hits)
    }

    @Test
    fun `reset restores counters and re-enables a spent rule`() {
        val s = store(rule("once", times = 1))
        s.resolve(login)
        s.reset("once")
        val entry = s.entries().single()
        assertTrue(entry.rule.enabled)
        assertEquals(1, entry.state.remaining)
        assertEquals(0, entry.state.hits)
        assertEquals(500, mockStatus(s.resolve(login)))
    }

    @Test
    fun `setEnabled toggles without touching counters`() {
        val s = store(rule("r", times = 3))
        s.resolve(login)
        s.setEnabled("r", false)
        assertEquals(Outcome.PassThrough, s.resolve(login))
        s.setEnabled("r", true)
        assertEquals(2, s.entries().single().state.remaining)
    }

    @Test
    fun `replaceAll keeps counters for an unchanged rule and resets a changed one`() {
        val s = store(rule("keep", times = 3), rule("drop", path = "/v1/orders"))
        s.resolve(login)
        s.replaceAll(listOf(rule("keep", times = 3), rule("new", status = 418, path = "/x")))
        assertEquals(listOf("keep", "new"), s.entries().map { it.rule.id })
        assertEquals(1, s.entries().first { it.rule.id == "keep" }.state.hits)
        assertEquals(2, s.entries().first { it.rule.id == "keep" }.state.remaining)
        assertNull(s.entries().first { it.rule.id == "new" }.state.lastHitAt)

        s.replaceAll(listOf(rule("keep", times = 3, priority = 1)))
        assertEquals(0, s.entries().single().state.hits)
        assertEquals(3, s.entries().single().state.remaining)
    }

    @Test
    fun `remove and clear`() {
        val s = store(rule("a"), rule("b", path = "/v1/orders"))
        s.remove("a")
        assertEquals(listOf("b"), s.entries().map { it.rule.id })
        s.clear()
        assertTrue(s.entries().isEmpty())
    }

    @Test
    fun `rules survive a restart through storage`() {
        val storage = InMemoryStorage()
        RuleStore(storage).replaceAll(listOf(rule("persisted", priority = 5)))
        val reloaded = RuleStore(storage)
        assertEquals("persisted", reloaded.entries().single().rule.id)
        assertEquals(5, reloaded.entries().single().rule.priority)
        assertEquals(500, mockStatus(reloaded.resolve(login)))
    }

    @Test
    fun `activeCount counts enabled non-spent rules`() {
        val s = store(rule("a", times = 1), rule("b", enabled = false), rule("c", path = "/x"))
        assertEquals(2, s.activeCount())
        s.resolve(login)
        assertEquals(1, s.activeCount())
    }

    @Test
    fun `listeners are notified on every change`() {
        val s = store()
        var changes = 0
        s.addListener { changes++ }
        s.upsert(rule("a"))
        s.setEnabled("a", false)
        s.remove("a")
        assertEquals(3, changes)
    }
}

class InMemoryStorage : RuleStorage {
    private var json: String? = null
    override fun read(): String? = json
    override fun write(json: String) { this.json = json }
}
