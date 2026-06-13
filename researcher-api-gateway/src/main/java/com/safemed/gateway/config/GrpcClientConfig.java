package com.safemed.gateway.config;

import com.safemed.grpc.audit.AuditServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// wires the grpc channel + blocking stub pointing at the audit-compliance service
@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel auditChannel(@Value("${grpc.audit.host:localhost}") String host,
                                       @Value("${grpc.audit.port:9090}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() // internal traffic, no TLS for now
                .build();
    }

    @Bean
    public AuditServiceGrpc.AuditServiceBlockingStub auditStub(ManagedChannel auditChannel) {
        return AuditServiceGrpc.newBlockingStub(auditChannel);
    }
}
