package com.curalink.patientservice.grpc;

import billing.BillingAccountRequest;
import billing.BillingAccountResponse;
import billing.BillingServiceGrpc;
import com.curalink.patientservice.kafka.KafkaProducer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
public class BillingServiceGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(
            BillingServiceGrpcClient.class);
    private final BillingServiceGrpc.BillingServiceBlockingStub blockingStub;
    private final KafkaProducer kafkaProducer;

    public BillingServiceGrpcClient(
            @Value("${billing.service.address:localhost}") String serverAddress,
            @Value("${billing.service.grpc.port:9001}") int serverPort, KafkaProducer kafkaProducer) {

        log.info("Connecting to Billing Service GRPC service at {}:{}",
                serverAddress, serverPort);

        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress,
                serverPort).usePlaintext().build();

        blockingStub = BillingServiceGrpc.newBlockingStub(channel);
        this.kafkaProducer = kafkaProducer;
    }

    @CircuitBreaker(name = "billingService", fallbackMethod = "billingFallback")
    @Retry(name = "billingRetry")
    public BillingAccountResponse createBillingAccount(String patientId, String name,
                                                String email) {

        BillingAccountRequest request = BillingAccountRequest.newBuilder().setPatientId(patientId)
                .setName(name).setEmail(email).build();

        BillingAccountResponse response = blockingStub.createBillingAccount(request);
        log.info("Received response from billing service via GRPC: {}", response);
        return response;
    }


    public BillingAccountResponse billingFallback(String patientId, String name, String email, Throwable throwable) {
        log.warn("[CIRCUIT BREAKER] Billing service is not available. Triggered fallback method. Exception: {}", throwable.getMessage());
        kafkaProducer.sendBillingAccountEvent(patientId, name, email);

        return BillingAccountResponse.newBuilder()
                .setAccountId("")
                .setStatus("PENDING")
                .build();
    }
}