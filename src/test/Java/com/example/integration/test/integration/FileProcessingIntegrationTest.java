// FileProcessingIntegrationTest.java
package com.example.integration.test.integration;

import com.example.integration.interceptor.TestChannelInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.context.MockIntegrationContext;
import org.springframework.integration.test.context.SpringIntegrationTest;
import org.springframework.integration.test.mock.MockIntegration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@SpringIntegrationTest
@TestPropertySource(properties = {
    "logging.level.org.springframework.integration=DEBUG"
})
@DirtiesContext
public class FileProcessingIntegrationTest {

    @TempDir
    Path tempDir;

    @Autowired
    private MessageChannel inboundFileChannel;

    @Autowired
    private MessageChannel processedFileChannel;

    @Autowired
    private MessageChannel copyToMonitorChannel;

    @Autowired
    private MessageChannel moveToPreProcessChannel;

    @Autowired
    private MockIntegrationContext mockIntegrationContext;

    private TestChannelInterceptor inboundInterceptor;
    private TestChannelInterceptor processedInterceptor;
    private TestAdvice monitorAdvice;
    private TestAdvice preProcessAdvice;

    private Path monitorDir;
    private Path preProcessDir;

    @BeforeEach
    void setUp() throws IOException {
        monitorDir = tempDir.resolve("monitor");
        preProcessDir = tempDir.resolve("pre-process");

        Files.createDirectories(monitorDir);
        Files.createDirectories(preProcessDir);

        // Set up interceptors
        inboundInterceptor = new TestChannelInterceptor();
        processedInterceptor = new TestChannelInterceptor();

        // Set up advice
        monitorAdvice = new TestAdvice();
        preProcessAdvice = new TestAdvice();

        // Add interceptors to channels
        ((DirectChannel) processedFileChannel).addInterceptor(processedInterceptor);
        ((QueueChannel) inboundFileChannel).addInterceptor(inboundInterceptor);

        // Replace handlers with mocked ones using advice
        mockIntegrationContext.substituteMessageHandlerFor("monitorFileHandler",
                MockIntegration.mockMessageHandler());
        mockIntegrationContext.substituteMessageHandlerFor("preProcessFileHandler",
                MockIntegration.mockMessageHandler());
    }

    @Test
    void testFileProcessingPipelineWithDirectMessageInjection() throws IOException, InterruptedException {
        // Create test file
        String testContent = "This is test content";
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, testContent.getBytes());

        // Create and send message directly to the inbound channel
        Message<File> message = MessageBuilder.withPayload(testFile.toFile()).build();
        
        // Send message directly without poller
        boolean sent = inboundFileChannel.send(message);
        assertTrue(sent, "Message should be sent successfully");

        // Wait a bit for async processing
        Thread.sleep(500);

        // Verify message was intercepted
        assertEquals(1, inboundInterceptor.getSentMessageCount(), "Should have sent 1 message to inbound channel");
        
        Message<?> lastInboundMessage = inboundInterceptor.getLastSentMessage();
        assertNotNull(lastInboundMessage, "Should have captured the inbound message");
        assertEquals(testFile.toFile(), lastInboundMessage.getPayload(), "Payload should match");

        // Verify both handlers received messages
        assertEquals(1, monitorAdvice.getHandledMessageCount(), "Monitor handler should have processed 1 message");
        assertEquals(1, preProcessAdvice.getHandledMessageCount(), "PreProcess handler should have processed 1 message");

        // Verify the last processed messages
        Message<?> lastMonitorMessage = monitorAdvice.getLastHandledMessage();
        Message<?> lastPreProcessMessage = preProcessAdvice.getLastHandledMessage();
        
        assertNotNull(lastMonitorMessage, "Should have captured monitor message");
        assertNotNull(lastPreProcessMessage, "Should have captured pre-process message");
        
        assertEquals(testFile.toFile(), lastMonitorMessage.getPayload(), "Monitor message payload should match");
        assertEquals(testFile.toFile(), lastPreProcessMessage.getPayload(), "PreProcess message payload should match");
    }

    @Test
    void testQueueChannelCapacityAndInterceptor() {
        QueueChannel channel = (QueueChannel) inboundFileChannel;
        assertEquals(50, channel.getRemainingCapacity() + channel.getQueueSize(),
                "Queue channel should have capacity of 50");

        // Test that interceptor captures messages
        File testFile = new File("test.txt");
        Message<File> message = MessageBuilder.withPayload(testFile).build();
        
        channel.send(message);
        
        assertEquals(1, inboundInterceptor.getSentMessageCount(), "Interceptor should capture sent message");
        assertEquals(testFile, inboundInterceptor.getLastSentMessage().getPayload(), "Payload should match");
    }

    @Test
    void testMultipleFilesWithMessageTracking() throws InterruptedException {
        // Create multiple test files and send them directly
        for (int i = 1; i <= 3; i++) {
            File testFile = new File("test" + i + ".txt");
            Message<File> message = MessageBuilder.withPayload(testFile).build();
            inboundFileChannel.send(message);
        }

        // Wait for processing
        Thread.sleep(1000);

        // Verify all messages were intercepted
        assertEquals(3, inboundInterceptor.getSentMessageCount(), "Should have sent 3 messages");
        assertEquals(3, monitorAdvice.getHandledMessageCount(), "Monitor should have processed 3 messages");
        assertEquals(3, preProcessAdvice.getHandledMessageCount(), "PreProcess should have processed 3 messages");
    }

    @Test
    void testMessageHeadersAndPayloadTransformation() throws IOException {
        // Create test file
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "test content".getBytes());

        // Create message with custom headers
        Message<File> message = MessageBuilder
                .withPayload(testFile.toFile())
                .setHeader("testHeader", "testValue")
                .setHeader("timestamp", System.currentTimeMillis())
                .build();

        inboundFileChannel.send(message);

        // Wait for processing
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify headers are preserved
        Message<?> capturedMessage = inboundInterceptor.getLastSentMessage();
        assertNotNull(capturedMessage, "Should have captured message");
        assertEquals("testValue", capturedMessage.getHeaders().get("testHeader"), "Custom header should be preserved");
        assertNotNull(capturedMessage.getHeaders().get("timestamp"), "Timestamp header should be preserved");
    }

    @Test
    void testAdviceCapturingResults() {
        // Test that advice can capture method results
        File testFile = new File("test.txt");
        Message<File> message = MessageBuilder.withPayload(testFile).build();
        
        inboundFileChannel.send(message);
        
        // Wait briefly for async processing
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify advice captured the processing
        assertTrue(monitorAdvice.getHandledMessageCount() > 0, "Monitor advice should have captured processing");
        assertTrue(preProcessAdvice.getHandledMessageCount() > 0, "PreProcess advice should have captured processing");
    }

    @Test
    void testChannelInterceptorThreadSafety() throws InterruptedException {
        int messageCount = 10;
        CountDownLatch latch = new CountDownLatch(messageCount);

        // Send multiple messages from different threads
        for (int i = 0; i < messageCount; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    File testFile = new File("test" + index + ".txt");
                    Message<File> message = MessageBuilder.withPayload(testFile).build();
                    inboundFileChannel.send(message);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Wait for all messages to be sent
        assertTrue(latch.await(5, TimeUnit.SECONDS), "All messages should be sent within 5 seconds");
        
        // Wait a bit more for processing
        Thread.sleep(500);

        // Verify all messages were captured
        assertEquals(messageCount, inboundInterceptor.getSentMessageCount(), 
                "All messages should be captured by interceptor");
    }
}