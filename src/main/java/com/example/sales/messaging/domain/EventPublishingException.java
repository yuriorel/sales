package com.example.sales.messaging.domain;

public class EventPublishingException extends RuntimeException {

    public EventPublishingException(SalesEvent event, Throwable cause) {
        super("Could not publish " + event, cause);
    }
}
