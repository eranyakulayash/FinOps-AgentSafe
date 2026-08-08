package com.finops.agentsafe.validator;

public class InvariantViolationException extends RuntimeException {
    public InvariantViolationException(String message) {
        super(message);
    }
}
