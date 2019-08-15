package ebay.tx.stats

import com.google.common.base.Stopwatch
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.Instant.now
import javax.inject.Inject
import kotlin.random.Random
import kotlin.streams.toList


@MicronautTest(application = TxStatsApp::class)
class TxtStatsTests {

    @Inject
    lateinit var server: EmbeddedServer
    @Inject
    lateinit var context: ApplicationContext
    @Inject
    private lateinit var txRepo: TxRepo


    @Test
    fun testAddSalesAllValid() {
        txRepo.cache.clear()

        val maxBufferSize = Config.SECONDS_TO_BUFFER * 1000
        val start = now()

        val populationTimer = Stopwatch.createStarted()
        val orders1 = populateTxRepo(txRepo, maxBufferSize, start) { BigDecimal(Random.nextDouble(0.01, 1000.0)) }
        populationTimer.stop()

        assert(orders1.size == txRepo.cache.size)

        val localOrderStats = statsFromList(orders1)
        assert(orders1.size == localOrderStats.orderCount)
        { "Local reduction correctness" }

        val repoTotals = txRepo.cache.values.reduce { v1, v2 -> v1.sumWith(v2) }
        val statisticsTimer = Stopwatch.createStarted()
        val totalsForLastMinute = txRepo.fetchStats()
        statisticsTimer.stop()
        assertEquals(localOrderStats.amount, BigDecimal(repoTotals.amount).divide(BigDecimal(100)).setScale(2))
        { "Local data should match repo totals (ignoring the time)" }

        assert(txRepo.fetchStats(start.minusSeconds(Config.SECONDS_TO_BUFFER)).amount == localOrderStats.amount)
        { "Amount of orders matches" }

        assert(totalsForLastMinute.amount < localOrderStats.amount)
        { "As certain time has passed, the last-minute-stats should be less than original" }

        log.info("Population time:\t$populationTimer\tCalculation time:\t$statisticsTimer")

        val order2sumFixed = 1000 //Each order's amount is the same during the second run

        val repoStatsBefore = txRepo.fetchStats(start.minusSeconds(Config.SECONDS_TO_BUFFER))
        populateTxRepo(txRepo, maxBufferSize, start) { BigDecimal(order2sumFixed) }
        val repoStatsAfter = txRepo.fetchStats(start.minusSeconds(Config.SECONDS_TO_BUFFER))

        assertEquals(repoStatsAfter.orderCount / 2, repoStatsBefore.orderCount)
        { "Amount of orders should be doubled after the second run" }

        assertEquals(repoStatsAfter.amount - repoStatsBefore.amount, BigDecimal(Config.SECONDS_TO_BUFFER * 1000 * order2sumFixed).setScale(2))
        { "The total amount should differ by 60M after the second run" }
    }

    private fun statsFromList(orders: List<Statistics>): Statistics {
        return orders.reduce { a, b ->
            Statistics(a.t, a.amount.plus(b.amount), a.orderCount + b.orderCount)
        }
    }

    private fun populateTxRepo(
            repo: TxRepo,
            maxBufferSize: Long,
            start: Instant,
            amountGen: () -> BigDecimal) = LongRange(0, maxBufferSize - 1)
            // We populate TxRepo in parallel with randomized data
            .shuffled().toList().parallelStream().map {
                val t = start.minusMillis(it)
                val amount = amountGen().setScale(2, RoundingMode.FLOOR)
                repo.add(t, amount) // populate
                Statistics(t, amount, 1) // data for further analysis
            }.toList()

    companion object {
        private val log = LoggerFactory.getLogger(TxtStatsTests::class.java)
    }

}