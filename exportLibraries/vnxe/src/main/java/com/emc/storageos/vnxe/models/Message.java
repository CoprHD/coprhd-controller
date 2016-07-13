/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

public class Message {
    private String locale;
    private String message;

    public Message() {
    };

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
