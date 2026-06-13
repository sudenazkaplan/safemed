package com.safemed.audit_compliance_service.grpc;

import com.safemed.audit_compliance_service.model.AuditLog;
import com.safemed.audit_compliance_service.repository.AuditLogRepository;
import com.safemed.grpc.audit.AuditServiceGrpc;
import com.safemed.grpc.audit.LogRequest;
import com.safemed.grpc.audit.LogResponse;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// grpc endpoint, persists logs coming from the gateway into postgres
@Component
public class AuditGrpcService extends AuditServiceGrpc.AuditServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(AuditGrpcService.class);

    private final AuditLogRepository repository;

    public AuditGrpcService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void sendLog(LogRequest request, StreamObserver<LogResponse> responseObserver) {
        log.info("gRPC SendLog received: action={}, status={}, correlationId={}",
                request.getAction(), request.getStatus(), request.getCorrelationId());

        // correlationId becomes the tracking id so we can stitch the request together
        AuditLog entry = AuditLog.builder()
                .trackingId(request.getCorrelationId())
                .userId("researcher-api-gateway")
                .action(request.getAction())
                .status(request.getStatus())
                .details(request.getDetails())
                .timestamp(LocalDateTime.now())
                .build();
        AuditLog saved = repository.save(entry);

        LogResponse response = LogResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Audit log stored with id " + saved.getId())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
