package com.safemed.schema_registry.service;

import com.safemed.schema_registry.model.HospitalSchema;
import com.safemed.schema_registry.repository.HospitalSchemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SchemaRegistryService {

    private final HospitalSchemaRepository repository;

    public HospitalSchema createSchema(HospitalSchema schema) {
        return repository.save(schema);
    }

    public List<HospitalSchema> getAllActiveSchemas() {
        return repository.findByIsActiveTrue();
    }

    public HospitalSchema getSchemaById(Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Şema bulunamadı ID: " + id));
    }

    public HospitalSchema updateSchema(Long id, HospitalSchema updatedData) {
        HospitalSchema existingSchema = getSchemaById(id);
        existingSchema.setInstitutionName(updatedData.getInstitutionName());
        existingSchema.setFormatType(updatedData.getFormatType());
        existingSchema.setMappingRules(updatedData.getMappingRules());
        return repository.save(existingSchema);
    }

    public void deleteSchema(Long id) {
        HospitalSchema schema = getSchemaById(id);
        schema.setActive(false);
        repository.save(schema);
    }
}