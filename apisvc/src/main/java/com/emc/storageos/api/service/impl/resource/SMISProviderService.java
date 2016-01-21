/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.SystemsMapper.map;
import static com.emc.storageos.api.mapper.SystemsMapper.mapStorageProviderToSMISRep;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.functions.MapSmisProvider;
import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorIntf;
import com.emc.storageos.api.service.impl.resource.utils.DiscoveredObjectTaskScheduler;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DecommissionedResource;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.smis.SMISProviderBulkRep;
import com.emc.storageos.model.smis.SMISProviderCreateParam;
import com.emc.storageos.model.smis.SMISProviderList;
import com.emc.storageos.model.smis.SMISProviderRestRep;
import com.emc.storageos.model.smis.SMISProviderUpdateParam;
import com.emc.storageos.model.smis.StorageSystemSMISCreateParam;
import com.emc.storageos.model.smis.StorageSystemSMISRequestParam;
import com.emc.storageos.model.smis.StorageSystemSMISUpdateParam;
import com.emc.storageos.model.systems.StorageSystemList;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.varray.DecommissionedResources;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.StorageController;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

/**
 * SMISProvider resource implemented to allow the user to perform the following activities.
 * 1. Allows user to create/list SMISProvider managing block devices such as vnxfile, vmax.
 * This information can be used to get the indications from block devices.
 * 2. User can register and deregister SMISProvider.
 * 
 * This class is deprecated class. Use /vdc/storage-providers instead of /vdc/smis-providers.
 */
