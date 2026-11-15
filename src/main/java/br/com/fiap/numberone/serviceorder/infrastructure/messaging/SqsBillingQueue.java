package br.com.fiap.numberone.serviceorder.infrastructure.messaging;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;

import java.util.List;
import java.util.Map;

record SqsBillingQueue(SqsClient sqs, String queueUrl) implements BillingQueue {
    @Override
    public Map<String, String> attributes() {
        return sqs.getQueueAttributes(request -> request.queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.ALL)).attributesAsStrings();
    }

    @Override
    public List<Message> receive() {
        return sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl)
                .waitTimeSeconds(20).visibilityTimeout(60).maxNumberOfMessages(1)
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT).build()).messages();
    }

    @Override
    public void acknowledge(String receiptHandle) {
        sqs.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl).receiptHandle(receiptHandle).build());
    }

    @Override
    public void renewVisibility(String receiptHandle) {
        sqs.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                .queueUrl(queueUrl).receiptHandle(receiptHandle).visibilityTimeout(60).build());
    }

    @Override
    public void close() {
        sqs.close();
    }
}
