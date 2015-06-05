/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.cloud.message.utils;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;


@Service
public class MessageResolverService {
    static MessageResolverService singleton;
    
    @Autowired
    ApplicationContext applicationContext;
    
    public MessageResolverService() {
        this.singleton = this;
    }
    
    static public String resolveMessage(String key, String[] params) {
       String message =  singleton.getApplicationContext().getMessage(key, params, Locale.US);
       return message;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
