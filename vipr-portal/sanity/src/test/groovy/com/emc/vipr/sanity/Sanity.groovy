/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity

import org.apache.commons.collections.ExtendedProperties
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

import com.emc.vipr.client.AuthClient
import com.emc.vipr.client.ClientConfig
import com.emc.vipr.client.ViPRCatalogClient2
import com.emc.vipr.client.ViPRCoreClient
import com.emc.vipr.client.ViPRPortalClient
import com.emc.vipr.client.ViPRSystemClient
import com.emc.vipr.sanity.setup.HostSetup
import com.emc.vipr.sanity.setup.ProjectSetup
import com.emc.vipr.sanity.setup.SecuritySetup
import com.emc.vipr.sanity.setup.SystemSetup
import com.emc.vipr.sanity.setup.VCenterSetup
import com.emc.vipr.sanity.setup.VMAXSetup
import com.emc.vipr.sanity.setup.VirtualArraySetup
import com.emc.vipr.sanity.setup.RemoteReplicationSetup

class Sanity {

    static enum LogLevel {ERROR,WARN,INFO,VERBOSE,DEBUG}
    static currentLogLevel = LogLevel.VERBOSE  // adjust to change amount of output
    static removeTopologyWhenDone = true

    static final Integer API_TASK_TIMEOUT = 120000

    static ExtendedProperties properties
    static ClientConfig clientConfig
    static String authToken
    static int services_run = 0

    // Clients
    static ViPRCoreClient client
    static ViPRCatalogClient2 catalog
    static ViPRPortalClient portal
    static ViPRSystemClient sys

    static Class catalogTests = com.emc.vipr.sanity.tests.CatalogAPISanity.class
    static Class blockTests = com.emc.vipr.sanity.tests.CatalogBlockServicesSanity.class
    static Class protectionTests = com.emc.vipr.sanity.tests.CatalogBlockProtectionServicesSanity.class
    static Class vmwareTests = com.emc.vipr.sanity.tests.CatalogVmwareBlockServicesSanity.class
    static Class remoteReplicationTests = com.emc.vipr.sanity.tests.CatalogRemoteReplicationServicesSanity.class

    static allTests = [
        catalogTests,
        blockTests,
        protectionTests,
        vmwareTests
        //,remoteReplicationTests  // omit RR tests so catalog testing does not dependent on it
    ] as Class[]

    public static void main(String[] args) {
        println "----------------------  Starting tests --------------------"
        JUnitCore junit = new JUnitCore()
        junit.addListener(new RunListener() {
                    @Override
                    public void testFailure(Failure failure) throws Exception {
                        println failure.getDescription().getDisplayName()
                        println failure.getException()
                        println failure.getTrace()
                    }
                    @Override
                    public void testAssumptionFailure(Failure failure) {
                        println failure.getDescription().getDisplayName()
                        println failure.getException()
                        println failure.getTrace()
                    }
                });

        String catalogTest = System.getenv("CatalogTest")
        Result result = null
        switch (catalogTest) {
            case "all":
                setup()
                result = junit.run(allTests)
                break
            case "catalog":
                setup()
                result = junit.run(catalogTests)
                break
            case "block":
                setup()
                result = junit.run(blockTests)
                break
            case "protection":
                setup()
                result = junit.run(protectionTests)
                break
            case "vmware":
                setup()
                result = junit.run(vmwareTests)
                break
           case "remotereplication":
                initClients("ldapvipruser1@viprsanity.com","secret")
                currentLogLevel = LogLevel.INFO
                //removeTopologyWhenDone = false
                RemoteReplicationSetup.loadTopology()
                result = junit.run(remoteReplicationTests)
                break
           default:
                println "Not running any tests. Parameter = " + catalogTest
                break
        }

        if (result != null && result.failureCount > 0) {
            System.exit(1);
        }
    }

    static void setup() {
        initClients()
        SystemSetup.commonSetup()
        SecuritySetup.setupActiveDirectory()
        VirtualArraySetup.setup()
        ProjectSetup.setup()
        VirtualArraySetup.updateAcls(client.userTenantId)
        HostSetup.setup()
        VMAXSetup.setupSimulator()
        VCenterSetup.setup()
    }

    static void initClients() {
        initClients(System.getenv("SYSADMIN"), System.getenv("SYSADMIN_PASSWORD"))
    }

    static void initClients(String username, String password) {
        clientConfig = new ClientConfig(
                host: "localhost",
                mediaType: "application/xml",
                requestLoggingEnabled: false,
                ignoreCertificates: true
                )

        login(username,password)
    }

    static void login(String username, String password) {
        println "Logging in to ViPR as $username"
        authToken = new AuthClient(clientConfig).login(username, password)
        client = new ViPRCoreClient(clientConfig).withAuthToken(authToken)
        catalog = new ViPRCatalogClient2(clientConfig).withAuthToken(authToken)
        portal = new ViPRPortalClient(clientConfig).withAuthToken(authToken)
        sys = new ViPRSystemClient(clientConfig).withAuthToken(authToken)
    }

    /*
     * logging methods
     */
    static void printError(Object msg) {
        printMsg(msg,LogLevel.ERROR)
    }

    static void printWarn(Object msg) {
        printMsg(msg,LogLevel.WARN)
    }

    static void printInfo(Object msg) {
        printMsg(msg,LogLevel.INFO)
    }

    static void printVerbose(Object msg) {
        printMsg(msg,LogLevel.VERBOSE)
    }

   static void printDebug(Object msg) {
        printMsg(msg,LogLevel.DEBUG)
    }

    static void printMsg(Object msg, LogLevel level) {
        if ( level <= currentLogLevel) {
            println msg.toString();
        }
    }
}