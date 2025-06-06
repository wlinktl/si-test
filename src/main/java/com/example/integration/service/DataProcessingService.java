// FileProcessingService.java
package com.example.integration.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class DataProcessingService {

    @Autowired
    MessageChannel copyToMonitorChannel;

    @Autowired
    MessageChannel moveToPreProcessChannel;

    @ServiceActivator(inputChannel = "inboundFileChannel")
    public void processFile(Message<File> message) {
        File file = message.getPayload();
        System.out.println("Processing file: " + file.getName());
        
        // Send to both channels for parallel processing
        copyToMonitorChannel.send(MessageBuilder.withPayload(file).build());
        moveToPreProcessChannel.send(MessageBuilder.withPayload(file).build());
    }
}