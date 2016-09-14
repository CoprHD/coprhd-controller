package com.emc.storageos.vasa.async;

class AsyncJobException extends java.lang.Exception {

    private static final long serialVersionUID = 1L;

    public AsyncJobException(String message, Throwable cause) {
        super(message, cause);
    }

    public AsyncJobException(String message) {
        super(message);
    }
}