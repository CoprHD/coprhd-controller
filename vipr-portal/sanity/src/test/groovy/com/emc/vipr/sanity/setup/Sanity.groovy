package com.emc.vipr.sanity.setup

import com.emc.vipr.client.ViPRPortalClient
import org.apache.commons.collections.ExtendedProperties

import com.emc.vipr.client.AuthClient
import com.emc.vipr.client.ClientConfig
import com.emc.vipr.client.ViPRCatalogClient2
import com.emc.vipr.client.ViPRCoreClient
import com.emc.vipr.client.ViPRSystemClient

class Sanity {
    static final Integer API_TASK_TIMEOUT = 120000

    static ExtendedProperties properties
    static ClientConfig clientConfig
    static String authToken

    // Clients
    static ViPRCoreClient client
    static ViPRCatalogClient2 catalog
    static ViPRPortalClient portal
    static ViPRSystemClient sys

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
