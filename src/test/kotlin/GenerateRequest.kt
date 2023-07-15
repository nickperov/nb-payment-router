import com.fasterxml.jackson.databind.ObjectMapper
import com.nickperov.labs.nb.payment_router.PaymentDTO
import com.nickperov.labs.nb.payment_router.PaymentsBatchDTO
import java.io.BufferedWriter
import java.io.FileWriter
import java.math.BigDecimal
import java.util.*
import kotlin.random.Random


fun main() {

    val mapper = ObjectMapper()
    val paymentCount = 20000
    val payments = generateSequence {}.take(paymentCount).map { PaymentDTO(UUID.randomUUID(), generateAcc(), generateAcc(), generateAmount()) }.toList()
    val paymentsBatch = PaymentsBatchDTO(paymentCount, 10, Date(), payments)
    
    val writer = BufferedWriter(FileWriter("20k-test.json", true))
    writer.append(mapper.writeValueAsString(paymentsBatch))
    writer.close()
}


fun generateAmount(): BigDecimal {
    val rnd = Random
    val integralPart = rnd.nextInt(100_000_000)
    val decimalPart: Double = (rnd.nextInt(1000).toDouble() / 1000)

    return BigDecimal.valueOf(integralPart + decimalPart)
}


fun generateAcc(): String {
    val rnd = Random
    val bankNumber = String.format("%02d", rnd.nextInt(10))
    val branchNumber = String.format("%03d", rnd.nextInt(100))
    val accNumber = String.format("%06d", rnd.nextInt(10000))
    return "$bankNumber-$branchNumber-$accNumber"
}