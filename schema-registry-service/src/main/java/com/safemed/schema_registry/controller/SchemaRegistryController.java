package com.safemed.schema_registry.controller;

import com.safemed.schema_registry.dto.HospitalSchemaDTO;
import com.safemed.schema_registry.model.HospitalSchema;
import com.safemed.schema_registry.service.SchemaRegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schemas")
@RequiredArgsConstructor
public class SchemaRegistryController {

    private final SchemaRegistryService service;

    @PostMapping
    public ResponseEntity<HospitalSchemaDTO> createSchema(@Valid @RequestBody HospitalSchemaDTO request) {
        HospitalSchema saved = service.createSchema(HospitalSchema.fromDTO(request));
        return new ResponseEntity<>(saved.toDTO(), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<HospitalSchemaDTO>> getAllSchemas() {
        List<HospitalSchemaDTO> schemas = service.getAllActiveSchemas().stream()
                .map(HospitalSchema::toDTO)
                .toList();
        return ResponseEntity.ok(schemas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HospitalSchemaDTO> getSchemaById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getSchemaById(id).toDTO());
    }

    @PutMapping("/{id}")
    public ResponseEntity<HospitalSchemaDTO> updateSchema(@PathVariable Long id,
                                                          @Valid @RequestBody HospitalSchemaDTO request) {
        HospitalSchema updated = service.updateSchema(id, HospitalSchema.fromDTO(request));
        return ResponseEntity.ok(updated.toDTO());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchema(@PathVariable Long id) {
        service.deleteSchema(id);
        return ResponseEntity.noContent().build();
    }
}
