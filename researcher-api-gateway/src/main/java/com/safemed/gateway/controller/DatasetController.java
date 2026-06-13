package com.safemed.gateway.controller;

import com.safemed.gateway.dto.DatasetRequestDTO;
import com.safemed.gateway.dto.WebhookRegisterDTO;
import com.safemed.gateway.model.DatasetRequest;
import com.safemed.gateway.service.DatasetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

// secured dataset + webhook endpoints
@RestController
@RequestMapping("/api/v1")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    // create a new dataset query, username comes from the jwt
    @PostMapping("/datasets/request")
    public ResponseEntity<DatasetRequest> createRequest(@RequestBody DatasetRequestDTO dto, Principal principal) {
        DatasetRequest created = datasetService.createRequest(dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // list the caller's own requests (cached in redis)
    @GetMapping("/datasets/requests/my")
    public ResponseEntity<List<DatasetRequest>> myRequests(Principal principal) {
        return ResponseEntity.ok(datasetService.getRequestsByResearcher(principal.getName()));
    }

    // pull anonymized records for a ready dataset
    @GetMapping("/datasets/download/{downloadToken}")
    public ResponseEntity<List<Map<String, Object>>> download(@PathVariable String downloadToken) {
        return ResponseEntity.ok(datasetService.getAnonymizedDataByToken(downloadToken));
    }

    // simulate the dataset becoming ready -> flips to PROCESSED and fires the webhook
    @PostMapping("/datasets/requests/{id}/complete")
    public ResponseEntity<DatasetRequest> completeRequest(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(datasetService.completeRequest(id, principal.getName()));
    }

    // store the authenticated researcher's callback url
    @PostMapping("/researchers/webhooks")
    public ResponseEntity<Map<String, String>> registerWebhook(@RequestBody WebhookRegisterDTO dto, Principal principal) {
        datasetService.registerWebhook(principal.getName(), dto.getCallbackUrl());
        return ResponseEntity.ok(Map.of("message", "Callback URL registered: " + dto.getCallbackUrl()));
    }
}
