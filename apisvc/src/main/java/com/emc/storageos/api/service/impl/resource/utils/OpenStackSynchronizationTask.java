/*
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.api.service.impl.resource.*;
import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.keystone.restapi.model.response.KeystoneTenant;
import com.emc.storageos.keystone.restapi.utils.KeystoneUtils;
import com.emc.storageos.model.project.ProjectElement;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.security.authentication.InternalTenantServiceClient;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class OpenStackSynchronizationTask extends ResourceService {

    private static final Logger _log = LoggerFactory.getLogger(OpenStackSynchronizationTask.class);

    // Constants
    // Interval delay between each execution in seconds.
    public static final int DEFAULT_INTERVAL_DELAY = 900;
    // Maximum time in seconds for a timeout when awaiting for termination.
    private static final int MAX_TERMINATION_TIME = 120;
    // Minimum interval in seconds.
    public static final int MIN_INTERVAL_DELAY = 10;
    // The number of threads to keep in the pool
    public static final int NUMBER_OF_THREADS = 1;

    private static final String OPENSTACK = "OpenStack";

    // Services
    private KeystoneUtils _keystoneUtilsService;

    private InternalTenantServiceClient _internalTenantServiceClient;

    private ScheduledExecutorService _dataCollectionExecutorService;

    private ScheduledFuture _synchronizationTask;

    public void setInternalTenantServiceClient(InternalTenantServiceClient internalTenantServiceClient) {
        this._internalTenantServiceClient = internalTenantServiceClient;
    }

    public ScheduledFuture getSynchronizationTask() {
        return _synchronizationTask;
    }

    public void setKeystoneUtilsService(KeystoneUtils keystoneUtilsService) {
        this._keystoneUtilsService = keystoneUtilsService;
    }

    public void start(int interval) throws Exception {

        _log.info("Start OpenStack Synchronization Task");
        _dataCollectionExecutorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS);

        // Schedule task at fixed interval.
        _synchronizationTask = _dataCollectionExecutorService.scheduleAtFixedRate(
                new SynchronizationScheduler(),
                interval, interval, TimeUnit.SECONDS);
    }

    public void stop() {

        _log.info("Stop OpenStack Synchronization Task");
        try {
            _dataCollectionExecutorService.shutdown();
            _dataCollectionExecutorService.awaitTermination(MAX_TERMINATION_TIME, TimeUnit.SECONDS);
            _synchronizationTask = null;
        } catch (Exception e) {
            _log.error("TimeOut occurred after waiting Client Threads to finish");
        }
    }

    /**
     * Reschedule synchronization task with given interval. Previous task is canceled and a new one is scheduled.
     *
     * @param newInterval New interval delay between each execution in seconds.
     */
    public void rescheduleTask(int newInterval) {

        if (_synchronizationTask != null && newInterval >= MIN_INTERVAL_DELAY) {
            _synchronizationTask.cancel(false);
            _synchronizationTask = _dataCollectionExecutorService
                    .scheduleAtFixedRate(new SynchronizationScheduler(), newInterval, newInterval, TimeUnit.SECONDS);
            _log.debug("Synchronization task has been rescheduled with {}s interval.", newInterval);
        } else {
            throw APIException.internalServerErrors.rescheduleSynchronizationTaskError();
        }
    }

    /**
     * Retrieves Keystone Authentication Provider from CoprHD database.
     *
     * @return Keystone Authentication Provider.
     */
    public AuthnProvider getKeystoneProvider() {

        return _keystoneUtilsService.getKeystoneProvider();
    }

    /**
     * Retrieves interval value of Sync Task from Keystone Authentication Provider.
     *
     * @param keystoneProvider Keystone Authentication Provider.
     *
     * @return OpenStack Synchronization Task interval.
     */
    public int getTaskInterval(AuthnProvider keystoneProvider) {

        if (keystoneProvider != null && keystoneProvider.getTenantsSynchronizationOptions() != null) {

            String taskInterval = getIntervalFromTenantSyncSet(keystoneProvider.getTenantsSynchronizationOptions());
            if (taskInterval != null) {
                return Integer.parseInt(taskInterval);
            }
        }

        throw APIException.internalServerErrors.targetIsNullOrEmpty("keystone provider or tenantsSynchronizationOptions");
    }

    /**
     * Retrieves interval value from Tenants Synchronization Options in Keystone Authentication Provider.
     *
     * @param tenantsSynchronizationOptions Tenants Synchronization Options.
     *
     * @return interval.
     */
    public String getIntervalFromTenantSyncSet(StringSet tenantsSynchronizationOptions) {

        for (String option : tenantsSynchronizationOptions) {
            // There is only ADDITION, DELETION and interval in this StringSet.
            if (!AuthnProvider.TenantsSynchronizationOptions.ADDITION.toString().equals(option)
                    && !AuthnProvider.TenantsSynchronizationOptions.DELETION.toString().equals(option)) {
                return option;
            }
        }

        return null;
    }

    /**
     * Finds CoprHD Tenants with OpenStack ID that are different from their reflection in OpenStack.
     * Those found Tenants need to be updated.
     *
     * @param osTenantList List of OpenStack Tenants.
     * @param coprhdTenantList List of CoprHD Tenants related to OpenStack.
     * @return List of CoprHD Tenants that needs to be updated.
     */
    private List<TenantOrg> getListOfTenantsToUpdate(List<KeystoneTenant> osTenantList, List<TenantOrg> coprhdTenantList) {

        List<TenantOrg> tenantsToUpdate = null;

        ListIterator<KeystoneTenant> osIter = osTenantList.listIterator();

        while (osIter.hasNext()) {
            ListIterator<TenantOrg> coprhdIter = coprhdTenantList.listIterator();
            KeystoneTenant osTenant = osIter.next();
            // Update information about this Tenant in CoprHD database.
            createOrUpdateOpenstackTenantInCoprhd(osTenant);
            while (coprhdIter.hasNext()) {

                TenantOrg coprhdTenant = coprhdIter.next();
                String tenantMapping = _keystoneUtilsService.getCoprhdTenantUserMapping(coprhdTenant);
                String tenantId = _keystoneUtilsService.getTenantIdFromUserMapping(tenantMapping);
                if (tenantId.equals(osTenant.getId())) {
                    if (!areTenantsIdentical(osTenant, coprhdTenant)) {
                        coprhdTenant.setDescription(osTenant.getDescription());
                        String tenantName = OPENSTACK + " " + osTenant.getName();
                        coprhdTenant.setLabel(tenantName);

                        if (tenantsToUpdate == null) {
                            tenantsToUpdate = new ArrayList<>();
                        }

                        tenantsToUpdate.add(coprhdTenant);
                    }

                    coprhdIter.remove();
                    osIter.remove();
                }
            }
        }

        return tenantsToUpdate;
    }

    /**
     * Updates or creates CoprHD representation of OpenStack Tenant.
     *
     * @param tenant OpenStack Tenant.
     * @return Updated or Created Tenant.
     */
    private OSTenant createOrUpdateOpenstackTenantInCoprhd(KeystoneTenant tenant) {

        OSTenant osTenant = _keystoneUtilsService.findOpenstackTenantInCoprhd(tenant.getId());

        if (osTenant != null) {
            if (!osTenant.getDescription().equals(tenant.getDescription())) {
                osTenant.setDescription(tenant.getDescription());
            }
            if (!osTenant.getName().equals(tenant.getName())) {
                osTenant.setName(tenant.getName());
            }
            if (osTenant.getEnabled() != Boolean.parseBoolean(tenant.getEnabled())) {
                osTenant.setEnabled(Boolean.parseBoolean(tenant.getEnabled()));
            }

            _dbClient.updateObject(osTenant);

        } else {
            osTenant = _keystoneUtilsService.mapToOsTenant(tenant);
            osTenant.setId(URIUtil.createId(OSTenant.class));
            _dbClient.createObject(osTenant);
        }

        return osTenant;
    }

    /**
     * Compares OpenStack Tenant with CoprHD Tenant (both need to have same OpenStack ID).
     *
     * @param osTenant OpenStack Tenant.
     * @param coprhdTenant CoprHD Tenant related to OpenStack.
     * @return True if tenants are identical, false otherwise.
     */
    private boolean areTenantsIdentical(KeystoneTenant osTenant, TenantOrg coprhdTenant) {

        String osTenantName = OPENSTACK + " " + osTenant.getName();
        if (!osTenantName.equals(coprhdTenant.getLabel())) {
            return false;
        }

        if (!osTenant.getDescription().equals(coprhdTenant.getDescription())) {
            return false;
        }

        return true;
    }

    /**
     * Creates a CoprHD Tenant for given OpenStack Tenant.
     * Sends internal POST API call to InternalTenantsService in order to create Tenant.
     *
     * @param tenant OpenStack Tenant.
     *
     * @return URI of newly created Tenant.
     */
    public URI createTenant(KeystoneTenant tenant) {

        TenantOrgRestRep tenantResp = _internalTenantServiceClient.createTenant(_keystoneUtilsService.prepareTenantParam(tenant));

        return tenantResp.getId();
    }

    /**
     * Creates a CoprHD Project for given Tenant.
     * Sends internal POST API call to InternalTenantsService in order to create Project.
     *
     * @param tenantOrgId ID of the Project owner.
     * @param tenant OpenStack Tenant.
     *
     * @return URI of newly created Project.
     */
    public URI createProject(URI tenantOrgId, KeystoneTenant tenant) {

        ProjectParam projectParam = new ProjectParam(tenant.getName() + CinderConstants.PROJECT_NAME_SUFFIX);
        ProjectElement projectResp = _internalTenantServiceClient.createProject(tenantOrgId, projectParam);

        return projectResp.getId();
    }

    /**
     * Starts synchronization between CoprHD and OpenStack Tenants (i.e. starts Synchronization Task).
     *
     * @param interval Task interval.
     */
    public void startSynchronizationTask(int interval) {
        try {
            if (interval > MIN_INTERVAL_DELAY) {
                start(interval);
            }
        } catch (Exception e) {
            _log.error("Exception when trying to start synchronization task: {}", e.getMessage());
        }
    }

    /**
     * Stops synchronization between CoprHD and OpenStack Tenants.
     *
     */
    public void stopSynchronizationTask() {
        try {
            stop();
        } catch (Exception e) {
            _log.error("Exception when trying to stop synchronization task: {}", e.getMessage());
        }
    }

    private class SynchronizationScheduler implements Runnable {

        @Override
        public void run() {
            try {
                // List of OpenStack Tenants.
                List<KeystoneTenant> osTenantList = _keystoneUtilsService.getOpenStackTenants();
                List<KeystoneTenant> openstackTenants = new ArrayList<>(osTenantList);
                // List of CoprHD Tenants.
                List<TenantOrg> coprhdTenantList = _keystoneUtilsService.getCoprhdTenantsWithOpenStackId();
                // List of CoprHD Tenants to update.
                List<TenantOrg> tenantsToUpdate = getListOfTenantsToUpdate(osTenantList, coprhdTenantList);

                int size = (tenantsToUpdate == null) ? 0 : tenantsToUpdate.size();
                _log.debug("Tenants to update: {}, to create: {}, to delete: {}", size, osTenantList.size(),
                        coprhdTenantList.size());

                AuthnProvider keystoneProvider = getKeystoneProvider();

                // Update every Tenant on tenantsToUpdate list.
                if (tenantsToUpdate != null && !tenantsToUpdate.isEmpty()) {
                    for (TenantOrg tenant : tenantsToUpdate) {
                        _dbClient.updateObject(tenant);
                    }
                }

                _internalTenantServiceClient.setServer(_keystoneUtilsService.getVIP());

                StringSet syncOptions = keystoneProvider.getTenantsSynchronizationOptions();

                // Check whether Automatic Addition is enabled.
                if (!osTenantList.isEmpty() &&
                        syncOptions.contains(AuthnProvider.TenantsSynchronizationOptions.ADDITION.toString())) {
                    for (KeystoneTenant tenant : osTenantList) {

                        OSTenant osTenant = _keystoneUtilsService.findOpenstackTenantInCoprhd(tenant.getId());
                        if (osTenant != null && !osTenant.getExcluded()) {
                            URI tenantOrgId = createTenant(tenant);
                            URI projectId = createProject(tenantOrgId, tenant);
                            _keystoneUtilsService.tagProjectWithOpenstackId(projectId, tenant.getId(),
                                    tenantOrgId.toString());
                        }
                    }
                }

                // Synchronize OSTenants with Tenants in OpenStack.
                List<URI> osTenantURI = _dbClient.queryByType(OSTenant.class, true);
                Iterator<OSTenant> osTenantIter = _dbClient.queryIterativeObjects(OSTenant.class, osTenantURI);

                while (osTenantIter.hasNext()) {
                    OSTenant osTenant = osTenantIter.next();

                    // Maps openstack Tenants to a map with IDs only and then filters for specific ID.
                    int matches = (int) openstackTenants.stream().map(KeystoneTenant::getId).filter(id -> id.equals(osTenant.getOsId())).count();

                    // Remove OSTenant when Tenant with the same ID in OpenStack is deleted.
                    if (matches < 1) {
                        _dbClient.removeObject(osTenant);
                    }
                }

                // Removes CoprHD Tenants related to OpenStack that are absent in OS.
                if (!coprhdTenantList.isEmpty() &&
                        syncOptions.contains(AuthnProvider.TenantsSynchronizationOptions.DELETION.toString())) {

                    List<URI> projectURI = _dbClient.queryByType(Project.class, true);
                    Iterator<Project> projectIter = _dbClient.queryIterativeObjects(Project.class, projectURI);

                    for (TenantOrg tenant : coprhdTenantList) {

                        while (projectIter.hasNext()) {
                            Project project = projectIter.next();
                            if (project.getTenantOrg().getURI().equals(tenant.getId())) {

                                ArgValidator.checkReference(Project.class, project.getId(), checkForDelete(project));
                                _dbClient.markForDeletion(project);
                            }
                        }

                        ArgValidator.checkReference(TenantOrg.class, tenant.getId(), checkForDelete(tenant));
                        _dbClient.markForDeletion(tenant);
                    }
                }

            } catch (Exception e) {
                _log.error("Exception caught when trying to run OpenStack Synchronization job: {}", e);
            }
        }
    }
}
