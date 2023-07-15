package com.nickperov.labs.nb.payment_router

import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import java.util.concurrent.CompletionStage

@Path("/payment")
@RegisterRestClient
interface PaymentProcessor {

    @POST
    @Path("/execute")
    fun processPayment(payment: PaymentDTO): Boolean
    
    @POST
    @Path("/execute")
    fun processPaymentAsync(payment: PaymentDTO): CompletionStage<Boolean>
    
    @POST
    @Path("/execute")
    suspend fun processPaymentSuspend(payment: PaymentDTO): Boolean
}