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
package com.emc.storageos.systemservices.impl.logsvc;

/**
 * Class defines the log service request parameter names.
 * <p/>
 * Note that using a class rather than an enum because the values are used in the JAX-RS QueryParam annotations in the LogService resource,
 * which require a constant expression.
 */
public class LogRequestParam {

    public static final String ID = "id";
    public static final String NODE_ID = "node_id";
    public static final String LOG_NAME = "log_name";
    public static final String SEVERITY = "severity";
    public static final String START_TIME = "start";
    public static final String END_TIME = "end";
    public static final String MSG_REGEX = "msg_regex";
    public static final String MAX_COUNT = "maxcount";
    public static final String DRY_RUN = "dryrun";
}
