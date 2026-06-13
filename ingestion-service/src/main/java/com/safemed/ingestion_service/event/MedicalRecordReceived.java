package com.safemed.ingestion_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// past-tense domain event, carries the full patient state (event-carried state transfer)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecordReceived {

    private String eventId;        // unique id, used by the consumer for idempotency
    private String eventType;      // always "MedicalRecordReceived"
    private String correlationId;  // trace id propagated across services
    private String trackingId;     // business tracking id
    private String occurredAt;     // iso-8601 timestamp

    // full carried state so the consumer doesn't need a callback to the source
    private String patientName;
    private String nationalId;
    private String diseaseInfo;
    private String hospitalName;
}
