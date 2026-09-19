package com.yudistirosaputro.dimock.core.engine

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

/** Time-sortable transaction ids: 10 chars of millisecond timestamp (base32) + 3 chars counter + 3 random. */
object Ids {
    private const val ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz"
    private val counter = AtomicInteger()
    private val random = SecureRandom()

    fun transaction(now: Long = System.currentTimeMillis()): String {
        val sb = StringBuilder(16)
        var t = now
        val time = CharArray(10)
        for (i in 9 downTo 0) { time[i] = ALPHABET[(t and 31).toInt()]; t = t shr 5 }
        sb.append(time)
        var c = counter.incrementAndGet() and 0x7fff
        for (i in 0 until 3) { sb.append(ALPHABET[c and 31]); c = c shr 5 }
        repeat(3) { sb.append(ALPHABET[random.nextInt(32)]) }
        return sb.toString()
    }
}
