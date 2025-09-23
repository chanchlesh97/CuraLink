package com.curalink.billingservice.grpc;


import billing.BillingAccountRequest;
import billing.BillingAccountResponse;
import billing.BillingServiceGrpc.BillingServiceImplBase;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class BillingGrpcService extends BillingServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(BillingGrpcService.class);

    @Override
    public void createBillingAccount(BillingAccountRequest request, StreamObserver<BillingAccountResponse> responseObserver) {
//        super.createBillingAccount(request, responseObserver);
        log.info("Received createBillingAccount request: {}", request.toString());

        BillingAccountResponse response = BillingAccountResponse.newBuilder()
                .setAccountId("acc-" + System.currentTimeMillis())
                .setStatus("CREATED")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

    }
}
