/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.logging;

/**
 * Enumeration defines the valid log level scopes.
 */
public enum LogScopeEnum {

    SCOPE_DEFAULT("0"), // root logger
    SCOPE_DEPENDENCY("1"); // root logger and the logger of specified dependencies

    private String value;

    LogScopeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static String getName(String value) {
        for (LogScopeEnum e : LogScopeEnum.values()) {
            if (e.value.equals(value)) {
                return e.name();
            }
        }
        return null;
    }
}
