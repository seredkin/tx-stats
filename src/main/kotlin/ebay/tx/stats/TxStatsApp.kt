package ebay.tx.stats

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import ebay.tx.stats.Config.CLEANUP_AFTER
import ebay.tx.stats.Config.SECONDS_TO_BUFFER
import ebay.tx.stats.Config.mathContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponse.accepted
import io.micronaut.http.HttpResponse.ok
import io.micronaut.http.HttpResponse.serverError
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.runtime.Micronaut
import io.reactivex.Single
import io.reactivex.Single.just
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.Instant.now
import java.util.concurrent.ConcurrentSkipListMap
import javax.inject.Singleton
import javax.validation.constraints.Size

object TxStatsApp {

    @JvmStatic
    fun main(args: Array<String>) {
        Micronaut.build()
                .packages("ebay.tx.stats")
                .mainClass(TxStatsApp.javaClass)
                .start()
    }
}

/** Json Response for the '/statistics' endpoint */
data class Statistics(@JsonIgnore val t: Instant,
                      @JsonProperty("total_sales_amount") val amount: BigDecimal,
                      @JsonIgnore val orderCount: Int) {
    @JsonProperty("average_amount_per_order")
    fun avg() = amount.divide(BigDecimal(orderCount), RoundingMode.HALF_UP).setScale(2)
}

object Config {
    val mathContext: MathContext = MathContext.DECIMAL128
    const val SECONDS_TO_BUFFER: Long = 60
    const val CLEANUP_AFTER: Long = 120
}

@Controller
open class Rest(private val txRepo: TxRepo) {
    @Get("/statistics")
    open fun fetchStats(): HttpResponse<Single<Statistics>> = ok(just(txRepo.fetchStats()))

    @Post(
            value = "sales",
            processes = [MediaType.APPLICATION_FORM_URLENCODED],
            produces = [MediaType.APPLICATION_JSON],
            single = true)
    open fun addTx(
            @Size(max = 512) @QueryValue("sales_amount") amount: String,
            @Size(max = 2) @Body bodyStub: String?): HttpResponse<Single<String>> {
        return try {
            val txAmount = BigDecimal(amount, mathContext).setScale(2, RoundingMode.HALF_DOWN)
            txRepo.add(now(), txAmount);
            accepted()
        } catch (e: NumberFormatException) {
            val msg = "Cannot parse param:'sales_amount' value of $amount"
            log.error(msg)
            serverError(just(msg))
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Rest::class.java)
    }
}

@Singleton
class TxRepo {

    // Space requirements for 1 record: 70 bytes in total (instance: 16bytes, Int field: 16 bytes, 2x Long fields: 48 bytes)
    // 70 bytes * 120_000 records (max cache size before cleanup) = 8MB
    internal data class StatsRecord(val t: Long, val amount: Long, val orderCount: Int) {
        fun sumWith(sr: StatsRecord) = StatsRecord(this.t, this.amount + sr.amount, this.orderCount + sr.orderCount)
    }

    internal val cache = ConcurrentSkipListMap<Long, StatsRecord>()

    fun add(t: Instant, amount: BigDecimal) {
        val now = now()
        val expiration = now.minusSeconds(SECONDS_TO_BUFFER).toEpochMilli()

        if (now >= t && t.toEpochMilli() - expiration <= SECONDS_TO_BUFFER * 1000) {
            cache.merge(
                    t.toEpochMilli(),
                    StatsRecord(t.toEpochMilli(), amount.multiply(BigDecimal(100)).toLong(), 1)) //New entry
            { p1, p2 -> p1.sumWith(p2) } //Reduce with existing entry
        } else {
            println("Skipped value $t at $now")
        }
        cleanUp()
    }

    fun fetchStats(since: Instant = expirationTime()): Statistics =
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
        val cleanupBefore = now().minusSeconds(CLEANUP_AFTER).toEpochMilli()
        if (cache.firstEntry().key < cleanupBefore) {
            cache.entries.removeIf { it.key < cleanupBefore }
        }
    }

    private fun expirationTime() = now().minusSeconds(SECONDS_TO_BUFFER)

}




