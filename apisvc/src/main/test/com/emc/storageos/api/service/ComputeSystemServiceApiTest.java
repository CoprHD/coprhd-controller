/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.beanutils.BeanUtils;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.compute.ComputeElementListRestRep;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.compute.ComputeSystemCreate;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.storageos.model.tenant.TenantResponse;
import com.emc.storageos.services.util.EnvConfig;

public class ComputeSystemServiceApiTest extends ApiTestBase {
    private String _server = "localhost";
    private String _apiServer = "https://" + _server + ":4443";

    private static final String COMPUTE_SYSTEM_RESOURCE = "/vdc/compute-systems";

    private static final String DEREGISTER_RELATIVE_PATH = "/deregister";
    private static final String REGISTER_RELATIVE_PATH = "/register";
    private static final String DEACTIVATE_RELATIVE_PATH = "/deactivate";
    private static final String COMPUTE_ELEMENTS_RELATIVE_PATH = "/compute-elements";
    private static final String COMPUTE_ELEMENT_RESOURCE = "/vdc/compute-elements";

    ComputeSystemRestRep computeSystem = null;
    ComputeSystemRestRep invalidAddressComputeSystem = null;
    ComputeSystemRestRep invalidCredsComputeSystem = null;

    private URI _rootTenantId;
    private String _rootToken;

    @BeforeClass(alwaysRun = true)
    public void setup() throws Exception {

        List<String> urls = new ArrayList<String>();
        urls.add(_apiServer);
        rSys = createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, urls);

