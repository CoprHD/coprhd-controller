/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

public class ClientTimeoutException extends ClientGeneralException {
    ClientTimeoutException(String message) {
        super(ClientMessageKeys.TIMED_OUT, new String[]{message});
    }
}
