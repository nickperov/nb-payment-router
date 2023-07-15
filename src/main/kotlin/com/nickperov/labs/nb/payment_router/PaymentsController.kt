package com.nickperov.labs.nb.payment_router

import jakarta.enterprise.inject.Default
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import java.math.BigDecimal

@Path("payments")
class PaymentsController {

    enum class Router {
        SEQ, PARALLEL_STREAM, PARALLEL_FIX_100, PARALLEL_NO_LIM, ASYNC, REACTIVE, COROUTINES, VIRTUAL_THREADS
    }

    @Inject
    @field: Default
    lateinit var paymentsRouter: PaymentsRouter

    @POST
    @Path("/submit")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun submit(paymentsBatch: PaymentsBatchDTO, @HeaderParam("Router") router: Router): PaymentsBatchProcessResultDTO {
        return try {
            when (router) {
                Router.SEQ -> paymentsRouter.processPaymentsSequentially(paymentsBatch)
                Router.PARALLEL_STREAM -> paymentsRouter.processPaymentsParallelStream(paymentsBatch)
                Router.PARALLEL_FIX_100 -> paymentsRouter.processPaymentsParallelFix100(paymentsBatch)
                Router.PARALLEL_NO_LIM -> paymentsRouter.processPaymentsParallelNoLimits(paymentsBatch)
                Router.ASYNC -> paymentsRouter.processPaymentsAsync(paymentsBatch)
                Router.REACTIVE -> paymentsRouter.processPaymentsReactive(paymentsBatch)
                Router.COROUTINES -> paymentsRouter.processPaymentsCoRoutines(paymentsBatch)
                Router.VIRTUAL_THREADS -> paymentsRouter.processPaymentsGreenThreads(paymentsBatch)
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
            PaymentsBatchProcessResultDTO(BatchProcessingResult.ERROR, BigDecimal.ZERO, emptyList())
        }
    }
}