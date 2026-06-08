package com.safemed.gateway.model;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

// one data request made by a researcher
// implements Serializable so it can be stored in the redis cache (jdk serialization)
@Entity
@Table(name = "dataset_requests")
@Data
public class DatasetRequest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // links back to the researcher (User id)
    @Column(name = "researcher_id", nullable = false)
    private Long researcherId;

    // query filters
    @Column(name = "age_min")
    private Integer ageMin;

    @Column(name = "age_max")
    private Integer ageMax;

    @Column(name = "disease_type")
    private String diseaseType;

    // PENDING or PROCESSED
    @Column(nullable = false)
    private String status = "PENDING";

    // set once the dataset is ready to download
    @Column(name = "download_token")
    private String downloadToken;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
