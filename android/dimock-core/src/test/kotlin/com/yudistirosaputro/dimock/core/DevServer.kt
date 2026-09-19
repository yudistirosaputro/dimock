@file:JvmName("DevServer")

package com.yudistirosaputro.dimock.core

import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.core.model.Transaction
import com.yudistirosaputro.dimock.core.server.WireServer

/**
 * Runs a WireServer on the JVM with a few seeded captures so the scripts under `scripts/contract` can exercise the protocol
 * without a device. Gradle: `./gradlew :dimock-core:runDevServer -Pport=6767`.
 */
fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: System.getProperty("dimock.port")?.toIntOrNull() ?: 6767
    val engine = DimockEngine(HostInfo("com.yudistirosaputro.dimock.devserver", "0.0.0", "dev"))
    engine.record(
        Transaction(
            id = "seed-portfolio", startedAt = System.currentTimeMillis() - 2_000, durationMs = 241, method = "GET",
            url = "https://api.example.com/v1/portfolio/summary", host = "api.example.com", path = "/v1/portfolio/summary",
            requestHeaders = mapOf("Authorization" to listOf("Bearer seed"), "Accept" to listOf("application/json")), requestBody = null,
            responseCode = 200, responseHeaders = mapOf("Content-Type" to listOf("application/json")),
            responseBody = Body.Text("""{"total":1250000,"positions":[{"symbol":"BBCA","qty":100}]}""", "application/json"),
            error = null, mocked = false, mockRuleId = null,
        ),
    )
    val server = WireServer(engine, port)
    val bound = server.start()
    println("dimock dev wire server on http://127.0.0.1:$bound  (Ctrl-C to stop)")
    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
    Thread.currentThread().join()
}
