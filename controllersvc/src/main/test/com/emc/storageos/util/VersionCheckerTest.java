/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.util;

import static org.easymock.EasyMock.createMock;

import java.util.HashMap;
import java.util.Map;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

public class VersionCheckerTest {

    @BeforeClass
    public static void setup() {
        CoordinatorClient coordinator = createMock(CoordinatorClient.class);
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("controller_brocade_firmware_version", "11.2.1");
        properties.put("controller_mds_firmware_version", "5.0(1a)");
        properties.put("controller_rp_firmware_version", "4.1");
        properties.put("controller_vmax_firmware_version", "5876.221");
        properties.put("controller_vnxblock_firmware_version", "05.32");
        properties.put("controller_vnxfile_firmware_version", "7.1.71");
        properties.put("controller_isilon_firmware_version", "7.0.2.0");
        properties.put("controller_netapp_firmware_version", "8.1.1");
        properties.put("controller_vplex_firmware_version", "5.2");
        properties.put("controller_smis_provider_version", "4.6.1.1");
        properties.put("compute_windows_version", "6.0.6002");
        properties.put("compute_suse_linux_version", "11");
        properties.put("compute_redhat_linux_version", "5.9");
        properties.put("compute_hpux_version", "11.31");
        PropertyInfo propertyInfo = new PropertyInfo(properties);
        EasyMock.expect(coordinator.getPropertyInfo()).andReturn(propertyInfo).anyTimes();
        EasyMock.replay(coordinator);
        new VersionChecker().setCoordinator(coordinator);
    }

