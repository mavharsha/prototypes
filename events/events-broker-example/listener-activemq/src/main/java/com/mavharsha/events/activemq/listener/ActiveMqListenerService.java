package com.mavharsha.events.activemq.listener;

import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class ActiveMqListenerService {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveMqListenerService.class);

    @Queue("events-queue")
    void onMessage(@MessageBody String body) {
        LOG.info("Received ActiveMQ message: {}", body);
    }
}
