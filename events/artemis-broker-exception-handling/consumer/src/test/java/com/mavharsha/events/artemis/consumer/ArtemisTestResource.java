package com.mavharsha.events.artemis.consumer;

import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.util.HashMap;
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
                        "/var/lib/artemis-instance/etc-override/broker.xml")
                .waitingFor(Wait.forListeningPort());
        ARTEMIS.start();
    }

    @Override
    public Map<String, String> getProperties() {
        String url = "tcp://" + ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(61616);
        Map<String, String> props = new HashMap<>();
        props.put("micronaut.jms.activemq.artemis.enabled", "true");
        props.put("micronaut.jms.activemq.artemis.connection-string", url);
        props.put("micronaut.jms.activemq.artemis.username", "artemis");
        props.put("micronaut.jms.activemq.artemis.password", "artemis");
        props.put("emitter.enabled", "false");
        props.put("micronaut.server.port", "-1");
        return props;
    }
}
