package com.church.operation.exception;

public class EmailDeliveryException extends RuntimeException {
    private final Category category;

    public EmailDeliveryException(Category category) {
        super(message(category));
        this.category = category;
    }

    public Category category() {
        return category;
    }

    private static String message(Category category) {
        return switch (category) {
            case CONNECTION -> "The application could not connect to the email server.";
            case AUTHENTICATION -> "The email server rejected the configured credentials.";
            case TLS -> "The secure connection to the email server could not be established.";
            case SENDER_REJECTED -> "The email server rejected the configured sender address.";
            case RECIPIENT_REJECTED -> "The email server rejected the recipient address.";
            case DELIVERY -> "The test email could not be delivered.";
        };
    }

    public enum Category {
        CONNECTION,
        AUTHENTICATION,
        TLS,
        SENDER_REJECTED,
        RECIPIENT_REJECTED,
        DELIVERY
    }
}
