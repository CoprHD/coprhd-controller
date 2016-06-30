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
import com.emc.storageos.keystone.restapi.model.response.TenantV2;
import com.emc.storageos.keystone.restapi.utils.KeystoneUtils;
import com.emc.storageos.model.project.ProjectElement;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.security.authentication.InternalTenantSvcClient;
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
    // Maximum time for a timeout when awaiting for termination.
    private static final int MAX_TERMINATION_TIME = 120;
    // Minimum interval in seconds.
    public static final int MIN_INTERVAL_DELAY = 10;

    private static final String OPENSTACK = "OpenStack";

    // Services
    private KeystoneUtils _keystoneUtilsService;

    private InternalTenantSvcClient _internalTenantSvcClient;

    private ScheduledExecutorService _dataCollectionExecutorService;

    private ScheduledFuture synchronizationTask;

    public void setInternalTenantSvcClient(InternalTenantSvcClient _internalTenantSvcClient) {
        this._internalTenantSvcClient = _internalTenantSvcClient;
    }

    public ScheduledFuture getSynchronizationTask() {
        return synchronizationTask;
    }

    public void setKeystoneUtilsService(KeystoneUtils _keystoneUtilsService) {
        this._keystoneUtilsService = _keystoneUtilsService;
    }

    public void start(int interval) throws Exception {

        _log.info("Start OpenStack Synchronization Task");
        _dataCollectionExecutorService = Executors.newScheduledThreadPool(1);

        // Schedule task at fixed interval.
        synchronizationTask = _dataCollectionExecutorService.scheduleAtFixedRate(
                new SynchronizationScheduler(),
                interval, interval, TimeUnit.SECONDS);
    }

    public void stop() {

        _log.info("Stop OpenStack Synchronization Task");
        try {
            _dataCollectionExecutorService.shutdown();
            _dataCollectionExecutorService.awaitTermination(MAX_TERMINATION_TIME, TimeUnit.SECONDS);
            synchronizationTask = null;
        } catch (Exception e) {
            _log.error("TimeOut occurred after waiting Client Threads to finish");
        }
    }

    /**
     * Reschedule synchronization task with given interval.
     *
     * @param newInterval New interval delay between each execution in seconds.
     */
    public void rescheduleTask(int newInterval) {

        if (newInterval >= MIN_INTERVAL_DELAY && synchronizationTask != null) {
            synchronizationTask.cancel(false);
            synchronizationTask = _dataCollectionExecutorService
                    .scheduleAtFixedRate(new SynchronizationScheduler(), newInterval, newInterval, TimeUnit.SECONDS);
            _log.debug("Synchronization task has been rescheduled with {}s interval.", newInterval);
        } else {
            throw APIException.internalServerErrors.rescheduleSynchronizationTaskError();
        }
    }

    /**
     * Retrieves Keystone Authentication Provider from CoprHD.
     *
     * @return Keystone Authentication Provider.
     */
    public AuthnProvider getKeystoneProvider() {

        return _keystoneUtilsService.getKeystoneProvider();
    }

    public int getTaskInterval() {

        AuthnProvider keystoneProvider = getKeystoneProvider();
        int interval = DEFAULT_INTERVAL_DELAY;

        if (keystoneProvider != null && keystoneProvider.getTenantsSynchronizationOptions() != null) {

            String taskInterval = getSynchronizationOptionsInterval(keystoneProvider);
            if (taskInterval != null) {
                interval = Integer.parseInt(taskInterval);
            }
        }

        return interval;
    }

    public String getSynchronizationOptionsInterval(AuthnProvider keystoneProvider) {

        if (keystoneProvider != null && keystoneProvider.getTenantsSynchronizationOptions() != null) {
            return getIntervalFromStringSet(keystoneProvider.getTenantsSynchronizationOptions());
        }

        // Return default interval when getTenantsSynchronizationOptions() does not contain interval.
        return null;
    }

    public String getIntervalFromStringSet(StringSet tenantsSynchronizationOptions) {

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
     * Finds CoprHD Tenants with OpenStack ID that are different from their reflection in OpenStack. Those Tenants needs to be updated.
     *
     * @param osTenantList List of OpenStack Tenants.
     * @param coprhdTenantList List of CoprHD Tenants related to OpenStack.
     * @return List of CoprHD Tenants that needs to be updated.
     */
    private List<TenantOrg> getListOfTenantsToUpdate(List<TenantV2> osTenantList, List<TenantOrg> coprhdTenantList) {

        List<TenantOrg> tenantsToUpdate = null;

        ListIterator<TenantV2> osIter = osTenantList.listIterator();
        ListIterator<TenantOrg> coprhdIter = coprhdTenantList.listIterator();

        while (osIter.hasNext()) {

            TenantV2 osTenant = osIter.next();
            // Update information about this Tenant in CoprHD database.
            createOrUpdateOpenstackTenantInCoprhd(osTenant);
            while (coprhdIter.hasNext()) {

                TenantOrg coprhdTenant = coprhdIter.next();
                String tenantMapping = _keystoneUtilsService.getCoprhdTenantUserMapping(coprhdTenant);
                if (tenantMapping == null) {
                    throw APIException.internalServerErrors.targetIsNullOrEmpty("TenantMapping");
                }
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
            coprhdIter = coprhdTenantList.listIterator();
        }

        return tenantsToUpdate;
    }

    /**
     * Updates or creates CoprHD representation of OpenStack Tenant.
     *
     * @param tenant OpenStack Tenant.
     * @return Updated or Created Tenant.
     */
    private OSTenant createOrUpdateOpenstackTenantInCoprhd(TenantV2 tenant) {

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

            return osTenant;
        }

        osTenant = _keystoneUtilsService.mapToOsTenant(tenant);
        osTenant.setId(URIUtil.createId(OSTenant.class));
        _dbClient.createObject(osTenant);

        return osTenant;
    }

    /**
     * Compares OpenStack Tenant with CoprHD Tenant (both needs to have the same OpenStack ID).
     *
     * @param osTenant OpenStack Tenant.
     * @param coprhdTenant CoprHD Tenant related to OpenStack.
     * @return True if tenants are identical, false otherwise.
     */
    private boolean areTenantsIdentical(TenantV2 osTenant, TenantOrg coprhdTenant) {

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
     * Checks if Keystone Authentication Provider exists in CoprHD.
     *
     * @return True if Keystone Authentication Provider exists, false otherwise.
     */
    public boolean doesKeystoneProviderExist() {

        return getKeystoneProvider() != null;
    }

    /**
     * Creates a CoprHD Tenant for given OpenStack Tenant.
     *
     * @param tenant OpenStack Tenant.
     *
     * @return URI ow newly created Tenant.
     */
    public URI createTenant(TenantV2 tenant) {

        TenantOrgRestRep tenantResp = _internalTenantSvcClient.createTenant(_keystoneUtilsService.prepareTenantParam(tenant));

        return tenantResp.getId();
    }

    /**
     * Creates a CoprHD Project for given Tenant.
     *
     * @param tenantOrgId ID of the Project owner.
     * @param tenant OpenStack Tenant.
     *
     * @return URI ow newly created Project.
     */
    public URI createProject(URI tenantOrgId, TenantV2 tenant) {

        ProjectParam projectParam = new ProjectParam(tenant.getName() + CinderConstants.PROJECT_NAME_SUFFIX);
        ProjectElement projectResp = _internalTenantSvcClient.createProject(tenantOrgId, projectParam);

        return projectResp.getId();
    }

    /**
     * Starts synchronization between CoprHD and OpenStack Tenants.
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
                List<TenantV2> osTenantList = _keystoneUtilsService.getOpenStackTenants();
                List<TenantV2> openstackTenants = new ArrayList<>(osTenantList);
                // List of CoprHD Tenants.
                List<TenantOrg> coprhdTenantList = _keystoneUtilsService.getCoprhdTenantsWithOpenStackId();
                // List of CoprHD Tenants to update.
                List<TenantOrg> tenantsToUpdate = getListOfTenantsToUpdate(osTenantList, coprhdTenantList);

                int size = (tenantsToUpdate == null) ? 0 : tenantsToUpdate.size();
                _log.debug("Amount of tenants to update: {}, to create: {}, to delete: {}", size, osTenantList.size(),
                        coprhdTenantList.size());

                AuthnProvider keystoneProvider = getKeystoneProvider();

                if (keystoneProvider == null) {
                    throw APIException.internalServerErrors.targetIsNullOrEmpty("Keystone Authentication Provider");
                }

                // Update every Tenant on tenantsToUpdate list.
                if (tenantsToUpdate != null && !tenantsToUpdate.isEmpty()) {
                    for (TenantOrg tenant : tenantsToUpdate) {
                        _dbClient.updateObject(tenant);
                    }
                }

                _internalTenantSvcClient.setServer(_keystoneUtilsService.getVIP());

                StringSet syncOptions = keystoneProvider.getTenantsSynchronizationOptions();

                // Check whether Automatic Addition is enabled.
                if (!osTenantList.isEmpty() &&
                        syncOptions.contains(AuthnProvider.TenantsSynchronizationOptions.ADDITION.toString())) {
                    for (TenantV2 tenant : osTenantList) {

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
                    int matches = (int) openstackTenants.stream().map(TenantV2::getId).filter(id -> id.equals(osTenant.getOsId())).count();

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
