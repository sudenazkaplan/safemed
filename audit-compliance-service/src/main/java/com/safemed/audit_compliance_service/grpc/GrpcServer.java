package com.safemed.audit_compliance_service.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

// runs a standalone grpc server alongside the http (tomcat) server
@Component
public class GrpcServer {

    private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

    private final AuditGrpcService auditGrpcService;
    private Server server;

    @Value("${grpc.server.port:9090}")
    private int port;

    public GrpcServer(AuditGrpcService auditGrpcService) {
        this.auditGrpcService = auditGrpcService;
    }

    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(auditGrpcService)
                .build()
                .start();
        log.info("gRPC server started on port {}", port);
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdown();
            log.info("gRPC server stopped");
        }
    }
}
