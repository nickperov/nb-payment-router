package com.nickperov.labs.nb.payment_router

import java.math.BigDecimal
import java.util.*

data class PaymentsBatchDTO(val numberOfPayments: Int, val priority: Int, val date: Date, val payments: List<PaymentDTO>)

data class PaymentDTO(val reference: UUID, val debitAcc: String, val creditAcc: String, val amount: BigDecimal)