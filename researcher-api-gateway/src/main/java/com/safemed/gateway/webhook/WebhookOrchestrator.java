package com.safemed.gateway.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

// fires an outbound POST to the researcher's url once a dataset is ready
@Component
public class WebhookOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WebhookOrchestrator.class);

    private final RestTemplate restTemplate;

    public WebhookOrchestrator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // async so we don't block the request thread
    @Async
    public void triggerWebhook(String callbackUrl, String downloadToken) {
        Map<String, Object> payload = Map.of(
                "status", "READY",
                "message", "Your anonymized dataset is ready for retrieval",
                "downloadToken", downloadToken
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            log.info("Webhook firing -> POST {} token={}", callbackUrl, downloadToken);
            restTemplate.postForEntity(callbackUrl, entity, String.class);
        } catch (Exception e) {
            // researcher's endpoint might be down, just log it
            log.warn("Webhook delivery failed for {}: {}", callbackUrl, e.getMessage());
        }
    }
}
