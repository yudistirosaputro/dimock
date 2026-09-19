package com.yudistirosaputro.dimock.core.engine

import com.yudistirosaputro.dimock.core.model.Transaction
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList

/** In-memory ring buffer of captured transactions, bounded by count and by approximate bytes. Newest first on read. */
class CaptureStore(private val maxTransactions: Int, private val maxStoreBytes: Long) {

    private val lock = Any()
    private val deque = ArrayDeque<Transaction>() // oldest at head
    private val byId = HashMap<String, Transaction>()
    private var bytes = 0L
    private val listeners = CopyOnWriteArrayList<(Transaction) -> Unit>()

    fun add(tx: Transaction) {
        synchronized(lock) {
            deque.addLast(tx)
            byId[tx.id] = tx
            bytes += tx.sizeBytes
            while (deque.size > maxTransactions || (bytes > maxStoreBytes && deque.size > 1)) evictOldest()
        }
        for (l in listeners) l(tx)
    }

    fun get(id: String): Transaction? = synchronized(lock) { byId[id] }

    fun list(
        limit: Int = Int.MAX_VALUE,
        since: Long? = null,
        path: String? = null,
        method: String? = null,
        mocked: Boolean? = null,
    ): List<Transaction> = synchronized(lock) {
        deque.descendingIterator().asSequence()
            .filter { since == null || it.startedAt > since }
            .filter { path == null || Matcher.pathMatches(path, it.path) }
            .filter { method == null || it.method.equals(method, ignoreCase = true) }
            .filter { mocked == null || it.mocked == mocked }
            .take(limit)
            .toList()
    }

    fun count(): Int = synchronized(lock) { deque.size }
    fun sizeBytes(): Long = synchronized(lock) { bytes }

    fun clear() = synchronized(lock) {
        deque.clear(); byId.clear(); bytes = 0
    }

    fun addListener(listener: (Transaction) -> Unit) { listeners += listener }
    fun removeListener(listener: (Transaction) -> Unit) { listeners -= listener }

    private fun evictOldest() {
        val old = deque.pollFirst() ?: return
        byId.remove(old.id)
        bytes -= old.sizeBytes
    }
}
