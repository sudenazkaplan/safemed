package com.safemed.ingestion_service.controller;

import com.safemed.ingestion_service.dto.MedicalRecordDTO;
import com.safemed.ingestion_service.dto.RecordMetadataDTO;
import com.safemed.ingestion_service.dto.StatusResponseDTO;
import com.safemed.ingestion_service.model.AuditLog;
import com.safemed.ingestion_service.producer.RabbitMQProducer;
import com.safemed.ingestion_service.repository.AuditLogRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ingestion")
public class IngestionController {

    private final RabbitMQProducer rabbitMQProducer;
    private final AuditLogRepository auditLogRepository;

    // Temporary in-memory mock data. Will be replaced with a repository query once the DB integration is complete.
    private static final List<RecordMetadataDTO> MOCK_RECORDS = List.of(
            new RecordMetadataDTO(
                    "a1b2-track-001",
                    "John Doe",
                    "Ankara City Hospital",
                    Instant.parse("2026-06-01T18:00:00Z"),
                    "IN_QUEUE"
            ),
            new RecordMetadataDTO(
                    "c3d4-track-002",
                    "Jane Smith",
                    "Istanbul Training and Research Hospital",
                    Instant.parse("2026-06-01T18:05:00Z"),
                    "PROCESSED"
            )
    );

    @PostMapping("/submit")
    public ResponseEntity<String> receiveRecord(@Valid @RequestBody MedicalRecordDTO record) {
        String trackingId = UUID.randomUUID().toString();
        log.info("Raw record received. trackingId={}, hospital={}", trackingId, record.getHospitalName());

        rabbitMQProducer.sendEvent(record);
        log.info("Record forwarded to the queue. trackingId={}", trackingId);

        AuditLog auditLog = AuditLog.builder()
                .trackingId(trackingId)
                .hospitalName(record.getHospitalName())
                .diseaseInfo(record.getDiseaseInfo())
                .timestamp(LocalDateTime.now())
                .build();
        auditLogRepository.save(auditLog);
        log.info("Audit log persisted. trackingId={}", trackingId);

        return ResponseEntity.ok(
                "Record accepted and forwarded to the queue. Tracking ID: " + trackingId
        );
    }

    @GetMapping("/status/{trackingId}")
    public ResponseEntity<StatusResponseDTO> getStatus(@PathVariable String trackingId) {
        log.info("Status query received. trackingId={}", trackingId);

        StatusResponseDTO response = new StatusResponseDTO(
                trackingId,
                "IN_QUEUE",
                "Record is waiting in the queue and has not been processed yet."
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/records")
    public ResponseEntity<List<RecordMetadataDTO>> listRecords() {
        log.info("Raw record list requested. count={}", MOCK_RECORDS.size());
        return ResponseEntity.ok(MOCK_RECORDS);
    }

    @DeleteMapping("/records/{trackingId}")
    public ResponseEntity<String> deleteRecord(@PathVariable String trackingId) {
        log.info("Record deletion requested. trackingId={}", trackingId);

        // TODO: Implement real cancel/delete logic (DB removal + queue purge).
        log.info("Record cancellation/deletion completed. trackingId={}", trackingId);
        return ResponseEntity.ok(
                "Raw record with tracking ID " + trackingId + " was successfully cancelled/deleted."
        );
    }
}
