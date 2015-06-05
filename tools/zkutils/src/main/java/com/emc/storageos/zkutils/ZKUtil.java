/**
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
package com.emc.storageos.zkutils;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class ZKUtil {

    public static final String ZKUTI_CONF = "/zkutils-conf.xml";
    public static final String COORDINATOR_BEAN = "coordinator";
    public static final String NODE_ID_PROPERTY_NAME = "node_id";
    public static final String STANDALONE_NAME = "standalone";

    public static CoordinatorClient getCoordinatorClient() {
        ApplicationContext ctx = new ClassPathXmlApplicationContext(ZKUTI_CONF);
        return (CoordinatorClient) ctx.getBean(COORDINATOR_BEAN);
    }
}
