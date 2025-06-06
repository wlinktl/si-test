package com.example.integration.advice;

import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.messaging.Message;

public class TestAdvice extends AbstractRequestHandlerAdvice {
    private int count = 0;

    @Override
    protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) {
        count++;
        return callback.execute();
    }

    public Object testInvoke(Message<String> message) {
        return doInvoke(new ExecutionCallback() {
            @Override
            public Object execute() {
                return "handled";
            }

            @Override
            public Object cloneAndExecute() {
                throw new UnsupportedOperationException("cloneAndExecute not supported in this test");
            }
        }, null, message);
    }

    public int getCount() {
        return count;
    }
}


