package com.example.integration.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.test.context.SpringIntegrationTest;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringIntegrationTest
class FlowConfigTest {

    @Autowired
    private MessageChannel copyToMonitorChannel;

    @Autowired
    private MessageChannel moveToPreProcessChannel;

    @Test
    void testCopyToMonitorFlowHandlesFile() throws Exception {
        File testFile = new File("monitor_test.txt");
        if (!testFile.exists()) {
            boolean created = testFile.createNewFile();
            assertThat(created).isTrue();
        }
        copyToMonitorChannel.send(new GenericMessage<>(testFile));
        // Optionally, assert file exists in MONITOR_DIR
        File outFile = new File("monitor/monitor_monitor_test.txt");
        Thread.sleep(500);
        assertThat(outFile.exists()).isTrue();
        outFile.delete();
        testFile.delete();
    }

    @Test
    void testMoveToPreProcessFlowHandlesAndDeletes() throws Exception {
        File testFile = new File("preprocess_test.txt");
        if (!testFile.exists()) {
            boolean created = testFile.createNewFile();
            assertThat(created).isTrue();
        }
        moveToPreProcessChannel.send(new GenericMessage<>(testFile));
        Thread.sleep(500);
        assertThat(testFile.exists()).isFalse();
        File outFile = new File("pre-process/preprocess_test.txt");
        assertThat(outFile.exists()).isTrue();
        outFile.delete();
    }
}