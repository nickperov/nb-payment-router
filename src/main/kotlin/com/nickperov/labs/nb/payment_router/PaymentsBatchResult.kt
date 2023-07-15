package com.nickperov.labs.nb.payment_router

import java.math.BigDecimal
import java.util.*

data class PaymentsBatchProcessResultDTO(val batchProcessingResult: BatchProcessingResult, val totalAmount: BigDecimal, val paymentResults: List<PaymentProcessResultDTO>)

data class PaymentProcessResultDTO(val reference: UUID, val paymentProcessingResult: PaymentProcessingResult)

enum class PaymentProcessingResult {
    SUCCESS, FAILURE
}

enum class BatchProcessingResult {
    ALL, PARTIAL, NONE, ERROR
}


