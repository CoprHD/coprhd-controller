/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

public class ClientNotFoundException extends ClientGeneralException {
    public ClientNotFoundException(String type, String entityName) {
        super(ClientMessageKeys.NOT_FOUND_EXCEPTION, new String[] { type, entityName });
    }

    public ClientNotFoundException(String type, String entityName, Throwable cause) {
        super(ClientMessageKeys.NOT_FOUND_EXCEPTION, new String[] { type, entityName }, cause);
    }
}
