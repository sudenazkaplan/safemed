package com.safemed.gateway.service;

import com.safemed.gateway.dto.DatasetRequestDTO;
import com.safemed.gateway.exception.BadRequestException;
import com.safemed.gateway.exception.ResourceNotFoundException;
import com.safemed.gateway.integration.GrpcAuditClient;
import com.safemed.gateway.model.DatasetRequest;
import com.safemed.gateway.model.User;
import com.safemed.gateway.repository.DatasetRequestRepository;
import com.safemed.gateway.repository.UserRepository;
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

    public DatasetServiceImpl(UserRepository userRepository,
                              DatasetRequestRepository datasetRequestRepository,
                              GrpcAuditClient grpcAuditClient) {
        this.userRepository = userRepository;
        this.datasetRequestRepository = datasetRequestRepository;
        this.grpcAuditClient = grpcAuditClient;
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

        // internal audit log over (simulated) gRPC
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
