/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl;

import com.emc.storageos.systemservices.impl.security.SecretsInit;
import com.emc.storageos.systemservices.impl.security.IPSecMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.support.GenericXmlApplicationContext;

public class Main {
    private static final Logger _log = LoggerFactory.getLogger(Main.class);
    private static final String BUILD_TYPE = "buildType";
    private static final String SERVICE_BEAN = "syssvcserver";
    private static final String IPSEC_ROTATE_BEAN = "ipsecInitialRotate";

    private static final int WAIT_BEFORE_EXIT_IN_SECONDS = 300; // in seconds 

    public static void main(String[] args) {
        try {
            SLF4JBridgeHandler.install();
            GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
            ctx.getEnvironment().setActiveProfiles(System.getProperty(BUILD_TYPE));
            ctx.load(args);

            // start ipsec monitor
            IPSecMonitor ipsecMonitor = new IPSecMonitor();
            ipsecMonitor.setApplicationContext(ctx);
            ipsecMonitor.start();

            ctx.refresh();

            // start syssvc
            SysSvcImpl sysservice = (SysSvcImpl) ctx.getBean(SERVICE_BEAN);
            sysservice.start();

            // start initial ipsec key rotation
            SecretsInit initialRotate = (SecretsInit) ctx.getBean(IPSEC_ROTATE_BEAN);
            new Thread(initialRotate).start();
        } catch (Exception e) {
            _log.error("failed to start {}:", SERVICE_BEAN, e);
            
            // Add a delay here before terminating the service, so that DR ZK health 
            // monitor has a chance to run before restart
            _log.error("service is going to restart after {} seconds", WAIT_BEFORE_EXIT_IN_SECONDS);
            try {Thread.sleep(WAIT_BEFORE_EXIT_IN_SECONDS * 1000);} catch(Exception ex) {}
            System.exit(1);
        }
    }
}
