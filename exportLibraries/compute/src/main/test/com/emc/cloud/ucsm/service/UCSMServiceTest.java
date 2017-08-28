/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBElement;

import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.emc.cloud.platform.clientlib.ClientGeneralException;
import com.emc.cloud.platform.ucs.out.model.ComputeBlade;
import com.emc.cloud.platform.ucs.out.model.FabricFcSanEp;
import com.emc.cloud.platform.ucs.out.model.FabricVlan;
import com.emc.cloud.platform.ucs.out.model.FcPIo;
import com.emc.cloud.platform.ucs.out.model.LsPower;
import com.emc.cloud.platform.ucs.out.model.LsServer;
import com.emc.cloud.platform.ucs.out.model.SwFcSanEp;
import com.emc.cloud.platform.ucs.out.model.SwFcSanPc;
import com.emc.cloud.platform.ucs.out.model.SwVsan;
import com.emc.storageos.services.util.EnvConfig;
import com.google.common.collect.ImmutableMap;

@ContextConfiguration(locations = { "classpath:applicationContext.xml" })
public class UCSMServiceTest extends AbstractTestNGSpringContextTests {

    private static final String UCSM_SERVICE_PREFIX = "http://";
    private static final String UCSM_SERVICE_PREFIX_SSL = "http://";
    private static final String UCSM_SERVICE_POSTFIX = "/nuova";
    private static final String UCSM_SERVICE_POSTFIX_SSL = ":443/nuova";

    private static final String UNIT_TEST_CONFIG_FILE = "sanity";

    private static String UCSM_HOST = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.host");
    private static String UCSM_HOST_USERNAME = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.host.username");
    private static String UCSM_HOST_PASSWORD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.host.password");

    private static String UCSM_SERVICE = UCSM_SERVICE_PREFIX + UCSM_HOST + UCSM_SERVICE_POSTFIX;
    private static String UCSM_SERVICE_SSL = UCSM_SERVICE_PREFIX_SSL + UCSM_HOST + UCSM_SERVICE_POSTFIX_SSL;

    private static String UCSM_HOST2 = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.host2");
    private static String UCSM_HOST2_USERNAME = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.host2.username");
    private static String UCSM_HOST2_PASSWORD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.host2.password");

    private static String UCSM_SERVICE2 = UCSM_SERVICE_PREFIX + UCSM_HOST2 + UCSM_SERVICE_POSTFIX;

    private static String UCSM_HOST3 = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.host3");
    private static String UCSM_HOST3_USERNAME = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.host3.username");
    private static String UCSM_HOST3_PASSWORD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.host3.password");

    private static String UCSM_SERVICE3 = UCSM_SERVICE_PREFIX + UCSM_HOST3 + UCSM_SERVICE_POSTFIX;

    private static String UCSM_HOST4 = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.host4");
    private static String UCSM_HOST4_USERNAME = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.host4.username");
    private static String UCSM_HOST4_PASSWORD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.host4.password");

    private static String UCSM_SERVICE4 = UCSM_SERVICE_PREFIX + UCSM_HOST4 + UCSM_SERVICE_POSTFIX;

