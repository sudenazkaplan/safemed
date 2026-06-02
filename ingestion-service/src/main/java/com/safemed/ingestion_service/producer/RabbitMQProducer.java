package com.safemed.ingestion_service.producer;

import com.safemed.ingestion_service.config.RabbitMQConfig;
import com.safemed.ingestion_service.dto.MedicalRecordDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMQProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendEvent(MedicalRecordDTO record) {
        log.info("Publishing message to RabbitMQ exchange={} routingKey={}",
                RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                record
        );
        log.info("Message successfully published to the queue.");
    }
}
