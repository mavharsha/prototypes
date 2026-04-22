package com.mavharsha.events.artemis.consumer;

import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.util.Map;

public abstract class ArtemisTestResource implements TestPropertyProvider {

    private static final GenericContainer<?> ARTEMIS;

    static {
        ARTEMIS = new GenericContainer<>("apache/activemq-artemis:2.33.0")
                .withExposedPorts(61616, 8161)
                .withEnv("ARTEMIS_USER", "artemis")
                .withEnv("ARTEMIS_PASSWORD", "artemis")
                .withEnv("ANONYMOUS_LOGIN", "false")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../infra/artemis/broker.xml"),
                        "/var/lib/artemis-broker/etc/broker.xml")
                .waitingFor(Wait.forListeningPort());
        ARTEMIS.start();
    }

    @Override
    public Map<String, String> getProperties() {
        String url = "tcp://" + ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(61616);
        return Map.of(
                "micronaut.jms.activemq.artemis.enabled", "true",
                "micronaut.jms.activemq.artemis.connection-string", url,
                "micronaut.jms.activemq.artemis.username", "artemis",
                "micronaut.jms.activemq.artemis.password", "artemis",
                "emitter.enabled", "false"
        );
    }
}
