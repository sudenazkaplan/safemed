package com.safemed.gateway.service;

import com.safemed.gateway.config.CorrelationIdFilter;
import com.safemed.gateway.dto.DatasetRequestDTO;
import com.safemed.gateway.exception.BadRequestException;
import com.safemed.gateway.exception.ResourceNotFoundException;
import com.safemed.gateway.integration.GrpcAuditClient;
import com.safemed.gateway.model.DatasetRequest;
import com.safemed.gateway.model.User;
import com.safemed.gateway.repository.DatasetRequestRepository;
import com.safemed.gateway.repository.UserRepository;
import com.safemed.gateway.webhook.WebhookOrchestrator;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DatasetServiceImpl implements DatasetService {

    private final UserRepository userRepository;
    private final DatasetRequestRepository datasetRequestRepository;
    private final GrpcAuditClient grpcAuditClient;
    private final WebhookOrchestrator webhookOrchestrator;

    public DatasetServiceImpl(UserRepository userRepository,
                              DatasetRequestRepository datasetRequestRepository,
                              GrpcAuditClient grpcAuditClient,
                              WebhookOrchestrator webhookOrchestrator) {
        this.userRepository = userRepository;
        this.datasetRequestRepository = datasetRequestRepository;
        this.grpcAuditClient = grpcAuditClient;
        this.webhookOrchestrator = webhookOrchestrator;
    }

    @Override
    public DatasetRequest createRequest(DatasetRequestDTO dto, String username) {
        User user = findUser(username);

        DatasetRequest request = new DatasetRequest();
        request.setResearcherId(user.getId());
        request.setAgeMin(dto.getAgeMin());
        request.setAgeMax(dto.getAgeMax());
        request.setDiseaseType(dto.getDiseaseType());
        request.setStatus("PENDING");
        request.setDownloadToken(UUID.randomUUID().toString()); // temp token until processed

        DatasetRequest saved = datasetRequestRepository.save(request);

        // real gRPC audit
        grpcAuditClient.sendLogViaGrpc("DATASET_REQUEST_CREATED", "SUCCESS",
                "Researcher " + username + " created request " + saved.getId());

        return saved;
    }

    @Override
    @Cacheable(value = "datasetRequests", key = "#username")
    public List<DatasetRequest> getRequestsByResearcher(String username) {
        User user = findUser(username);
        return datasetRequestRepository.findByResearcherId(user.getId());
    }

    @Override
    public List<Map<String, Object>> getAnonymizedDataByToken(String downloadToken) {
        DatasetRequest request = datasetRequestRepository.findByDownloadToken(downloadToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid download token"));

        // only ready datasets can be downloaded
        if (!"PROCESSED".equals(request.getStatus())) {
            throw new BadRequestException("Dataset is not ready yet, current status: " + request.getStatus());
        }

        // TODO: replace with a real MongoDB query later
        return mockAnonymizedRecords();
    }

    @Override
    public void registerWebhook(String username, String callbackUrl) {
        User user = findUser(username);
        user.setCallbackUrl(callbackUrl);
        userRepository.save(user);
    }

    @Override
    @CacheEvict(value = "datasetRequests", key = "#username")
    public DatasetRequest completeRequest(Long requestId, String username) {
        User user = findUser(username);
        DatasetRequest request = datasetRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset request not found: " + requestId));

        // don't let a researcher complete someone else's request
        if (!request.getResearcherId().equals(user.getId())) {
            throw new ResourceNotFoundException("Dataset request not found: " + requestId);
        }

        // simulate the dataset becoming ready
        request.setStatus("PROCESSED");
        if (request.getDownloadToken() == null || request.getDownloadToken().isBlank()) {
            request.setDownloadToken(UUID.randomUUID().toString());
        }
        DatasetRequest saved = datasetRequestRepository.save(request);

        // audit the lifecycle transition over grpc
        grpcAuditClient.sendLogViaGrpc("DATASET_PROCESSED", "SUCCESS",
                "Dataset request " + saved.getId() + " marked PROCESSED for " + username);

        // fire the outbound notification if a callback url is registered
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (user.getCallbackUrl() != null && !user.getCallbackUrl().isBlank()) {
            webhookOrchestrator.triggerWebhook(user.getCallbackUrl(), saved.getDownloadToken(), correlationId);
        }

        return saved;
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    // fake anonymized data so the endpoint returns something for now
    private List<Map<String, Object>> mockAnonymizedRecords() {
        return List.of(
                Map.of("patientName", "S** K**", "nationalId", "123****8901", "diseaseInfo", "Type 2 Diabetes"),
                Map.of("patientName", "A** Y**", "nationalId", "456****2109", "diseaseInfo", "Hypertension")
        );
    }
}
