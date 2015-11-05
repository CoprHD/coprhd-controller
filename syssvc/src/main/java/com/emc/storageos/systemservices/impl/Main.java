/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl;

import com.emc.storageos.systemservices.impl.security.IPSecMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.support.GenericXmlApplicationContext;

public class Main {
    private static final Logger _log = LoggerFactory.getLogger(Main.class);
    private static final String BUILD_TYPE = "buildType";
    private static final String SERVICE_BEAN = "syssvcserver";
    private static final String IPSEC_MONITOR_BEAN = "ipsecMonitor";

    public static void main(String[] args) {
        try {
            SLF4JBridgeHandler.install();
            GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
            ctx.getEnvironment().setActiveProfiles(System.getProperty(BUILD_TYPE));
            _log.info("before load sys-conf.xml -- ipsec");
            ctx.load(args);
            _log.info("after load sys-conf.xml -- ipsec");
            ctx.refresh();
            _log.info("after load refresh -- ipsec");

            // start ipsec monitor
            IPSecMonitor ipsecMonitor = (IPSecMonitor) ctx.getBean(IPSEC_MONITOR_BEAN);
            ipsecMonitor.start();

            // start syssvc
            SysSvcImpl sysservice = (SysSvcImpl) ctx.getBean(SERVICE_BEAN);
            sysservice.start();
        } catch (Exception e) {
            _log.error("failed to start {}:", SERVICE_BEAN, e);
            System.exit(1);
        }
    }
}
