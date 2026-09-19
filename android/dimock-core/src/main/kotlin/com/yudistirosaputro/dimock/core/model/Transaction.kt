package com.yudistirosaputro.dimock.core.model

/** A captured HTTP exchange. Headers are already redacted when a Transaction is constructed. */
data class Transaction(
    val id: String,
    val startedAt: Long,
    val durationMs: Long?,
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val requestHeaders: Map<String, List<String>>,
    val requestBody: Body?,
    val responseCode: Int?,
    val responseHeaders: Map<String, List<String>>,
    val responseBody: Body?,
    val error: String?,
    val mocked: Boolean,
    val mockRuleId: String?,
    val tag: String? = null,
) {
    /** Approximate footprint used by the capture ring buffer. */
    val sizeBytes: Long
        get() = 256L + url.length + (requestBody?.storedBytes ?: 0) + (responseBody?.storedBytes ?: 0) +
            requestHeaders.entries.sumOf { (k, v) -> k.length + v.sumOf { it.length } } +
            responseHeaders.entries.sumOf { (k, v) -> k.length + v.sumOf { it.length } }
}

sealed interface Body {
    val storedBytes: Long

    data class Text(val text: String, val contentType: String?) : Body {
        override val storedBytes: Long get() = text.length.toLong()
    }

    /** Text body longer than the configured limit: only the first `text.length` chars are kept. */
    data class Truncated(val text: String, val totalBytes: Long, val contentType: String?) : Body {
        override val storedBytes: Long get() = text.length.toLong()
    }

    /** Binary payload (image, protobuf, ...): metadata only. */
    data class Binary(val totalBytes: Long, val contentType: String?) : Body {
        override val storedBytes: Long get() = 0
    }
}

/** The request as seen by the interceptor before any decision is made. */
data class RequestSnapshot(
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val query: Map<String, List<String>>,
    val headers: Map<String, List<String>>,
    val body: String?,
    /** Content type of the request body when known (OkHttp only adds the header later in the chain). */
    val bodyContentType: String? = null,
)
