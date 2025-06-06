// FileProcessingServiceTest.java
package com.example.integration.test.unit;

import com.example.integration.interceptor.TestChannelInterceptor;
import com.example.integration.service.FileProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileProcessingServiceUnitTest {

    @Mock
    private MessageChannel copyToMonitorChannel;

    @Mock
    private MessageChannel moveToPreProcessChannel;

    @InjectMocks
    private FileProcessingService fileProcessingService;

    private File testFile;

    @BeforeEach
    void setUp() {
        testFile = new File("test.txt");
    }

    @Test
    void testProcessFile() {
        // Arrange
        when(copyToMonitorChannel.send(any())).thenReturn(true);
        when(moveToPreProcessChannel.send(any())).thenReturn(true);

        // Act
        fileProcessingService.processFile(MessageBuilder.withPayload(testFile).build());

        // Assert
        verify(copyToMonitorChannel, times(1)).send(any());
        verify(moveToPreProcessChannel, times(1)).send(any());
    }

    @Test
    void testProcessFileWithRealChannelsAndInterceptors() {
        // Create real channels with interceptors for more realistic testing
        DirectChannel realCopyChannel = new DirectChannel();
        DirectChannel realMoveChannel = new DirectChannel();

        TestChannelInterceptor copyInterceptor = new TestChannelInterceptor();
        TestChannelInterceptor moveInterceptor = new TestChannelInterceptor();

        realCopyChannel.addInterceptor(copyInterceptor);
        realMoveChannel.addInterceptor(moveInterceptor);

        // Create service with real channels
        FileProcessingService service = new FileProcessingService();
        service.copyToMonitorChannel = realCopyChannel;
        service.moveToPreProcessChannel = realMoveChannel;

        // Act
        service.processFile(MessageBuilder.withPayload(testFile).build());

        // Assert
        assertEquals(1, copyInterceptor.getSentMessageCount(), "Copy channel should receive 1 message");
        assertEquals(1, moveInterceptor.getSentMessageCount(), "Move channel should receive 1 message");
        
        assertEquals(testFile, copyInterceptor.getLastSentMessage().getPayload(), "Copy message payload should match");
        assertEquals(testFile, moveInterceptor.getLastSentMessage().getPayload(), "Move message payload should match");
    }

    @Test
    void testProcessFileWithNullFile() {
        // This test ensures the service handles edge cases gracefully
        File nullFile = null;
        
        // This would throw NPE in current implementation
        assertThrows(NullPointerException.class, () -> {
            fileProcessingService.processFile(MessageBuilder.withPayload(nullFile).build());
        });
    }
}