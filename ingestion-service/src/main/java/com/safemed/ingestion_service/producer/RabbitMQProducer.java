package com.safemed.ingestion_service.producer;

import com.safemed.ingestion_service.config.RabbitMQConfig;
import com.safemed.ingestion_service.event.MedicalRecordReceived;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMQProducer {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private final RabbitTemplate rabbitTemplate;

    public void sendEvent(MedicalRecordReceived event) {
        log.info("Publishing {} to exchange={} routingKey={} eventId={}",
                event.getEventType(), RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, event.getEventId());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                event,
                message -> {
                    // propagate trace id + event id as amqp headers
                    message.getMessageProperties().setHeader(CORRELATION_ID_HEADER, event.getCorrelationId());
                    message.getMessageProperties().setMessageId(event.getEventId());
                    return message;
                }
        );
        log.info("Event published. eventId={}", event.getEventId());
    }
}
