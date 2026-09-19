package com.yudistirosaputro.dimock.ui

import com.google.common.truth.Truth.assertThat
import com.yudistirosaputro.dimock.core.engine.BodyView
import com.yudistirosaputro.dimock.core.engine.LocalPresets
import com.yudistirosaputro.dimock.core.engine.RuleEntry
import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.core.model.Match
import com.yudistirosaputro.dimock.core.model.MockRule
import com.yudistirosaputro.dimock.core.model.Respond
import com.yudistirosaputro.dimock.core.model.RuleState
import com.yudistirosaputro.dimock.core.model.Transaction
import com.yudistirosaputro.dimock.ui.theme.DimockColors
import org.junit.Test

/** Wave 4 rows and detail model (docs/prd.md §10). Pure JVM: no Android framework calls. */
class DisplayModelsTest {

    private fun tx(code: Int? = 200, error: String? = null, mocked: Boolean = false, body: Body? = Body.Text("""{"a":1}""", "application/json")) = Transaction(
        id = "t1", startedAt = 0, durationMs = 241, method = "get", url = "https://h/posts?x=1", host = "h", path = "/posts",
        requestHeaders = mapOf("Authorization" to listOf("«redacted»"), "Accept" to listOf("*/*")), requestBody = null,
        responseCode = code, responseHeaders = mapOf("Content-Type" to listOf("application/json")), responseBody = body,
        error = error, mocked = mocked, mockRuleId = if (mocked) "local:get-posts" else null,
    )

    @Test
    fun `traffic row for a plain call`() {
        val row = TrafficRow.from(tx(), null)
        assertThat(row.method).isEqualTo("GET")
        assertThat(row.status).isEqualTo("200")
        assertThat(row.statusClass).isEqualTo(2)
        assertThat(row.secondary).isEqualTo("h")
        assertThat(row.duration).isEqualTo("241 ms")
        assertThat(row.mocked).isFalse()
        assertThat(row.failed).isFalse()
    }

    @Test
    fun `mocked row names the rule, failed row shows ERR in red with the failure text`() {
        val mocked = TrafficRow.from(tx(code = 500, mocked = true), "GET /posts → 500")
        assertThat(mocked.secondary).isEqualTo("GET /posts → 500")
        assertThat(mocked.mocked).isTrue()
        val failed = TrafficRow.from(tx(code = null, error = "dimock: timed out after 10000 ms (rule r)", mocked = true), null)
        assertThat(failed.status).isEqualTo("ERR")
        assertThat(failed.statusColor).isEqualTo(DimockColors.Status5xx)
        assertThat(failed.failed).isTrue()
        assertThat(failed.secondary).isEqualTo("local:get-posts")
    }

    @Test
    fun `filters combine AND across groups OR within`() {
        val get200 = TrafficRow.from(tx(), null)
        val get500 = TrafficRow.from(tx(code = 500), null)
        val filters = TrafficFilters(methods = setOf("GET"), statusClasses = setOf(4, 5))
        assertThat(filters.matches(get200)).isFalse()
        assertThat(filters.matches(get500)).isTrue()
        assertThat(TrafficFilters(failedOnly = true).matches(get500)).isFalse()
        assertThat(TrafficFilters().isEmpty).isTrue()
    }

    @Test
    fun `rule row effect, origin and spent state`() {
        val local = RuleRow.from(RuleEntry(LocalPresets.status(tx(), 500), RuleState(), 1), now = 0)
        assertThat(local.effect).isEqualTo("500")
        assertThat(local.meta).containsExactly("local", "prio 100").inOrder()
        assertThat(local.spent).isFalse()
        val agent = MockRule(id = "a", name = "Login once", match = Match(method = "POST", path = "/login"), times = 1, respond = Respond(status = 401, body = "{}"))
        val spent = RuleRow.from(RuleEntry(agent.copy(enabled = false), RuleState(hits = 1, remaining = 0, lastHitAt = 0), 2), now = 120_000)
        assertThat(spent.spent).isTrue()
        assertThat(spent.effect).isEqualTo("401 · empty {}")
        assertThat(spent.meta).containsExactly("agent", "0 of 1 left", "1 hit", "last 2 min ago").inOrder()
    }

    @Test
    fun `detail model splits url, pretty prints, marks redacted headers and builds curl`() {
        val ui = DetailUi.from(tx(code = 500, mocked = true), LocalPresets.slow(tx(), 5_000), maxBodyBytes = 1024 * 1024)
        assertThat(ui.url).isEqualTo("https://h/posts")
        assertThat(ui.query).isEqualTo("?x=1")
        assertThat(ui.transport).isEqualTo("server error")
        assertThat(ui.mockRuleName).isEqualTo("GET /posts → slow")
        assertThat(ui.injectedDelay).isEqualTo("5.0 s")
        assertThat((ui.response.body as BodyView.Text).pretty).isEqualTo("{\n  \"a\": 1\n}")
        assertThat(ui.request.headers.first { it.name == "Authorization" }.redacted).isTrue()
        assertThat(ui.redactedHeaders).containsExactly("Authorization")
        assertThat(ui.curl).startsWith("curl -X GET https://h/posts?x=1")
        assertThat(ui.curl).doesNotContain("Bearer")
        assertThat(ui.truncationNote).isNull()
    }

    @Test
    fun `truncated bodies carry the note`() {
        val ui = DetailUi.from(tx(body = Body.Truncated("[1,2", 5_000_000, "application/json")), null, maxBodyBytes = 1024 * 1024)
        assertThat(ui.truncationNote).isEqualTo("… truncated at 1.0 MB")
    }
}
