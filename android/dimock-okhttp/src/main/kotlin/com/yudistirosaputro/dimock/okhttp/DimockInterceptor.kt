package com.yudistirosaputro.dimock.okhttp

import com.yudistirosaputro.dimock.core.DimockEngine
import com.yudistirosaputro.dimock.core.engine.Ids
import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.core.model.FailType
import com.yudistirosaputro.dimock.core.model.Outcome
import com.yudistirosaputro.dimock.core.model.RequestSnapshot
import com.yudistirosaputro.dimock.core.model.Respond
import com.yudistirosaputro.dimock.core.model.Transaction
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * One interceptor does both jobs: decide (mock, fail, or pass through) and capture.
 * Install it as an application interceptor, before any logging interceptor you want to see the mocked response.
 */
class DimockInterceptor internal constructor(
    private val engineProvider: () -> DimockEngine?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = { ms -> if (ms > 0) Thread.sleep(ms) },
) : Interceptor {

    constructor(engine: DimockEngine) : this({ engine })

    override fun intercept(chain: Interceptor.Chain): Response {
        val engine = engineProvider() ?: return chain.proceed(chain.request())
        val request = chain.request()
        val snapshot = snapshot(request, engine)
        val started = clock()

        return when (val outcome = engine.resolve(snapshot)) {
            is Outcome.Mock -> {
                sleeper(outcome.respond.delayMs)
                val response = mockResponse(request, outcome.respond)
                engine.record(transaction(snapshot, started, response, error = null, mocked = true, ruleId = outcome.rule.id, engine))
                response
            }

            is Outcome.Failure -> {
                val type = outcome.fail.type
                when (type) {
                    FailType.TIMEOUT -> {
                        val wait = if (outcome.fail.delayMs > 0) outcome.fail.delayMs else chain.readTimeoutMillis().toLong().takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS
                        sleeper(wait)
                        val e = SocketTimeoutException("dimock: timed out after $wait ms (rule ${outcome.rule.id})")
                        engine.record(transaction(snapshot, started, null, e.message, mocked = true, ruleId = outcome.rule.id, engine))
                        throw e
                    }
                    FailType.CONNECTION_RESET -> {
                        sleeper(outcome.fail.delayMs)
                        val e = IOException("dimock: connection reset (rule ${outcome.rule.id})")
                        engine.record(transaction(snapshot, started, null, e.message, mocked = true, ruleId = outcome.rule.id, engine))
                        throw e
                    }
                    FailType.MALFORMED_BODY -> {
                        sleeper(outcome.fail.delayMs)
                        val response = mockResponse(request, Respond(status = 200, headers = mapOf("Content-Type" to "application/json"), body = MALFORMED_JSON))
                        engine.record(transaction(snapshot, started, response, null, mocked = true, ruleId = outcome.rule.id, engine))
                        response
                    }
                    FailType.EMPTY_BODY -> {
                        sleeper(outcome.fail.delayMs)
                        val response = mockResponse(request, Respond(status = 200, headers = mapOf("Content-Type" to "application/json"), body = ""))
                        engine.record(transaction(snapshot, started, response, null, mocked = true, ruleId = outcome.rule.id, engine))
                        response
                    }
                }
            }

            Outcome.PassThrough -> {
                val response = try {
                    chain.proceed(request)
                } catch (e: IOException) {
                    engine.record(transaction(snapshot, started, null, e.toString(), mocked = false, ruleId = null, engine))
                    throw e
                }
                engine.record(transaction(snapshot, started, response, null, mocked = false, ruleId = null, engine))
                response
            }
        }
    }

    // ---- building blocks ------------------------------------------------------------------------------------

    private fun snapshot(request: Request, engine: DimockEngine): RequestSnapshot {
        val url = request.url
        val query = LinkedHashMap<String, MutableList<String>>()
        for (name in url.queryParameterNames) query[name] = url.queryParameterValues(name).map { it ?: "" }.toMutableList()
        return RequestSnapshot(
            method = request.method,
            url = url.toString(),
            host = url.host,
            path = url.encodedPath,
            query = query,
            headers = request.headers.toMultimap(),
            body = readRequestBody(request, engine.config.maxBodyBytes),
            bodyContentType = request.body?.contentType()?.toString(),
        )
    }

    private fun readRequestBody(request: Request, limit: Int): String? {
        val body = request.body ?: return null
        if (body.isDuplex() || body.isOneShot()) return null
        val ct = body.contentType()?.toString()
        if (engineProvider()?.limiter?.isBinary(ct) == true) return null
        val buffer = Buffer()
        return try {
            body.writeTo(buffer)
            if (buffer.size > limit) buffer.readUtf8(limit.toLong()) else buffer.readUtf8()
        } catch (_: IOException) {
            null
        }
    }

    private fun mockResponse(request: Request, respond: Respond): Response {
        val headers = Headers.Builder().apply {
            respond.headers.forEach { (k, v) -> add(k, v) }
            if (respond.headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) add("Content-Type", "application/json; charset=utf-8")
            add(MOCK_HEADER, "1")
        }.build()
        val contentType = headers["Content-Type"]?.toMediaTypeOrNull()
        val bodyText = respond.body ?: ""
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(respond.status)
            .message("dimock")
            .headers(headers)
            .body(bodyText.toResponseBody(contentType))
            .sentRequestAtMillis(clock())
            .receivedResponseAtMillis(clock())
            .build()
    }

    private fun transaction(
        snapshot: RequestSnapshot,
        started: Long,
        response: Response?,
        error: String?,
        mocked: Boolean,
        ruleId: String?,
        engine: DimockEngine,
    ): Transaction {
        val limiter = engine.limiter
        val requestCt = snapshot.bodyContentType
            ?: snapshot.headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value?.firstOrNull()
        val requestBody: Body? = snapshot.body?.let { limiter.text(it, requestCt) }
        val responseBody: Body? = response?.let { captureResponseBody(it, engine) }
        return Transaction(
            id = Ids.transaction(started),
            startedAt = started,
            durationMs = clock() - started,
            method = snapshot.method,
            url = snapshot.url,
            host = snapshot.host,
            path = snapshot.path,
            requestHeaders = snapshot.headers,
            requestBody = requestBody,
            responseCode = response?.code,
            responseHeaders = response?.headers?.toMultimap() ?: emptyMap(),
            responseBody = responseBody,
            error = error,
            mocked = mocked,
            mockRuleId = ruleId,
        )
    }

    private fun captureResponseBody(response: Response, engine: DimockEngine): Body? {
        val body = response.body ?: return null
        val ct = body.contentType()?.toString()
        val declared = body.contentLength()
        if (engine.limiter.isBinary(ct)) return engine.limiter.binary(if (declared >= 0) declared else -1, ct)
        val limit = engine.config.maxBodyBytes
        return try {
            val peeked = response.peekBody(limit.toLong() + 1)
            val text = peeked.string()
            if (text.length > limit) Body.Truncated(text.substring(0, limit), if (declared >= 0) declared else text.length.toLong(), ct)
            else Body.Text(text, ct)
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        const val MOCK_HEADER = "X-Dimock-Mocked"
        const val DEFAULT_TIMEOUT_MS = 10_000L
        const val MALFORMED_JSON = """{"dimock":"malformed_body","data":[1,2,"""
    }
}
