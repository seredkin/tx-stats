package ebay.tx.stats

import ebay.tx.stats.Config.SALES_AMOUNT
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType.APPLICATION_FORM_URLENCODED_TYPE
import io.micronaut.http.MediaType.APPLICATION_JSON_TYPE
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.DefaultHttpClient
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.*
import javax.inject.Inject


@MicronautTest(application = TxStatsApp::class)
class RestTest {

    @Inject
    lateinit var server: EmbeddedServer

    @Test
    fun getEmptyStatistics() {
        val statistics: Statistics = rxClient().retrieve(statisticsRequest(), Statistics::class.java).blockingSingle()

        assertEquals(statistics.orderCount, 0)
        { "Empty Statistics response should be 0" }
    }

    @Test
    fun postSalesAndGetStats() {
        val testAmount = "10.01"
        val salesRequest = salesRequest(testAmount)

        val successResponse = rxClient().exchange(salesRequest, Sales::class.java).blockingSingle()
        assertEquals(HttpStatus.ACCEPTED, successResponse.status) { "Response code should be ${HttpStatus.ACCEPTED} " }
        val statistics: Statistics = rxClient().retrieve(statisticsRequest(), Statistics::class.java).blockingSingle()
        assertEquals(testAmount, statistics.amount.toString())
    }

    @Test
    fun postInvalidData() {
        val response = rxClient().toBlocking()
                .retrieve(
                        salesRequest("10_02"),
                        Argument.of(HashMap::class.java),
                        Argument.of(HashMap::class.java))
        assertEquals(HttpStatus.BAD_REQUEST, response["code"])
    }

    private fun rxClient(): RxHttpClient = DefaultHttpClient(server.uri.toURL())

    private fun statisticsRequest() = HttpRequest.GET<Statistics>("/statistics")
            .accept(APPLICATION_JSON_TYPE)

    private fun salesRequest(testAmount: String): MutableHttpRequest<String>? {
        val salesRequest = HttpRequest
                .POST("/sales", "")
                .contentType(APPLICATION_FORM_URLENCODED_TYPE)
        salesRequest.parameters.add(SALES_AMOUNT, testAmount)
        return salesRequest
    }

    companion object {
        private val log = LoggerFactory.getLogger(RestTest::class.java)
    }


}