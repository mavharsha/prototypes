package com.mavharsha.events.sqs.listener;

import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Singleton
public class SqsListenerService {
    private static final Logger LOG = LoggerFactory.getLogger(SqsListenerService.class);

    private final SqsClient sqsClient;
    private final String queueName;
    private String queueUrl;

    public SqsListenerService(SqsClient sqsClient, @Value("${events.queue-name}") String queueName) {
        this.sqsClient = sqsClient;
        this.queueName = queueName;
    }

    @PostConstruct
    void init() {
        this.queueUrl = sqsClient.getQueueUrl(r -> r.queueName(queueName)).queueUrl();
        LOG.info("Listening on SQS queue: {} ({})", queueName, queueUrl);
    }

    @Scheduled(fixedDelay = "5s")
    void pollMessages() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(10)
                .build();

        List<Message> messages = sqsClient.receiveMessage(request).messages();

        for (Message message : messages) {
            LOG.info("Received SQS message [{}]: {}", message.messageId(), message.body());
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        }
    }
}
