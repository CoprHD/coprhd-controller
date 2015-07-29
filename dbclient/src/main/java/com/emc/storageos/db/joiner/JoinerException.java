/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
