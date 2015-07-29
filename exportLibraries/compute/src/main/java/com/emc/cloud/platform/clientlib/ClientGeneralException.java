/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

import com.emc.cloud.message.utils.ExternalizedException;

public class ClientGeneralException extends ExternalizedException {
    public ClientGeneralException(ClientMessageKeys key) {
        super(key);
    }

    public ClientGeneralException(ClientMessageKeys key, String[] params) {
        super(key, params);
    }

    public ClientGeneralException(ClientMessageKeys key, Throwable cause) {
        super(key, cause);
    }

    public ClientGeneralException(ClientMessageKeys key, String[] params, Throwable cause) {
        super(key, params, cause);
    }
}
