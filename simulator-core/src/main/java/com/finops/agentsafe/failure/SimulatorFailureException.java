package com.finops.agentsafe.failure;

public class SimulatorFailureException extends RuntimeException {
    private final int statusCode;

    public SimulatorFailureException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
