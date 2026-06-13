package com.safemed.gateway.integration;

import com.safemed.gateway.config.CorrelationIdFilter;
import com.safemed.grpc.audit.AuditServiceGrpc;
import com.safemed.grpc.audit.LogRequest;
import com.safemed.grpc.audit.LogResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

// real grpc client -> audit-compliance-service:9090
@Component
public class GrpcAuditClient {

    private static final Logger log = LoggerFactory.getLogger(GrpcAuditClient.class);

    private final AuditServiceGrpc.AuditServiceBlockingStub auditStub;

    public GrpcAuditClient(AuditServiceGrpc.AuditServiceBlockingStub auditStub) {
        this.auditStub = auditStub;
    }

    public void sendLogViaGrpc(String action, String status, String details) {
        // pull the current correlation id so the audit trail can be stitched together
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

        LogRequest request = LogRequest.newBuilder()
                .setAction(action == null ? "" : action)
                .setStatus(status == null ? "" : status)
                .setDetails(details == null ? "" : details)
                .setCorrelationId(correlationId == null ? "" : correlationId)
                .build();

        try {
            LogResponse response = auditStub.sendLog(request);
            log.info("gRPC audit log sent: success={}, msg={}", response.getSuccess(), response.getMessage());
        } catch (Exception e) {
            // audit service being down shouldn't break the researcher's request
            log.warn("gRPC audit log failed: {}", e.getMessage());
        }
    }
}
