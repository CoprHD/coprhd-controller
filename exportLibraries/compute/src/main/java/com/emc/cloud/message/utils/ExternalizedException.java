/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.message.utils;

/**
 * Base class for externalized exceptions. All constructors requre a MessageKeysEnumeration
 * that implements the MessageKeysInterface. Some constructors also require parameter arrays
 * or Throwable cause for nesting exceptions.
 * @author root
 *
 */
public class ExternalizedException extends Exception {
    MessageKeysInterface key;           // A message key enumerator (enums cannot be extended therefore no base)
    String[] params;                    // String parameters
    
    private ExternalizedException() { }
    
    public ExternalizedException(MessageKeysInterface key) {
        super(key.getDecodedMessage());
        this.key = key;
    }
    public ExternalizedException(MessageKeysInterface key, String[] params) {
        super(key.getDecodedMessage(params));
        this.key = key;
        this.params = params;
    }
    public ExternalizedException(MessageKeysInterface key, Throwable cause) {
        super(key.getDecodedMessage(), cause);
        this.key = key;
    }
    public ExternalizedException(MessageKeysInterface key, String[] params, Throwable cause) {
        super(key.getDecodedMessage(params), cause);
        this.key = key;
        this.params = params;
    }
    public MessageKeysInterface getKey() {
        return key;
    }
    public String[] getParams() {
        return params;
    }
}
