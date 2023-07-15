package com.nickperov.labs.nb.payment_router

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.kotlin.toFlowable
import io.reactivex.rxjava3.schedulers.Schedulers
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.net.URI
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors


@ApplicationScoped
class PaymentsRouter(@ConfigProperty(name = "payment.processor.url") private val paymentsProcessorUrl: String) {

    @Inject
    private lateinit var log: Logger

    private val fixThreadPool = Executors.newFixedThreadPool(100)
    private val cachedThreadPool = Executors.newCachedThreadPool()

    // Http client configuration
    private val paymentResource = QuarkusRestClientBuilder.newBuilder()
        .property("io.quarkus.rest.client.connection-pool-size", 5000)
        .baseUri(URI.create(paymentsProcessorUrl))
        .build(PaymentProcessor::class.java)

    fun processPaymentsSequentially(paymentsBatch: PaymentsBatchDTO): PaymentsBatchProcessResultDTO {
        val processedPayments = paymentsBatch.payments.map { payment ->
            val result = executePayment(payment)
            Pair(result, payment)
        }.groupBy({ p -> p.first }, { p -> p.second })
        return buildResponse(processedPayments)
    }

    fun processPaymentsParallelStream(paymentsBatch: PaymentsBatchDTO): PaymentsBatchProcessResultDTO {
        val processedPayments = paymentsBatch.payments.parallelStream()
            .map { payment ->
                val result = executePayment(payment)
                Pair(result, payment)
            }
            .collect(Collectors.groupingBy({ p -> p.first }, Collectors.mapping({ p -> p.second }, Collectors.toList())))

        return buildResponse(processedPayments)
    }

    fun processPaymentsParallelFix100(paymentsBatch: PaymentsBatchDTO): PaymentsBatchProcessResultDTO {
        return buildResponse(runPaymentRequestsThreadPool(paymentsBatch, fixThreadPool))
    }

    fun processPaymentsParallelNoLimits(paymentsBatch: PaymentsBatchDTO): PaymentsBatchProcessResultDTO {
        return buildResponse(runPaymentRequestsThreadPool(paymentsBatch, cachedThreadPool))
    }

