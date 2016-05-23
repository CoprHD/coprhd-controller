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
import com.emc.storageos.keystone.restapi.KeystoneApiClient;
import com.emc.storageos.keystone.restapi.model.response.TenantV2;
import com.emc.storageos.keystone.restapi.utils.KeystoneUtils;
import com.emc.storageos.model.tenant.TenantCreateParam;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenStackSynchronizationTask extends ResourceService {

    private static final Logger _log = LoggerFactory.getLogger(OpenStackSynchronizationTask.class);

    // Constants
    // Interval delay between each execution in seconds.
    public static final int DEFAULT_INTERVAL_DELAY = 60;
    // Initial delay before first execution in seconds.
    private static final int INITIAL_DELAY = 60;
    // Maximum time for a timeout when awaiting for termination.
    private static final int MAX_TERMINATION_TIME = 120;
    // Minimum interval in seconds.
    public static final int MIN_INTERVAL_DELAY = 10;
    // Default excluded option for OSTenant.
    private static final boolean DEFAULT_EXCLUDED_TENANT_OPTION = false;

    private static final String TENANT_ID = "tenant_id";
    private static final String OPENSTACK = "OpenStack";
    private static final String ROOT = "root";
    private static final String VALUES = "values";

    // Services
    private KeystoneUtils _keystoneUtilsService;
    private ScheduledExecutorService _dataCollectionExecutorService;
    private AuthnConfigurationService _authnConfigurationService;

    private ScheduledFuture synchronizationTask;

    public ScheduledFuture getSynchronizationTask() {
        return synchronizationTask;
    }

    public void setAuthnConfigurationService(AuthnConfigurationService authnConfigurationService) {
        this._authnConfigurationService = authnConfigurationService;
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
                INITIAL_DELAY, interval, TimeUnit.SECONDS);
    }

    public void stop() {

        _log.info("Stop OpenStack Synchronization Task");
        try {
            _dataCollectionExecutorService.shutdown();
            _dataCollectionExecutorService.awaitTermination(MAX_TERMINATION_TIME, TimeUnit.SECONDS);
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
                    .scheduleAtFixedRate(new SynchronizationScheduler(), INITIAL_DELAY, newInterval, TimeUnit.SECONDS);
            _log.debug("Synchronization task has been rescheduled with {}s interval.", newInterval);
        } else {
            throw APIException.internalServerErrors.rescheduleSynchronizationTaskError();
        }
    }

    /**
     * Retrieves OpenStack Tenants from Keystone.
     *
     * @return List of OpenStack Tenants.
     */
    private List<TenantV2> getOpenStackTenants() {

        AuthnProvider keystoneProvider = getKeystoneProvider();

        if (keystoneProvider == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Keystone provider");
        }

        // Get Keystone API client.
        KeystoneApiClient keystoneApiClient = _keystoneUtilsService.getKeystoneApi(keystoneProvider.getManagerDN(),
                keystoneProvider.getServerUrls(), keystoneProvider.getManagerPassword());

        // Get OpenStack Tenants.
        // You cannot remove or add elements dynamically to Arrays (Arrays.asList) that is why this needs to be wrapped in a new list.
        List<TenantV2> OSTenantList = new ArrayList<>(Arrays.asList(keystoneApiClient.getKeystoneTenants().getTenants()));

        _log.debug("OpenStack tenants[{}]: {}", OSTenantList.size(), listToString(OSTenantList));
        return OSTenantList;
    }

    /**
     * Retrieves CoprHD Tenants with OpenStack ID parameter.
     *
     * @return List of CoprHD Tenants.
     */
    public List<TenantOrg> getCoprhdTenantsWithOpenStackId() {

        List<URI> coprhdTenantsURI = _dbClient.queryByType(TenantOrg.class, true);
        Iterator<TenantOrg> tenantsIter = _dbClient.queryIterativeObjects(TenantOrg.class, coprhdTenantsURI);
        List<TenantOrg> tenants = new ArrayList<>();

        // Iterate over CoprHD Tenants and get only those that contain tenant_id parameter.
        while (tenantsIter.hasNext()) {
            TenantOrg tenant = tenantsIter.next();
            if (getCoprhdTenantUserMapping(tenant) != null) {
                tenants.add(tenant);
            }
        }

        _log.debug("CoprHD tenants[{}]", tenants.size());
        return tenants;
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
            for (String option : keystoneProvider.getTenantsSynchronizationOptions()) {
                // There is only ADDITION, DELETION and interval in this StringSet.
                if (!AuthnProvider.TenantsSynchronizationOptions.ADDITION.toString().equals(option)
                        && !AuthnProvider.TenantsSynchronizationOptions.DELETION.toString().equals(option)) {
                    return option;
                }
            }
        }

        // Return default interval when getTenantsSynchronizationOptions() does not contain interval.
        return Integer.toString(DEFAULT_INTERVAL_DELAY);
    }

    /**
     * Retrieves UserMapping from CoprHD Tenant.
     *
     * @param tenant CoprHD Tenant.
     * @return User Mapping for given Tenant.
     */
    public String getCoprhdTenantUserMapping(TenantOrg tenant) {

        StringSetMap userMappings = tenant.getUserMappings();
        // Ignore root Tenant.
        if (!TenantOrg.isRootTenant(tenant) && userMappings != null) {
            // Return mapping that contains tenant_id.
            for (AbstractChangeTrackingSet<String> userMappingSet : userMappings.values()) {
                for (String mapping : userMappingSet) {
                    if (mapping.contains(TENANT_ID)) {
                        return mapping;
                    }
                }
            }
        }

        // Return null whether Tenant has no mapping with tenant_id parameter.
        return null;
    }

    /**
     * Retrieves OpenStack Tenant ID from UserMapping.
     *
     * @param userMapping CoprHD UserMapping.
     * @return OpenStack Tenant ID.
     */
    private String getTenantIdFromUserMapping(String userMapping) {

        // Create split pattern.
        Pattern p = Pattern.compile("\\[(.*?)\\]");
        // Apply pattern.
        Matcher m = p.matcher(userMapping);
        String result;

        while (m.find()) {

            if (m.group().contains(VALUES)) {
                result = m.group().split(":")[1];

                return result.substring(2, result.length() - 2);
            }
        }

        return "";
    }

    private String listToString(List<?> list) {

        StringBuilder sb = new StringBuilder();
        for (Object o : list) {
            sb.append(o.toString());
            sb.append("\t");
        }

        return sb.toString();
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
            updateOrCreateOpenstackTenantInCoprhd(osTenant);
            while (coprhdIter.hasNext()) {

                TenantOrg coprhdTenant = coprhdIter.next();
                String tenantMapping = getCoprhdTenantUserMapping(coprhdTenant);
                if (tenantMapping == null) {
                    throw APIException.internalServerErrors.targetIsNullOrEmpty("TenantMapping");
                }
                String tenantId = getTenantIdFromUserMapping(tenantMapping);
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
    private OSTenant updateOrCreateOpenstackTenantInCoprhd(TenantV2 tenant) {

        OSTenant osTenant = findOpenstackTenantInCoprhd(tenant.getId());

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

        osTenant = mapTenant(tenant);
        osTenant.setId(URIUtil.createId(OSTenant.class));
        _dbClient.createObject(osTenant);

        return osTenant;
    }

    /**
     * Finds CoprHD representation of OpenStack Tenant.
     *
     * @param tenantId OpenStack Tenant ID.
     * @return CoprHD Tenant.
     */
    private OSTenant findOpenstackTenantInCoprhd(String tenantId) {

        List<URI> osTenantURI = _dbClient.queryByType(OSTenant.class, true);
        Iterator<OSTenant> osTenantIter = _dbClient.queryIterativeObjects(OSTenant.class, osTenantURI);

        while (osTenantIter.hasNext()) {
            OSTenant osTenant = osTenantIter.next();
            if (osTenant.getOsId().equals(tenantId)) {
                return osTenant;
            }
        }

        return null;
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
     */
    public TenantOrg createTenant(OSTenant tenant) {

        AuthnProvider provider = getKeystoneProvider();

        TenantCreateParam param = _authnConfigurationService.prepareTenantMappingForOpenstack(tenant, provider);

        TenantOrg subtenant = new TenantOrg();
        subtenant.setId(URIUtil.createId(TenantOrg.class));
        subtenant.setParentTenant(new NamedURI(_permissionsHelper.getRootTenant().getId(), param.getLabel()));
        subtenant.setLabel(param.getLabel());
        subtenant.setDescription(param.getDescription());
        List<BasePermissionsHelper.UserMapping> userMappings = BasePermissionsHelper.UserMapping.fromParamList(param.getUserMappings());
        for (BasePermissionsHelper.UserMapping userMapping : userMappings) {
            userMapping.setDomain(userMapping.getDomain().trim());
            subtenant.addUserMapping(userMapping.getDomain(), userMapping.toString());
        }
        subtenant.addRole(new PermissionsKey(PermissionsKey.Type.SID,
                ROOT).toString(), Role.TENANT_ADMIN.toString());

        _dbClient.createObject(subtenant);

        return subtenant;
    }

    /**
     * Creates a CoprHD Project for given Tenant.
     *
     * @param owner CoprHD Tenant that will own this project.
     * @param param OpenStack Tenant.
     */
    public Project createProject(TenantOrg owner, OSTenant param) {

        Project project = new Project();
        project.setId(URIUtil.createId(Project.class));
        project.setLabel(param.getName() + CinderConstants.PROJECT_NAME_SUFFIX);
        project.setTenantOrg(new NamedURI(owner.getId(), project.getLabel()));
        project.setOwner(ROOT);

        project.addAcl(
                new PermissionsKey(PermissionsKey.Type.SID, ROOT, owner.getId().toString()).toString(),
                ACL.OWN.toString());

        _dbClient.createObject(project);

        return project;
    }

    private OSTenant mapTenant(TenantV2 tenant) {

        OSTenant osTenant = new OSTenant();
        osTenant.setOsId(tenant.getId());
        osTenant.setDescription(tenant.getDescription());
        osTenant.setName(tenant.getName());
        osTenant.setEnabled(Boolean.parseBoolean(tenant.getEnabled()));
        osTenant.setExcluded(DEFAULT_EXCLUDED_TENANT_OPTION);

        return osTenant;
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
                List<TenantV2> osTenantList = getOpenStackTenants();
                // List of CoprHD Tenants.
                List<TenantOrg> coprhdTenantList = getCoprhdTenantsWithOpenStackId();
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

                StringSet syncOptions = keystoneProvider.getTenantsSynchronizationOptions();

                // Check whether Automatic Addition is enabled.
                if (!osTenantList.isEmpty() &&
                        syncOptions.contains(AuthnProvider.TenantsSynchronizationOptions.ADDITION.toString())) {
                    for (TenantV2 tenant : osTenantList) {

                        OSTenant osTenant = findOpenstackTenantInCoprhd(tenant.getId());
                        if (osTenant != null && !osTenant.getExcluded()) {
                            TenantOrg tenantOrg = createTenant(mapTenant(tenant));
                            Project project = createProject(tenantOrg, mapTenant(tenant));
                            _authnConfigurationService.tagProjectWithOpenstackId(project.getId(), tenant.getId(), tenantOrg.getId().toString());
                        }
                    }
                }

                // Remove CoprHD representation of OpenStack Tenant that are removed from OpenStack.
                if (syncOptions.contains(AuthnProvider.TenantsSynchronizationOptions.DELETION.toString())) {
                    for (TenantOrg tenant : coprhdTenantList) {
                        String tenantMapping = getCoprhdTenantUserMapping(tenant);

                        if (tenantMapping == null) {
                            throw APIException.internalServerErrors.targetIsNullOrEmpty("TenantMapping");
                        }

                        String tenantId = getTenantIdFromUserMapping(tenantMapping);
                        OSTenant osTenant = findOpenstackTenantInCoprhd(tenantId);

                        if (osTenant == null) {
                            throw APIException.internalServerErrors.targetIsNullOrEmpty("OSTenant");
                        }

                        _dbClient.markForDeletion(osTenant);
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
                _log.error(String.format("Exception caught when trying to run OpenStack Synchronization job"), e);
            }
        }
    }
}
