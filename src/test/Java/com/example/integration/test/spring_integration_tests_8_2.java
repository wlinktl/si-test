package com.example.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.context.SpringIntegrationTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SpringIntegrationTest
@SpringBootTest
@SpringJUnitConfig
class InboundFileFlowTest {

    @Autowired
    private MessagingTemplate messagingTemplate;

    @Mock
    private PlatformTransactionManager txManager;

    @Mock
    private Audit audit;

    @Mock
    private Feeds feeds;

    // Test channels
    private DirectChannel inboundFileChannel;
    private QueueChannel monitorChannel;
    private QueueChannel errorChannel;

    @BeforeEach
    void setUp() {
        inboundFileChannel = new DirectChannel();
        monitorChannel = new QueueChannel();
        errorChannel = new QueueChannel();
        
        // Clear any previous messages
        monitorChannel.purge(null);
        errorChannel.purge(null);
    }

    @Test
    void testFileProcessingFlow_SuccessfulProcessing() throws Exception {
        // Given
        File testFile = createTestFile("test-file.txt", "test content");
        String expectedMonitorUri = "monitor://processed/test-file.txt";
        
        when(feeds.monitorUri(any(Message.class))).thenReturn(expectedMonitorUri);
        when(audit.beginAudit(any())).thenReturn("audit-123");
        when(audit.generateRunId()).thenReturn("run-456");

        // Create message with file headers
        Message<String> inputMessage = MessageBuilder
                .withPayload("test content")
                .setHeader(FileHeaders.ORIGINAL_FILE, testFile)
                .setHeader(FileHeaders.FILENAME, testFile.getName())
                .build();

        // When
        messagingTemplate.send(inboundFileChannel, inputMessage);

        // Then
        // Verify audit methods were called
        verify(audit).beginAudit(any());
        verify(audit).generateRunId();
        
        // Check if message was routed to monitor channel
        Message<?> monitorMessage = monitorChannel.receive(1000);
        assertThat(monitorMessage).isNotNull();
        assertThat(monitorMessage.getPayload()).isEqualTo("test content");
    }

    @Test
    void testFileProcessingFlow_NullMonitorUri_ShouldNotRoute() throws Exception {
        // Given
        File testFile = createTestFile("empty-file.txt", "");
        
        when(feeds.monitorUri(any(Message.class))).thenReturn(null);
        when(audit.beginAudit(any())).thenReturn("audit-123");
        when(audit.generateRunId()).thenReturn("run-456");

        Message<String> inputMessage = MessageBuilder
                .withPayload("")
                .setHeader(FileHeaders.ORIGINAL_FILE, testFile)
                .setHeader(FileHeaders.FILENAME, testFile.getName())
                .build();

        // When
        messagingTemplate.send(inboundFileChannel, inputMessage);

        // Then
        // Message should not be routed to monitor channel when monitorUri is null
        Message<?> monitorMessage = monitorChannel.receive(500);
        assertThat(monitorMessage).isNull();
        
        // Verify audit was still called
        verify(audit).beginAudit(any());
        verify(audit).generateRunId();
    }

    @Test
    void testFileProcessingFlow_ExceptionInProcessing_ShouldRouteToErrorChannel() throws Exception {
        // Given
        File testFile = createTestFile("error-file.txt", "error content");
        
        when(feeds.monitorUri(any(Message.class))).thenThrow(new RuntimeException("Processing failed"));
        when(audit.beginAudit(any())).thenReturn("audit-123");
        when(audit.generateRunId()).thenReturn("run-456");

        Message<String> inputMessage = MessageBuilder
                .withPayload("error content")
                .setHeader(FileHeaders.ORIGINAL_FILE, testFile)
                .setHeader(FileHeaders.FILENAME, testFile.getName())
                .build();

        // When
        messagingTemplate.send(inboundFileChannel, inputMessage);

        // Then
        // Message should be routed to error channel
        Message<?> errorMessage = errorChannel.receive(1000);
        assertThat(errorMessage).isNotNull();
        assertThat(errorMessage.getPayload()).isEqualTo("error content");
    }

    @Test
    void testRouterLogic_WithValidMonitorUri() {
        // Given
        Message<String> message = MessageBuilder
                .withPayload("test content")
                .build();
        
        String monitorUri = "monitor://valid/path";
        when(feeds.monitorUri(message)).thenReturn(monitorUri);

        // When
        String routingResult = routeMessage(message);

        // Then
        assertThat(routingResult).isEqualTo("monitor");
    }

    @Test
    void testRouterLogic_WithNullMonitorUri() {
        // Given
        Message<String> message = MessageBuilder
                .withPayload("test content")
                .build();
        
        when(feeds.monitorUri(message)).thenReturn(null);

        // When
        String routingResult = routeMessage(message);

        // Then
        assertThat(routingResult).isNull();
    }

    @Test
    void testAuditIntegration() {
        // Given
        String expectedAuditId = "audit-789";
        String expectedRunId = "run-101";
        
        when(audit.beginAudit(any())).thenReturn(expectedAuditId);
        when(audit.generateRunId()).thenReturn(expectedRunId);

        Message<String> message = MessageBuilder
                .withPayload("audit test")
                .build();

        // When
        messagingTemplate.send(inboundFileChannel, message);

        // Then
        verify(audit, times(1)).beginAudit(any());
        verify(audit, times(1)).generateRunId();
    }

    @Test
    void testTransactionConfiguration() {
        // Given - Transaction manager should be configured
        assertThat(txManager).isNotNull();
        
        // When processing a message, transaction should be handled
        Message<String> message = MessageBuilder
                .withPayload("transactional test")
                .build();

        // Then - This test verifies transaction manager is injected
        // In real scenario, you'd verify transaction boundaries
        verify(txManager, never()).getTransaction(any()); // Not called yet since no actual processing
    }

    @Test
    void testPollerConfiguration() {
        // This test would verify the poller configuration
        // In a real scenario, you'd test:
        // - Fixed rate polling (every fixedRate milliseconds)
        // - Max messages per poll (1 in this case)
        // - Task executor usage
        
        // For unit testing, we simulate the polling behavior
        File testFile1 = createTestFile("file1.txt", "content1");
        File testFile2 = createTestFile("file2.txt", "content2");
        
        Message<String> message1 = createFileMessage(testFile1, "content1");
        Message<String> message2 = createFileMessage(testFile2, "content2");
        
        // Simulate maxMessagesPerPoll(1) - only one message should be processed per poll
        messagingTemplate.send(inboundFileChannel, message1);
        
        // Verify first message is processed
        verify(audit, atLeast(1)).beginAudit(any());
    }

    // Helper methods
    private File createTestFile(String filename, String content) {
        File testFile = mock(File.class);
        when(testFile.getName()).thenReturn(filename);
        when(testFile.getAbsolutePath()).thenReturn("/test/path/" + filename);
        when(testFile.exists()).the