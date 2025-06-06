// TestChannelInterceptor.java
package com.example.integration.test;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestChannelInterceptor implements ChannelInterceptor {
    
    private final ConcurrentLinkedQueue<Message<?>> sentMessages = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Message<?>> receivedMessages = new ConcurrentLinkedQueue<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        sentMessages.offer(message);
        return message;
    }

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        if (sent) {
            receivedMessages.offer(message);
        }
    }

    public List<Message<?>> getSentMessages() {
        return new ArrayList<>(sentMessages);
    }

    public List<Message<?>> getReceivedMessages() {
        return new ArrayList<>(receivedMessages);
    }

    public Message<?> getLastSentMessage() {
        return sentMessages.isEmpty() ? null : ((ArrayList<Message<?>>) getSentMessages()).get(sentMessages.size() - 1);
    }

    public Message<?> getLastReceivedMessage() {
        return receivedMessages.isEmpty() ? null : ((ArrayList<Message<?>>) getReceivedMessages()).get(receivedMessages.size() - 1);
    }

    public void clear() {
        sentMessages.clear();
        receivedMessages.clear();
    }

    public int getSentMessageCount() {
        return sentMessages.size();
    }

    public int getReceivedMessageCount() {
        return receivedMessages.size();
    }
}