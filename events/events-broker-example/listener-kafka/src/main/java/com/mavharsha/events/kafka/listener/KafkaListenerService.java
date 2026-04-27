package com.mavharsha.events.kafka.listener;

import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@KafkaListener(groupId = "events-listener")
public class KafkaListenerService {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaListenerService.class);

    @Topic("events-topic")
    void onMessage(String body) {
        LOG.info("Received Kafka message: {}", body);
    }
}