        TenantResponse tenantResp = rSys.path("/tenant").get(TenantResponse.class);
        _rootTenantId = tenantResp.getTenant();
        _rootToken = (String) _savedTokens.get("root");
    }

    @Test(groups = "runByDefault", timeOut = 100000)
    public void testCreateComputeSystem() throws Exception {
        TaskResourceRep taskCreateComputeSystem = createAndDiscoverComputeSystem(
                EnvConfig.get("sanity", "ucsm.host"),
                EnvConfig.get("sanity", "ucsm.host.username"),
                EnvConfig.get("sanity", "ucsm.host.password"),
                "api-test-compute-system",80);

        Assert.assertNotNull(taskCreateComputeSystem, "Compute System Task should not be null");
        Assert.assertNotNull(taskCreateComputeSystem.getOpId(), "Compute System Task Id should not be null");
        Assert.assertNotNull(taskCreateComputeSystem.getResource(), "Task related resource should not be null");

        computeSystem = rSys.path(COMPUTE_SYSTEM_RESOURCE + "/" + taskCreateComputeSystem.getResource().getId()).get(
                ComputeSystemRestRep.class);

        Assert.assertNotNull(computeSystem, "Created Compute System should not be null!");
        System.out.println("Created Compute System has id: " + computeSystem.getId());

        // Wait long enough for the Compute System to get discovered...
        while (computeSystem.getDiscoveryJobStatus().equals(
                DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS.name())) {
            computeSystem = rSys.path(COMPUTE_SYSTEM_RESOURCE + "/" + taskCreateComputeSystem.getResource().getId())
                    .get(ComputeSystemRestRep.class);
            Thread.sleep(1000);
        }
        // Refresh the compute system!
        computeSystem = rSys.path(COMPUTE_SYSTEM_RESOURCE + "/" + taskCreateComputeSystem.getResource().getId()).get(
                ComputeSystemRestRep.class);

        Assert.assertEquals(computeSystem.getDiscoveryJobStatus(),
                DiscoveredDataObject.DataCollectionJobStatus.COMPLETE.name());

    }

    @Test(groups = "runByDefault", dependsOnMethods = "testCreateComputeSystem")
    public void testComputeElements() throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        if (computeSystem == null) {
            Assert.fail("Unable to run the test as there's no Compute System to run the test against");
        }

        ComputeElementListRestRep computeElementList = rSys.path(
                COMPUTE_SYSTEM_RESOURCE + "/" + computeSystem.getId() + COMPUTE_ELEMENTS_RELATIVE_PATH).get(
                ComputeElementListRestRep.class);

        ComputeElementRestRep computeElementToDeAndReRegister = null;

        for (ComputeElementRestRep computeElement : computeElementList.getList()) {

            if (RegistrationStatus.REGISTERED.name().equals(computeElement.getRegistrationStatus())) {
                computeElementToDeAndReRegister = computeElement;
            }
            System.out.println(BeanUtils.describe(computeElement));
        }

        if (computeElementToDeAndReRegister != null) {
            System.out
                    .println("De-registering compute element: " + BeanUtils.describe(computeElementToDeAndReRegister));
            computeElementToDeAndReRegister = rSys
                    .path(COMPUTE_ELEMENT_RESOURCE + "/" + computeElementToDeAndReRegister.getId()
                            + DEREGISTER_RELATIVE_PATH).post(ComputeElementRestRep.class);

            Assert.assertEquals(computeElementToDeAndReRegister.getRegistrationStatus(),
                    RegistrationStatus.UNREGISTERED.name());

            System.out.println("De-registered compute element: " + BeanUtils.describe(computeElementToDeAndReRegister));

            System.out
                    .println("Re-registering compute element: " + BeanUtils.describe(computeElementToDeAndReRegister));
            computeElementToDeAndReRegister = rSys.path(
                    COMPUTE_ELEMENT_RESOURCE + "/" + computeElementToDeAndReRegister.getId() + REGISTER_RELATIVE_PATH)
                    .post(ComputeElementRestRep.class);
            Assert.assertEquals(computeElementToDeAndReRegister.getRegistrationStatus(),
                    RegistrationStatus.REGISTERED.name());
            System.out.println("Re-registered compute element: " + BeanUtils.describe(computeElementToDeAndReRegister));

        }

    }

    @Test(groups = "runByDefault", dependsOnMethods = "testComputeElements")
    public void testDeReDeregisterComputeSystem() throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
        Assert.assertTrue(deReDeregisterComputeSystem(computeSystem));
    }

    @Test(groups = "runByDefault", dependsOnMethods = "testDeReDeregisterComputeSystem")
    public void testDeactivateComputeSystem() throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, InterruptedException {
        Assert.assertTrue(deactivateComputeSystem(computeSystem));
    }

    @Test(groups = "runByDefault", timeOut = 100000)
    public void testInvalidCredsCreateComputeSystem() throws Exception {
        TaskResourceRep taskCreateComputeSystem = createAndDiscoverComputeSystem(
                EnvConfig.get("sanity", "ucsm.invalidhost"),
                EnvConfig.get("sanity", "ucsm.invalidhost.username"),
                EnvConfig.get("sanity", "ucsm.invalidhost.password"),
                "bad-creds-api-test-compute-system", 80);

        Assert.assertNotNull(taskCreateComputeSystem, "Compute System Task should not be null");
        Assert.assertNotNull(taskCreateComputeSystem.getOpId(), "Compute System Task Id should not be null");
        Assert.assertNotNull(taskCreateComputeSystem.getResource(), "Task related resource should not be null");

        invalidCredsComputeSystem = rSys.path(
                COMPUTE_SYSTEM_RESOURCE + "/" + taskCreateComputeSystem.getResource().getId()).get(
                ComputeSystemRestRep.class);

        Assert.assertNotNull(invalidCredsComputeSystem, "Created Compute System should not be null!");
        System.out.println("Created Compute System has id: " + invalidCredsComputeSystem.getId());

        // Wait long enough for the Compute System to get discovered...
        while (invalidCredsComputeSystem.getDiscoveryJobStatus().equals(
                DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS.name())) {
            invalidCredsComputeSystem = rSys.path(
                    COMPUTE_SYSTEM_RESOURCE + "/" + taskCreateComputeSystem.getResource().getId()).get(
                    ComputeSystemRestRep.class);
            Thread.sleep(1000);
        }
        // Refresh the compute system!
        invalidCredsComputeSystem = rSys.path(
                COMPUTE_SYSTEM_RESOURCE + "/" + taskCreateComputeSystem.getResource().getId()).get(
                ComputeSystemRestRep.class);

        Assert.assertEquals(invalidCredsComputeSystem.getDiscoveryJobStatus(),
                DiscoveredDataObject.DataCollectionJobStatus.ERROR.name());

    }

    @Test(groups = "runByDefault", dependsOnMethods = "testInvalidCredsCreateComputeSystem")
    public void testInvalidCredsDeReDeregisterComputeSystem() throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
        Assert.assertTrue(deReDeregisterComputeSystem(invalidCredsComputeSystem));
    }

    @Test(groups = "runByDefault", dependsOnMethods = "testInvalidCredsDeReDeregisterComputeSystem")
    public void testInvalidCredsDeactivateComputeSystem() throws IllegalAccessException, InvocationTargetException,
            InterruptedException, NoSuchMethodException {
        Assert.assertTrue(deactivateComputeSystem(invalidCredsComputeSystem));
    }

    @Test(groups = "runByDefault", timeOut = 100000)
    public void testInvalidAddressCreateComputeSystem() throws Exception {
        TaskResourceRep taskCreateComputeSystem = createAndDiscoverComputeSystem(
                EnvConfig.get("sanity", "ucsm.invalidAddress.host"),
                EnvConfig.get("sanity", "ucsm.invalidAddress.username"),
                EnvConfig.get("sanity", "ucsm.invalidAddress.password"),
                "bad-address-api-test-compute-system", 80);

        Assert.assertNotNull(taskCreateComputeSystem, "Compute System Task should not be null");
        Assert.assertNotNull(taskCreateComputeSystem.getOpId(), "Compute System Task Id should not be null");
        Assert.assertNotNull(taskCreateComputeSystem.getResource(), "Task related resource should not be null");

        invalidAddressComputeSystem = rSys.path(
                COMPUTE_SYSTEM_RESOURCE + "/" + taskCreateComputeSystem.getResource().getId()).get(
                ComputeSystemRestRep.class);

        Assert.assertNotNull(invalidAddressComputeSystem, "Created Compute System should not be null!");
        System.out.println("Created Compute System has id: " + invalidAddressComputeSystem.getId());

        // Wait long enough for the Compute System to get discovered...
        while (invalidAddressComputeSystem.getDiscoveryJobStatus().equals(
                DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS.name())) {
            invalidAddressComputeSystem = rSys.path(
                    COMPUTE_SYSTEM_RESOURCE + "/" + taskCreateComputeSystem.getResource().getId()).get(
                    ComputeSystemRestRep.class);
            Thread.sleep(1000);
        }
        // Refresh the compute system!
        invalidAddressComputeSystem = rSys.path(
                COMPUTE_SYSTEM_RESOURCE + "/" + taskCreateComputeSystem.getResource().getId()).get(
                ComputeSystemRestRep.class);

        Assert.assertEquals(invalidAddressComputeSystem.getDiscoveryJobStatus(),
                DiscoveredDataObject.DataCollectionJobStatus.ERROR.name());

    }

    @Test(groups = "runByDefault", dependsOnMethods = "testInvalidAddressCreateComputeSystem")
    public void testInvalidAddressDeReDeregisterComputeSystem() throws IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {
        Assert.assertTrue(deReDeregisterComputeSystem(invalidAddressComputeSystem));
    }

    @Test(groups = "runByDefault", dependsOnMethods = "testInvalidAddressDeReDeregisterComputeSystem")
    public void testInvalidAddressDeactivateComputeSystem() throws IllegalAccessException, InvocationTargetException,
            InterruptedException, NoSuchMethodException {
        Assert.assertTrue(deactivateComputeSystem(invalidAddressComputeSystem));
    }

    @AfterSuite
    public void teardown() throws Exception {

    }

    private TaskResourceRep createAndDiscoverComputeSystem(String ip, String username, String password, String name,
            int portname) throws Exception {

        TaskResourceRep taskCreateComputeSystem = null;
        ComputeSystemCreate computeSystemCreate = new ComputeSystemCreate();
        computeSystemCreate.setIpAddress(ip);
        computeSystemCreate.setName(name);
        computeSystemCreate.setPassword(password);
        computeSystemCreate.setUserName(username);
        computeSystemCreate.setPortNumber(portname);
        computeSystemCreate.setSystemType(ComputeSystem.Type.ucs.name());

        return taskCreateComputeSystem = rSys.path(COMPUTE_SYSTEM_RESOURCE).post(TaskResourceRep.class,
                computeSystemCreate);
    }

    private Boolean deReDeregisterComputeSystem(ComputeSystemRestRep cs) throws IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {

        if (cs == null) {
            System.out.println("Unable to run the test as there's no Compute System to run the test against");
            return false;
        }

        System.out.println("De-registering compute system: " + BeanUtils.describe(cs));
        cs = rSys.path(COMPUTE_SYSTEM_RESOURCE + "/" + cs.getId() + DEREGISTER_RELATIVE_PATH).post(cs.getClass());

        Assert.assertEquals(cs.getRegistrationStatus(), RegistrationStatus.UNREGISTERED.name());
        System.out.println("De-registered compute system: " + BeanUtils.describe(cs));

        System.out.println("Re-registering compute system: " + BeanUtils.describe(cs));
        cs = rSys.path(COMPUTE_SYSTEM_RESOURCE + "/" + cs.getId() + REGISTER_RELATIVE_PATH).post(cs.getClass());

        if (cs.getRegistrationStatus().equals(RegistrationStatus.REGISTERED.name())) {
            System.out.println("Re-registered compute element: " + BeanUtils.describe(cs));
        } else {
            return false;
        }
        System.out.println("De-registering compute element for eventual deactivation: " + BeanUtils.describe(cs));
        cs = rSys.path(COMPUTE_SYSTEM_RESOURCE + "/" + cs.getId() + DEREGISTER_RELATIVE_PATH).post(cs.getClass());

        if (cs.getRegistrationStatus().equals(RegistrationStatus.UNREGISTERED.name())) {
            System.out.println("De-registered compute element: " + BeanUtils.describe(cs));
        } else {
            return false;
        }
        return true;
    }

    private Boolean deactivateComputeSystem(ComputeSystemRestRep cs) throws IllegalAccessException,
            InvocationTargetException, NoSuchMethodException, InterruptedException {

        if (cs == null) {
            System.out.println("Unable to run the test as there's no Compute System to run the test against");
            return false;
        }

        System.out.println("De-activating compute element: " + BeanUtils.describe(cs));
        TaskResourceRep deactivateTask = rSys.path(
                COMPUTE_SYSTEM_RESOURCE + "/" + cs.getId() + DEACTIVATE_RELATIVE_PATH).post(TaskResourceRep.class);

        System.out.println("Waiting for compute element deactivation: ");

        Thread.sleep(60000 / 8);

        cs = rSys.path(COMPUTE_SYSTEM_RESOURCE + "/" + cs.getId()).get(ComputeSystemRestRep.class);

        if (cs.getInactive().booleanValue() == false) {
            return false;
        }

        System.out.println("Compute System should be deleted from the system! Safe to run the test over...");
        return true;
    }
}
