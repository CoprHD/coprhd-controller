package com.emc.storageos.driver.scaleio.api.restapi

import com.emc.storageos.driver.scaleio.api.ScaleIOConstants
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Created by shujinwu on 11/19/15.
 */
class ScaleIORestClientTest extends groovy.util.GroovyTestCase {
    private static Logger log = LoggerFactory.getLogger(ScaleIORestClientTest.class);
    private static ScaleIORestClient restClient;
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";
    private static final String HOST = "10.193.17.97";
    private static final String USER = "admin";
    private static final String PASSWORD = "Scaleio123";
    private static int PORT = 443;
    void setUp() {
        super.setUp()
        ScaleIORestClientFactory factory = new ScaleIORestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        String endpoint = ScaleIOConstants.getAPIBaseURI(HOST, PORT);
        restClient = (ScaleIORestClient) factory.getRESTClient(URI.create(endpoint), USER, PASSWORD, true);
    }

    void testGetVersion() {
        String result = null;
        try {
            result = restClient.getVersion();
        } catch (Exception e) {
            log.error("Exception: ", e);
        }

        System.out.printf("version %s", result);
    }

    void testSetUsername() {

    }

    void testSetPassword() {

    }

    void testInit() {

    }

    void testQueryAllSDC() {

    }

    void testQueryAllSDS() {

    }

    void testQueryStoragePool() {

    }

    void testAddVolume() {

    }

    void testAddVolume1() {

    }

    void testRemoveVolume() {

    }

    void testModifyVolumeCapacity() {

    }

    void testSnapshotVolume() {

    }

    void testSnapshotMultiVolume() {

    }

    void testMapVolumeToSDC() {

    }

    void testUnMapVolumeToSDC() {

    }

    void testRemoveConsistencyGroupSnapshot() {

    }

    void testQueryAllSCSIInitiators() {

    }

    void testMapVolumeToSCSIInitiator() {

    }

    void testUnMapVolumeFromSCSIInitiator() {

    }



    void testSetResourceHeaders() {

    }

    void testAuthenticate() {

    }

    void testCheckResponse() {

    }

    void testGetSystem() {

    }

    void testGetProtectionDomains() {

    }

    void testGetProtectionDomainStoragePools() {

    }

    void testGetStoragePoolStats() {

    }

    void testGetSystemId() {

    }

    void testQueryVolume() {

    }

    void testGetVolumes() {

    }

    void testGetVolumeNameMap() {

    }
}
