package ebay.tx.stats

import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.ConcurrentSkipListMap
import javax.inject.Singleton

@Singleton
internal class TxRepo {

    // Space requirements for 1 record: 70 bytes in total (instance: 16bytes, Int field: 16 bytes, 2x Long fields: 48 bytes)
    // 70 bytes * 120_000 records (max cache size before cleanup) = 8MB
    internal data class StatsRecord(val t: Long, val amount: Long, val orderCount: Int) {
        fun sumWith(sr: StatsRecord) = StatsRecord(this.t, this.amount + sr.amount, this.orderCount + sr.orderCount)
    }

    internal val cache = ConcurrentSkipListMap<Long, StatsRecord>()

    internal fun add(t: Instant, amount: BigDecimal) {
        val now = Instant.now()
        val expiration = now.minusSeconds(Config.SECONDS_TO_BUFFER).toEpochMilli()

        if (now >= t && t.toEpochMilli() - expiration <= Config.SECONDS_TO_BUFFER * 1000) {
            cache.merge(
                    t.toEpochMilli(),
                    StatsRecord(t.toEpochMilli(), amount.multiply(BigDecimal(100)).toLong(), 1)) //New entry
            { p1, p2 -> p1.sumWith(p2) } //Reduce with existing entry
        } else {
            println("Skipped value $t at $now")
        }
        cleanUp()
    }

    internal fun fetchStats(since: Instant = expirationTime()): Statistics =
            when (cache.isEmpty() || cache.lastKey() < since.toEpochMilli()) {
                true -> Statistics(since, BigDecimal.ZERO, 0)
                false -> {
                    val stats = cache.tailMap(since.toEpochMilli()).values.parallelStream()
                            .reduce { sr1, sr2 -> sr1.sumWith(sr2) }.get()
                    val total = BigDecimal(stats.amount).divide(BigDecimal(100))
                    Statistics(t = since, amount = total, orderCount = stats.orderCount)
                }
            }

    private fun cleanUp() {
        val cleanupBefore = Instant.now().minusSeconds(Config.CLEANUP_AFTER).toEpochMilli()
        if (cache.firstEntry().key < cleanupBefore) {
            cache.entries.removeIf { it.key < cleanupBefore }
        }
    }

    private fun expirationTime() = Instant.now().minusSeconds(Config.SECONDS_TO_BUFFER)

}