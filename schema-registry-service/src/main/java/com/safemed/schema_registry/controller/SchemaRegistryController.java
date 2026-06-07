package com.safemed.schema_registry.controller;

import com.safemed.schema_registry.model.HospitalSchema;
import com.safemed.schema_registry.service.SchemaRegistryService;
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
    public ResponseEntity<HospitalSchema> createSchema(@RequestBody HospitalSchema schema) {
        return new ResponseEntity<>(service.createSchema(schema), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<HospitalSchema>> getAllSchemas() {
        return ResponseEntity.ok(service.getAllActiveSchemas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<HospitalSchema> getSchemaById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getSchemaById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HospitalSchema> updateSchema(@PathVariable Long id, @RequestBody HospitalSchema schema) {
        return ResponseEntity.ok(service.updateSchema(id, schema));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchema(@PathVariable Long id) {
        service.deleteSchema(id);
        return ResponseEntity.noContent().build();
    }
}