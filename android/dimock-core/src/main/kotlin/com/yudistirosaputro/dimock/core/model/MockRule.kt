package com.yudistirosaputro.dimock.core.model

/**
 * A mock rule as authored by the agent, the CLI, or the in-app "Force a state" sheet (`LocalPresets`).
 *
 * Matching: every present field of [match] must match (AND). Among matching, enabled rules the highest
 * [priority] wins; ties go to the most recently added rule. [times] consumes the rule after N hits and
 * auto-disables it (it stays in the store, marked spent). [sequence] overrides [respond]/[fail] per hit,
 * clamping at the last step.
 */
data class MockRule(
    val id: String,
    val name: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val match: Match,
    val times: Int? = null,
    val sequence: List<Step> = emptyList(),
    val respond: Respond? = null,
    val fail: Fail? = null,
) {
    init {
        require(id.isNotBlank()) { "rule id must not be blank" }
        require(sequence.isNotEmpty() || (respond != null) xor (fail != null)) {
            "rule '$id' needs exactly one of respond | fail, or a non-empty sequence"
        }
        require(times == null || times > 0) { "rule '$id': times must be > 0" }
    }
}

data class Match(
    val method: String? = null,
    /** Glob (`*` within a segment, `**` across segments) or `re:<regex>`. Matched against the URL path only. */
    val path: String? = null,
    /** Glob against the host, e.g. `api.example.com` or `*.example.com`. */
    val host: String? = null,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    /** JSONPath assertions against the request body. */
    val body: List<BodyMatch> = emptyList(),
)

/** `path` is a JSONPath subset: `$`, `.key`, `[index]`. `equals` compares against the stringified leaf. */
data class BodyMatch(val path: String, val equals: String)

sealed interface Step {
    data class RespondStep(val respond: Respond) : Step
    data class FailStep(val fail: Fail) : Step
}

data class Respond(
    val status: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    /** Inline body. `bodyFile` is resolved by the client before it reaches the device, so only `body` exists here. */
    val body: String? = null,
    val delayMs: Long = 0,
) {
    init { require(status in 100..599) { "status must be 100..599" } }
}

data class Fail(val type: FailType, val delayMs: Long = 0)

enum class FailType(val wire: String) {
    TIMEOUT("timeout"),
    CONNECTION_RESET("connection_reset"),
    MALFORMED_BODY("malformed_body"),
    EMPTY_BODY("empty_body");

    companion object {
        fun fromWire(value: String): FailType =
            entries.firstOrNull { it.wire == value } ?: throw IllegalArgumentException("unknown fail type '$value'")
    }
}

/** Runtime counters kept beside a rule; reset by PATCH /rules/{id} or when the rule is replaced. */
data class RuleState(
    val hits: Int = 0,
    val remaining: Int? = null,
    val sequenceIndex: Int = 0,
    val lastHitAt: Long? = null,
) {
    val spent: Boolean get() = remaining != null && remaining <= 0
}

/** What the interceptor should do for one request. */
sealed interface Outcome {
    data class Mock(val rule: MockRule, val respond: Respond) : Outcome
    data class Failure(val rule: MockRule, val fail: Fail) : Outcome
    data object PassThrough : Outcome
}
