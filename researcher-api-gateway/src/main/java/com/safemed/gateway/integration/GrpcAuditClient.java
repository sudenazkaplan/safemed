package com.safemed.gateway.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// simulates a gRPC channel to the audit-compliance service (port 8083)
@Component
public class GrpcAuditClient {

    private static final Logger log = LoggerFactory.getLogger(GrpcAuditClient.class);

    // real impl would use a generated gRPC stub + protobuf message
    public void sendLogViaGrpc(String action, String status, String details) {
        log.info("gRPC Channel open: Sending binary protocol buffers payload to port 8083...");
        log.info("gRPC audit -> action={}, status={}, details={}", action, status, details);
    }
}
