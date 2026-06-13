package com.safemed.gateway.webhook;

import com.safemed.gateway.config.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

// real outbound webhook, represents the "DatasetReadyNotificationSent" event notification
@Component
public class WebhookOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WebhookOrchestrator.class);

    private final RestTemplate restTemplate;

    public WebhookOrchestrator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // runs off the request thread, the task executor copies the mdc so the correlation id survives
    @Async
    public void triggerWebhook(String callbackUrl, String downloadToken, String correlationId) {
        // linkedhashmap keeps field order and tolerates a null correlationId
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "PROCESSED");
        payload.put("downloadToken", downloadToken);
        payload.put("message", "Your secure medical dataset is ready for extraction.");
        payload.put("correlationId", correlationId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            log.info("DatasetReadyNotificationSent -> POST {} token={} correlationId={}",
                    callbackUrl, downloadToken, correlationId);
            restTemplate.postForEntity(callbackUrl, entity, String.class);
            log.info("Webhook delivered to {}", callbackUrl);
        } catch (Exception e) {
            // researcher's endpoint might be down, just log and move on
            log.warn("Webhook delivery failed for {}: {}", callbackUrl, e.getMessage());
        }
    }
}
