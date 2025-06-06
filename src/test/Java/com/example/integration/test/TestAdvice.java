// src/main/java/com/example/integration/test/TestAdvice.java
package com.example.integration.advice;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.messaging.Message;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TestAdvice implements MethodInterceptor {
    private final AtomicInteger handledMessageCount = new AtomicInteger();
    private final AtomicReference<Message<?>> lastHandledMessage = new AtomicReference<>();

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        for (Object arg : args) {
            if (arg instanceof Message) {
                lastHandledMessage.set((Message<?>) arg);
                handledMessageCount.incrementAndGet();
            }
        }
        return invocation.proceed();
    }

    public int getHandledMessageCount() {
        return handledMessageCount.get();
    }

    public Message<?> getLastHandledMessage() {
        return lastHandledMessage.get();
    }
}