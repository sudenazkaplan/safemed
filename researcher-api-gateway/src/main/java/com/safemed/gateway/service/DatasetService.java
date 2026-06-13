package com.safemed.gateway.service;

import com.safemed.gateway.dto.DatasetRequestDTO;
import com.safemed.gateway.model.DatasetRequest;

import java.util.List;
import java.util.Map;

public interface DatasetService {

    DatasetRequest createRequest(DatasetRequestDTO dto, String username);

    List<DatasetRequest> getRequestsByResearcher(String username);

    // mock anonymized records from the data store
    List<Map<String, Object>> getAnonymizedDataByToken(String downloadToken);

    // store the researcher's callback url for dataset-ready notifications
    void registerWebhook(String username, String callbackUrl);

    // simulate the dataset becoming ready -> mark PROCESSED and fire the webhook
    DatasetRequest completeRequest(Long requestId, String username);
}
