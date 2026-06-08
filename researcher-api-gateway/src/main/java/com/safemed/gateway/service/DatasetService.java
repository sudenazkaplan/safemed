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
}
