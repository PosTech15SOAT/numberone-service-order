package br.com.fiap.numberone.serviceorder.infrastructure.messaging;

import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.Map;

interface BillingQueue extends AutoCloseable {
    Map<String, String> attributes();

    List<Message> receive();

    void acknowledge(String receiptHandle);

    void renewVisibility(String receiptHandle);

    @Override
    void close();
}
