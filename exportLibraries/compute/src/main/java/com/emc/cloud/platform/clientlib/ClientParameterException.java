/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

public class ClientParameterException extends ClientGeneralException {
    public ClientParameterException(ClientMessageKeys key, String[] params) {
        super(key, params);
    }
}
