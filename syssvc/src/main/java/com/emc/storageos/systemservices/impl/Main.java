/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl;

import com.emc.storageos.systemservices.impl.security.IPSecInitialRotate;
import com.emc.storageos.systemservices.impl.security.IPSecMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;

public class Main {
    private static final Logger _log = LoggerFactory.getLogger(Main.class);
    private static final String BUILD_TYPE = "buildType";
    private static final String SERVICE_BEAN = "syssvcserver";
    private static final String IPSEC_MONITOR_BEAN = "ipsecMonitor";
    private static final String IPSEC_ROTATE_BEAN = "ipsecInitialRotate";

    public static void main(String[] args) {
        try {
            SLF4JBridgeHandler.install();
            GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
            ctx.getEnvironment().setActiveProfiles(System.getProperty(BUILD_TYPE));
            ctx.load(args);

            // start ipsec monitor
            IPSecMonitor ipsecMonitor = (IPSecMonitor) ctx.getBean(IPSEC_MONITOR_BEAN);
            ipsecMonitor.setApplicationContext(ctx);
            ipsecMonitor.start();

            ctx.refresh();

            // start syssvc
            SysSvcImpl sysservice = (SysSvcImpl) ctx.getBean(SERVICE_BEAN);
            sysservice.start();

            // start initial ipsec key rotation
            IPSecInitialRotate initialRotate = (IPSecInitialRotate) ctx.getBean(IPSEC_ROTATE_BEAN);
            initialRotate.run();
        } catch (Exception e) {
            _log.error("failed to start {}:", SERVICE_BEAN, e);
            System.exit(1);
        }
    }
}
