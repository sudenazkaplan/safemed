package com.safemed.ingestion_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// past-tense failure event, lands in the DLQ when anonymization can't complete
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecordAnonymizationFailed {

    private String eventId;        // new id for the failure event
    private String eventType;      // always "MedicalRecordAnonymizationFailed"
    private String correlationId;  // same trace id as the original event
    private String trackingId;
    private String occurredAt;
    private String reason;         // why the anonymization failed

    // original carried state, so a DLQ consumer can react without a lookup
    private String patientName;
    private String nationalId;
    private String diseaseInfo;
    private String hospitalName;
}
