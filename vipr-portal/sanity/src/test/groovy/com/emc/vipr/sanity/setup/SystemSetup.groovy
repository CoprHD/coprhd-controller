package com.emc.vipr.sanity.setup

import static com.emc.vipr.sanity.setup.Sanity.*
import com.emc.storageos.model.property.PropertyInfoUpdate
import java.util.concurrent.TimeoutException

class SystemSetup {
    // Sleep time of 15 seconds between calls
    static final int SLEEP_MS = 30000
    // Maximum of 20 minutes to wait for STABLE
    static final int MAX_WAIT_MS = 1200000

    static void commonSetup() {
        // If licensed, skip the setup assuming it has already beed done
        if (sys.license().get().licenseFeatures.size() > 0) {
            println "System is licensed, skipping initial configuration"
            return
        }

        println "Configuring system properties"
        sys.config().setProperties(new PropertyInfoUpdate(properties: [
            // Connect EMC Email
            system_connectemc_transport: "SMTP",
            system_connectemc_smtp_server: properties.SMTP_SERVER,
            system_connectemc_smtp_to: properties.SMTP_PRIMARY_EMAIL,
            system_connectemc_smtp_from: properties.SMTP_SENDER_EMAIL,

            // DNS
            network_nameservers: properties.DNS_PROP_VALUE.join(','),

            // NTP
            network_ntpservers: properties.NTP_PROP_VALUE.join(','),

            // Extra Nodes
            system_datanode_ipaddrs: properties.getList("EXTRA_NODES").join(','),

            // Update Repository
            system_update_repo: properties.SYSTEM_UPDATE_DIRECTORY_URL,

            // Proxy User Password
            system_proxyuser_encpassword: properties.PROXYUSER_PASSWORD
        ]))

        // wait for stable
        waitForStable()

        println "Adding license file to the system"
        sys.license().set(SystemSetup.class.getResource("/license.txt").text)

        println "Registering portal initial setup complete"
        portal.setup().skip();
    }

    static void waitForStable() {
        println "Waiting for cluster state to become STABLE"

        if (Sanity.properties.getBoolean(SanityProperties.SKIP_STABLE, false)) {
            return
        }

        int timeCounter = 0
        while (timeCounter < MAX_WAIT_MS) {
            try {
                print "."
                if (sys.upgrade().clusterInfo.currentState == 'STABLE') {
                    println "\nCluster is STABLE"
                    // One last sleep as sometimes it takes a few seconds before everything is available
                    sleep(SLEEP_MS)
                    return
                }
            }
            catch (Exception e) {
                // Ignore this excpetion
            }

            timeCounter += SLEEP_MS
            sleep(SLEEP_MS)
        }
        println "\nCluster is NOT STABLE"
        throw new TimeoutException("Timeout occurred while waiting for cluster state to become STABLE. Please try again or check syssvc.log on your appliance for more information.")
    }
}
