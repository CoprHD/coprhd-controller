/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.SystemsMapper.map;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
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

import com.emc.storageos.api.mapper.functions.MapStorageProvider;
import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorIntf;
import com.emc.storageos.api.service.impl.resource.utils.DiscoveredObjectTaskScheduler;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DecommissionedResource;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.smis.StorageProviderBulkRep;
import com.emc.storageos.model.smis.StorageProviderCreateParam;
import com.emc.storageos.model.smis.StorageProviderList;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.model.smis.StorageProviderUpdateParam;
import com.emc.storageos.model.smis.StorageSystemProviderRequestParam;
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
import com.emc.storageos.vplexcontroller.VPlexController;

/**
 * StorageProvider resource represents a thirdparty providers which are similar to SMIS Provider.
 * Currently the only provider implemented are HDS and SMIS
 * 1. Allows user to create/list StorageProvider managing block devices.
 * 2. User can register and deactivate StorageProvider.
 * 
 */
@Path("/vdc/storage-providers")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class StorageProviderService extends TaskResourceService {
    private static final Logger log = LoggerFactory.getLogger(StorageProviderService.class);
    private static final String EVENT_SERVICE_TYPE = "provider";

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
            return ResourceOperationTypeEnum.SCAN_STORAGEPROVIDER;
        }
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * @brief Show Storage provider
     *        This call allows user to fetch Storage Provider details such as provider
     *        host access credential details.
     * 
     * @param id Storage Provider Identifier
     * @return Storage Provider details.
     */

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StorageProviderRestRep getStorageProvider(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, StorageProvider.class, "id");
        StorageProvider mgmtProvider = queryResource(id);
        return map(mgmtProvider);
    }

    @Override
    protected StorageProvider queryResource(URI id) {
        return queryObject(StorageProvider.class, id, true);
    }

    /**
     * @brief List Storage providers
     *        This function allows user to fetch list of all Storage Providers information.
     * 
     * @return List of Storage Providers.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StorageProviderList getStorageProviderList() {
        List<URI> ids = _dbClient.queryByType(StorageProvider.class, true);
        List<StorageProvider> mgmtProviders = _dbClient.queryObject(StorageProvider.class, ids);
        if (mgmtProviders == null) {
            throw APIException.badRequests.unableToFindStorageProvidersForIds(ids);
        }
        StorageProviderList providerList = new StorageProviderList();
        for (StorageProvider provider : mgmtProviders) {
            providerList.getStorageProviders().add(toNamedRelatedResource(provider));
        }
        return providerList;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<StorageProvider> getResourceClass() {
        return StorageProvider.class;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.STORAGE_PROVIDER;
    }

    /**
     * Retrieve resource representations based on input ids.
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
    public StorageProviderBulkRep getBulkResources(BulkIdParam param) {
        return (StorageProviderBulkRep) super.getBulkResources(param);
    }

    @Override
    public StorageProviderBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    @Override
    public StorageProviderBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<StorageProvider> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new StorageProviderBulkRep(BulkList.wrapping(_dbIterator, MapStorageProvider.getInstance()));
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep registerStorageProvider(StorageProviderCreateParam param)
            throws ControllerException {
        ArgValidator.checkFieldNotEmpty(param.getName(), "name");
        checkForDuplicateName(param.getName(), StorageProvider.class);

        ArgValidator.checkFieldNotEmpty(param.getIpAddress(), "ip_address");
        ArgValidator.checkFieldNotNull(param.getPortNumber(), "port_number");
        ArgValidator.checkFieldNotEmpty(param.getUserName(), "user_name");
        ArgValidator.checkFieldNotEmpty(param.getPassword(), "password");
        ArgValidator.checkFieldRange(param.getPortNumber(), 1, 65535, "port_number");
        ArgValidator.checkFieldValueFromEnum(param.getInterfaceType(), "interface_type",
                StorageProvider.InterfaceType.class);
        String providerKey = param.getIpAddress() + "-" + param.getPortNumber();
        List<StorageProvider> providers = CustomQueryUtility.getActiveStorageProvidersByProviderId(_dbClient, providerKey);
        if (providers != null && !providers.isEmpty()) {
            throw APIException.badRequests.invalidParameterStorageProviderAlreadyRegistered(providerKey);
        }

        // Set the SSL parameter
        Boolean useSSL = param.getUseSSL();
        if (useSSL == null) {
            useSSL = StorageProviderCreateParam.USE_SSL_DEFAULT;
        }

        StorageProvider provider = new StorageProvider();
        provider.setId(URIUtil.createId(StorageProvider.class));
        provider.setLabel(param.getName());
        provider.setIPAddress(param.getIpAddress());
        provider.setPortNumber(param.getPortNumber());
        provider.setUserName(param.getUserName());
        provider.setPassword(param.getPassword());
        provider.setUseSSL(useSSL);
        provider.setInterfaceType(param.getInterfaceType());
        provider.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        provider.setConnectionStatus(ConnectionStatus.INITIALIZING.name());
        provider.setSecondaryUsername(param.getSecondaryUsername());
        provider.setSecondaryPassword(param.getSecondaryPassword());
        provider.setElementManagerURL(param.getElementManagerURL());
        if (param.getSioCLI() != null) {
            // TODO: Validate the input?
            provider.addKey(StorageProvider.GlobalKeys.SIO_CLI.name(), param.getSioCLI());
        }

        if (StorageProvider.InterfaceType.ibmxiv.name().equalsIgnoreCase(provider.getInterfaceType())) {
            provider.setManufacturer("IBM");
        }

        _dbClient.createObject(provider);

        auditOp(OperationTypeEnum.REGISTER_STORAGEPROVIDER, true, null,
                provider.getLabel(), provider.getId().toString(), provider.getIPAddress(),
                provider.getPortNumber(), provider.getUserName(), provider.getInterfaceType());

        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        String taskId = UUID.randomUUID().toString();
        tasks.add(new AsyncTask(StorageProvider.class, provider.getId(), taskId));

        BlockController controller = getController(BlockController.class, provider.getInterfaceType());
        log.debug("controller.getClass().getName() :{}", controller.getClass().getName());
        log.debug("controller.getClass().getSimpleName() :{}", controller.getClass().getSimpleName());
        /**
         * Creates MonitoringJob token for vnxblock/vmax, hds, cinder and IBM XIV device on zooKeeper queue
         */
        // TODO : If all interface types have monitoring impl class added (scaleIO is missing),
        // this check can be removed.
        String interfaceType = provider.getInterfaceType();
        if (StorageProvider.InterfaceType.hicommand.name().equalsIgnoreCase(interfaceType)
                || StorageProvider.InterfaceType.smis.name().equalsIgnoreCase(interfaceType)
                || StorageProvider.InterfaceType.cinder.name().equalsIgnoreCase(interfaceType)
                || StorageProvider.InterfaceType.ibmxiv.name().equalsIgnoreCase(interfaceType)) {
            controller.startMonitoring(new AsyncTask(StorageProvider.class, provider.getId(), taskId),
                    getSystemTypeByInterface(interfaceType));
        }

        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(_dbClient, new ScanJobExec(controller));
        TaskList taskList = scheduler.scheduleAsyncTasks(tasks);
        return taskList.getTaskList().listIterator().next();
    }

    private Type getSystemTypeByInterface(String interfaceType) {
        if (StorageProvider.InterfaceType.hicommand.name().equalsIgnoreCase(interfaceType)) {
            return StorageSystem.Type.hds;
        } else if (StorageProvider.InterfaceType.smis.name().equalsIgnoreCase(interfaceType)) {
            return StorageSystem.Type.vnxblock;
        } else if (StorageProvider.InterfaceType.cinder.name().equalsIgnoreCase(interfaceType)) {
            return StorageSystem.Type.openstack;
        } else if (StorageProvider.InterfaceType.ibmxiv.name().equalsIgnoreCase(interfaceType)) {
            return StorageSystem.Type.ibmxiv;
        }
        return null;
    }

    /**
     * Scan all Storage providers.
     * 
     * @brief Scan Storage providers
     * @return TasList of all created asynchronous tasks
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/scan")
    public TaskList scanStorageProviders() {
        TaskList taskList = new TaskList();
        List<URI> providerURIList = _dbClient.queryByType(StorageProvider.class, true);
        /**
         * TODO needs to remove hard code device type to fetch the controller instance
         */
        BlockController controller = getController(BlockController.class, "vnxblock");
        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(_dbClient, new ScanJobExec(controller));
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>();
        if (providerURIList != null) {
            for (URI providerURI : providerURIList) {
                String taskId = UUID.randomUUID().toString();
                tasks.add(new AsyncTask(StorageProvider.class, providerURI, taskId));
            }
            taskList = scheduler.scheduleAsyncTasks(tasks);
        }
        return taskList;
    }

    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteStorageProvider(@PathParam("id") URI id) {
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
                if (storageSys != null && !storageSys.getInactive() &&
                        storageSys.getProviders() != null && storageSys.getProviders().size() == 1) {
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

        auditOp(OperationTypeEnum.DELETE_STORAGEPROVIDER, true, null,
                provider.getId().toString(), provider.getLabel(), provider.getIPAddress(),
                provider.getPortNumber(), provider.getUserName(), provider.getInterfaceType());

        return Response.ok().build();
    }

    /**
     * Update the Storage Provider. This is useful when we move arrays to some other
     * provider.
     * 
     * @param id the URN of a ViPR Storage Provider
     * @brief Update Storage provider
     * @return Updated Storage Provider information.
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StorageProviderRestRep updateStorageProvider(@PathParam("id") URI id,
            StorageProviderUpdateParam param) {
        StorageProvider storageProvider = _dbClient.queryObject(StorageProvider.class,
                id);
        if (null == storageProvider || storageProvider.getInactive()) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        } else {
            /*
             * Usecase is not to remove the provider instead we can update the old storage provider with
             * new provider details.
             */
            if (param.getName() != null && !param.getName().equals("") && !param.getName().equalsIgnoreCase(storageProvider.getLabel())) {
                checkForDuplicateName(param.getName(), StorageProvider.class);
                storageProvider.setLabel(param.getName());
            }

            // if the ip or port passed are different from the existing one
            // check to ensure a provider does not exist with the new ip + port combo
            String existingIPAddress = storageProvider.getIPAddress();
            Integer existingPortNumber = storageProvider.getPortNumber();
            if ((param.getIpAddress() != null && !param.getIpAddress().equals(existingIPAddress)) ||
                    (param.getPortNumber() != null && !param.getPortNumber().equals(existingPortNumber))) {
                String ipAddress = (param.getIpAddress() != null) ? param.getIpAddress() : existingIPAddress;
                Integer portNumber = (param.getPortNumber() != null) ? param.getPortNumber() : existingPortNumber;
                ArgValidator.checkFieldRange(portNumber, 1, 65535, "port_number");

                String providerKey = ipAddress + "-" + portNumber;
                List<StorageProvider> providers = CustomQueryUtility.getActiveStorageProvidersByProviderId(_dbClient, providerKey);
                if (providers != null && !providers.isEmpty()) {
                    throw APIException.badRequests.invalidParameterStorageProviderAlreadyRegistered(providerKey);
                }

                // User can update IP address for an existing Provider object
                // if and only if the connection with old IP is not alive.
                if (!existingIPAddress.equals(param.getIpAddress()) &&
                        isOldConnectionAlive(existingIPAddress, existingPortNumber, storageProvider.getInterfaceType())
                        && (storageProvider.getStorageSystems() != null && !storageProvider.getStorageSystems().isEmpty())) {
                    throw APIException.badRequests.cannotUpdateProviderIP(existingIPAddress + "-" + existingPortNumber);
                }

                storageProvider.setIPAddress(ipAddress);
                storageProvider.setPortNumber(portNumber);
            }
            if (param.getUserName() != null && StringUtils.isNotBlank(param.getUserName())) {
                storageProvider.setUserName(param.getUserName());
            }
            if (param.getPassword() != null && StringUtils.isNotBlank(param.getPassword())) {
                storageProvider.setPassword(param.getPassword());
            }
            if (param.getUseSSL() != null) {
                storageProvider.setUseSSL(param.getUseSSL());
            }

            if (param.getInterfaceType() != null) {
                ArgValidator.checkFieldValueFromEnum(param.getInterfaceType(), "interface_type", EnumSet.of(
                        StorageProvider.InterfaceType.hicommand, StorageProvider.InterfaceType.smis,
                        StorageProvider.InterfaceType.ibmxiv, StorageProvider.InterfaceType.scaleioapi, 
                        StorageProvider.InterfaceType.xtremio, StorageProvider.InterfaceType.ddmc));
                storageProvider.setInterfaceType(param.getInterfaceType());
            }

            if (param.getSecondaryUsername() != null) {
                storageProvider.setSecondaryUsername(param.getSecondaryUsername());
            }
            if (param.getSecondaryPassword() != null) {
                storageProvider.setSecondaryPassword(param.getSecondaryPassword());
            }
            if (param.getElementManagerURL() != null) {
                storageProvider.setElementManagerURL(param.getElementManagerURL());
            }

            _dbClient.persistObject(storageProvider);
        }

        auditOp(OperationTypeEnum.UPDATE_STORAGEPROVIDER, true, null,
                storageProvider.getId().toString(), storageProvider.getLabel(), storageProvider.getIPAddress(),
                storageProvider.getPortNumber(), storageProvider.getUserName(), storageProvider.getInterfaceType());

        return map(storageProvider);
    }

    private boolean isOldConnectionAlive(String ipAddress,
            Integer portNumber, String interfaceType) {
        log.info("Validating {} storage provider connection at {}.", interfaceType, ipAddress);
        if (StorageProvider.InterfaceType.vplex.name().equals(interfaceType)) {
            VPlexController controller =
                    getController(VPlexController.class, DiscoveredDataObject.Type.vplex.toString());
            return controller.validateStorageProviderConnection(ipAddress, portNumber);
        } else {
            BlockController controller = getController(BlockController.class, "vnxblock");
            return controller.validateStorageProviderConnection(ipAddress, portNumber, interfaceType);
        }
    }

    /**
     * Get zone role assignments
     * 
     * @brief List zone role assignments
     * @return Role assignment details
     */
    @GET
    @Path("/deactivated-systems")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
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
     * 
     * @param id the URN of a ViPR Storage provider
     * 
     * @brief List Storage provider storage systems
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

    /**
     * Allows the user to remove a storage system from the list of decommisioned resources
     * After that corresponding provider should be able to be rescanned and add this system back to the list of managed systems.
     * 
     * @param id id the URN of a ViPR Storage provider
     * @param param The storage system details.
     * 
     * @brief removes the storage system from the list of decommissioned systems and rescans the provider.
     * @return An asynchronous task corresponding to the scan job scheduled for the provider.
     * 
     * @throws BadRequestException When the system type is not valid or a
     *             storage system with the same native guid already exists.
     * @throws com.emc.storageos.db.exceptions.DatabaseException When an error occurs querying the database.
     * @throws ControllerException When an error occurs discovering the storage
     *             system.
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Path("/{id}/storage-systems")
    public TaskResourceRep addStorageSystem(@PathParam("id") URI id,
            StorageSystemProviderRequestParam param) throws ControllerException {
        TaskResourceRep taskRep;
        URIQueryResultList list = new URIQueryResultList();

        ArgValidator.checkFieldNotEmpty(param.getSystemType(), "system_type");
        if (!StorageSystem.Type.isProviderStorageSystem(param.getSystemType())) {
            throw APIException.badRequests.cannotAddStorageSystemTypeToStorageProvider(param.getSystemType());
        }

        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, id);
        ArgValidator.checkEntityNotNull(provider, id, isIdEmbeddedInURL(id));

        ArgValidator.checkFieldNotEmpty(param.getSerialNumber(), "serialNumber");

        String nativeGuid = NativeGUIDGenerator.generateNativeGuid(param.getSystemType(),
                param.getSerialNumber());
        // check for duplicate StorageSystem.

        List<StorageSystem> systems = CustomQueryUtility.getActiveStorageSystemByNativeGuid(_dbClient, nativeGuid);
        if (systems != null && !systems.isEmpty()) {
            throw APIException.badRequests.invalidParameterProviderStorageSystemAlreadyExists("nativeGuid", nativeGuid);
        }

        int cleared = DecommissionedResource.removeDecommissionedFlag(_dbClient, nativeGuid, StorageSystem.class);
        if (cleared == 0) {
            log.info("Cleared {} decommissioned systems", cleared);
        }
        else {
            log.info("Did not find any decommissioned systems to clear. Continue to scan.");
        }

        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        String taskId = UUID.randomUUID().toString();
        tasks.add(new AsyncTask(StorageProvider.class, provider.getId(), taskId));

        BlockController controller = getController(BlockController.class, provider.getInterfaceType());
        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(_dbClient, new ScanJobExec(controller));
        TaskList taskList = scheduler.scheduleAsyncTasks(tasks);
        return taskList.getTaskList().listIterator().next();
    }

    /**
     * Allows the user to get data for the storage system with the passed system
     * id that is associated with the storage provider with the passed provider
     * id.
     * 
     * 
     * @param id the URN of a ViPR Storage provider
     * @param systemId The id of the storage system.
     * 
     * @brief Show Storage provider storage system
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
        ArgValidator.checkFieldUriType(systemId, StorageSystem.class, "id");

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

}
