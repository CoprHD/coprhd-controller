/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.addAutoTierPolicy;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;
import static com.emc.storageos.api.mapper.SystemsMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.functions.MapStoragePort;
import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorIntf;
import com.emc.storageos.api.service.impl.resource.utils.DiscoveredObjectTaskScheduler;
import com.emc.storageos.api.service.impl.resource.utils.PurgeRunnable;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.DecommissionedResource;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.OperationalStatus;
import com.emc.storageos.db.client.model.StoragePort.PortType;
import com.emc.storageos.db.client.model.StoragePort.TransportType;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObject.ExportType;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.UnManagedVolumeList;
import com.emc.storageos.model.block.tier.AutoTierPolicyList;
import com.emc.storageos.model.file.UnManagedFileSystemList;
import com.emc.storageos.model.pools.StoragePoolList;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.ports.StoragePortList;
import com.emc.storageos.model.ports.StoragePortRequestParam;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.rdfgroup.RDFGroupList;
import com.emc.storageos.model.rdfgroup.RDFGroupRestRep;
import com.emc.storageos.model.systems.StorageSystemBulkRep;
import com.emc.storageos.model.systems.StorageSystemConnectivityList;
import com.emc.storageos.model.systems.StorageSystemList;
import com.emc.storageos.model.systems.StorageSystemRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.systems.StorageSystemUpdateRequestParam;
import com.emc.storageos.model.vnas.VirtualNASList;
import com.emc.storageos.protectioncontroller.RPController;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileController;
import com.emc.storageos.volumecontroller.ObjectController;
import com.emc.storageos.volumecontroller.StorageController;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.cinder.CinderUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.google.common.base.Function;

