// src/test/java/com/example/integration/advice/TestAdviceTest.java
package com.example.integration.advice;

import org.junit.jupiter.api.Test;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import static org.junit.jupiter.api.Assertions.*;

class TestAdviceTest {

    @Test
    void testAdviceIncrementsCounter() throws Throwable {
        TestAdvice advice = new TestAdvice();
        Message<String> message = MessageBuilder.withPayload("test").build();

        Object result = advice.testInvoke(message);

        assertEquals("handled", result);
        assertEquals(1, advice.getCount());
    }
}