    fun processPaymentsAsync(paymentsBatch: PaymentsBatchDTO): PaymentsBatchProcessResultDTO {
        val payments = paymentsBatch.payments
        val latch = CountDownLatch(payments.size)
        val processedPayments = ConcurrentHashMap<Boolean, MutableList<PaymentDTO>>()
        processedPayments[true] = Collections.synchronizedList(ArrayList())
        processedPayments[false] = Collections.synchronizedList(ArrayList())

        payments.forEach { payment ->
            executePaymentAsync(payment).handleAsync { result, exception ->
                run {
                    try {
                        if (exception != null) {
                            log.error("Payment execution error", exception)
                            processedPayments[false]?.add(payment) //!!! Exception handling 
                        } else {
                            processedPayments[result]?.add(payment)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
        }

        latch.await()

        return buildResponse(processedPayments)
    }

    fun processPaymentsReactive(paymentsBatch: PaymentsBatchDTO): PaymentsBatchProcessResultDTO {
        val latch = CountDownLatch(1)
        val processedPaymentsRef: AtomicReference<Map<Boolean, MutableList<PaymentDTO>>> = AtomicReference(Collections.emptyMap())

        // Processing using RxJava
        paymentsBatch.payments.toFlowable()
            .flatMap({ payment ->
                Flowable.fromCompletionStage(paymentResource.processPaymentAsync(payment))
                    .map { Pair(it, payment) }
                    .doOnError { e ->
                        log.error("Payment execution error", e)
                        Pair(false, payment) // Return failed payment
                    }
                    .subscribeOn(Schedulers.computation()) //!!! Computation thread pool
            }, false, 3000) //!!! Max concurrency
            .groupBy { it.first }
            .flatMap { groupedResult ->
                groupedResult.map { it.second }.toList().map { list -> Pair(groupedResult.key, list) }.toFlowable()
            }.toMap({ it.first == true }, { it.second }, { ConcurrentHashMap() })
            .doOnSuccess { map ->
                log.info("All payments processed")
                processedPaymentsRef.set(map)
                latch.countDown()
            }.doOnError { err ->
                log.error("Payments processing failed", err)
                latch.countDown()
            }.subscribe()

        latch.await()

        return buildResponse(processedPaymentsRef.get())
    }

    fun processPaymentsCoRoutines(paymentsBatch: PaymentsBatchDTO): PaymentsBatchProcessResultDTO {

        val requestSemaphore = Semaphore(3000) //!!! Not a java semaphore, but kotlin semaphore

        val processedPayments =
            runBlocking {//!!! Adapter between blocking and non-blocking code
                paymentsBatch.payments.map { payment ->
                    this.async {
                        requestSemaphore.withPermit {
                            val result = executePaymentSuspend(payment)
                            Pair(result, payment)
                        }
                    }
                }.awaitAll().groupBy({ p -> p.first }, { p -> p.second })
            }

        return buildResponse(processedPayments)
    }

    fun processPaymentsGreenThreads(paymentsBatch: PaymentsBatchDTO): PaymentsBatchProcessResultDTO {

        val requestSemaphore = java.util.concurrent.Semaphore(2000) //!!! Yes, good old java semaphore

        val processedPayments = ConcurrentHashMap<Boolean, MutableList<PaymentDTO>>()
        processedPayments[true] = Collections.synchronizedList(ArrayList())
        processedPayments[false] = Collections.synchronizedList(ArrayList())

        paymentsBatch.payments.map { payment ->
            Thread.startVirtualThread {
                requestSemaphore.acquire()
                try {
                    val result = executePayment(payment)
                    processedPayments[result]?.add(payment)
                } finally {
                    requestSemaphore.release()
                }
            }
        }.forEach { it.join() }

        return buildResponse(processedPayments)
    }

    private fun runPaymentRequestsThreadPool(paymentsBatch: PaymentsBatchDTO, executorService: ExecutorService): Map<Boolean, MutableList<PaymentDTO>> {
        val paymentsList = paymentsBatch.payments
        val doneSignal = CountDownLatch(paymentsList.size)
        val processedPayments = ConcurrentHashMap<Boolean, MutableList<PaymentDTO>>()
        processedPayments[true] = Collections.synchronizedList(ArrayList())
        processedPayments[false] = Collections.synchronizedList(ArrayList())

        paymentsList.forEach { payment ->
            executorService.submit {
                try {
                    log.info("Executing payment ${payment.reference}")
                    val result = paymentResource.processPayment(payment)
                    processedPayments[result]?.add(payment)
                } finally {
                    doneSignal.countDown()
                }
            }
        }
        doneSignal.await()
        return processedPayments
    }

    private fun buildResponse(processedPayments: Map<Boolean, List<PaymentDTO>>): PaymentsBatchProcessResultDTO {
        val succeedPayments = processedPayments[true]
        val failedPayments = processedPayments[false]

        val result: BatchProcessingResult = if (failedPayments.isNullOrEmpty() && !succeedPayments.isNullOrEmpty()) {
            BatchProcessingResult.ALL
        } else if (succeedPayments.isNullOrEmpty()) {
            BatchProcessingResult.NONE
        } else {
            BatchProcessingResult.PARTIAL
        }

        val totalAmount = succeedPayments?.map { payment -> payment.amount }?.fold(BigDecimal.ZERO, BigDecimal::add) ?: BigDecimal.ZERO

        val paymentsResults: List<PaymentProcessResultDTO> = (succeedPayments?.map { p -> PaymentProcessResultDTO(p.reference, PaymentProcessingResult.SUCCESS) }
            ?: emptyList())
            .plus(failedPayments?.map { p -> PaymentProcessResultDTO(p.reference, PaymentProcessingResult.FAILURE) } ?: emptyList())

        return PaymentsBatchProcessResultDTO(result, totalAmount, paymentsResults)
    }

    private suspend fun executePaymentSuspend(payment: PaymentDTO): Boolean {
        log.info("Executing payment ${payment.reference}")
        return paymentResource.processPaymentSuspend(payment)
    }

    private fun executePayment(payment: PaymentDTO): Boolean {
        log.info("Executing payment ${payment.reference}")
        return paymentResource.processPayment(payment)
    }

    private fun executePaymentAsync(payment: PaymentDTO): CompletionStage<Boolean> {
        log.info("Executing payment ${payment.reference}")
        return paymentResource.processPaymentAsync(payment)
    }
}