@Deprecated
@Path("/vdc/smis-providers")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN })
public class SMISProviderService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(SMISProviderService.class);

    private static final String EVENT_SERVICE_TYPE = "provider";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    private static class ScanJobExec implements AsyncTaskExecutorIntf {

        private final StorageController _controller;

        ScanJobExec(StorageController controller) {
            _controller = controller;
        }

        @Override
        public void executeTasks(AsyncTask[] tasks) throws ControllerException {
            _controller.scanStorageProviders(tasks);
        }

        @Override
        public ResourceOperationTypeEnum getOperation() {
            return ResourceOperationTypeEnum.SCAN_SMISPROVIDER;
        }
    }

    /**
     * Register an SMI-S provider to create storage systems of type
     * vnxblock and vmax. This call is not used to create SMI-S
     * providers for vnxfile.
     * <p>
     * The method is deprecated. Use /vdc/storage-providers instead.
     * 
     * @param param SMIS-Provider parameters
     * @brief Register SMI-S provider
     * @return Newly registered SMIS-Provider details
     * @throws ControllerException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public TaskResourceRep registerSMISProvider(SMISProviderCreateParam param)
            throws ControllerException {
        String providerKey = param.getIpAddress() + "-" + param.getPortNumber();
        List<StorageProvider> providers = CustomQueryUtility.getActiveStorageProvidersByProviderId(_dbClient, providerKey);
        if (providers != null && !providers.isEmpty()) {
            throw APIException.badRequests.invalidParameterSMISProviderAlreadyRegistered(providerKey);
        }

        ArgValidator.checkFieldNotEmpty(param.getName(), "name");
        checkForDuplicateName(param.getName(), StorageProvider.class);
        ArgValidator.checkFieldNotEmpty(param.getIpAddress(), "ip_address");
        ArgValidator.checkFieldNotNull(param.getPortNumber(), "port_number");
        ArgValidator.checkFieldNotEmpty(param.getUserName(), "user_name");
        ArgValidator.checkFieldNotEmpty(param.getPassword(), "password");
        ArgValidator.checkFieldNotNull(param.getUseSSL(), "use_ssl");

        ArgValidator.checkFieldRange(param.getPortNumber(), 1, 65535, "port_number");

        StorageProvider smisProvider = new StorageProvider();
        smisProvider.setInterfaceType(StorageProvider.InterfaceType.smis.name());
        smisProvider.setId(URIUtil.createId(StorageProvider.class));
        smisProvider.setLabel(param.getName());
        smisProvider.setIPAddress(param.getIpAddress());
        smisProvider.setPortNumber(param.getPortNumber());
        smisProvider.setUserName(param.getUserName());
        smisProvider.setPassword(param.getPassword());
        smisProvider.setUseSSL(param.getUseSSL());
        smisProvider.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        _dbClient.createObject(smisProvider);

        auditOp(OperationTypeEnum.REGISTER_SMISPROVIDER, true, null,
                smisProvider.getLabel(), smisProvider.getId().toString(), smisProvider.getIPAddress(),
                smisProvider.getPortNumber(), smisProvider.getUserName());

        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        String taskId = UUID.randomUUID().toString();
        tasks.add(new AsyncTask(StorageProvider.class, smisProvider.getId(), taskId));

        // @TODO revisit this to avoid hard coding.
        BlockController controller = getController(BlockController.class, "vnxblock");
        /**
         * Creates MonitoringJob token for vnxblock/vmax device on zooKeeper queue
         */
        controller.startMonitoring(new AsyncTask(StorageProvider.class, smisProvider.getId(), taskId),
                StorageSystem.Type.vnxblock);
        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(_dbClient, new ScanJobExec(controller));
        TaskList taskList = scheduler.scheduleAsyncTasks(tasks);

        return taskList.getTaskList().listIterator().next();
    }

    /**
     * This call allows user to fetch SMI-S Provider details such as provider
     * host access credential details.
     * <p>
     * The method is deprecated. Use /vdc/storage-providers/{id} instead.
     * 
     * @param id the URN of a ViPR SMIS-Provider
     * @brief Show SMI-S provider
     * @return SMIS-Provider details.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public SMISProviderRestRep getSMISProvider(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, StorageProvider.class, "id");
        StorageProvider smisProvider = queryResource(id);
        return mapStorageProviderToSMISRep(smisProvider);
    }

    @Override
    protected StorageProvider queryResource(URI id) {
        ArgValidator.checkUri(id);
        StorageProvider smisProvider = _dbClient.queryObject(StorageProvider.class, id);
        ArgValidator.checkEntityNotNull(smisProvider, id, isIdEmbeddedInURL(id));
        return smisProvider;
    }

    /**
     * This function allows user to fetch list of all SMI-S Providers information.
     * <p>
     * The method is deprecated. Use /vdc/storage-providers instead.
     * 
     * @brief List SMI-S providers
     * @return List of SMIS-Providers.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public SMISProviderList getSmiSProviderList() {
        List<StorageProvider> providerList = CustomQueryUtility.
                getActiveStorageProvidersByInterfaceType(_dbClient, StorageProvider.InterfaceType.smis.name());
        /*
         * List<URI> ids = _dbClient.queryByType(SMISProvider.class);
         * List<SMISProvider> smisProviders = _dbClient.queryObject(SMISProvider.class, ids);
         * if (smisProviders == null) {
         * throw APIException.badRequests.unableToFindSMISProvidersForIds(ids);
         * }
         */
        SMISProviderList smisProviderList = new SMISProviderList();
        for (StorageProvider provider : providerList) {
            smisProviderList.getSmisProviders().
                    add(toNamedRelatedResource(ResourceTypeEnum.SMIS_PROVIDER, provider.getId(), provider.getLabel()));
        }
        return smisProviderList;
    }

    /**
     * Update the SMISProvider. This is useful when we move arrays to some other
     * provider.
     * <p>
     * The method is deprecated. Use /vdc/storage-providers/{id} instead.
     * 
     * @param id the URN of a ViPR SMIS-Provider
     * @brief Update SMI-S provider
     * @return Updated SMIS-Provider information.
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public SMISProviderRestRep updateSMISProvider(@PathParam("id") URI id,
            SMISProviderUpdateParam param) {
        StorageProvider smisProvider = _dbClient.queryObject(StorageProvider.class,
                id);
        if (null == smisProvider) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        } else if (!smisProvider.getInactive()) {
            /*
             * Usecase is not to remove the provider instead we can update the old smisprovider with
             * new provider details.
             */
            if (param.getName() != null && !param.getName().equals("") && !param.getName().equalsIgnoreCase(smisProvider.getLabel())) {
                checkForDuplicateName(param.getName(), StorageProvider.class);
                smisProvider.setLabel(param.getName());
            }

            // if the ip or port passed are different from the existing one
            // check to ensure a provider does not exist with the new ip + port combo
            String existingIPAddress = smisProvider.getIPAddress();
            Integer existingPortNumber = smisProvider.getPortNumber();
            if ((param.getIpAddress() != null && !param.getIpAddress().equals(existingIPAddress)) ||
                    (param.getPortNumber() != null && !param.getPortNumber().equals(existingPortNumber))) {
                String ipAddress = (param.getIpAddress() != null) ? param.getIpAddress() : existingIPAddress;
                Integer portNumber = (param.getPortNumber() != null) ? param.getPortNumber() : existingPortNumber;
                ArgValidator.checkFieldRange(portNumber, 1, 65535, "port_number");

                String providerKey = ipAddress + "-" + portNumber;
                List<StorageProvider> providers = CustomQueryUtility.getActiveStorageProvidersByProviderId(_dbClient, providerKey);
                if (providers != null && !providers.isEmpty()) {
                    throw APIException.badRequests.invalidParameterSMISProviderAlreadyRegistered(providerKey);
                }

                // User can update IP address for an existing Provider object
                // if and only if the connection with old IP is not alive.
                if (!existingIPAddress.equals(param.getIpAddress()) &&
                        isOldConnectionAlive(existingIPAddress, existingPortNumber, smisProvider.getInterfaceType())) {
                    throw APIException.badRequests.cannotUpdateProviderIP(existingIPAddress + "-" + existingPortNumber);
                }

                smisProvider.setIPAddress(ipAddress);
                smisProvider.setPortNumber(portNumber);
            }
            if (param.getUserName() != null && !param.getUserName().equals("")) {
                smisProvider.setUserName(param.getUserName());
            }
            if (param.getPassword() != null && !param.getPassword().equals("")) {
                smisProvider.setPassword(param.getPassword());
            }
            if (param.getUseSSL() != null) {
                smisProvider.setUseSSL(param.getUseSSL());
            }

            _dbClient.persistObject(smisProvider);
        }

        auditOp(OperationTypeEnum.UPDATE_SMISPROVIDER, true, null,
                smisProvider.getId().toString(), smisProvider.getLabel(), smisProvider.getIPAddress(),
                smisProvider.getPortNumber(), smisProvider.getUserName());

        return mapStorageProviderToSMISRep(smisProvider);
    }

    private boolean isOldConnectionAlive(String ipAddress,
            Integer portNumber, String interfaceType) {
        BlockController controller = getController(BlockController.class, "vnxblock");
        return controller.validateStorageProviderConnection(ipAddress, portNumber, interfaceType);
    }

    /**
     * Scan all SMI-S providers.
     * <p>
     * The method is deprecated. Use /vdc/storage-providers/scan instead.
     * 
     * @brief Scan SMI-S providers
     * @return TasList of all created asynchronous tasks
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Path("/scan")
    public TaskList scanSMISProviders() {
        TaskList taskList = new TaskList();
        List<StorageProvider> providerList = CustomQueryUtility.getActiveStorageProvidersByInterfaceType(_dbClient,
                StorageProvider.InterfaceType.smis.name());
        if (providerList == null || providerList.isEmpty()) {
            return taskList;
        }
        BlockController controller = getController(BlockController.class, "vnxblock");
        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(_dbClient, new ScanJobExec(controller));
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>();
        for (StorageProvider smisProvider : providerList) {
            String taskId = UUID.randomUUID().toString();
            tasks.add(new AsyncTask(StorageProvider.class, smisProvider.getId(), taskId));
        }
        taskList = scheduler.scheduleAsyncTasks(tasks);

        return taskList;
    }

    /**
     * Allows the user to deactivate an SMI-S provider.
     * <p>
     * The method is deprecated. Use /vdc/storage-providers/{id}/deactivate instead.
     * 
     * @param id the URN of a ViPR SMI-S provider
     * 
     * @brief Delete SMI-S provider
     * @return Status indicating success or failure.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public Response deleteSMISProvider(@PathParam("id") URI id) {
        // Validate the provider
        ArgValidator.checkFieldUriType(id, StorageProvider.class, "id");
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, id);
        ArgValidator.checkEntityNotNull(provider, id, isIdEmbeddedInURL(id));

        // Verify the provider can be removed without leaving "dangling" storages.
        StringSet providerStorageSystems = provider.getStorageSystems();
        if (null != providerStorageSystems && !providerStorageSystems.isEmpty()) {
            // First we need to verify that all related storage systems has at least 2 providers
            for (String system : providerStorageSystems) {
                StorageSystem storageSys = _dbClient.queryObject(StorageSystem.class, URI.create(system));
                if (storageSys.getProviders().size() == 1) {
                    throw APIException.badRequests.cannotDeleteProviderWithManagedStorageSystems(storageSys.getId());
                }
            }
            // Next we can clear this provider from storage systems.
            for (String system : providerStorageSystems) {
                StorageSystem storageSys = _dbClient.queryObject(StorageSystem.class, URI.create(system));
                provider.removeStorageSystem(_dbClient, storageSys);
            }
        }

        StringSet decommissionedSystems = provider.getDecommissionedSystems();
        if (null != decommissionedSystems && !decommissionedSystems.isEmpty()) {
            for (String decommissioned : decommissionedSystems) {
                DecommissionedResource oldRes = _dbClient.queryObject(DecommissionedResource.class, URI.create(decommissioned));
                if (oldRes != null) {
                    _dbClient.markForDeletion(oldRes);
                }
            }
        }

        // Set to inactive.
        _dbClient.markForDeletion(provider);

        auditOp(OperationTypeEnum.DELETE_SMISPROVIDER, true, null,
                provider.getId().toString(), provider.getLabel(), provider.getIPAddress(),
                provider.getPortNumber(), provider.getUserName());

        return Response.ok().build();
    }

    /**
     * Allows the user to manually create a SMIS managed storage system. This call is
     * applicable to systems that cannot be discovered via an SMI-S provider.
     * Only VNX storage system can be mapped programatically into SMIS Provider.
     * Otherwise this method should be used to reinstall previously decommissioned Arrays.
     * <p>
     * The method is deprecated. Use /vdc/storage-providers/storage-systems instead.
     * 
     * @param param The storage system details.
     * 
     * @brief Create a storage system and add it to the SMI-S providers.
     * @return An asynchronous task corresponding to the discovery job scheduled for the new Storage System.
     * 
     * @throws BadRequestException When the system type is not valid or a
     *             storage system with the same native guid already exists.
     * @throws com.emc.storageos.db.exceptions.DatabaseException When an error occurs querying the database.
     * @throws ControllerException When an error occurs discovering the storage
     *             system.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Path("/storage-systems")
    public TaskResourceRep addStorageSystem(StorageSystemSMISCreateParam param) throws ControllerException {
        TaskResourceRep taskRep;
        URIQueryResultList list = new URIQueryResultList();

        ArgValidator.checkFieldNotEmpty(param.getSystemType(), "system_type");
        if (!StorageSystem.Type.isProviderStorageSystem(Type.valueOf(param.getSystemType()))) {
            throw APIException.badRequests.cannotAddStorageSystemTypeToStorageProvider(param.getSystemType());
        }
        if ((Type.valueOf(param.getSystemType()) == Type.vnxblock) && StringUtils.isNotBlank(param.getIpAddress())) {
            // If IP address is not null, the user will need to provide all remaining info
            // to add VNX to the SMIS
            ArgValidator.checkFieldNotEmpty(param.getSecondaryIPs(), "secondary_ips");
            ArgValidator.checkFieldNotEmpty(param.getSecondaryIPs().get(0), "secondary_ips");
            ArgValidator.checkFieldNotEmpty(param.getPassword(), "password");
            ArgValidator.checkFieldNotEmpty(param.getUserName(), "userName");
        }
        if (param.getSmisProviders() == null || param.getSmisProviders().isEmpty()) {
            throw APIException.badRequests.invalidParameterSMISProviderListIsEmpty();
        }
        ArgValidator.checkFieldNotEmpty(param.getSerialNumber(), "serialNumber");

        String nativeGuid = NativeGUIDGenerator.generateNativeGuid(param.getSystemType(),
                param.getSerialNumber());
        // check for duplicate StorageSystem.

        List<StorageSystem> systems = CustomQueryUtility.getActiveStorageSystemByNativeGuid(_dbClient, nativeGuid);
        if (systems != null && !systems.isEmpty()) {
            throw APIException.badRequests.invalidParameterProviderStorageSystemAlreadyExists("nativeGuid", nativeGuid);
        }

        String taskId = UUID.randomUUID().toString();

        URI[] providers = new URI[param.getSmisProviders().size()];
        int idx = 0;
        for (URI provider : param.getSmisProviders()) {
            StorageProvider providerObj = _dbClient.queryObject(StorageProvider.class, provider);
            ArgValidator.checkEntity(providerObj, provider, isIdEmbeddedInURL(provider));
            if (!providerObj.connected()) {
                throw APIException.badRequests.invalidParameterSMISProviderNotConnected(providerObj.getIPAddress());
            }
            providers[idx++] = provider;
        }

        StorageSystem system = prepareStorageSystem(param);

        BlockController controller = getController(BlockController.class, param.getSystemType());

        Operation op = _dbClient.createTaskOpStatus(StorageSystem.class, system.getId(),
                taskId, ResourceOperationTypeEnum.CREATE_STORAGE_SYSTEM);

        controller.addStorageSystem(system.getId(), providers, true, taskId);

        return toTask(system, taskId, op);
    }

    /**
     * Allows the user to update credentials for a storage system that
     * is not connected to any SMIS Provider.
     * This update call is applicable to systems that cannot be discovered
     * via an SMI-S provider.
     * This API call creates an asynchronous operation to add providers to the SMI-S provider,
     * verifies that the storage system is visible through the provider, and performs discovery of
     * the storage system
     * Note: only vnxblock can be actively connected to the SMIS provider. VMAX skips this step,
     * the system must be visible to the SMIS provider beforehand.
     * <p>
     * The method is deprecated. Use /vdc/storage-providers/storage-systems/{system_id} instead.
     * 
     * @param id the URN of a ViPR SMI-S provider
     * @param param The storage system details to update.
     * 
     * @brief Update storage system credentials and the list of SMI-S providers
     * @return A TaskResourceRep reference specifying the storage system
     *         data.
     * @throws ControllerException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/storage-systems/{system_id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public TaskResourceRep updateStorageSystem(@PathParam("id") URI id,
            StorageSystemSMISUpdateParam param) throws ControllerException {

        StorageSystem system = _dbClient.queryObject(StorageSystem.class, id);
        ArgValidator.checkEntity(system, id, isIdEmbeddedInURL(id));

        if (system.getSystemType().equals(StorageSystem.Type.vmax.name())) {
            throw APIException.badRequests.unableToUpdateStorageSystem(StorageSystem.Type.vmax.name());
        }

        String taskId = UUID.randomUUID().toString();

        if (param.getSmisProviders() == null || param.getSmisProviders().isEmpty()) {
            throw APIException.badRequests.invalidParameterSMISProviderListIsEmpty();
        }

        URI[] providers = new URI[param.getSmisProviders().size()];
        int idx = 0;
        for (URI provider : param.getSmisProviders()) {
            StorageProvider providerObj = _dbClient.queryObject(StorageProvider.class, provider);
            ArgValidator.checkEntity(providerObj, provider, isIdEmbeddedInURL(provider));
            if (!providerObj.connected()) {
                throw APIException.badRequests.invalidParameterSMISProviderNotConnected(providerObj.getIPAddress());
            }
            if (system.getProviders() != null && system.getProviders().contains(provider)) {
                throw APIException.badRequests.invalidParameterSMISProviderAlreadyRegistered(providerObj.getIPAddress());
            }
            if (system.getProviders() != null && system.getProviders().contains(provider)) {
                throw APIException.badRequests.invalidParameterSMISProviderAlreadyRegistered(providerObj.getIPAddress());
            }
            providers[idx++] = provider;
        }

        updateStorageObj(system, param);

        // Update SMIS Providers for the storage
        BlockController controller = getController(BlockController.class,
                system.getSystemType());
        Operation op = _dbClient.createTaskOpStatus(StorageSystem.class, system.getId(),
                taskId, ResourceOperationTypeEnum.UPDATE_STORAGE_SYSTEM);

        boolean activeProvider = (system.getProviders() == null) || (system.getProviders().isEmpty());
        controller.addStorageSystem(system.getId(), providers, activeProvider, taskId);

        return toTask(system, taskId, op);
    }

    /**
     * Get zone role assignments
     * The method is deprecated. Use /vdc/storage-providers/deactivated-systems
     * 
     * @brief List zone role assignments
     * @return Role assignment details
     */
    @GET
    @Path("/deactivated-systems")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public DecommissionedResources getDecommissionedResources() {
        List<URI> resList = _dbClient.queryByType(DecommissionedResource.class, true);
        DecommissionedResources results = new DecommissionedResources();
        for (URI res : resList) {
            DecommissionedResource resource = _dbClient.queryObject(DecommissionedResource.class, res);
            if ("StorageSystem".equals(resource.getType())) {
                results.addResource(map(resource));
            }
        }
        return results;
    }

    /**
     * Allows the user to get the id, name, and self link for all storage
     * systems visible to the provider with the passed id.
     * <p>
     * The method is deprecated. Use /vdc/storage-providers/{id}/storage-systems
     * 
     * @param id the URN of a ViPR SMI-S provider
     * 
     * @brief List SMI-S provider storage systems
     * @return A StorageSystemList reference specifying the id, name, and self
     *         link for the storage systems visible to the provider.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/storage-systems")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StorageSystemList getStorageSystems(@PathParam("id") URI id) {

        // Validate the provider
        ArgValidator.checkFieldUriType(id, StorageProvider.class, "id");
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, id);
        ArgValidator.checkEntityNotNull(provider, id, isIdEmbeddedInURL(id));

        // Return the list of storage systems for the provider.
        StorageSystemList storageSystemsForProvider = new StorageSystemList();
        StringSet providerSystemURIStrs = provider.getStorageSystems();
        if (providerSystemURIStrs != null) {
            for (String providerSystemURIStr : providerSystemURIStrs) {
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                        URI.create(providerSystemURIStr));
                if (storageSystem != null) {
                    storageSystemsForProvider.getStorageSystems().add(toNamedRelatedResource(storageSystem));
                }
            }
        }

        return storageSystemsForProvider;
    }

    private StorageSystem prepareStorageSystem(StorageSystemSMISRequestParam param) throws DatabaseException {
        StorageSystem system = new StorageSystem();
        system.setId(URIUtil.createId(StorageSystem.class));
        system.setSystemType(param.getSystemType());
        system.setAutoDiscovered(false);
        system.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        system.setSerialNumber(param.getSerialNumber());
        if (StringUtils.isNotBlank(param.getIpAddress())) {
            system.setIpAddress(param.getIpAddress());
        }
        if (param.getSecondaryIPs() != null && !param.getSecondaryIPs().isEmpty()) {
            system.setSecondaryIPs(new StringSet(param.getSecondaryIPs()));
        }
        system.setPortNumber(param.getPortNumber());
        if (StringUtils.isNotBlank(param.getUserName())) {
            system.setUsername(param.getUserName());
        }
        if (StringUtils.isNotBlank(param.getPassword())) {
            system.setPassword(param.getPassword());
        }
        String nativeGuid = NativeGUIDGenerator.generateNativeGuid(param.getSystemType(),
                param.getSerialNumber());
        system.setNativeGuid(nativeGuid);
        if (param.getName() != null && !param.getName().equals("")) {
            system.setLabel(param.getName());
        }
        else {
            system.setLabel(nativeGuid);
        }
        _dbClient.createObject(system);
        _log.info("Created Storage System with Native Guid:" + system.getNativeGuid());

        return system;
    }

    private void updateStorageObj(StorageSystem system, StorageSystemSMISRequestParam param) {
        if (param.getIpAddress() != null && !param.getIpAddress().equals("")) {
            system.setIpAddress(param.getIpAddress());
        }
        if (param.getPortNumber() != null) {
            system.setPortNumber(param.getPortNumber());
        }
        if (param.getUserName() != null && !param.getUserName().equals("")) {
            system.setUsername(param.getUserName());
        }
        if (param.getPassword() != null && !param.getPassword().equals("")) {
            system.setPassword(param.getPassword());
        }
        if (param.getSecondaryIPs() != null) {
            system.setSecondaryIPs(new StringSet(param.getSecondaryIPs()));
        }
        if (param.getSerialNumber() != null && !param.getSerialNumber().equals("")) {
            String nativeGuid = NativeGUIDGenerator.generateNativeGuid(system.getSystemType(),
                    param.getSerialNumber());
            system.setNativeGuid(nativeGuid);
            system.setSerialNumber(param.getSerialNumber());
        }
        if (param.getName() != null && !param.getName().equals("")) {
            system.setLabel(param.getName());
        }

        _dbClient.persistObject(system);
    }

    /**
     * Allows the user to get data for the storage system with the passed system
     * id that is associated with the SMI-S provider with the passed provider
     * id.
     * <p>
     * The method is deprecated. Use /vdc/storage-providers/{id}/storage-systems/{systemId}
     * 
     * @param id the URN of a ViPR SMI-S provider
     * @param systemId The id of the storage system.
     * 
     * @brief Show SMI-S provider storage system
     * @return A StorageSystemRestRep reference specifying the data for the
     *         storage system.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/storage-systems/{systemId}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StorageSystemRestRep getStorageSystem(@PathParam("id") URI id,
            @PathParam("systemId") URI systemId) {

        // Validate the provider.
        ArgValidator.checkFieldUriType(id, StorageProvider.class, "id");
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, id);
        ArgValidator.checkEntityNotNull(provider, id, isIdEmbeddedInURL(id));

        // Return the storage system if found.
        StringSet providerSystemURIStrs = provider.getStorageSystems();
        if (providerSystemURIStrs != null) {
            for (String providerSystemURIStr : providerSystemURIStrs) {
                if (providerSystemURIStr.equals(systemId.toString())) {
                    StorageSystem storageSystem = _dbClient.queryObject(
                            StorageSystem.class, URI.create(providerSystemURIStr));
                    if (storageSystem != null) {
                        return map(storageSystem);
                    }
                    break;
                }
            }
        }

        throw APIException.notFound.unableToFindEntityInURL(id);
    }

    /**
     * Invoke connect storage. Once system is verified to be registered.
     * Statistics, Events will be collected for only registered systems.
     * 
     * @param system Storage system to start Metering & Monitoring.
     * @throws ControllerException
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void startStorageSystem(StorageSystem system) throws ControllerException {
        String systemType = system.getSystemType();
        Class controllerClass = StorageSystemService.storageSystemClass(systemType);
        StorageController controller = (StorageController) getController(controllerClass, systemType);
        controller.connectStorage(system.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * Retrieve resource representations based on input ids.
     * The method is deprecated. Use /vdc/storage-providers/bulk
     * 
     * @param param POST data containing the id list.
     * @brief List data of SMI-S provider resources
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public SMISProviderBulkRep getBulkResources(BulkIdParam param) {
        return (SMISProviderBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<StorageProvider> getResourceClass() {
        return StorageProvider.class;
    }

    @Override
    public SMISProviderBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<StorageProvider> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new SMISProviderBulkRep(BulkList.wrapping(_dbIterator, MapSmisProvider.getInstance()));
    }

    @Override
    public SMISProviderBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.SMIS_PROVIDER;
    }

}