    private void exceptionHelper(DiscoveredDataObject.Type systemType, String version) {
        String minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(systemType);
        if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, version) >= 0) {
            Assert.fail("This should have failed but did not. Min version supported: " + minimumSupportedVersion + "; Version checked: "
                    + version);
        }
    }

    private void noExceptionHelper(DiscoveredDataObject.Type systemType, String version) {
        String minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(systemType);
        if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, version) < 0) {
            Assert.fail("This should have passed but did not. Min version supported: " + minimumSupportedVersion + "; Version checked: "
                    + version);
        }
    }

    private void exceptionHelper(String minimumSupportedVersionKey, String version) {
        String minimumSupportedVersion = ControllerUtils
                .getPropertyValueFromCoordinator(new VersionChecker().getCoordinator(),
                        minimumSupportedVersionKey);
        if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, version) >= 0) {
            Assert.fail("This should have failed but did not. Min version supported: " + minimumSupportedVersion + "; Version checked: "
                    + version);
        }
    }

    private void noExceptionHelper(String minimumSupportedVersionKey, String version) {
        String minimumSupportedVersion = ControllerUtils
                .getPropertyValueFromCoordinator(new VersionChecker().getCoordinator(),
                        minimumSupportedVersionKey);
        if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, version) < 0) {
            Assert.fail("This should have passed but did not. Min version supported: " + minimumSupportedVersion + "; Version checked: "
                    + version);
        }
    }

    @Test
    public void testGenericVersions() {
        /* These versions are all equal */
        Assert.assertTrue(VersionChecker.verifyVersionDetails("1.0", "1.0") == 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("0.1", "0.1") == 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("1.2.3.4", "1.2.3.4") == 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("1.0a", "1.0a") == 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("5.0(1a)", "5.0(1a)") == 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("3.5.SP2", "3.5.SP2") == 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("05.32", "05.32") == 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("1", "1") == 0);

        /* The second version is higher than the first version */
        Assert.assertTrue(VersionChecker.verifyVersionDetails("1.0", "1.0.1") > 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("1.0", "1.0(4a)") > 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("1.2.3", "1.2.3.1") > 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("10.4", "10.5") > 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("22.4(2b)", "22.13(2b)") > 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("3.5.SP2", "3.5.SP12") > 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("05.32", "05.40") > 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("5.0(1a)", "5.0(7)") > 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("1", "2") > 0);

        /* The second version is lower than the first version */
        Assert.assertTrue(VersionChecker.verifyVersionDetails("1.0", "0.9") < 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("1.0(2a)", "1.0(1a)") < 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("1.2.3", "1.2") < 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("22.4(2b)", "22.4(2a)") < 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("10.4", "10.3") < 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("3.5.SP12", "3.5.SP2") < 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("05.32", "05.31") < 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("5.0(7)", "5.0(6b)") < 0);
        Assert.assertTrue(VersionChecker.verifyVersionDetails("2", "1") < 0);
    }

    @Test
    public void testBrocade() throws Exception {
        noExceptionHelper(DiscoveredDataObject.Type.brocade, "11.2.1 build 165");
        noExceptionHelper(DiscoveredDataObject.Type.brocade, "11.2.1");
        exceptionHelper(DiscoveredDataObject.Type.brocade, "11.2.0");
        exceptionHelper(DiscoveredDataObject.Type.brocade, "8.5");
    }

    @Test
    public void testCisco() throws Exception {
        noExceptionHelper(DiscoveredDataObject.Type.mds, "5.0(4b)");
        noExceptionHelper(DiscoveredDataObject.Type.mds, "5.0(1a)");
        noExceptionHelper(DiscoveredDataObject.Type.mds, "5.0(7)");
        exceptionHelper(DiscoveredDataObject.Type.mds, "5.0");
        exceptionHelper(DiscoveredDataObject.Type.mds, "4.9(5b)");
    }

    @Test
    public void testRp() throws Exception {
        noExceptionHelper(DiscoveredDataObject.Type.rp, "4.1");
        noExceptionHelper(DiscoveredDataObject.Type.rp, "4.2");
        noExceptionHelper(DiscoveredDataObject.Type.rp, "4.1.1");
        noExceptionHelper(DiscoveredDataObject.Type.rp, "5.0");
        exceptionHelper(DiscoveredDataObject.Type.rp, "3.6");
        exceptionHelper(DiscoveredDataObject.Type.rp, "3.7.P2(n.128)");
        exceptionHelper(DiscoveredDataObject.Type.rp, "3.5.P1");
        exceptionHelper(DiscoveredDataObject.Type.rp, "3.4.SP2(p.74)");
        exceptionHelper(DiscoveredDataObject.Type.rp, "3.0");
        exceptionHelper(DiscoveredDataObject.Type.rp, "4.0");
        exceptionHelper(DiscoveredDataObject.Type.rp, "4.0.1");
    }

    @Test
    public void testVmax() throws Exception {
        noExceptionHelper(DiscoveredDataObject.Type.vmax, "5876.222");
        noExceptionHelper(DiscoveredDataObject.Type.vmax, "5876.231");
        exceptionHelper(DiscoveredDataObject.Type.vmax, "5876.211");
        exceptionHelper(DiscoveredDataObject.Type.vmax, "5876.22");
    }

    @Test
    public void testVnxBlock() throws Exception {
        noExceptionHelper(DiscoveredDataObject.Type.vnxblock, "05.33");
        noExceptionHelper(DiscoveredDataObject.Type.vnxblock, "05.42");
        exceptionHelper(DiscoveredDataObject.Type.vnxblock, "05.31");
        exceptionHelper(DiscoveredDataObject.Type.vnxblock, "5.22");
    }

    @Test
    public void testVnxFile() throws Exception {
        noExceptionHelper(DiscoveredDataObject.Type.vnxfile, "7.1.72");
        noExceptionHelper(DiscoveredDataObject.Type.vnxfile, "7.2.71");
        exceptionHelper(DiscoveredDataObject.Type.vnxfile, "7.1.70");
        exceptionHelper(DiscoveredDataObject.Type.vnxfile, "7.0.71");
    }

    @Test
    public void testIsilon() throws Exception {
        noExceptionHelper(DiscoveredDataObject.Type.isilon, "7.0.2.1");
        noExceptionHelper(DiscoveredDataObject.Type.isilon, "7.0.3.0");
        exceptionHelper(DiscoveredDataObject.Type.isilon, "7.0.1.0");
        exceptionHelper(DiscoveredDataObject.Type.isilon, "7.0.1.9");
    }

    @Test
    public void testNetApp() throws Exception {
        noExceptionHelper(DiscoveredDataObject.Type.netapp, "8.2.1");
        noExceptionHelper(DiscoveredDataObject.Type.netapp, "8.1.2");
        exceptionHelper(DiscoveredDataObject.Type.netapp, "8.1");
        exceptionHelper(DiscoveredDataObject.Type.netapp, "8.0.1");
    }

    @Test
    public void testVplex() throws Exception {
        noExceptionHelper(DiscoveredDataObject.Type.vplex, "5.13");
        noExceptionHelper(DiscoveredDataObject.Type.vplex, "5.2");
        noExceptionHelper(DiscoveredDataObject.Type.vplex, "5.5");
        exceptionHelper(DiscoveredDataObject.Type.vplex, "5.0");
        exceptionHelper(DiscoveredDataObject.Type.vplex, "5.1");
    }

    @Test
    public void testSMISProvider() throws Exception {
        noExceptionHelper("controller_smis_provider_version", "4.6.1.12");
        noExceptionHelper("controller_smis_provider_version", "4.6.2.0");
        exceptionHelper("controller_smis_provider_version", "4.6.1.0");
        exceptionHelper("controller_smis_provider_version", "4.6.0.2");
    }

    @Test
    public void testWindowsHost() throws Exception {
        noExceptionHelper("compute_windows_version", "6.0.6002");
        noExceptionHelper("compute_windows_version", "6.0.7002");
        exceptionHelper("compute_windows_version", "6.0.6001");
        exceptionHelper("compute_windows_version", "6.0.7");
    }

    @Test
    public void testSuseLinuxHost() throws Exception {
        noExceptionHelper("compute_suse_linux_version", "11");
        noExceptionHelper("compute_suse_linux_version", "12");
        exceptionHelper("compute_suse_linux_version", "10");
        exceptionHelper("compute_suse_linux_version", "10.9");
    }

    @Test
    public void testRedhatLinuxHost() throws Exception {
        noExceptionHelper("compute_redhat_linux_version", "5.9");
        noExceptionHelper("compute_redhat_linux_version", "6");
        exceptionHelper("compute_redhat_linux_version", "5");
        exceptionHelper("compute_redhat_linux_version", "4.9");
    }

    @Test
    public void testHpuxHost() throws Exception {
        noExceptionHelper("compute_hpux_version", "11.31");
        exceptionHelper("compute_hpux_version", "11.30");
        exceptionHelper("compute_hpux_version", "10");
        exceptionHelper("compute_hpux_version", "10.31");
    }
}