    private static final String SP_DN = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "ucsm.spdn.ls");
    private static final String SP_DN2 = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "org-root/ls-janardhan-test");
    private static final String SP_DN3 = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "org-root/ls-test-janardhan1");
    private static final String SP_DN4 = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "sys/chassis-1/blade-6");
    private static final String SP_DN5 = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "sys/chassis-4/blade-1");

    @Autowired
    UCSMService ucsmService;

    @Test(groups = "runByDefault")
    public void testGetBlades() throws ClientGeneralException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {

        List<ComputeBlade> blades = ucsmService.getComputeBlades(UCSM_SERVICE, UCSM_HOST_USERNAME,
                UCSM_HOST_PASSWORD);

        if (blades != null && !blades.isEmpty()) {
            for (ComputeBlade blade : blades) {
                System.out.println(BeanUtils.describe(blade));
            }
        }
        System.out.println("Number of blades found: " + blades.size());

    }

    @Test(groups = "runByDefault", dependsOnMethods = "testGetBlades")
    public void testGetAssociatedLsServers() throws ClientGeneralException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {

        Map<String, LsServer> associatedLsServers = ucsmService.getAllAssociatedLsServers(UCSM_SERVICE,
                UCSM_HOST_USERNAME, UCSM_HOST_PASSWORD);

        if (associatedLsServers != null && !associatedLsServers.isEmpty()) {
            for (LsServer lsServer : associatedLsServers.values()) {
                System.out.println(BeanUtils.describe(lsServer));
            }
            System.out.println("Number of associated lsServers found: " + associatedLsServers.size());
        }

    }

    @Test(groups = "runByDefault", dependsOnMethods = "testGetAssociatedLsServers")
    public void testServiceProfileTemplateLsServers() throws ClientGeneralException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {

        List<LsServer> sptLsServers = ucsmService.getServiceProfileTemplates(UCSM_SERVICE,
                UCSM_HOST_USERNAME, UCSM_HOST_PASSWORD);

        if (sptLsServers != null && !sptLsServers.isEmpty()) {
            for (LsServer lsServer : sptLsServers) {
                System.out.println(BeanUtils.describe(lsServer));
            }
            System.out.println("Number of ServiceProfileTemplates found: " + sptLsServers.size());
        }

    }

    @Test(groups = "onDemand1")
    public void testSetLsServerPowerState() throws ClientGeneralException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {

        Map<String, LsServer> associatedLsServers = ucsmService.getAllAssociatedLsServers(UCSM_SERVICE,
                UCSM_HOST_USERNAME, UCSM_HOST_PASSWORD);

        String powerStateToSet = null;

        if (associatedLsServers != null && !associatedLsServers.isEmpty()) {
            for (LsServer lsServer : associatedLsServers.values()) {
                if (SP_DN.equals(lsServer.getDn())) {
                    if (lsServer.getContent() != null && !lsServer.getContent().isEmpty()) {
                        for (Object object : lsServer.getContent()) {
                            if (object instanceof JAXBElement<?>) {
                                if (((JAXBElement) object).getValue() instanceof LsPower) {

                                    LsPower lsPower = ((JAXBElement<LsPower>) object).getValue();
                                    if ("up".equals(lsPower.getState())) {
                                        powerStateToSet = "down";
                                    } else if ("down".equals(lsPower.getState())) {
                                        powerStateToSet = "up";
                                    }
                                    LsServer lsServerOut = ucsmService.setLsServerPowerState(
                                            UCSM_SERVICE,
                                            UCSM_HOST_USERNAME, UCSM_HOST_PASSWORD, lsServer.getDn(),
                                            powerStateToSet, new StringBuilder());

                                    if (lsServerOut.getContent() != null && !lsServerOut.getContent().isEmpty()) {
                                        for (Object object2 : lsServerOut.getContent()) {
                                            if (object2 instanceof JAXBElement<?>) {
                                                if (((JAXBElement) object2).getValue() instanceof LsPower) {
                                                    LsPower lsPower2 = ((JAXBElement<LsPower>) object2).getValue();
                                                    Assert.assertTrue(powerStateToSet.equals(lsPower2.getState()));
                                                }

                                            }
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Number of associated lsServers found: " + associatedLsServers.size());

    }

    LsServer createdServiceProfile = null;

    @Test(groups = "onDemand")
    public void testCreateServiceProfileFromTemplate() throws ClientGeneralException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {

        createdServiceProfile = ucsmService.createServiceProfileFromTemplate(UCSM_SERVICE2,
                UCSM_HOST2_USERNAME, UCSM_HOST2_PASSWORD, SP_DN2, "test-sp-janardhan", new StringBuffer());

        Assert.assertNotNull(createdServiceProfile, "Created Service Profile should not be null...");
        Assert.assertNotNull(createdServiceProfile, "DN of Created Service Profile Should not be null...");
    }

    @Test(groups = "onDemand", dependsOnMethods = "testCreateServiceProfileFromTemplate")
    public void testBindServiceProfileToCE() throws ClientGeneralException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {

        createdServiceProfile = ucsmService.bindSPToComputeElement(UCSM_SERVICE2, UCSM_HOST2_USERNAME,
                UCSM_HOST2_PASSWORD, createdServiceProfile.getDn(), SP_DN4, new StringBuilder());

        Assert.assertNotNull(createdServiceProfile, "Created Service Profile should not be null...");
        Assert.assertNotNull(createdServiceProfile.getDn(), "DN of Created Service Profile Should not be null...");
        Assert.assertNotNull(createdServiceProfile.getUuid(),
                "UUID of the associated Service Profile should not be null!");
    }

    LsServer pulledServiceProfile = null;

    @Test(groups = "onDemand", dependsOnMethods = "testBindServiceProfileToCE")
    public void testPullServiceProfile() throws ClientGeneralException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException, InterruptedException {

        Assert.assertNotNull(createdServiceProfile, "No Service Profile was created/returned... Failing Test");

        pulledServiceProfile = createdServiceProfile;

        System.out.println("Initial Binding State: " + pulledServiceProfile.getOperState());

        do {
            Thread.sleep(30000);
            pulledServiceProfile = ucsmService.getManagedObject(UCSM_SERVICE2, UCSM_HOST2_USERNAME,
                    UCSM_HOST2_PASSWORD, pulledServiceProfile.getDn(), false, LsServer.class);
            System.out.println("Intermediate Binding State: " + pulledServiceProfile.getOperState());
        } while (!LsServerOperStates.isTerminal(pulledServiceProfile.getOperState()));

        pulledServiceProfile = ucsmService.getManagedObject(UCSM_SERVICE2, UCSM_HOST2_USERNAME, UCSM_HOST2_PASSWORD,
                pulledServiceProfile.getDn(), true, LsServer.class);

        System.out.println("Final Binding State: " + pulledServiceProfile.getOperState());

        Assert.assertEquals(LsServerOperStates.fromString(pulledServiceProfile.getOperState()), LsServerOperStates.OK);
        Assert.assertEquals(pulledServiceProfile.getAssocState(), "associated");
    }

    @Test(groups = "onDemand")
    public void testGetVlanById() throws ClientGeneralException {
        String vlanId = "631";
        FabricVlan fabricVlan = ucsmService
                .getVlanById(UCSM_SERVICE2, UCSM_HOST2_USERNAME, UCSM_HOST2_PASSWORD, vlanId);
        Assert.assertNotNull(fabricVlan, "No VLAN with ID : " + vlanId + " exists in the Compute System");
        System.out.println("Found VLAN with Name : " + fabricVlan.getName());

    }

    Map<String, Boolean> vlanMap = null;

    @Test(groups = "onDemand2")
    public void testSetOsInstallVlan() throws ClientGeneralException {

        vlanMap = ucsmService.setOsInstallVlan(UCSM_SERVICE2, UCSM_HOST2_USERNAME, UCSM_HOST2_PASSWORD, SP_DN,
                "631");

        System.out.println("Found vLANs : " + vlanMap);

    }

    @Test(groups = "onDemand2", dependsOnMethods = "testSetOsInstallVlan")
    public void testRemoveOsInstallVlan() throws ClientGeneralException {

        ucsmService.removeOsInstallVlan(UCSM_SERVICE2, UCSM_HOST2_USERNAME, UCSM_HOST2_PASSWORD, SP_DN, "631",
                vlanMap);

        Assert.assertNotNull(vlanMap, "No VLANs were associated " + SP_DN);
        System.out.println("Reset the vLANs : " + vlanMap);

    }

    @Test(groups = "onDemand50", dependsOnMethods = "testSetNoBoot")
    public void testSetLanBoot() throws ClientGeneralException {

        LsServer lsServer = ucsmService.setServiceProfileToLanBoot(UCSM_SERVICE3, UCSM_HOST3_USERNAME, UCSM_HOST3_PASSWORD, SP_DN, new StringBuilder());

        Assert.assertNotNull(lsServer, "Couldn't update the LsServer's boot order " + SP_DN);
        System.out.println("Current State of the lsServer : " + lsServer.getOperState());

    }

    @Test(groups = "onDemand50")
    public void testSetNoBoot() throws ClientGeneralException {

        LsServer lsServer = ucsmService.setServiceProfileToNoBoot(UCSM_SERVICE3, UCSM_HOST3_USERNAME, UCSM_HOST3_PASSWORD, SP_DN, new StringBuilder());

        Assert.assertNotNull(lsServer, "Couldn't update the LsServer's boot order " + SP_DN);
        System.out.println("Current State of the lsServer : " + lsServer.getOperState());

    }

    @Test(groups = "onDemand50", dependsOnMethods = "testSetLanBoot")
    public void testSetSanBootTarget() throws ClientGeneralException {

        String aSide = "fc0";
        String bside = "fc1";

        Map<String, Map<String, Integer>> hbaToStoragePortMap = new HashMap<String, Map<String, Integer>>();

        hbaToStoragePortMap.put(
                bside,
                ImmutableMap.of("50:00:09:73:00:12:9D:60", 1, "50:00:09:73:00:12:9D:1C", 1,
                        "50:00:09:73:00:12:9D:7F", 1));
        hbaToStoragePortMap.put(
                aSide,
                ImmutableMap.of("50:00:09:73:00:12:9D:60", 0, "50:00:09:73:00:12:9D:1C", 0,
                        "50:00:09:73:00:12:9D:7F", 0));

        LsServer lsServer = ucsmService.setServiceProfileToSanBoot(UCSM_SERVICE3, UCSM_HOST3_USERNAME, UCSM_HOST3_PASSWORD, SP_DN,
                hbaToStoragePortMap, new StringBuilder());
        Assert.assertNotNull(lsServer, "Couldn't update the LsServer's boot order " + SP_DN);
        System.out.println("Current State of the lsServer : " + lsServer.getOperState());

    }

    @Test(groups = "runByDefault")
    public void testDeviceVersion() throws ClientGeneralException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
        String version = ucsmService.getDeviceVersion(UCSM_SERVICE, UCSM_HOST_USERNAME, UCSM_HOST_PASSWORD);
        System.out.println("Version found is :" + version);
        Assert.assertNotNull(version);
    }

    @Test(groups = "onDemand5")
    public void testGetComputeBlade() throws ClientGeneralException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
        ComputeBlade computeBlade = ucsmService.getManagedObject(UCSM_SERVICE, UCSM_HOST_USERNAME,
                UCSM_HOST_PASSWORD, SP_DN5, false, ComputeBlade.class);
        Assert.assertNotNull(computeBlade);
        System.out.println("Blade Found :" + computeBlade.getDn());
        System.out.println("Blade :" + BeanUtils.describe(computeBlade));
        System.out.println("Blade Oper State:" + computeBlade.getOperState());

    }

    @Test(groups = "onDemand6")
    public void testDeleteServiceProfile() throws ClientGeneralException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {
        ucsmService.deleteServiceProfile(UCSM_SERVICE, UCSM_HOST_USERNAME, UCSM_HOST_PASSWORD,
                SP_DN3, new StringBuilder());

        LsServer deletedServiceProfile = ucsmService.getManagedObject(UCSM_SERVICE, UCSM_HOST_USERNAME,
                UCSM_HOST_PASSWORD, SP_DN3, true, LsServer.class);

        Assert.assertNull(deletedServiceProfile);

        System.out.println("Service Profile : " + SP_DN3 + " : DELETED");

    }

    @Test(groups = "runByDefault")
    public void testGetFICUplinkPorts() throws ClientGeneralException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {
        Map<String, FcPIo> ficUplinkPorts = ucsmService.getFICUplinkPorts(UCSM_SERVICE,
                UCSM_HOST_USERNAME, UCSM_HOST_PASSWORD);
        Assert.assertNotNull(ficUplinkPorts);
        Assert.assertFalse(ficUplinkPorts.isEmpty());
    }

    @Test(groups = "runByDefault")
    public void testGetUplinkFCInterfaces() throws ClientGeneralException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {
        Map<String, FabricFcSanEp> uplinkFCInterfaces = ucsmService.getUplinkFCInterfaces(UCSM_SERVICE,
                UCSM_HOST_USERNAME, UCSM_HOST_PASSWORD);
        Assert.assertNotNull(uplinkFCInterfaces);
        Assert.assertFalse(uplinkFCInterfaces.isEmpty());
    }

    @Test(groups = "runByDefault")
    public void testGetFCBorders() throws ClientGeneralException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
        Map<String, SwFcSanEp> fcSanMap = ucsmService.getSwitchFCInterfaces(UCSM_SERVICE,
                UCSM_HOST_USERNAME, UCSM_HOST_PASSWORD);
        Assert.assertNotNull(fcSanMap);
        Assert.assertFalse(fcSanMap.isEmpty());
    }

    @Test(groups = "runByDefault")
    public void testGetUcsVsans() throws ClientGeneralException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
        List<SwVsan> swVsanList = ucsmService.getUcsSwitchVSans(UCSM_SERVICE, UCSM_HOST_USERNAME,
                UCSM_HOST_PASSWORD);
        Assert.assertNotNull(swVsanList);
        Assert.assertFalse(swVsanList.isEmpty());
    }

    @Test(groups = "runByDefault")
    public void testGetUplinkPortChannels() throws ClientGeneralException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {
        Map<String, SwFcSanPc> uplinkPortChannels = ucsmService.getUplinkPortChannels(UCSM_SERVICE4,
                UCSM_HOST4_USERNAME, UCSM_HOST4_PASSWORD);
        Assert.assertNotNull(uplinkPortChannels);
        Assert.assertFalse(uplinkPortChannels.isEmpty());
    }

    @Test(groups = "runByDefault")
    public void testGetBladesSSL() throws ClientGeneralException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {

        List<ComputeBlade> blades = ucsmService.getComputeBlades(UCSM_SERVICE_SSL, UCSM_HOST_USERNAME,
                UCSM_HOST_PASSWORD);

        if (blades != null && !blades.isEmpty()) {
            for (ComputeBlade blade : blades) {
                System.out.println(BeanUtils.describe(blade));
            }
        }
        System.out.println("Number of blades found: " + blades.size());

    }

}
