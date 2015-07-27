/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.setup;

import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.ViPRSystemClient;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;


public class LocalUserMode {
    private static Logger logger = LoggerFactory.getLogger(LocalUserMode.class);

    protected static String rootPassword = "ChangeMe"; // NOSONAR ("Suppressing: removing this hard-coded password since it's default vipr's password")
    protected static String controllerNodeEndpoint;
    protected static String dataNodeEndpoint;
    protected static ViPRSystemClient systemClient;
    protected static ViPRCoreClient coreClient;

    @BeforeClass
    public synchronized static void setup_LocalUserModeBaseClass() throws Exception {
        //get the Bourne IP from parameter
        String  param_IP = System.getProperty("APP_HOST_NAMES");
        if(param_IP != null){
            controllerNodeEndpoint = param_IP;
        }else{
            Properties properties = new Properties();
            properties.load(ClassLoader.class.getResourceAsStream("/test-env.conf"));
            controllerNodeEndpoint = properties.getProperty("APP_HOST_NAMES");
        }
        logger.info("Controller node endpoint: " + controllerNodeEndpoint);

        systemClient = new ViPRSystemClient(controllerNodeEndpoint, true).withLogin("root", rootPassword);
        coreClient = new ViPRCoreClient(controllerNodeEndpoint, true).withLogin("root", rootPassword);

        waitForClusterStable();

        PropertyInfoRestRep propertyInfoRestRep = systemClient.config().getProperties();
        String viprDataIps = propertyInfoRestRep.getProperty("system_datanode_ipaddrs");
        if (viprDataIps != null) {
            dataNodeEndpoint = viprDataIps.split(",")[0];
        }
    }

    @AfterClass
    public static void teardown_LocalUserModeBaseClass() throws Exception {
        if (systemClient != null) {
            systemClient.auth().logout();
        }

        if (coreClient != null) {
            coreClient.auth().logout();
        }
    }


    protected static void waitForClusterStable() throws Exception {
        Long timeout = 1200L;
        String state = systemClient.upgrade().getClusterState();

        Long startTime= System.currentTimeMillis();
        Long timeoutInMilliSeconds = timeout * 1000;

        while(true){
            if(systemClient.upgrade().getClusterState().contains("STABLE")){

                // Wait an extra 10 seconds before returning...
                // Thread.sleep(10000);
                logger.info("Cluster is STABLE");
                return;
            }

            // retry after 10 seconds...
            logger.info("Cluster is " + state + ", retry after 10 seconds");
            Thread.sleep(10000);

            //  No need to try further...
            if((System.currentTimeMillis() - startTime) > timeoutInMilliSeconds){
                logger.info("Cluster is still not stable after waiting for " + timeout + " seconds");
                break;
            }
        }
    }
}
