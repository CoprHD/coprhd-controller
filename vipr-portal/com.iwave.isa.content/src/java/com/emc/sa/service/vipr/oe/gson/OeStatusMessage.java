package com.emc.sa.service.vipr.oe.gson;

public class OeStatusMessage {
    private String message;

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public boolean isValid() {
        return message != null;
    }
}
