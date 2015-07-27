/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.vipr.model.sys.logging;

import java.util.Arrays;
import java.util.List;

/**
 * Enumeration defines the valid log level scopes.
 */
public enum LogScopeEnum {

    SCOPE_DEFAULT("0"), //root logger
    SCOPE_DEPENDENCY("1"); //root logger and the logger of specified dependencies

    private String value;

    LogScopeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static String getName(String value){
        for(LogScopeEnum e : LogScopeEnum.values()){
            if (e.value.equals(value)){
                return e.name();
            }
        }
        return null;
    }
} 

