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
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StoragePort.TransportType;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.StoragePort.OperationalStatus;
import com.emc.storageos.db.client.model.StoragePort.PortType;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
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
import com.emc.storageos.model.systems.StorageSystemBulkRep;
import com.emc.storageos.model.systems.StorageSystemConnectivityList;
import com.emc.storageos.model.systems.StorageSystemList;
import com.emc.storageos.model.systems.StorageSystemRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.systems.StorageSystemUpdateRequestParam;
import com.emc.storageos.model.vnas.VirtualNASList;
import com.emc.storageos.model.vnas.VirtualNASRestRep;
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
import com.emc.storageos.volumecontroller.StorageController;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.cinder.CinderUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.google.common.base.Function;
import com.vmware.vim25.mox.VirtualMachineDeviceManager.VirtualNetworkAdapterType;

@Path("/vdc/vnasservers")
@DefaultPermissions(read_roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        write_roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class VNASServerService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(VNASServerService.class);
    private static final String EVENT_SERVICE_TYPE = "VirtualNAS";

    protected static final String PORT_EVENT_SERVICE_SOURCE = "StoragePortService";
    private static final String PORT_EVENT_SERVICE_TYPE = "storageport";
    protected static final String STORAGEPORT_REGISTERED_DESCRIPTION = "Storage Port Registered";

    protected static final String POOL_EVENT_SERVICE_SOURCE = "StoragePoolService";
    private static final String POOL_EVENT_SERVICE_TYPE = "storagepool";
    protected static final String STORAGEPOOL_REGISTERED_DESCRIPTION = "Storage Pool Registered";

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
    
    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.STORAGE_SYSTEM;
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
     * @return A reference to VirtualNAS.
     * 
     * @throws EntityNotFoundException When the storage system is not
     *             found.
     */
    @Override
    protected VirtualNAS queryResource(URI id) {
        ArgValidator.checkUri(id);
        VirtualNAS system = _dbClient.queryObject(VirtualNAS.class, id);
        ArgValidator.checkEntityNotNull(system, id, isIdEmbeddedInURL(id));
        return system;
    }

    /**
     * Gets the storage system with the passed id from the database.
     * 
     * @param id the URN of a ViPR storage system
     * 
     * @return A reference to the registered VirtualNAS.
     * 
     * @throws ServiceCodeException When the storage system is not
     *             registered.
     */
    protected VirtualNAS queryRegisteredSystem(URI id) {
        ArgValidator.checkUri(id);
        VirtualNAS system = _dbClient.queryObject(VirtualNAS.class, id);
        ArgValidator.checkEntityNotNull(system, id, isIdEmbeddedInURL(id));
        if (!RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                system.getRegistrationStatus())) {
            throw APIException.badRequests.resourceNotRegistered(VirtualNAS.class.getSimpleName(), id);
        }
        return system;
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
    public VirtualNASList getVNAServers() {
        
        VirtualNASList vnasList = new VirtualNASList();

        List<URI> ids = _dbClient.queryByType(VirtualNAS.class, true);
        
        Iterator<VirtualNAS> iter = _dbClient.queryIterativeObjects(VirtualNAS.class, ids);
        while (iter.hasNext()) {
            vnasList.getVNASServers().add(toNamedRelatedResource(iter.next()));
        }
        return vnasList;
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
    @Path("/{id}")
    public VirtualNASList getVNAServer(@PathParam("id") URI id) {
        
        ArgValidator.checkFieldUriType(id, VirtualNAS.class, "id");
        
        VirtualNASList vnasList = new VirtualNASList();
        
        VirtualNAS virtualNAS = queryResource(id);
        VirtualNASRestRep restRep = map(vitualNAS);
        restRep.setNumResources(getNumResources(system, _dbClient));
        
        return restRep;
        
    }


}
