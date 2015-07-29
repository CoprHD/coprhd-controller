/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.joiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoinerException extends RuntimeException {
    private static final Logger _log = LoggerFactory.getLogger(Joiner.class);
    String message;

    public JoinerException(String message) {
        super(message);
        _log.error(message, this);
    }
}
