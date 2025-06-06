// FileProcessingConfig.java
package com.example.integration.config;

import com.example.integration.advice.TestAdvice;
import org.aopalliance.aop.Advice;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.FileWritingMessageHandler;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.Message;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.io.File;

import java.util.concurrent.Executor;

@Configuration
@EnableIntegration
public class FlowConfig {

    private static final String MONITOR_DIR = "monitor";
    private static final String PRE_PROCESS_DIR = "pre-process";

    @Autowired
    private BeanFactory beanFactory;

    private BeanFactory getBeanFactory() {
        return this.beanFactory;
    }

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("file-processor-");
        executor.initialize();
        return executor;
    }

    @Bean
    public MessageChannel inboundFileChannel() {
        return new QueueChannel(50);
    }

    @Bean
    public MessageChannel copyToMonitorChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel moveToPreProcessChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel processedFileChannel() {
        return new DirectChannel();
    }

    @Bean
    public PublishSubscribeChannel pubSubChannel(Executor taskExecutor) {
        return new PublishSubscribeChannel(taskExecutor);
    }

    @Bean
    public IntegrationFlow fileProcessingFlow() {
        return IntegrationFlow
                .from(inboundFileChannel())
                .transform((Message<?> m) -> {
                    System.out.println("Processing file: " + ((File) m.getPayload()).getName());
                    return m;
                })
                .channel(pubSubChannel(taskExecutor()))
                .get();
    }

    @Bean
    public IntegrationFlow pubSubFlow() {
        return IntegrationFlow
                .from(pubSubChannel(taskExecutor()))
                .publishSubscribeChannel(c -> c
                        .subscribe(f -> f.channel(copyToMonitorChannel()))
                        .subscribe(f -> f.channel(moveToPreProcessChannel()))
                )
                // Remove .channel(processedFileChannel())
                .get();
    }

    @Bean
    public IntegrationFlow copyToMonitorFlow(@Qualifier("monitorAdvice") Advice monitorAdvice) {
        return IntegrationFlow
                .from(copyToMonitorChannel())
                .handle(monitorFileHandler(), e -> e.advice(monitorAdvice))
                .get();
    }

    @Bean
    public IntegrationFlow moveToPreProcessFlow(@Qualifier("preProcessAdvice") Advice preProcessAdvice) {
        return IntegrationFlow
                .from(moveToPreProcessChannel())
                .handle(preProcessFileHandler(), e -> e.advice(preProcessAdvice))
                .get();
    }

    @Bean
    public MessageHandler monitorFileHandler() {
        FileWritingMessageHandler handler = new FileWritingMessageHandler(new File(MONITOR_DIR));
        handler.setFileExistsMode(FileExistsMode.REPLACE);
        handler.setDeleteSourceFiles(false);
        handler.setFileNameGenerator(message -> {
            File originalFile = (File) message.getPayload();
            return "monitor_" + originalFile.getName();
        });
        handler.setExpectReply(false); // This is the key change
        return handler;
    }

    @Bean
    public MessageHandler preProcessFileHandler() {
        FileWritingMessageHandler handler = new FileWritingMessageHandler(new File(PRE_PROCESS_DIR));
        handler.setFileExistsMode(FileExistsMode.REPLACE);
        handler.setDeleteSourceFiles(true);
        handler.setFileNameGenerator(message -> {
            File originalFile = (File) message.getPayload();
            return originalFile.getName();
        });
        handler.setExpectReply(false); // This is the key change
        return handler;
    }

    // Register advice beans for injection
    @Bean
    public Advice monitorAdvice() {
        return new TestAdvice();
    }

    @Bean
    public Advice preProcessAdvice() {
        return new TestAdvice();
    }


    @Bean
    public MessageSource<File> fileReadingMessageSource() {
        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(new File("input-directory")); // Replace with your directory path
        source.setFilter(new SimplePatternFileListFilter("*.txt")); // Example filter for .txt files
        return source;
    }

    @Bean
    public IntegrationFlow fileInboundFlow() {
        return IntegrationFlow
                .from(fileReadingMessageSource(), c -> c.poller(Pollers.fixedDelay(1000)))
                .channel(inboundFileChannel())
                .get();
    }

}