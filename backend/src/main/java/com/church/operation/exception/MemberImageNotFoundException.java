package com.church.operation.exception;

public class MemberImageNotFoundException extends RuntimeException {
    public MemberImageNotFoundException() {
        super("Member image was not found.");
    }
}
