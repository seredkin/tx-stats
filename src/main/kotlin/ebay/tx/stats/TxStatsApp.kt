package ebay.tx.stats

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import ebay.tx.stats.Config.SALES_AMOUNT
import ebay.tx.stats.Config.mathContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponse.accepted
import io.micronaut.http.HttpResponse.badRequest
import io.micronaut.http.HttpResponse.ok
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
data class Statistics(@JsonIgnore val t: Instant = now(),
                      @JsonProperty("total_sales_amount") val amount: BigDecimal,
                      @JsonIgnore val orderCount: Int) {
    @JsonProperty("average_amount_per_order")
    fun avg(): BigDecimal = when (orderCount > 0) {
        true -> amount.divide(BigDecimal(orderCount), RoundingMode.HALF_UP).setScale(2)
        else -> BigDecimal.ZERO
    }
}

data class Sales(@JsonProperty(SALES_AMOUNT) val amount: String)

internal object Config {
    val mathContext: MathContext = MathContext.DECIMAL128
    const val SECONDS_TO_BUFFER: Long = 60
    const val CLEANUP_AFTER: Long = 120
    const val SALES_AMOUNT = "sales_amount"

}

@Controller
internal open class Rest(private val txRepo: TxRepo) {
    @Get("/statistics")
    open fun fetchStats(): HttpResponse<Single<Statistics>> = ok(just(txRepo.fetchStats()))

    @Post(
            value = "sales",
            processes = [MediaType.APPLICATION_FORM_URLENCODED],
            produces = [MediaType.APPLICATION_JSON],
            single = true)
    open fun addTx(
            @Size(max = 512) @QueryValue(SALES_AMOUNT) amount: String,
            @Size(max = 2) @Body bodyStub: String?): HttpResponse<Single<String>> {
        return try {
            val txAmount = BigDecimal(amount, mathContext).setScale(2, RoundingMode.HALF_DOWN)
            txRepo.add(now(), txAmount);
            accepted()
        } catch (e: NumberFormatException) {
            val msg = "Cannot parse param:'$SALES_AMOUNT' value of $amount"
            log.error(msg)
            badRequest(just(msg))
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Rest::class.java)
    }
}




