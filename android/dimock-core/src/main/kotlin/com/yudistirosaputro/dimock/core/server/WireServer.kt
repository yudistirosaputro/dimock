package com.yudistirosaputro.dimock.core.server

import com.yudistirosaputro.dimock.core.DimockEngine
import com.yudistirosaputro.dimock.core.engine.ActivityEntry
import com.yudistirosaputro.dimock.core.json.RuleCodec
import com.yudistirosaputro.dimock.core.json.TransactionCodec
import com.yudistirosaputro.dimock.core.json.WireFormatException
import com.yudistirosaputro.dimock.core.json.bool
import com.yudistirosaputro.dimock.core.json.parse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The device side of the wire protocol (docs/wire-protocol.md): a small HTTP/1.1 server on loopback.
 * Hand-written on ServerSocket so the library adds no HTTP server dependency to host apps.
 */
class WireServer(
    private val engine: DimockEngine,
    private val port: Int = DimockEngine.DEFAULT_PORT,
    private val bindAddress: String = "127.0.0.1",
) {
    private var socket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "dimock-wire").apply { isDaemon = true } }
    private var acceptThread: Thread? = null

    val boundAddress: String get() = bindAddress
    val boundPort: Int get() = socket?.localPort ?: -1

    /** Starts accepting and returns the bound port (useful with port 0 in tests). */
    fun start(): Int {
        if (running.getAndSet(true)) return boundPort
        val ss = ServerSocket(port, 50, InetAddress.getByName(bindAddress))
        socket = ss
        acceptThread = Thread({
            while (running.get()) {
                val client = try { ss.accept() } catch (_: SocketException) { break } catch (_: IOException) { continue }
                pool.execute { handle(client) }
            }
        }, "dimock-accept").apply { isDaemon = true; start() }
        return ss.localPort
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { socket?.close() }
        socket = null
        pool.shutdownNow()
    }

    // ---- request handling ------------------------------------------------------------------------------------

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: String)

    private class Response(val status: Int, val body: String? = null, val contentType: String = "application/json; charset=utf-8", val extraHeaders: Map<String, String> = emptyMap())

    private fun handle(client: Socket) {
        client.use { s ->
            s.soTimeout = 15_000
            val input = BufferedInputStream(s.getInputStream())
            val output = s.getOutputStream()
            val request = try { readRequest(input) } catch (e: Exception) { write(output, Response(400, error("malformed request: ${e.message}"))); return }
                ?: return
            if (request.method == "GET" && request.path == "/events") {
                streamEvents(s, output)
                return
            }
            val response = try { route(request) } catch (e: WireFormatException) { Response(400, error(e.message ?: "invalid payload")) } catch (e: IllegalArgumentException) { Response(400, error(e.message ?: "invalid payload")) } catch (e: Exception) { Response(500, error("internal: ${e.message}")) }
            write(output, response)
        }
    }

    private fun route(r: Request): Response {
        val segments = r.path.trim('/').split('/').filter { it.isNotEmpty() }
        fun requireWrite(): Response? = if (engine.agentWriteEnabled) null else Response(403, error("agent_write_disabled"))
        fun read(summary: String) = engine.logActivity(ActivityEntry.Kind.READ, summary, "${r.method} ${r.path}")
        fun wrote(summary: String) = engine.logActivity(ActivityEntry.Kind.WRITE, summary, "${r.method} ${r.path}")

        return when {
            r.method == "GET" && r.path == "/health" -> {
                engine.clientSeen(r.headers["x-dimock-client"])
                Response(200, engine.healthJson())
            }

            r.path == "/transactions" && r.method == "GET" -> {
                val list = engine.captures.list(
                    limit = r.query["limit"]?.toIntOrNull() ?: 100,
                    since = r.query["since"]?.toLongOrNull(),
                    path = r.query["path"],
                    method = r.query["method"],
                    mocked = r.query["mocked"]?.toBooleanStrictOrNull(),
                )
                read("Read ${list.size} capture${if (list.size == 1) "" else "s"}")
                Response(200, TransactionCodec.encodeSummaries(list))
            }
            r.path == "/transactions" && r.method == "DELETE" -> {
                requireWrite()?.let { return it }
                engine.captures.clear(); wrote("Cleared captures"); Response(204)
            }
            segments.size == 2 && segments[0] == "transactions" && r.method == "GET" -> {
                val tx = engine.captures.get(segments[1]) ?: return Response(404, error("transaction not found"))
                read("Read capture ${tx.method} ${tx.path}")
                Response(200, TransactionCodec.encodeFull(tx))
            }

            r.path == "/rules" && r.method == "GET" -> Response(200, engine.rulesJson())
            r.path == "/rules" && r.method == "PUT" -> {
                requireWrite()?.let { return it }
                val rules = RuleCodec.decodeList(r.body)
                engine.rules.replaceAll(rules)
                wrote("Pushed ${rules.size} rule${if (rules.size == 1) "" else "s"}")
                Response(200, engine.rulesJson())
            }
            r.path == "/rules" && r.method == "POST" -> {
                requireWrite()?.let { return it }
                val rule = RuleCodec.decode(r.body)
                engine.rules.upsert(rule)
                wrote("Added rule ${rule.name ?: rule.id}")
                val entry = engine.rules.get(rule.id)!!
                Response(201, RuleCodec.encodeEntry(entry.rule, entry.state))
            }
            r.path == "/rules" && r.method == "DELETE" -> {
                requireWrite()?.let { return it }
                engine.rules.clear(); wrote("Removed all rules"); Response(204)
            }
            segments.size == 2 && segments[0] == "rules" -> {
                val id = segments[1]
                val entry = engine.rules.get(id) ?: return Response(404, error("rule not found"))
                when (r.method) {
                    "GET" -> Response(200, RuleCodec.encodeEntry(entry.rule, entry.state))
                    "PATCH" -> {
                        requireWrite()?.let { return it }
                        val patch = parse(r.body) as? JsonObject ?: throw WireFormatException("patch must be an object")
                        if (patch.bool("reset") == true) engine.rules.reset(id)
                        patch.bool("enabled")?.let { engine.rules.setEnabled(id, it) }
                        val updated = engine.rules.get(id)!!
                        wrote(
                            when {
                                patch.bool("reset") == true -> "Reset rule ${updated.rule.name ?: id}"
                                patch.bool("enabled") == false -> "Disabled rule ${updated.rule.name ?: id}"
                                else -> "Enabled rule ${updated.rule.name ?: id}"
                            },
                        )
                        Response(200, RuleCodec.encodeEntry(updated.rule, updated.state))
                    }
                    "DELETE" -> {
                        requireWrite()?.let { return it }
                        engine.rules.remove(id); wrote("Removed rule ${entry.rule.name ?: id}"); Response(204)
                    }
                    else -> Response(405, error("method not allowed"))
                }
            }

            r.path == "/agent/activity" && r.method == "GET" ->
                Response(200, JsonArray(engine.activity.list(r.query["limit"]?.toIntOrNull() ?: 100).map { it.toJson() }).toString())
            r.path == "/agent/activity" && r.method == "DELETE" -> { engine.activity.clear(); Response(204) }

            else -> Response(404, error("no route for ${r.method} ${r.path}"))
        }
    }

    private fun streamEvents(socket: Socket, output: OutputStream) {
        socket.soTimeout = 0
        val head = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: keep-alive\r\nX-Dimock-Protocol: ${DimockEngine.PROTOCOL_VERSION}\r\n\r\n"
        output.write(head.toByteArray()); output.flush()
        output.write(": connected\n\n".toByteArray()); output.flush()
        val closed = AtomicBoolean(false)
        val unsubscribe = engine.events.subscribe { event ->
            if (closed.get()) return@subscribe
            try {
                synchronized(output) {
                    output.write("event: ${event.type}\ndata: ${event.data}\n\n".toByteArray())
                    output.flush()
                }
            } catch (_: IOException) {
                closed.set(true)
            }
        }
        try {
            // Block until the client goes away; a read returning -1 or throwing means the peer closed.
            val probe = socket.getInputStream()
            while (!closed.get() && running.get()) {
                try { if (probe.read() == -1) break } catch (_: IOException) { break }
            }
        } finally {
            closed.set(true)
            unsubscribe()
        }
    }

    // ---- HTTP plumbing ----------------------------------------------------------------------------------------

    private fun readRequest(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        if (requestLine.isBlank()) return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) throw IOException("bad request line")
        val method = parts[0].uppercase()
        val target = parts[1]
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (length > 0) {
            val buf = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(buf, read, length - read)
                if (n < 0) break
                read += n
            }
            String(buf, 0, read, Charsets.UTF_8)
        } else ""
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate {
            val k = it.substringBefore('=')
            val v = it.substringAfter('=', "")
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
        return Request(method, path, query, headers, body)
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
        }
        return buf.toString("UTF-8")
    }

    private fun write(output: OutputStream, response: Response) {
        val bytes = response.body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(response.status).append(' ').append(reason(response.status)).append("\r\n")
        sb.append("X-Dimock-Protocol: ").append(DimockEngine.PROTOCOL_VERSION).append("\r\n")
        if (bytes.isNotEmpty()) sb.append("Content-Type: ").append(response.contentType).append("\r\n")
        sb.append("Content-Length: ").append(bytes.size).append("\r\n")
        sb.append("Connection: close\r\n")
        response.extraHeaders.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("\r\n")
        try {
            output.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            if (bytes.isNotEmpty()) output.write(bytes)
            output.flush()
        } catch (_: IOException) {
            // client went away; nothing to do
        }
    }

    private fun error(message: String): String = buildJsonObject { put("error", message) }.toString()

    private fun reason(status: Int) = when (status) {
        200 -> "OK"; 201 -> "Created"; 204 -> "No Content"; 400 -> "Bad Request"; 403 -> "Forbidden"
        404 -> "Not Found"; 405 -> "Method Not Allowed"; 500 -> "Internal Server Error"; else -> "Status"
    }
}
