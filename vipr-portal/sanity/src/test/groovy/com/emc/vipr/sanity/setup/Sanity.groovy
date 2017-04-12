package com.emc.vipr.sanity.setup

import org.apache.commons.collections.ExtendedProperties
import org.junit.runner.JUnitCore

import com.emc.vipr.client.AuthClient
import com.emc.vipr.client.ClientConfig
import com.emc.vipr.client.ViPRCatalogClient2
import com.emc.vipr.client.ViPRCoreClient
import com.emc.vipr.client.ViPRPortalClient
import com.emc.vipr.client.ViPRSystemClient

class Sanity {
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

    public static void main(String[] args) {
        setup()
        VNXSetup.setupSimulator()
        JUnitCore junit = new JUnitCore()
        String catalogTest = System.getenv("CatalogTest")
        switch (catalogTest) {
            case "catalog":
                junit.main(com.emc.vipr.sanity.CatalogAPISanity)
                break
            case "block":
                junit.main(com.emc.vipr.sanity.CatalogBlockServicesSanity)
                break
            case "protection":
                junit.main(com.emc.vipr.sanity.CatalogBlockProtectionServicesSanity)
            case "vmware":
                VCenterSetup.setup()
                junit.main(com.emc.vipr.sanity.CatalogVmwareBlockServicesSanity)
                break
            default:
                println "Not running any tests. Parameter = " + catalogTest
                break
        }
    }

    static void initialize() {
        println "Initializing Sanity Test Harness"
        // Initialize java clients
        initClients()
    }

    static void setup() {
        initialize()
        SystemSetup.commonSetup()
        SecuritySetup.setupActiveDirectory()
        VirtualArraySetup.setup()
        ProjectSetup.setup()
        VirtualArraySetup.updateAcls(client.userTenantId)
        HostSetup.setup()



    }

    static void initClients() {
        clientConfig = new ClientConfig(
                host: "localhost",
                mediaType: "application/xml",
                requestLoggingEnabled: false,
                ignoreCertificates: true
                )

        login(System.getenv("SYSADMIN"), System.getenv("SYSADMIN_PASSWORD"))
    }

    static void login(String username, String password) {
        println "Logging in to ViPR as $username"
        authToken = new AuthClient(clientConfig).login(username, password)
        client = new ViPRCoreClient(clientConfig).withAuthToken(authToken)
        catalog = new ViPRCatalogClient2(clientConfig).withAuthToken(authToken)
        portal = new ViPRPortalClient(clientConfig).withAuthToken(authToken)
        sys = new ViPRSystemClient(clientConfig).withAuthToken(authToken)
    }
}
