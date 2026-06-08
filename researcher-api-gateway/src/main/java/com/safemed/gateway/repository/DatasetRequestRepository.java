package com.safemed.gateway.repository;

import com.safemed.gateway.model.DatasetRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatasetRequestRepository extends JpaRepository<DatasetRequest, Long> {

    // a researcher's own requests
    List<DatasetRequest> findByResearcherId(Long researcherId);

    // used on the download endpoint
    Optional<DatasetRequest> findByDownloadToken(String downloadToken);
}
