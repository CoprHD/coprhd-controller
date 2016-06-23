/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.exception;

/**
 * This class defines the default VMAX V3 REST Call exception.
 *
 * Created by gang on 6/22/16.
 */
public class Vmaxv3RestCallException extends RuntimeException {

    public Vmaxv3RestCallException() {
        super();
    }

    public Vmaxv3RestCallException(String message) {
        super(message);
    }

    public Vmaxv3RestCallException(String message, Throwable cause) {
        super(message, cause);
    }

    public Vmaxv3RestCallException(Throwable cause) {
        super(cause);
    }
}