@Path("/vdc/storage-systems")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class StorageSystemService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(StorageSystemService.class);
    private static final String EVENT_SERVICE_TYPE = "StorageSystem";

    protected static final String PORT_EVENT_SERVICE_SOURCE = "StoragePortService";
    private static final String PORT_EVENT_SERVICE_TYPE = "storageport";
    protected static final String STORAGEPORT_REGISTERED_DESCRIPTION = "Storage Port Registered";

    protected static final String POOL_EVENT_SERVICE_SOURCE = "StoragePoolService";
    private static final String POOL_EVENT_SERVICE_TYPE = "storagepool";
    protected static final String STORAGEPOOL_REGISTERED_DESCRIPTION = "Storage Pool Registered";

    private static final String TRUE_STR = "true";

    private static final String FALSE_STR = "false";

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private RPHelper rpHelper;

    @Autowired
    private PortMetricsProcessor portMetricsProcessor;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    // how many times to retry a procedure before returning failure to the user.
    // Is used with "system delete" operation.
    private int _retry_attempts;

    private static class DiscoverJobExec implements AsyncTaskExecutorIntf {

        private final StorageController _controller;

        DiscoverJobExec(StorageController controller) {
            _controller = controller;
        }

        @Override
        public void executeTasks(AsyncTask[] tasks) throws ControllerException {
            _controller.discoverStorageSystem(tasks);
        }

        @Override
        public ResourceOperationTypeEnum getOperation() {
            return ResourceOperationTypeEnum.DISCOVER_STORAGE_SYSTEM;
        }
    }

    public void setRetryAttempts(int retries) {
        _retry_attempts = retries;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * Gets the storage system with the passed id from the database.
     * 
     * @param id the URN of a ViPR storage system
     * 
     * @return A reference to StorageSystem.
     * 
     * @throws EntityNotFoundException When the storage system is not
     *             found.
     */
    @Override
    protected StorageSystem queryResource(URI id) {
        ArgValidator.checkUri(id);
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, id);
        ArgValidator.checkEntityNotNull(system, id, isIdEmbeddedInURL(id));
        return system;
    }

    /**
     * Gets the storage system with the passed id from the database.
     * 
     * @param id the URN of a ViPR storage system
     * 
     * @return A reference to the registered StorageSystem.
     * 
     * @throws ServiceCodeException When the storage system is not
     *             registered.
     */
    protected StorageSystem queryRegisteredSystem(URI id) {
        ArgValidator.checkUri(id);
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, id);
        ArgValidator.checkEntityNotNull(system, id, isIdEmbeddedInURL(id));
        if (!RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                system.getRegistrationStatus())) {
            throw APIException.badRequests.resourceNotRegistered(StorageSystem.class.getSimpleName(), id);
        }
        return system;
    }

    /**
     * Manually create a storage system that cannot be discovered using a SMI-S provider. By
     * default the storage system will be auto-registered upon its creation.
     * For the Block type storage system, the method would add a new system to the SMIS provider.
     * The SMIS provider field in the input parameter file is ignored for file type storage systems
     * (VNX file and Isilon )
     * 
     * @param param The storage system details.
     * @prereq none
     * @brief Create storage system
     * @return An asynchronous task corresponding to the discovery job scheduled for the new Storage System.
     * 
     * @throws BadRequestException When the system type is not valid or a
     *             storage system with the same native guid already exists.
     * @throws DatabaseException When an error occurs querying the database.
     * @throws ControllerException When an error occurs discovering the storage
     *             system.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep createStorageSystem(StorageSystemRequestParam param) throws Exception {

        ArgValidator.checkFieldNotEmpty(param.getSystemType(), "system_type");
        ArgValidator.checkFieldValueFromEnum(param.getSystemType(), "system_type", EnumSet.of(
                StorageSystem.Type.vnxfile, StorageSystem.Type.isilon, StorageSystem.Type.rp,
                StorageSystem.Type.netapp, StorageSystem.Type.netappc, StorageSystem.Type.vnxe,
                StorageSystem.Type.xtremio, StorageSystem.Type.ecs));
        StorageSystem.Type systemType = StorageSystem.Type.valueOf(param.getSystemType());
        if (systemType.equals(StorageSystem.Type.vnxfile)) {
            validateVNXFileSMISProviderMandatoryDetails(param);
        }
        ArgValidator.checkFieldNotEmpty(param.getName(), "name");
        checkForDuplicateName(param.getName(), StorageSystem.class);

        ArgValidator.checkFieldValidIP(param.getIpAddress(), "ip_address");
        ArgValidator.checkFieldNotNull(param.getPortNumber(), "port_number");
        ArgValidator.checkFieldRange(param.getPortNumber(), 1, 65535, "port_number");
        validateStorageSystemExists(param.getIpAddress(), param.getPortNumber());

        StorageSystem system = prepareStorageSystem(param);

        auditOp(OperationTypeEnum.CREATE_STORAGE_SYSTEM, true, null, param.getSerialNumber(),
                param.getSystemType(), param.getIpAddress(), param.getPortNumber());

        startStorageSystem(system);

        // Rather if else everywhere some code duplication with object and file
        if (StorageSystem.Type.ecs.toString().equals(system.getSystemType())) {
            ObjectController controller = getController(ObjectController.class, param.getSystemType());
            ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
            String taskId = UUID.randomUUID().toString();
            tasks.add(new AsyncTask(StorageSystem.class, system.getId(), taskId));

            TaskList taskList = discoverStorageSystems(tasks, controller);
            return taskList.getTaskList().listIterator().next();

        } else {
            FileController controller = getController(FileController.class, param.getSystemType());
            ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
            String taskId = UUID.randomUUID().toString();
            tasks.add(new AsyncTask(StorageSystem.class, system.getId(), taskId));
            /**
             * Creates MonitoringJob token on ZooKeeper for vnxfile/isilon device.
             * Currently we are handling monitoring for vnxfile/vmax/vnxblock/isilon devices.
             * We should not create MonitoringJob token for netapp/rp now.
             */
            if (StorageSystem.Type.vnxfile.toString().equals(system.getSystemType()) ||
                    StorageSystem.Type.isilon.toString().equals(system.getSystemType())) {
                controller.startMonitoring(new AsyncTask(StorageSystem.class, system.getId(), taskId),
                        StorageSystem.Type.valueOf(system.getSystemType()));
            }

            TaskList taskList = discoverStorageSystems(tasks, controller);
            return taskList.getTaskList().listIterator().next();
        }
    }

    /**
     * Validates SMI-S Provider attributes of the vnxFile as it is a mandatory fields for indications
     * 
     * @param param
     */
    private void validateVNXFileSMISProviderMandatoryDetails(StorageSystemRequestParam param) {
        ArgValidator.checkFieldValidIP(param.getSmisProviderIP(), "smis_provider_ip");
        ArgValidator.checkFieldNotNull(param.getSmisPortNumber(), "smis_port_number");
        ArgValidator.checkFieldRange(param.getSmisPortNumber(), 1, 65535, "smis_port_number");
        ArgValidator.checkFieldNotEmpty(param.getSmisUserName(), "smis_user_name");
        ArgValidator.checkFieldNotEmpty(param.getSmisPassword(), "smis_password");
    }

    /**
     * Validates SMI-S Provider attributes of the vnxFile as it is a mandatory fields for indications
     * 
     * @param param
     */
    private void validateVNXFileSMISProviderMandatoryDetails(StorageSystemUpdateRequestParam param) {
        /**
         * We need to validate only non-null attributes passed by client.
         * Because while doing update client can try to update one among all existing mandatory fields.
         */
        if (param.getSmisProviderIP() != null) {
            ArgValidator.checkFieldValidIP(param.getSmisProviderIP(), "smis_provider_ip");
        }
        if (param.getSmisUserName() != null) {
            ArgValidator.checkFieldNotEmpty(param.getSmisUserName(), "smis_user_name");
        }
        if (param.getSmisPassword() != null) {
            ArgValidator.checkFieldNotEmpty(param.getSmisPassword(), "smis_password");
        }
        if (param.getSmisPortNumber() != null) {
            ArgValidator.checkFieldRange(param.getSmisPortNumber(), 1, 65535, "smis_port_number");
        }
    }

    /**
     * Validates a storage system if it already exists for same ipaddress & portNumber
     * 
     * @param ipAddress
     * @param portNumber
     */
    private void validateStorageSystemExists(String ipAddress, Integer portNumber) {
        String systemUniqueKey = ipAddress + "-" + portNumber;
        List<StorageSystem> systems = CustomQueryUtility.getActiveStorageSystemByMgmAccessId(_dbClient, systemUniqueKey);
        if (systems != null && !systems.isEmpty()) {
            throw APIException.badRequests.invalidParameterProviderStorageSystemAlreadyExists("mgmtAccessPoint", systemUniqueKey);
        }
    }

    /**
     * 
     * Remove a storage system. The method would remove the storage system from the
     * system control and will remove all resources associated with the storage system from the database.
     * Note that resources (pools, ports, volumes, etc.) are not removed from the storage system physically,
     * but become unavailable for the user.
     * 
     * @param id the URN of a ViPR storage system
     * @prereq none
     * @brief Remove a storage system
     * @return An asynchronous task.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */

    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep deleteStorageSystem(@PathParam("id") URI id) throws DatabaseException

    {
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, id);
        ArgValidator.checkEntityNotNull(system, id, isIdEmbeddedInURL(id));

        if (!RegistrationStatus.UNREGISTERED.toString().equals(system.getRegistrationStatus())) {
            throw APIException.badRequests.cannotDeactivateStorageSystem();
        }

        // Ensure the storage system has no active RecoverPoint volumes under management.
        if (rpHelper.containsActiveRpVolumes(id)) {
            throw APIException.badRequests.cannotDeactivateStorageSystemActiveRpVolumes();
        }

        if (DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS.toString().equals(system.getDiscoveryStatus())
                || DiscoveredDataObject.DataCollectionJobStatus.SCHEDULED.toString().equals(system.getDiscoveryStatus())) {
            throw APIException.serviceUnavailable.cannotDeactivateStorageSystemWhileInDiscover(system.getId());
        }

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(StorageSystem.class, system.getId(),
                taskId, ResourceOperationTypeEnum.DELETE_STORAGE_SYSTEM);

        if (StringUtils.isNotBlank(system.getNativeGuid()) && system.storageSystemHasProvider()) {
            DecommissionedResource oldStorage = null;
            List<URI> oldResources = _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getDecommissionedResourceIDConstraint(id
                    .toString()));
            if (oldResources != null)
            {
                List<DecommissionedResource> objects = _dbClient.queryObject(DecommissionedResource.class, oldResources);
                for (DecommissionedResource decomObj : objects) {
                    if (!decomObj.getInactive()) {
                        oldStorage = decomObj;
                        break;
                    }
                }
            }
            if (oldStorage == null) {
                oldStorage = new DecommissionedResource();
                oldStorage.setNativeGuid(system.getNativeGuid());
                oldStorage.setType(TypeMap.getCFName(StorageSystem.class));
                oldStorage.setUser(getUserFromContext().getName());
                oldStorage.setDecommissionedId(system.getId());
                oldStorage.setLabel(system.getLabel());
                oldStorage.setId(URIUtil.createId(DecommissionedResource.class));
                _dbClient.createObject(oldStorage);
            }
            if (system.getActiveProviderURI() != null) {
                StorageProvider provider = _dbClient.queryObject(StorageProvider.class, system.getActiveProviderURI());
                if (provider != null) {
                    StringSet providerDecomSys = new StringSet();
                    providerDecomSys.add(oldStorage.getId().toString());
                    provider.setDecommissionedSystems(providerDecomSys);
                    _dbClient.persistObject(provider);
                }
            }
        }

        PurgeRunnable.executePurging(_dbClient, _dbPurger,
                _asynchJobService.getExecutorService(), system,
                _retry_attempts, taskId, 60);
        return toTask(system, taskId, op);
    }

    /**
     * Allows the user to update credentials for a manually created storage systems.
     * Allows the user to update only the name field for vmax and vnx block systems.
     * 
     * @param id the URN of a ViPR storage system
     * @param param The storage system details to update.
     * 
     * @brief Update storage system credentials
     * @return A StorageSystemRestRep reference specifying the storage system
     *         data.
     * 
     * @throws BadRequestException When the system is not valid.
     * @throws ControllerException When an error occurs discovering the storage
     *             system.
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep updateStorageSystem(@PathParam("id") URI id,
            StorageSystemUpdateRequestParam param)
            throws ControllerException {
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, id);
        ArgValidator.checkEntity(system, id, isIdEmbeddedInURL(id));

        StorageSystem.Type systemType = StorageSystem.Type.valueOf(system.getSystemType());

        if (param.getName() != null && !param.getName().isEmpty() && !param.getName().equalsIgnoreCase(system.getLabel())) {
            checkForDuplicateName(param.getName(), StorageSystem.class);
            system.setLabel(param.getName());
        }

        // If unlimited resources is specified and set to true, then no need to look at max resources
        // If unlimited resources is set to false, then max resources should also be specified. If not specified, throw error
        if (null != param.getIsUnlimitedResourcesSet()) {
            if (param.getIsUnlimitedResourcesSet()) {
                system.setIsResourceLimitSet(false);
            } else {
                if (null != param.getMaxResources()) {
                    system.setIsResourceLimitSet(true);
                    system.setMaxResources(param.getMaxResources());
                } else {
                    throw APIException.badRequests.parameterMaxResourcesMissing();
                }
            }

        } else if (null != param.getMaxResources()) {
            system.setMaxResources(param.getMaxResources());
            system.setIsResourceLimitSet(true);
        }

        // if system type is vmax, vnxblock, hds, openstack, scaleio or xtremio, update the name or max_resources field alone.
        // create Task with ready state and return it. Discovery not needed.
        if (systemType.equals(StorageSystem.Type.vmax) || systemType.equals(StorageSystem.Type.vnxblock)
                || systemType.equals(StorageSystem.Type.hds) || systemType.equals(StorageSystem.Type.openstack)
                || systemType.equals(StorageSystem.Type.scaleio) || systemType.equals(StorageSystem.Type.xtremio)) {
            // this check is to inform the user that he/she can not update fields other than name and max_resources.
            if (param.getIpAddress() != null || param.getPortNumber() != null || param.getUserName() != null ||
                    param.getPassword() != null || param.getSmisProviderIP() != null || param.getSmisPortNumber() != null ||
                    param.getSmisUserName() != null || param.getSmisPassword() != null || param.getSmisUseSSL() != null) {
                throw APIException.badRequests.onlyNameAndMaxResourceCanBeUpdatedForSystemWithType(systemType.name());
            }
            _dbClient.persistObject(system);

            String taskId = UUID.randomUUID().toString();
            TaskList taskList = new TaskList();
            Operation op = new Operation();
            op.ready("Updated Storage System name");
            op.setResourceType(ResourceOperationTypeEnum.UPDATE_STORAGE_SYSTEM);
            _dbClient.createTaskOpStatus(StorageSystem.class, system.getId(), taskId, op);

            taskList.getTaskList().add(toTask(system, taskId, op));
            return taskList.getTaskList().listIterator().next();
        }

        if (systemType.equals(StorageSystem.Type.vnxfile)) {
            validateVNXFileSMISProviderMandatoryDetails(param);
        }

        String existingIPAddress = system.getIpAddress();
        Integer existingPortNumber = system.getPortNumber();

        // if the ip or port passed are different from the existing system
        // check to ensure a system does not exist with the new ip + port combo
        if (((param.getIpAddress() != null && !param.getIpAddress().equals(existingIPAddress)) || (param.getPortNumber() != null && !param
                .getPortNumber().equals(existingPortNumber)))) {

            String ipAddress = (param.getIpAddress() != null) ? param.getIpAddress() : system.getIpAddress();
            Integer portNumber = (param.getPortNumber() != null) ? param.getPortNumber() : system.getPortNumber();
            ArgValidator.checkFieldValidIP(ipAddress, "ip_address");
            ArgValidator.checkFieldRange(portNumber, 1, 65535, "port_number");
            validateStorageSystemExists(ipAddress, portNumber);
            system.setMgmtAccessPoint(ipAddress + "-" + portNumber);
        }

        updateStorageObj(system, param);

        auditOp(OperationTypeEnum.UPDATE_STORAGE_SYSTEM, true, null,
                id.toString(), param.getIpAddress(), param.getPortNumber());

        startStorageSystem(system);

        // execute discovery
        StorageController controller = getController(FileController.class,
                system.getSystemType());
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        String taskId = UUID.randomUUID().toString();
        tasks.add(new AsyncTask(StorageSystem.class, system.getId(), taskId));

        TaskList taskList = discoverStorageSystems(tasks, controller);
        return taskList.getTaskList().listIterator().next();
    }

    private StorageSystem prepareStorageSystem(StorageSystemRequestParam param) throws DatabaseException {
        StorageSystem system = new StorageSystem();
        system.setId(URIUtil.createId(StorageSystem.class));
        system.setSystemType(param.getSystemType());
        system.setAutoDiscovered(false);
        system.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        system.setIpAddress(param.getIpAddress());
        system.setPortNumber(param.getPortNumber());
        system.setMgmtAccessPoint(param.getIpAddress() + "-" + param.getPortNumber());
        system.setUsername(param.getUserName());
        system.setPassword(param.getPassword());
        system.setLabel(param.getName());
        system.setSmisProviderIP(param.getSmisProviderIP());
        system.setSmisPortNumber(param.getSmisPortNumber());
        system.setSmisUserName(param.getSmisUserName());
        system.setSmisPassword(param.getSmisPassword());
        system.setSmisUseSSL(param.getSmisUseSSL());

        _dbClient.createObject(system);
        _log.info("Created Storage System with Native Guid:" + system.getNativeGuid());
        return system;
    }

    private void updateStorageObj(StorageSystem system, StorageSystemUpdateRequestParam param) {
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
        if (param.getName() != null && !param.getName().equals("")) {
            system.setLabel(param.getName());
        }
        if (param.getSmisUseSSL() != null) {
            system.setSmisUseSSL(param.getSmisUseSSL());
        }
        system.setSmisProviderIP(param.getSmisProviderIP());
        system.setSmisPortNumber(param.getSmisPortNumber());
        system.setSmisUserName(param.getSmisUserName());
        system.setSmisPassword(param.getSmisPassword());

        _dbClient.persistObject(system);
    }

    /**
     * Allows the user to manually discover all storage systems.
     * 
     * @brief Discover all storage systems
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/discover")
    public TaskList discoverStorageSystemsAll() {

        Iterator<URI> storageIter = _dbClient.queryByType(StorageSystem.class, true).iterator();
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>();
        while (storageIter.hasNext()) {
            URI storage = storageIter.next();
            String taskId = UUID.randomUUID().toString();
            tasks.add(new AsyncTask(StorageSystem.class, storage, taskId));
        }

        BlockController controller = getController(BlockController.class, "vnxblock");

        return discoverStorageSystems(tasks, controller);
    }

    private TaskList discoverStorageSystems(List<AsyncTask> storageTasks,
            StorageController controller) {
        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(_dbClient, new DiscoverJobExec(controller));
        return scheduler.scheduleAsyncTasks(storageTasks);
    }

    /**
     * Allows the user to manually discover the registered storage system with
     * the passed id.
     * 
     * @param id the URN of a ViPR storage system.
     * @QueryParam namespace
     *             StorageSystem Auto Discovery is grouped into multiple namespaces.
     *             Namespace is used to discover specific parts of Storage System.
     * 
     *             Possible Values :
     *             UNMANAGED_VOLUMES
     *             UNMANAGED_FIESYSTEMS
     *             ALL
     * 
     *             UNMANAGED_VOLUMES will discover all the Volumes which are present in the Array,
     *             and only supported on vmax and vnxblock.
     *             Using UNMANAGED_VOLUMES Namespace in other system types would result in error.
     * 
     *             UNMANAGED_FILESYSTEMS will discover all the fileystems which are present in the Array,
     *             and only supported on netapp.
     * 
     *             Using UNMANAGED_FILESYSTEMS Namespace in other system types would result in error.
     * 
     * @brief Discover storage system
     * @throws ControllerException When an error occurs discovering the storage
     *             system.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/{id}/discover")
    public TaskResourceRep discoverSystem(@PathParam("id") URI id,
            @QueryParam("namespace") String namespace) {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, id);
        ArgValidator.checkEntity(storageSystem, id, isIdEmbeddedInURL(id), true);
        String deviceType = storageSystem.getSystemType();
        // If Namespace is empty or null set it to ALL as default
        if (namespace == null || namespace.trim().length() < 1) {
            namespace = Discovery_Namespaces.ALL.toString();
        }

        if (!validateNameSpace(namespace, storageSystem)) {
            throw APIException.badRequests.invalidParameterStorageSystemNamespace(namespace);
        }

        // check Storage system's compatibility for unmanaged resource discovery.
        // Trigger unmanaged resource discovery only when system is compatible.
        if ((Discovery_Namespaces.UNMANAGED_VOLUMES.name().equalsIgnoreCase(namespace) ||
                Discovery_Namespaces.BLOCK_SNAPSHOTS.name().equalsIgnoreCase(namespace) ||
                Discovery_Namespaces.UNMANAGED_FILESYSTEMS.name().equalsIgnoreCase(namespace)) &&
                !CompatibilityStatus.COMPATIBLE.name().equalsIgnoreCase(storageSystem.getCompatibilityStatus())) {
            throw APIException.badRequests.cannotDiscoverUnmanagedResourcesForUnsupportedSystem();
        }

        BlockController controller = getController(BlockController.class, deviceType);
        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(
                _dbClient, new DiscoverJobExec(controller));
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        String taskId = UUID.randomUUID().toString();
        tasks.add(new AsyncTask(StorageSystem.class, storageSystem.getId(), taskId,
                namespace));
        TaskList taskList = scheduler.scheduleAsyncTasks(tasks);
        return taskList.getTaskList().listIterator().next();
    }

    /**
     * Gets the id, name, and self link for all registered storage systems.
     * 
     * @brief List storage systems
     * @return A reference to a StorageSystemList.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StorageSystemList getStorageSystems() {
        StorageSystemList systemsList = new StorageSystemList();

        List<URI> ids = _dbClient.queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> iter = _dbClient.queryIterativeObjects(StorageSystem.class, ids);
        while (iter.hasNext()) {
            systemsList.getStorageSystems().add(toNamedRelatedResource(iter.next()));
        }
        return systemsList;
    }

    /**
     * Get information about the registered storage system with the passed id.
     * 
     * @param id the URN of a ViPR storage system.
     * 
     * @brief Show storage system
     * @return A reference to a StorageSystemRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StorageSystemRestRep getStorageSystem(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        StorageSystem system = queryResource(id);
        StorageSystemRestRep restRep = map(system);
        restRep.setNumResources(getNumResources(system, _dbClient));
        return restRep;
    }

    /**
     * Allows the user register the storage system with the passed id.
     * 
     * @param id the URN of a ViPR storage system.
     * 
     * @brief Register storage system
     * @return A StorageSystemRestRep reference specifying the data for the
     *         updated storage system.
     * @throws ControllerException
     * 
     * 
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/register")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StorageSystemRestRep registerStorageSystem(@PathParam("id") URI id) throws ControllerException {

        // Validate the storage system.
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, id);
        ArgValidator.checkEntity(storageSystem, id, isIdEmbeddedInURL(id));

        // If not already registered, register it now.
        if (RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(
                storageSystem.getRegistrationStatus())) {
            storageSystem.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
            _dbClient.persistObject(storageSystem);
            startStorageSystem(storageSystem);
            auditOp(OperationTypeEnum.REGISTER_STORAGE_SYSTEM, true, null,
                    storageSystem.getId().toString(), id.toString());
        }

        // Register all Pools.
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePoolConstraint(id),
                storagePoolURIs);
        Iterator<URI> storagePoolIter = storagePoolURIs.iterator();
        List<StoragePool> registeredPools = new ArrayList<StoragePool>();
        while (storagePoolIter.hasNext()) {
            StoragePool pool = _dbClient.queryObject(StoragePool.class, storagePoolIter.next());
            if (pool.getInactive() ||
                    DiscoveredDataObject.RegistrationStatus.REGISTERED.toString().equals(pool.getRegistrationStatus())) {
                continue;
            }
            registerStoragePool(pool);
            registeredPools.add(pool);
        }

        // Register all Ports.
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(id),
                storagePortURIs);
        Iterator<URI> storagePortIter = storagePortURIs.iterator();
        while (storagePortIter.hasNext()) {
            StoragePort port = _dbClient.queryObject(StoragePort.class, storagePortIter.next());
            if (port.getInactive() ||
                    DiscoveredDataObject.RegistrationStatus.REGISTERED.toString().equals(port.getRegistrationStatus())) {
                continue;
            }
            registerStoragePort(port);
        }
        // Pool registration also update its varray relationship, so, we should also update vpool to pool relation.
        ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVirtualPool(registeredPools, _dbClient, _coordinator);
        return map(storageSystem);
    }

    /**
     * Allows the user register the storage system with the passed id.
     * 
     * @param id the URN of a ViPR storage system.
     * 
     * @brief Deregister storage system
     * @return A StorageSystemRestRep reference specifying the data for the
     *         updated storage system.
     * @throws ControllerException
     * 
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deregister")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StorageSystemRestRep deregisterStorageSystem(@PathParam("id") URI id) throws ControllerException {

        // Validate the storage system.
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, id);
        ArgValidator.checkEntity(storageSystem, id, isIdEmbeddedInURL(id));

        if (!RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(
                storageSystem.getRegistrationStatus())) {
            storageSystem.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
            _dbClient.persistObject(storageSystem);
            stopStorageSystem(storageSystem);
        }

        // Deregister all Pools.
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePoolConstraint(id),
                storagePoolURIs);
        Iterator<URI> storagePoolIter = storagePoolURIs.iterator();
        List<StoragePool> modifiedPools = new ArrayList<StoragePool>();
        while (storagePoolIter.hasNext()) {
            StoragePool pool = _dbClient.queryObject(StoragePool.class, storagePoolIter.next());
            modifiedPools.add(pool);
            if (pool.getInactive() ||
                    DiscoveredDataObject.RegistrationStatus.UNREGISTERED.toString().equals(pool.getRegistrationStatus())) {
                continue;
            }
            // Setting status to UNREGISTERED.
            pool.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
            _dbClient.persistObject(pool);
            auditOp(OperationTypeEnum.DEREGISTER_STORAGE_POOL, true, null, id.toString());
        }

        // Deregister all Ports.
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(id),
                storagePortURIs);
        Iterator<URI> storagePortIter = storagePortURIs.iterator();
        while (storagePortIter.hasNext()) {
            StoragePort port = _dbClient.queryObject(StoragePort.class, storagePortIter.next());
            if (port.getInactive() ||
                    DiscoveredDataObject.RegistrationStatus.UNREGISTERED.toString().equals(port.getRegistrationStatus())) {
                continue;
            }
            // Setting status to UNREGISTERED.
            port.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
            _dbClient.persistObject(port);
            auditOp(OperationTypeEnum.DEREGISTER_STORAGE_PORT, true, null, port.getLabel(), port.getId().toString());
        }

        ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVirtualPool(modifiedPools, _dbClient, _coordinator);
        auditOp(OperationTypeEnum.DEREGISTER_STORAGE_SYSTEM, true, null,
                storageSystem.getId().toString(), id.toString());

        return map(storageSystem);
    }

    /**
     * Get information about the connectivity of the registered protection system with the passed id.
     * 
     * @param id the URN of a ViPR protection system.
     * 
     * @brief Show registered protection system connectivity
     * @return A StorageSystemConnectivityRestRep object
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/connectivity")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StorageSystemConnectivityList getStorageSystemConnectivity(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        return getConnectivity(queryRegisteredSystem(id));
    }

    /**
     * Return the connectivity rest response for a storage system.
     * This method uses the RPSiteArray table in Cassandra to determine which storage systems
     * are connected to this storage system via protection. In the future, we may have connectivity
     * for other features as well.
     * 
     * @param system protection system
     * @return rest response
     */
    private StorageSystemConnectivityList getConnectivity(StorageSystem system) {
        BlockServiceApi apiImpl =
                BlockService.getBlockServiceImpl(system.getSystemType().toString());
        if (apiImpl == null) {
            apiImpl = BlockService.getBlockServiceImpl(BlockServiceApi.DEFAULT);
        }
        return apiImpl.getStorageSystemConnectivity(system);
    }

    /**
     * Invoke connect storage. Once system is verified to be registered.
     * Statistics, Events will be collected for only registered systems.
     * 
     * @param system Storage system to start Metering & Monitoring.
     * @throws ControllerException
     */
    private void startStorageSystem(StorageSystem system) throws ControllerException {
        if (!DiscoveredDataObject.Type.vplex.name().equals(system.getSystemType())) {
            StorageController controller = getStorageController(system.getSystemType());
            controller.connectStorage(system.getId());
        }
    }

    /**
     * Invoke disconnect storage to stop events and statistics gathering of this
     * storage system.
     * 
     * @param storageSystem A reference to the storage system.
     * @throws ControllerException When an error occurs disconnecting the
     *             storage system.
     */
    private void stopStorageSystem(StorageSystem storageSystem) throws ControllerException {
        if (!DiscoveredDataObject.Type.vplex.name().equals(storageSystem.getSystemType())) {
            StorageController controller = getStorageController(storageSystem.getSystemType());
            controller.disconnectStorage(storageSystem.getId());
        }
    }

    /**
     * Return the storage controller for a given system type.
     * 
     * @param systemType The type of the storage system.
     * 
     * @return A reference to the storage controller
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private StorageController getStorageController(String systemType) {
        Class controllerClass = storageSystemClass(systemType);
        StorageController controller = (StorageController) getController(controllerClass, systemType);
        return controller;
    }

    /**
     * Checks if this system supports FileShare Ops FIX ME -- hook this up into
     * the placement logic's supported protocols check
     * 
     * @param systemType
     * 
     * @return The file/block class of storage system
     */
    @SuppressWarnings("rawtypes")
    public static Class storageSystemClass(String systemType) {

        if (systemType.equals(StorageSystem.Type.isilon.toString())
                || systemType.equals(StorageSystem.Type.vnxfile.toString())
                || systemType.equals(StorageSystem.Type.netapp.toString())
                || systemType.equals(StorageSystem.Type.netappc.toString())
                || systemType.equals(StorageSystem.Type.vnxe.toString())) {
            return FileController.class;
        } else if (systemType.equals(StorageSystem.Type.rp.toString())) {
            return RPController.class;
        } else if (systemType.equals(StorageSystem.Type.ecs.toString())) {
            return ObjectController.class;
        }

        return BlockController.class;
    }

    /**
     * Manually register the discovered storage pool with the passed id on the
     * registered storage system with the passed id.
     * 
     * @param id the URN of a ViPR storage system.
     * @param poolId The id of the storage pool.
     * 
     * @brief Register storage system storage pool
     * @return A reference to a StoragePoolRestRep specifying the data for the
     *         registered storage pool.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/{id}/storage-pools/{poolId}/register")
    public StoragePoolRestRep registerStoragePool(@PathParam("id") URI id,
            @PathParam("poolId") URI poolId) {

        // Make sure storage system is registered.
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        queryRegisteredSystem(id);

        ArgValidator.checkFieldUriType(poolId, StoragePool.class, "poolId");
        StoragePool pool = _dbClient.queryObject(StoragePool.class, poolId);
        ArgValidator.checkEntity(pool, poolId, isIdEmbeddedInURL(poolId));
        if (!id.equals(pool.getStorageDevice())) {
            throw APIException.badRequests.poolNotBelongingToSystem(poolId, id);
        }

        // if not register, registered it. Otherwise, dont do anything
        if (RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(pool.getRegistrationStatus())) {
            registerStoragePool(pool);
            // Pool registration also update its varray relationship, so, we should also update vpool to pool relation.
            ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVirtualPool(Arrays.asList(pool), _dbClient, _coordinator);
        }

        return StoragePoolService.toStoragePoolRep(pool, _dbClient, _coordinator);
    }

    private void registerStoragePool(StoragePool pool) {
        pool.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        _dbClient.updateAndReindexObject(pool);

        // record storage port register event.
        recordStoragePoolPortEvent(OperationTypeEnum.STORAGE_POOL_REGISTER,
                STORAGEPOOL_REGISTERED_DESCRIPTION, pool.getId(), POOL_EVENT_SERVICE_TYPE);

        auditOp(OperationTypeEnum.REGISTER_STORAGE_POOL, true,
                null, pool.getId().toString(), pool.getStorageDevice().toString());
    }

    /**
     * Gets all virtual NAS for the registered storage system with the passed
     * id.
     * 
     * @param id the URN of a ViPR storage system.
     * 
     * @brief List storage system virtual nas servers
     * @return A reference to a StoragePooList specifying the id and self link
     *         for each storage pool.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/vnasservers")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public VirtualNASList getVnasServers(@PathParam("id") URI id) {
        // Make sure storage system is registered.
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        StorageSystem system = queryResource(id);
        ArgValidator.checkEntity(system, id, isIdEmbeddedInURL(id));

        VirtualNASList vNasList = new VirtualNASList();
        URIQueryResultList vNasURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceVirtualNasConstraint(id),
                vNasURIs);
        Iterator<URI> vNasIter = vNasURIs.iterator();
        while (vNasIter.hasNext()) {
            URI vNasURI = vNasIter.next();
            VirtualNAS vNas = _dbClient.queryObject(VirtualNAS.class,
                    vNasURI);
            if (vNas != null && !vNas.getInactive()) {
                vNasList.getVNASServers().add(toNamedRelatedResource(vNas, vNas.getNativeGuid()));

            }
        }
        return vNasList;
    }

    /**
     * Gets all storage pools for the registered storage system with the passed
     * id.
     * 
     * @param id the URN of a ViPR storage system.
     * 
     * @brief List storage system storage pools
     * @return A reference to a StoragePooList specifying the id and self link
     *         for each storage pool.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/storage-pools")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StoragePoolList getAllStoragePools(@PathParam("id") URI id) {
        // Make sure storage system is registered.
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        StorageSystem system = queryResource(id);
        ArgValidator.checkEntity(system, id, isIdEmbeddedInURL(id));

        StoragePoolList poolList = new StoragePoolList();
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePoolConstraint(id),
                storagePoolURIs);
        Iterator<URI> storagePoolIter = storagePoolURIs.iterator();
        while (storagePoolIter.hasNext()) {
            URI storagePoolURI = storagePoolIter.next();
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                    storagePoolURI);
            if (storagePool != null && !storagePool.getInactive()) {
                poolList.getPools().add(toNamedRelatedResource(storagePool, storagePool.getNativeGuid()));
            }
        }
        return poolList;
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/rdf-groups")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RDFGroupList getAllRAGroups(@PathParam("id") URI id) {

        // Make sure storage system is registered.
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        StorageSystem system = queryResource(id);
        ArgValidator.checkEntity(system, id, isIdEmbeddedInURL(id));

        RDFGroupList rdfGroupList = new RDFGroupList();
        URIQueryResultList rdfGroupURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceRemoteGroupsConstraint(id),
                rdfGroupURIs);
        Iterator<URI> rdfGroupIter = rdfGroupURIs.iterator();
        while (rdfGroupIter.hasNext()) {
            URI rdfGroupURI = rdfGroupIter.next();
            RemoteDirectorGroup rdfGroup = _dbClient.queryObject(RemoteDirectorGroup.class, rdfGroupURI);
            if (rdfGroup != null && !rdfGroup.getInactive()) {
                rdfGroupList.getRdfGroups().add(toNamedRelatedResource(rdfGroup, rdfGroup.getNativeGuid()));
            }
        }
        return rdfGroupList;
    }

    /**
     * Gets all AutoTier policies associated with registered storage system with the passed
     * id. Only policies which satisfy the below will be returned
     * 1. AutoTiering should be enabled on StorageSystem
     * 2. AutoTierPolicy should be in Enabled State.
     * 
     * @param id the URN of a ViPR storage system.
     * @brief List storage system autotier policies
     * @return A reference to a AutoTierPolicy List specifying the id and self link
     *         for each storage pool.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/auto-tier-policies")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public AutoTierPolicyList getAllFastPolicies(@PathParam("id") URI id,
            @QueryParam("unique_policy_names") Boolean uniquePolicyNames) {

        // Make sure storage system is registered.
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        StorageSystem system = queryRegisteredSystem(id);
        if (!system.getAutoTieringEnabled()) {
            throw APIException.badRequests.autoTieringNotEnabledOnStorageSystem(id);
        }
        if (uniquePolicyNames == null) {
            uniquePolicyNames = false;
        }
        AutoTierPolicyList policyList = new AutoTierPolicyList();
        URIQueryResultList fastPolicyURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceFASTPolicyConstraint(id),
                fastPolicyURIs);
        Iterator<URI> fastPolicyIterator = fastPolicyURIs.iterator();
        while (fastPolicyIterator.hasNext()) {
            URI fastPolicyURI = fastPolicyIterator.next();
            AutoTieringPolicy fastPolicy = _dbClient.queryObject(AutoTieringPolicy.class,
                    fastPolicyURI);

            if (null != fastPolicy && fastPolicy.getPolicyEnabled()) {
                addAutoTierPolicy(fastPolicy, policyList, uniquePolicyNames);
            }
        }
        return policyList;

    }

    /**
     * Get information about the storage pool with the passed id on the
     * registered storage system with the passed id.
     * 
     * @param id the URN of a ViPR storage system.
     * @param poolId The id of the storage pool.
     * 
     * @brief Show storage system storage pool
     * @return A StoragePoolRestRep reference specifying the data for the
     *         requested storage pool.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/storage-pools/{poolId}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StoragePoolRestRep getStoragePool(@PathParam("id") URI id,
            @PathParam("poolId") URI poolId) {
        // Make sure storage system is registered.
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        StorageSystem system = queryResource(id);
        ArgValidator.checkEntity(system, id, isIdEmbeddedInURL(id));

        ArgValidator.checkFieldUriType(poolId, StoragePool.class, "poolId");
        StoragePool storagePool = _dbClient.queryObject(StoragePool.class, poolId);
        ArgValidator.checkEntity(storagePool, poolId, isIdEmbeddedInURL(poolId));

        return StoragePoolService.toStoragePoolRep(storagePool, _dbClient, _coordinator);
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/rdf-groups/{rdfGrpId}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RDFGroupRestRep getRDFGroup(@PathParam("id") URI id, @PathParam("rdfGrpId") URI rdfGroupId) {
        // Make sure storage system is registered.
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        StorageSystem system = queryResource(id);
        ArgValidator.checkEntity(system, id, isIdEmbeddedInURL(id));

        ArgValidator.checkFieldUriType(rdfGroupId, RemoteDirectorGroup.class, "rdfGrpId");
        RemoteDirectorGroup raGroup = _dbClient.queryObject(RemoteDirectorGroup.class, rdfGroupId);
        ArgValidator.checkEntity(raGroup, rdfGroupId, isIdEmbeddedInURL(rdfGroupId));

        return toRDFGroupRep(raGroup, _dbClient, _coordinator);
    }

    private RDFGroupRestRep toRDFGroupRep(RemoteDirectorGroup rdfGroup, DbClient dbClient,
            CoordinatorClient coordinator) {

        List<URI> volumeList = new ArrayList<URI>();
        StringSet volumes = rdfGroup.getVolumes();
        if (volumes != null) {
            for (String volNativeGuid : volumes) {
                try {
                    Volume vol = DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, volNativeGuid);
                    if (vol != null && vol.isSRDFSource()) {
                        volumeList.add(vol.getId());
                    }
                } catch (IOException e) {
                    _log.error(e.getMessage(), e);
                }
            }
        }

        return map(rdfGroup, volumeList);
    }

    /**
     * Manually register the discovered storage port with the passed id on the
     * registered storage system with the passed id.
     * 
     * @param id the URN of a ViPR storage system.
     * @param portId The id of the storage port.
     * 
     * @brief Register storage system storage port
     * @return A reference to a StoragePortRestRep specifying the data for the
     *         registered storage port.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/{id}/storage-ports/{portId}/register")
    public StoragePortRestRep registerStoragePort(@PathParam("id") URI id,
            @PathParam("portId") URI portId) {

        // Make sure the storage system is registered.
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        queryRegisteredSystem(id);

        ArgValidator.checkFieldUriType(portId, StoragePort.class, "portId");
        StoragePort port = _dbClient.queryObject(StoragePort.class, portId);
        ArgValidator.checkEntity(port, portId, isIdEmbeddedInURL(portId));
        if (!id.equals(port.getStorageDevice())) {
            throw APIException.badRequests.portNotBelongingToSystem(portId, id);
        }

        // register port if not registered. Otherwise, do nothing
        if (RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(port.getRegistrationStatus())) {
            registerStoragePort(port);
        }

        return MapStoragePort.getInstance(_dbClient).toStoragePortRestRep(port);

    }

    private void registerStoragePort(StoragePort port) {
        port.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        _dbClient.persistObject(port);

        // record storage port register event.
        recordStoragePoolPortEvent(OperationTypeEnum.STORAGE_PORT_REGISTER,
                STORAGEPORT_REGISTERED_DESCRIPTION, port.getId(), PORT_EVENT_SERVICE_TYPE);

        auditOp(OperationTypeEnum.REGISTER_STORAGE_PORT,
                true, null, port.getId().toString(), port.getStorageDevice().toString());

    }

    /**
     * Record Bourne Event for the completed operations
     * 
     * @param type
     * @param type
     * @param description
     * @param storagePort
     */
    private void recordStoragePoolPortEvent(OperationTypeEnum opType, String description,
            URI resourcdId, String resType) {

        String evType;
        evType = opType.getEvType(true);

        String service = PORT_EVENT_SERVICE_TYPE;
        String eventSource = PORT_EVENT_SERVICE_SOURCE;

        if (resType.equalsIgnoreCase("StoragePool")) {
            service = POOL_EVENT_SERVICE_TYPE;
            eventSource = POOL_EVENT_SERVICE_SOURCE;
        }

        RecordableBourneEvent event = new RecordableBourneEvent(
                /* String */evType,
                /* tenant id */null,
                /* user id ?? */URI.create("ViPR-User"),
                /* project ID */null,
                /* VirtualPool */null,
                /* service */service,
                /* resource id */resourcdId,
                /* description */description,
                /* timestamp */System.currentTimeMillis(),
                /* extensions */"",
                /* native guid */null,
                /* record type */RecordType.Event.name(),
                /* Event Source */eventSource,
                /* Operational Status codes */"",
                /* Operational Status Descriptions */"");
        try {
            _evtMgr.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}. Error: ",
                    description, ex);
        }
    }

    /**
     * Get all storage ports for the registered storage system with the passed
     * id.
     * 
     * @param id the URN of a ViPR storage system.
     * 
     * @brief List storage system storage ports
     * @return A reference to a StoragePortList specifying the id and self link
     *         for each port.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/storage-ports")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StoragePortList getAllStoragePorts(@PathParam("id") URI id) {
        // Make sure the storage system is registered.
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        StorageSystem system = queryResource(id);
        ArgValidator.checkEntity(system, id, isIdEmbeddedInURL(id));
        {	// Update the port metrics calculations. This makes the UI display up-to-date when ports shown.
            URIQueryResultList storagePortURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(id),
                    storagePortURIs);
            List<StoragePort> storagePorts = _dbClient.queryObject(StoragePort.class, storagePortURIs);
            portMetricsProcessor.computeStoragePortUsage(storagePorts, system, true);
        }
        StoragePortList portList = new StoragePortList();
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(id),
                storagePortURIs);
        Iterator<URI> storagePortsIter = storagePortURIs.iterator();
        while (storagePortsIter.hasNext()) {
            URI storagePortURI = storagePortsIter.next();
            StoragePort storagePort = _dbClient.queryObject(StoragePort.class,
                    storagePortURI);
            if (storagePort != null && !storagePort.getInactive()) {
                portList.getPorts().add(toNamedRelatedResource(storagePort, storagePort.getNativeGuid()));
            }
        }
        return portList;
    }

    /**
     * Get information about the storage port with the passed id on the
     * registered storage system with the passed id.
     * 
     * @param id the URN of a ViPR storage system.
     * @param portId The id of the storage port.
     * 
     * @brief Show storage system storage port
     * @return A StoragePortRestRep reference specifying the data for the
     *         requested port.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/storage-ports/{portId}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StoragePortRestRep getStoragePort(@PathParam("id") URI id,
            @PathParam("portId") URI portId) {
        // Make sure the storage system is registered.
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        StorageSystem system = queryResource(id);
        ArgValidator.checkEntity(system, id, isIdEmbeddedInURL(id));

        ArgValidator.checkFieldUriType(portId, StoragePort.class, "portId");
        StoragePort port = _dbClient.queryObject(StoragePort.class, portId);
        ArgValidator.checkEntity(port, portId, isIdEmbeddedInURL(portId));
        return MapStoragePort.getInstance(_dbClient).toStoragePortRestRep(port);
    }

    /**
     * Creates the storage port.
     * It is only applicable to cinder storage systems for users to manually create it on ViPR.
     * Currently there is no API available to get these information from Cinder.
     * 
     * @param id the storage system id
     * @param param the StoragePortRequestParam
     * @return A StoragePortRestRep reference specifying the data for the
     *         created port.
     * @throws ControllerException the controller exception
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/{id}/storage-ports")
    public StoragePortRestRep createStoragePort(@PathParam("id") URI id,
            StoragePortRequestParam param) throws ControllerException {

        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        StorageSystem system = queryResource(id);

        // Creating storage ports is supported only for openstack system
        if (!Type.openstack.name().equalsIgnoreCase(system.getSystemType())) {
            throw APIException.badRequests.cannotCreatePortForSystem(system.getSystemType());
        }

        ArgValidator.checkFieldNotEmpty(param.getName(), "name");
        String portName = param.getName();

        // validate transport type
        ArgValidator.checkFieldNotEmpty(param.getTransportType(), "transport_type");
        ArgValidator.checkFieldValueFromEnum(param.getTransportType(), "transport_type",
                EnumSet.of(TransportType.FC, TransportType.IP));
        String transportType = param.getTransportType();

        // validate port network id
        String portNetworkId = param.getPortNetworkId();
        ArgValidator.checkFieldNotEmpty(param.getPortNetworkId(), "port_network_id");
        StoragePortService.checkValidPortNetworkId(transportType, portNetworkId);

        // check for duplicate port name on the same system
        checkForDuplicatePortName(portName, id);

        // check for duplicate port network id within the system
        StoragePortService.checkForDuplicatePortNetworkIdWithinSystem(
                _dbClient, portNetworkId, id);

        StorageHADomain adapter = CinderUtils.getStorageAdapter(system, _dbClient);

        StoragePort port = new StoragePort();
        port.setId(URIUtil.createId(StoragePort.class));
        port.setStorageDevice(id);
        String nativeGuid = NativeGUIDGenerator.generateNativeGuid(system,
                portName, NativeGUIDGenerator.PORT);
        port.setNativeGuid(nativeGuid);
        port.setPortNetworkId(portNetworkId);

        port.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED
                .toString());
        // always treat it as a frontend port
        port.setPortType(PortType.frontend.name());
        port.setOperationalStatus(OperationalStatus.OK.toString());
        port.setTransportType(transportType);
        port.setLabel(portName);
        port.setPortName(portName);
        port.setStorageHADomain(adapter.getId());
        port.setPortGroup(CinderConstants.CINDER_PORT_GROUP);
        port.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
        _dbClient.createObject(port);

        // runs pool matcher as well
        StoragePortAssociationHelper.runUpdatePortAssociationsProcess(
                Collections.singleton(port), null, _dbClient,
                _coordinator, null);

        // Create an audit log entry
        auditOp(OperationTypeEnum.CREATE_STORAGE_PORT, true, null,
                port.getLabel(), port.getId().toString());

        return MapStoragePort.getInstance(_dbClient).toStoragePortRestRep(port);
    }

    /**
     * Check if a storage port with the same name exists for the passed storage system.
     * 
     * @param name Port name
     * @param id Storage system id
     */
    private void checkForDuplicatePortName(String name, URI systemURI) {

        URIQueryResultList storagePortURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(systemURI),
                storagePortURIs);
        Iterator<URI> storagePortIter = storagePortURIs.iterator();
        while (storagePortIter.hasNext()) {
            StoragePort port = _dbClient.queryObject(StoragePort.class, storagePortIter.next());
            if (port != null && !port.getInactive() && port.getLabel().equalsIgnoreCase(name)) {
                throw APIException.badRequests.duplicateLabel(name);
            }
        }
    }

    /**
     * 
     * List all unmanaged volumes that are available for a storage system.Unmanaged volumes refers to volumes which are available within
     * underlying storage systems , but
     * still not managed in ViPR.
     * As these volumes are not managed in ViPR, there will not be any ViPR specific
     * details associated such as, virtual array, virtual pool, or project.
     * 
     * @param id the URN of a ViPR storage system
     * @prereq none
     * @brief List of all unmanaged volumes available for a storage system
     * @return UnManagedVolumeList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}/unmanaged/volumes")
    public UnManagedVolumeList getUnManagedVolumes(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        UnManagedVolumeList unManagedVolumeList = new UnManagedVolumeList();
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceUnManagedVolumeConstraint(id), result);
        while (result.iterator().hasNext()) {
            URI unManagedVolumeUri = result.iterator().next();
            unManagedVolumeList.getUnManagedVolumes()
                    .add(toRelatedResource(ResourceTypeEnum.UNMANAGED_VOLUMES, unManagedVolumeUri));
        }
        return unManagedVolumeList;
    }

    /**
     * 
     * List all unmanaged volumes that are available for a storage system &
     * given vpool.
     * 
     * Unmanaged volumes refers to volumes which are available within underlying
     * storage systems, but still not managed in ViPR. As these volumes are not
     * managed in ViPR, there will not be any ViPR specific details associated
     * such as, virtual array, virtual pool, or project.
     * 
     * @param id
     *            the URN of a ViPR storage system
     * @prereq none
     * @param vPoolId
     *            the URN of the Virtual Pool
     * @param exportType
     *            Specifies the type of UnManaged Volume.
     * @brief List of all unmanaged volumes available for a storage system & vpool
     * @return UnManagedVolumeList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}/unmanaged/{vpoolid}/volumes")
    public UnManagedVolumeList getUnManagedVPoolVolumes(@PathParam("id") URI id,
            @PathParam("vpoolid") URI vPoolId, @QueryParam("exportType") String exportType) {
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        ArgValidator.checkFieldUriType(vPoolId, VirtualPool.class, "id");
        if (exportType == null || exportType.trim().length() < 1) {
            exportType = ExportType.UNEXPORTED.name();
        }

        if (ExportType.lookup(exportType) == null) {
            throw APIException.badRequests.invalidParameterForUnManagedVolumeQuery(exportType);
        }
        String isExportedSelected = exportType.equalsIgnoreCase(ExportType.EXPORTED.name()) ? TRUE_STR
                : FALSE_STR;
        UnManagedVolumeList unManagedVolumeList = new UnManagedVolumeList();
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getUnManagedVolumeSupportedVPoolConstraint(vPoolId.toString()),
                result);
        Iterator<UnManagedVolume> unmanagedVolumeItr = _dbClient.queryIterativeObjects(UnManagedVolume.class,
                result, true);
        while (unmanagedVolumeItr.hasNext()) {
            UnManagedVolume umv = unmanagedVolumeItr.next();
            String umvExportStatus = umv.getVolumeCharacterstics().get(
                    SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString());
            if (umv.getStorageSystemUri().equals(id) && null != umvExportStatus
                    && umvExportStatus.equalsIgnoreCase(isExportedSelected)) {
                String name = (null == umv.getLabel()) ? umv.getNativeGuid() : umv.getLabel();
                unManagedVolumeList.getNamedUnManagedVolumes().add(
                        toNamedRelatedResource(ResourceTypeEnum.UNMANAGED_VOLUMES, umv.getId(), name));
            } else {
                _log.debug("Ignoring unmanaged volume: {}", umv.getNativeGuid());
            }
        }
        return unManagedVolumeList;
    }

    /**
     * 
     * List all unmanaged FileSystems that are available for a storage system &
     * given vpool.
     * 
     * Unmanaged FileSystems refers to volumes which are available within
     * underlying storage systems, but still not managed in ViPR. As these
     * FileSystems are not managed in ViPR, there will not be any ViPR specific
     * details associated such as, virtual array, virtual pool, or project.
     * 
     * @param id
     *            the URN of a ViPR storage system
     * @prereq none
     * @param vPoolId
     *            the URN of the Virtual Pool
     * @param exportType
     *            Specifies the type of UnManaged FileSystem.
     * @brief List of all unmanaged filesystems available for a storage system & vpool
     * @return UnManagedFileSystemList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}/unmanaged/{vpoolid}/filesystems")
    public UnManagedFileSystemList getUnManagedVPoolFileSystems(@PathParam("id") URI id,
            @PathParam("vpoolid") URI vPoolId, @QueryParam("exportType") String exportType) {
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        ArgValidator.checkFieldUriType(vPoolId, VirtualPool.class, "id");
        if (exportType == null || exportType.trim().length() < 1) {
            exportType = ExportType.UNEXPORTED.toString();
        }

        if (ExportType.lookup(exportType) == null) {
            throw APIException.badRequests.invalidParameterForUnManagedVolumeQuery(exportType);
        }
        String isExportedSelected = exportType.equalsIgnoreCase(ExportType.EXPORTED.name()) ? TRUE_STR
                : FALSE_STR;
        UnManagedFileSystemList unManagedFileSystemList = new UnManagedFileSystemList();
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getUnManagedFileSystemSupportedVPoolConstraint(vPoolId.toString()), result);
        Iterator<UnManagedFileSystem> unmanagedFileSystemItr = _dbClient.queryIterativeObjects(
                UnManagedFileSystem.class, result, true);
        while (unmanagedFileSystemItr.hasNext()) {
            UnManagedFileSystem umfs = unmanagedFileSystemItr.next();
            String umfsExportStatus = umfs.getFileSystemCharacterstics().get(
                    SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED.toString());
            if (umfs.getStorageSystemUri().equals(id) && null != umfsExportStatus
                    && isExportedSelected.equalsIgnoreCase(umfsExportStatus)) {
                String name = (null == umfs.getLabel()) ? umfs.getNativeGuid() : umfs.getLabel();
                unManagedFileSystemList.getNamedUnManagedFileSystem().add(
                        toNamedRelatedResource(ResourceTypeEnum.UNMANAGED_FILESYSTEMS, umfs.getId(), name));
            } else {
                _log.info("Ignoring unmanaged filesystem: {}", umfs.getNativeGuid());
            }
        }
        return unManagedFileSystemList;
    }

    /**
     * 
     * List all unmanaged file systems which are available for a storage system.Unmanaged file systems refers to file systems which are
     * available within underlying storage systems , but
     * still not managed in ViPR.
     * As these file systems are not managed in ViPR, there will not be any ViPR specific
     * details associated such as, virtual array, virtual pool, or project.
     * 
     * @param id the URN of a ViPR storage system
     * 
     * @prereq none
     * @brief List of all unmanaged file systems available for a storage system
     * @return UnManagedFileSystemList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}/unmanaged/filesystems")
    public UnManagedFileSystemList getUnManagedFileSystems(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, StorageSystem.class, "id");
        UnManagedFileSystemList unManagedFileSystemList = new UnManagedFileSystemList();
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceUnManagedFileSystemConstraint(id), result);
        while (result.iterator().hasNext()) {
            URI unManagedFileSystemUri = result.iterator().next();
            unManagedFileSystemList.getUnManagedFileSystem()
                    .add(toRelatedResource(ResourceTypeEnum.UNMANAGED_FILESYSTEMS, unManagedFileSystemUri));
        }
        return unManagedFileSystemList;
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List data of storage system resources
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public StorageSystemBulkRep getBulkResources(BulkIdParam param) {
        return (StorageSystemBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<StorageSystem> getResourceClass() {
        return StorageSystem.class;
    }

    private class mapStorageSystemWithResources implements Function<StorageSystem, StorageSystemRestRep> {
        @Override
        public StorageSystemRestRep apply(StorageSystem system) {
            StorageSystemRestRep restRep = map(system);
            restRep.setNumResources(getNumResources(system, _dbClient));
            return restRep;
        }
    }

    @Override
    public StorageSystemBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<StorageSystem> _dbIterator = _dbClient.queryIterativeObjects(
                StorageSystem.class, ids);
        return new StorageSystemBulkRep(BulkList.wrapping(_dbIterator, new mapStorageSystemWithResources()));
    }

    @Override
    public StorageSystemBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.STORAGE_SYSTEM;
    }

    // Counts and returns the number of resources in a storage system
    public static Integer getNumResources(StorageSystem system, DbClient dbClient) {
        StorageSystem.Type systemType = StorageSystem.Type.valueOf(system.getSystemType());
        if (systemType == null) {
            return 0;
        }
        if (StorageSystem.Type.isFileStorageSystem(systemType)) {
            return dbClient.countObjects(FileShare.class, "storageDevice", system.getId());
        }
        else {
            return dbClient.countObjects(Volume.class, "storageDevice", system.getId());
        }
    }

    /*
     * Checks for valid Name space for discovery
     * Valid Name space for Block ALL & UNMANAGED_VOLUMES
     * Valid Name space for File ALL & UNMANAGED_FILESYSTEMS
     */
    private boolean validateNameSpace(String nameSpace, StorageSystem storageSystem) {
        boolean validNameSpace = false;

        if (Discovery_Namespaces.BLOCK_SNAPSHOTS.name().equalsIgnoreCase(nameSpace)) {
            if (Type.vmax.name().equalsIgnoreCase(storageSystem.getSystemType()) ||
                    Type.vnxblock.name().equalsIgnoreCase(storageSystem.getSystemType())) {
                return true;
            }

            return false;
        }

        // VNXe storage system supports both block and file type unmanaged objects discovery
        if (Type.vnxe.toString().equalsIgnoreCase(storageSystem.getSystemType())) {
            if (nameSpace.equalsIgnoreCase(Discovery_Namespaces.UNMANAGED_FILESYSTEMS.toString()) ||
                    nameSpace.equalsIgnoreCase(Discovery_Namespaces.UNMANAGED_VOLUMES.toString()) ||
                    nameSpace.equalsIgnoreCase(Discovery_Namespaces.ALL.toString())) {
                return true;
            }
        }

        boolean isFileStorageSystem = storageSystem.storageSystemIsFile();
        if (isFileStorageSystem) {
            if (nameSpace.equalsIgnoreCase(Discovery_Namespaces.UNMANAGED_FILESYSTEMS.toString()) ||
                    nameSpace.equalsIgnoreCase(Discovery_Namespaces.ALL.toString())) {
                validNameSpace = true;
            }
        } else {
            if (nameSpace.equalsIgnoreCase(Discovery_Namespaces.UNMANAGED_VOLUMES.toString()) ||
                    nameSpace.equalsIgnoreCase(Discovery_Namespaces.ALL.toString())) {
                validNameSpace = true;
            }
        }
        return validNameSpace;
    }

    public PortMetricsProcessor getPortMetricsProcessor() {
        return portMetricsProcessor;
    }

    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        this.portMetricsProcessor = portMetricsProcessor;
    }

}
