package com.safemed.schema_registry.repository;

import com.safemed.schema_registry.model.HospitalSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HospitalSchemaRepository extends JpaRepository<HospitalSchema, Long> {
    List<HospitalSchema> findByIsActiveTrue();
    Optional<HospitalSchema> findByInstitutionNameAndIsActiveTrue(String institutionName);
}