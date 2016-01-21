/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.NetworkMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.api.mapper.functions.MapNetworkSystem;
import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorIntf;
import com.emc.storageos.api.service.impl.resource.utils.DiscoveredObjectTaskScheduler;
import com.emc.storageos.api.service.impl.resource.utils.PurgeRunnable;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.network.FCEndpointRestRep;
import com.emc.storageos.model.network.FCEndpoints;
import com.emc.storageos.model.network.FCZoneReferences;
import com.emc.storageos.model.network.Fabrics;
import com.emc.storageos.model.network.NetworkSystemBulkRep;
import com.emc.storageos.model.network.NetworkSystemCreate;
import com.emc.storageos.model.network.NetworkSystemList;
import com.emc.storageos.model.network.NetworkSystemRestRep;
import com.emc.storageos.model.network.NetworkSystemUpdate;
import com.emc.storageos.model.network.SanZone;
import com.emc.storageos.model.network.SanZoneCreateParam;
import com.emc.storageos.model.network.SanZoneMemberRestRep;
import com.emc.storageos.model.network.SanZoneRestRep;
import com.emc.storageos.model.network.SanZoneUpdateParam;
import com.emc.storageos.model.network.SanZoneUpdateParams;
import com.emc.storageos.model.network.SanZonesDeleteParam;
import com.emc.storageos.model.network.SanZonesRestRep;
import com.emc.storageos.model.network.WwnAliasParam;
import com.emc.storageos.model.network.WwnAliasUpdateParam;
import com.emc.storageos.model.network.WwnAliasUpdateParams;
import com.emc.storageos.model.network.WwnAliasesCreateParam;
import com.emc.storageos.model.network.WwnAliasesDeleteParam;
import com.emc.storageos.model.network.WwnAliasesParam;
import com.emc.storageos.model.valid.Endpoint.EndpointType;
import com.emc.storageos.networkcontroller.NetworkController;
import com.emc.storageos.networkcontroller.impl.NetworkAssociationHelper;
import com.emc.storageos.networkcontroller.impl.mds.Zone;
import com.emc.storageos.networkcontroller.impl.mds.ZoneMember;
import com.emc.storageos.networkcontroller.impl.mds.ZoneMember.ConnectivityMemberType;
import com.emc.storageos.networkcontroller.impl.mds.ZoneUpdate;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAlias;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAliasUpdate;
import com.emc.storageos.networkcontroller.impl.mds.Zoneset;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

/**
 * NetworkDevice resource implementation
 */
@Path("/vdc/network-systems")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class NetworkSystemService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(NetworkSystemService.class);

    // how many times to retry a procedure before returning failure to the user.
    // Is used with "system delete" operation.
    private int _retry_attempts;

    private static final String EVENT_SERVICE_TYPE = "network";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    private static final String BROCADE_ZONE_NAME_EXP = "[a-zA-Z0-9_]+";
    private static final String CISCO_ZONE_NAME_EXP = "[a-zA-Z0-9_\\-]+";
    private static final int ZONE_NAME_LENGTH = 64;

    private static class NetworkJobExec implements AsyncTaskExecutorIntf {

        private final NetworkController _controller;

        NetworkJobExec(NetworkController controller) {
            _controller = controller;
        }

        @Override
        public void executeTasks(AsyncTask[] tasks) throws ControllerException {
            _controller.discoverNetworkSystems(tasks);
        }

        @Override
        public ResourceOperationTypeEnum getOperation() {
            return ResourceOperationTypeEnum.DISCOVER_NETWORK_SYSTEM;
        }
    }

    /**
     * Given a device type ("mds", or "brocade"), return the appropriate NetworkController.
     * 
     * @param deviceType
     * @return NetworkController
     */
    private NetworkController getNetworkController(String deviceType) {
        NetworkController controller = getController(NetworkController.class, deviceType);
        return controller;
    }

    /**
     * Invoke connect to the NetworkSystem. This will check for basic connectivity,
     * support for the device, etc.
     * 
     * @param device
     * @throws InternalException
     */
    private void startNetworkSystem(NetworkSystem device) throws InternalException {
        NetworkController controller = getNetworkController(device.getSystemType());
        controller.connectNetwork(device.getId());
    }

    /**
     * Returns list of all network systems. Each item in the list contains
     * an id, name, and link.
     * 
     * @prereq none
     * @brief List network systems
     * @return NetworkSystemList for each NetworkSystem, each containing an id, name, and link.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public NetworkSystemList getNetworkSystemList() {
        NetworkSystem system;
        List<URI> ids;
        ids = _dbClient.queryByType(NetworkSystem.class, true);
        NetworkSystemList list = new NetworkSystemList();
        for (URI id : ids) {
            system = _dbClient.queryObject(NetworkSystem.class, id);
            if (system != null) {
                list.getSystems().add(toNamedRelatedResource(system));
            }
        }
        return list;
    }

    /**
     * Check on creation that this is not a duplication of an existing device
     */
    private void checkForDuplicateDevice(URI id, String ip_address, Integer port_number,
            String smis_provider_ip, Integer smis_port_number, String name) {
        List<URI> existingDevices = _dbClient.queryByType(NetworkSystem.class, true);
        for (URI uri : existingDevices) {
            NetworkSystem existing = _dbClient.queryObject(NetworkSystem.class, uri);
            if (existing == null || existing.getId().equals(id)) {
                continue;
            }
            if (existing.getIpAddress() != null && existing.getPortNumber() != null
                    && ip_address != null && port_number != null
                    && existing.getIpAddress().equalsIgnoreCase(ip_address)
                    && existing.getPortNumber().equals(port_number)) {
                throw APIException.badRequests.networkSystemExistsAtIPAddress(ip_address);
            }
            if (existing.getSmisProviderIP() != null && existing.getSmisPortNumber() != null
                    && smis_provider_ip != null && smis_port_number != null
                    && existing.getSmisProviderIP().equalsIgnoreCase(smis_provider_ip)
                    && existing.getSmisPortNumber().equals(smis_port_number)) {
                throw APIException.badRequests.networkSystemSMISProviderExistsAtIPAddress(smis_provider_ip);
            }
            if (existing.getLabel() != null && name != null
                    && existing.getLabel().equalsIgnoreCase(name)) {
                throw APIException.badRequests.resourceExistsWithSameName(NetworkSystem.class.getSimpleName());
            }
        }
    }

    /**
     * Creates a new network system. This can either represent an SSH connection to a Cisco
     * MDS or Nexus switch, or an SMI-S connection to the Brocade Network Advisor.
     * The call will return before communication has been established, but discovery of
     * the device will be initiated.
     * 
     * @param param The NetworkSystemCreate object contains all the parameters for creation.
     * @prereq none
     * @brief Create network system
     * @return A REST representation of the newly created network device.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep createNetworkSystem(NetworkSystemCreate param)
            throws Exception {

        // check device type
        ArgValidator.checkFieldValueFromEnum(param.getSystemType(), "system_type",
                EnumSet.of(NetworkSystem.Type.brocade, NetworkSystem.Type.mds));
        NetworkSystem.Type deviceType = NetworkSystem.Type.valueOf(param.getSystemType());

        if (NetworkSystem.Type.valueOf(param.getSystemType()) == NetworkSystem.Type.brocade) { // Validate fields required for brocade
            ArgValidator.checkFieldNotNull(param.getSmisProviderIp(), "smis_provider_ip");
            ArgValidator.checkFieldNotNull(param.getSmisPortNumber(), "smis_port_number");
            ArgValidator.checkFieldNotNull(param.getSmisUserName(), "smis_user_name");
            ArgValidator.checkFieldNotNull(param.getSmisPassword(), "smis_password");
        } else if (NetworkSystem.Type.valueOf(param.getSystemType()) == NetworkSystem.Type.mds) { // Validate fields required for mds
            ArgValidator.checkFieldNotNull(param.getIpAddress(), "ip_address");
            ArgValidator.checkFieldNotNull(param.getPortNumber(), "port_number");
            ArgValidator.checkFieldNotNull(param.getUserName(), "user_name");
            ArgValidator.checkFieldNotNull(param.getPassword(), "password");
        }

        // Check for existing device.
        checkForDuplicateDevice(null, param.getIpAddress(), param.getPortNumber(), param.getSmisProviderIp(), param.getSmisPortNumber(),
                param.getName());

        NetworkSystem device = new NetworkSystem();
        URI id = URIUtil.createId(NetworkSystem.class);
        device.setId(id);
        device.setLabel(param.getName());
        device.setIpAddress(param.getIpAddress());
        device.setPortNumber(param.getPortNumber());
        device.setUsername(param.getUserName());
        device.setPassword(param.getPassword());
        device.setSystemType(deviceType.name());
        device.setSmisProviderIP(param.getSmisProviderIp());
        device.setSmisPortNumber(param.getSmisPortNumber());
        device.setSmisUserName(param.getSmisUserName());
        device.setSmisPassword(param.getSmisPassword());
        device.setSmisUseSSL(param.getSmisUseSsl());
        device.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(device));
        device.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.name());

        _dbClient.createObject(device);
        auditOp(OperationTypeEnum.CREATE_NETWORK_SYSTEM, true, null,
                device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL(),
                device.getVersion(), device.getUptime());
        return doDiscoverNetworkSystem(device);
    }

    /**
     * Updates an already present network system by matching the URI. This can be used to
     * change the IP address or port, change the credentials, or update the name of a
     * network sytem. A refresh (discovery) is asynchronously initiated as a result of this call.
     * 
     * @param id - the URN of a ViPR NetworkSystem
     * @param param structure contains all the parameters that can be updated.
     * @prereq none
     * @brief Update network system
     * @return The rest representation for the updated NetworkSystem
     * @throws InternalException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep updateNetworkSystem(@PathParam("id") URI id, NetworkSystemUpdate param) throws InternalException {

        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        NetworkSystem device = _dbClient.queryObject(NetworkSystem.class, id);
        ArgValidator.checkEntityNotNull(device, id, isIdEmbeddedInURL(id));

        // Check for existing device.
        checkForDuplicateDevice(device.getId(), param.getIpAddress(), param.getPortNumber(), param.getSmisProviderIp(),
                param.getSmisPortNumber(), param.getName());
        if (param.getName() != null) {
            device.setLabel(param.getName());
        }
        if (param.getIpAddress() != null) {
            device.setIpAddress(param.getIpAddress());
        }
        if (param.getPortNumber() != null) {
            device.setPortNumber(param.getPortNumber());
        }
        if (param.getUserName() != null) {
            device.setUsername(param.getUserName());
        }
        if (param.getPassword() != null) {
            device.setPassword(param.getPassword());
        }
        if (param.getSmisProviderIp() != null) {
            device.setSmisProviderIP(param.getSmisProviderIp());
        }
        if (param.getSmisPortNumber() != null) {
            device.setSmisPortNumber(param.getSmisPortNumber());
        }
        if (param.getSmisUserName() != null) {
            device.setSmisUserName(param.getSmisUserName());
        }
        if (param.getSmisPassword() != null) {
            device.setSmisPassword(param.getSmisPassword());
        }
        if (param.getSmisUseSsl() != null) {
            device.setSmisUseSSL(param.getSmisUseSsl());
        }
        device.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(device));
        _dbClient.persistObject(device);
        startNetworkSystem(device);
        auditOp(OperationTypeEnum.UPDATE_NETWORK_SYSTEM, true, null,
                device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL(),
                device.getVersion(), device.getUptime());

        return doDiscoverNetworkSystem(device);
    }

    /**
     * Shows the attributes of the network system.
     * 
     * @param id the URN of a ViPR network system.
     * @prereq none
     * @brief Show network system
     * @return A rest representation of the attributes of the network system.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public NetworkSystemRestRep getNetworkSystem(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        NetworkSystem device = queryResource(id);
        return map(device);
    }

    /**
     * Delete a network system. The method will delete the
     * network system and all resources associated with it.
     * 
     * @prereq The network system must be unregistered
     * @brief Delete network system
     * @return An asynchronous task.
     * 
     * @throws DatabaseException
     *             When an error occurs querying the database.
     */
    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep deleteNetworkSystem(@PathParam("id") URI id)
            throws DatabaseException {
        NetworkSystem system = queryObject(NetworkSystem.class, id, true);
        ArgValidator.checkEntity(system, id, isIdEmbeddedInURL(id));
        if (!RegistrationStatus.UNREGISTERED.toString().equals(system.getRegistrationStatus())) {
            throw APIException.badRequests.invalidParameterCannotDeactivateRegisteredNetworkSystem(system.getId());
        }

        if (DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS.toString().equals(system.getDiscoveryStatus()) ||
                DiscoveredDataObject.DataCollectionJobStatus.SCHEDULED.toString().equals(system.getDiscoveryStatus())) {
            throw APIException.serviceUnavailable.cannotDeactivateStorageSystemWhileInDiscover(system.getId());
        }

        List<Network> networkList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, Network.class,
                AlternateIdConstraint.Factory.getConstraint(Network.class, "networkSystems", system.getId().toString()));

        for (Network network : networkList) {
            if (network != null
                    && network.getInactive() != true
                    && network.getConnectedVirtualArrays() != null
                    && !network.getConnectedVirtualArrays().isEmpty()
                    && (network.getNetworkSystems() != null
                            && network.getNetworkSystems().contains(system.getId().toString())
                            && network.getNetworkSystems().size() == 1)) {
                throw APIException.badRequests.invalidParameterNetworkMustBeUnassignedFromVirtualArray(network.getLabel(),
                        system.getLabel());
            }
        }

        Map<String, List<FCZoneReference>> zonesMap = getNetworkSystemZoneRefs(system);
        List<URI> nsystems = null;
        List<FCZoneReference> zones = null;
        // for each network handles associated objects. The network itself will be handled
        // by the purge process
        for (Network network : networkList) {
            // remove references from ports
            nsystems = StringSetUtil.stringSetToUriList(network.getNetworkSystems());
            nsystems.remove(system.getId());
            if (nsystems.isEmpty()) {
                // This network will be removed - Remove any storage port references
                List<StoragePort> netPorts = NetworkAssociationHelper.getNetworkStoragePorts(
                        network.getId().toString(), null, _dbClient);
                NetworkAssociationHelper.clearPortAssociations(netPorts, _dbClient);
            } else {
                // This network will remain, update any zone references to use another network system
                URI nsUri = nsystems.get(0);
                zones = zonesMap.get(network.getNativeId());
                if (zones != null) {
                    for (FCZoneReference zone : zones) {
                        zone.setNetworkSystemUri(nsUri);
                    }
                    _dbClient.persistObject(zones);
                }
            }
        }

        String taskId = UUID.randomUUID().toString();

        Operation op = _dbClient.createTaskOpStatus(NetworkSystem.class, system.getId(),
                taskId, ResourceOperationTypeEnum.DELETE_NETWORK_SYSTEM);

        PurgeRunnable.executePurging(_dbClient, _dbPurger,
                _asynchJobService.getExecutorService(), system,
                _retry_attempts, taskId, 60);

        auditOp(OperationTypeEnum.DELETE_NETWORK_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN,
                system.getId().toString(), system.getLabel(), system.getPortNumber(), system.getUsername(),
                system.getSmisProviderIP(), system.getSmisPortNumber(), system.getSmisUserName(), system.getSmisUseSSL(),
                system.getVersion(), system.getUptime());

        return toTask(system, taskId, op);
    }

    private Map<String, List<FCZoneReference>> getNetworkSystemZoneRefs(NetworkSystem system) {
        Map<String, List<FCZoneReference>> map = new HashMap<String, List<FCZoneReference>>();
        List<FCZoneReference> zoneList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FCZoneReference.class,
                AlternateIdConstraint.Factory.getConstraint(FCZoneReference.class, "networkSystemUri", system.getId().toString()));
        List<FCZoneReference> zones = null;
        for (FCZoneReference zone : zoneList) {
            zones = map.get(zone.getFabricId());
            if (zones == null) {
                zones = new ArrayList<FCZoneReference>();
                map.put(zone.getFabricId(), zones);
            }
            zones.add(zone);
        }
        return map;
    }

    /**
     * Reassign the FCZoneReference to another NetworkSystem that manages the
     * zone's fabric
     * 
     * @param zone FCZoneReference that will be reassigned to another
     *            NetworkSystem
     */
    private void reassignZoneNetworkSystem(FCZoneReference zone) {
        URI networkSystemURI = NullColumnValueGetter.getNullURI();
        List<Network> networkList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, Network.class,
                AlternateIdConstraint.Factory.getConstraint(Network.class, "nativeId", zone.getFabricId()));

        Iterator<Network> networkIter = networkList.iterator();
        while (networkIter.hasNext() && networkSystemURI.equals(NullColumnValueGetter.getNullURI())) {
            Network network = networkIter.next();
            for (String nsURI : network.getNetworkSystems()) {
                if (!nsURI.equals(zone.getNetworkSystemUri().toString())) {
                    networkSystemURI = URI.create(nsURI);
                    break;
                }
            }
        }
        zone.setNetworkSystemUri(networkSystemURI);

        _dbClient.persistObject(zone);
    }

    /**
     * Discovers (refreshes) a network system. This is an asynchronous call.
     * Currently this updates the end point (topology) information,
     * which will be reconciled with the existing networks.
     * 
     * @param id the URN of a ViPR Network System.
     * @prereq none
     * @brief Discover network system
     * @return TaskResourceRep (asynchronous call)
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/discover")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep discoverNetworkSystem(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        NetworkSystem device = queryObject(NetworkSystem.class, id, true);

        return doDiscoverNetworkSystem(device);
    }

    /**
     * Common code for submitting a request for discovery. The request may not be performed
     * by the discovery framework if a discovery was recently performed or is ongoing for
     * the network system.
     * 
     * @param device the network system to be discovered to re-discovered.
     *            provided, a new taskId is generated.
     * @return the task used to track the discovery job
     */
    private TaskResourceRep doDiscoverNetworkSystem(NetworkSystem device) {
        NetworkController controller = getNetworkController(device.getSystemType());
        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(_dbClient, new NetworkJobExec(controller));
        String taskId = UUID.randomUUID().toString();
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        tasks.add(new AsyncTask(NetworkSystem.class, device.getId(), taskId));

        TaskList taskList = scheduler.scheduleAsyncTasks(tasks);
        return taskList.getTaskList().iterator().next();
    }

    /**
     * Register a network system.
     * 
     * @param id the URN of a ViPR network system.
     * 
     * @prereq none
     * @brief Register network system
     * @return A NetworkSystemRestRep reference specifying the data for the
     *         updated network system.
     * @throws ControllerException
     * 
     * @throws IllegalArgumentException When the network system is already
     *             registered.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/register")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public NetworkSystemRestRep registerNetworkSystem(@PathParam("id") URI id) throws ControllerException {

        // Validate the network system.
        ArgValidator.checkUri(id);
        NetworkSystem networkSystem = _dbClient.queryObject(NetworkSystem.class, id);
        ArgValidator.checkEntity(networkSystem, id, isIdEmbeddedInURL(id));

        // If not already registered, register it now.
        if (RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(
                networkSystem.getRegistrationStatus())) {
            // Register all Networks for this system.
            List<Network> networkList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, Network.class,
                    AlternateIdConstraint.Factory.getConstraint(Network.class, "networkSystems", networkSystem.getId().toString()));
            for (Network network : networkList) {
                if (network.getInactive() ||
                        DiscoveredDataObject.RegistrationStatus.REGISTERED.toString().equals(network.getRegistrationStatus())) {
                    continue;
                }
                network.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                _dbClient.persistObject(network);
                auditOp(OperationTypeEnum.REGISTER_NETWORK, true, null, network.getId().toString());
            }
            networkSystem.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
            _dbClient.persistObject(networkSystem);
            auditOp(OperationTypeEnum.REGISTER_NETWORK_SYSTEM, true, null,
                    networkSystem.getId().toString(), networkSystem.getLabel(), networkSystem.getPortNumber(), networkSystem.getUsername(),
                    networkSystem.getSmisProviderIP(), networkSystem.getSmisPortNumber(), networkSystem.getSmisUserName(),
                    networkSystem.getSmisUseSSL());
        }
        return map(networkSystem);
    }

    /**
     * Deregister a network system.
     * 
     * @param id the URN of a ViPR network system.
     * 
     * @prereq none
     * @brief Deregister network system
     * @return A NetworkSystemRestRep reference specifying the data for the
     *         updated network system.
     * @throws ControllerException
     * 
     * @throws IllegalArgumentException When the network system is already
     *             registered.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deregister")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public NetworkSystemRestRep deregisterNetworkSystem(@PathParam("id") URI id) throws ControllerException {

        // Validate the storage system.
        ArgValidator.checkUri(id);
        NetworkSystem networkSystem = _dbClient.queryObject(NetworkSystem.class, id);
        ArgValidator.checkEntity(networkSystem, id, isIdEmbeddedInURL(id));

        if (!RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(
                networkSystem.getRegistrationStatus())) {
            // Deregister all Networks for this system.
            List<Network> networkList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, Network.class,
                    AlternateIdConstraint.Factory.getConstraint(Network.class, "networkSystems", networkSystem.getId().toString()));
            for (Network network : networkList) {
                if (network.getInactive() ||
                        DiscoveredDataObject.RegistrationStatus.UNREGISTERED.toString().equals(network.getRegistrationStatus())) {
                    continue;
                }
                List<URI> registeredNetworkSystems = NetworkService.getRegisteredNetworkSystems(network, _dbClient);
                registeredNetworkSystems.remove(networkSystem.getId());
                // Only unregister Network if it is not managed by other registered NetworkSystems
                if (registeredNetworkSystems.isEmpty()) {
                    network.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
                    _dbClient.persistObject(network);
                    auditOp(OperationTypeEnum.DEREGISTER_NETWORK, true, null, id.toString());
                }
            }
            networkSystem.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
            _dbClient.persistObject(networkSystem);
            auditOp(OperationTypeEnum.DEREGISTER_NETWORK_SYSTEM, true, null,
                    networkSystem.getId().toString(), networkSystem.getLabel(), networkSystem.getPortNumber(), networkSystem.getUsername(),
                    networkSystem.getSmisProviderIP(), networkSystem.getSmisPortNumber(), networkSystem.getSmisUserName(),
                    networkSystem.getSmisUseSSL());
        }
        return map(networkSystem);
    }

    /**
     * This returns the cached fiber channel connectivity information of a given fabric id
     * between the network system and external systems, such as host initiators or storage array ports.
     * If fabric id is not specified, get all connections of the network system.
     * The connectivity information is periodically updated, or can be refreshed on demand
     * using a POST /vdc/network-systems/{id}/refresh.
     * 
     * @prereq none
     * @param id the URN of a ViPR Network System
     * @param fabricId The name of the VSAN or fabric as returned by /vdc/network-systems/{id}/san-fabrics
     *            or the VSAN or fabric WWN
     * @brief List network system fiber channel connectivity
     * @return A list of FCEndpoint structures, each containing information about one connection.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/fc-endpoints/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public FCEndpoints getFCEndpointsByFabric(@PathParam("id") URI id, @QueryParam("fabric-id") String fabricId) {
        FCEndpoints connections = new FCEndpoints();
        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");

        if (WWNUtility.isValidWWN(fabricId)) {
            fabricId = fabricId.replaceAll(":", "");
        }

        NetworkSystem device = queryResource(id);
        List<URI> uriList = _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getNetworkSystemFCPortConnectionConstraint(device.getId()));

        connections.setConnections(getFCEndPointRestReps(uriList, fabricId));
        return connections;
    }

    /**
     * This information is intended only for internal debugging.
     * The FCZoneReference structure represents a use of a SAN Zone by an ExportGroup/Volume
     * combination. This call allows one to check what ExportGroups/Volumes are using a given
     * SAN Zone.
     * Gets the FCZoneReference entries for a given wwnList (used to make the key).
     * 
     * @param wwnList A comma separated list of wwn Zone members is used to make the search key.
     * @brief INTERNAL ONLY
     * @return FCZoneReferences a list of FCZoneReference structures
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/san-references/{wwnList}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public FCZoneReferences getReferences(@PathParam("wwnList") String wwnList) {
        FCZoneReferences references = new FCZoneReferences();
        List<String> wwns = Arrays.asList(wwnList.split(","));
        for (int i = 0; i < wwns.size() - 1; i++) {
            for (int j = i + 1; j < wwns.size(); j++) {
                String key = FCZoneReference.makeEndpointsKey(Arrays.asList(new String[] {
                        wwns.get(i).toUpperCase(), wwns.get(j).toUpperCase() }));
                List<URI> uris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                        getFCZoneReferenceKeyConstraint(key));
                for (URI uri : uris) {
                    FCZoneReference ref = _dbClient.queryObject(FCZoneReference.class, uri);
                    if (ref != null) {
                        if (ref.getInactive() == false) {
                            Volume vol = _dbClient.queryObject(Volume.class, ref.getVolumeUri());
                            if (vol != null && vol.getInactive() == false) {
                                references.getReferences().add(map(ref));
                            }
                        }
                    }
                }

            }
        }

        return references;
    }

    /**
     * Returns a list of the VSAN or fabric names configured on this network system.
     * Note: This is a synchronous call to the device and may take a while to receive a response.
     * 
     * @param id the URN of a ViPR network system.
     * @prereq none
     * @brief List network system VSANs and fabrics
     * @return A list of fabric names configured on the Network System.
     * @throws InternalException
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/san-fabrics")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public Fabrics getFabrics(@PathParam("id") URI id) throws InternalException {
        Fabrics fabrics = new Fabrics();
        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        NetworkSystem device = queryResource(id);
        NetworkController controller = getNetworkController(device.getSystemType());
        List<String> fabricIds = controller.getFabricIds(device.getId());
        fabrics.setFabricIds(fabricIds);
        return fabrics;
    }

    /**
     * Returns a list of the active zones (and their zone members) for the specified
     * fabric or VSAN in a network system. E.g., ../san-fabrics/{fabric-id}/san-zones?zone-name="abc-zone"&exclude-members=true
     * Note: This is a synchronous call to the device and may take a while to receive a response.
     * 
     * @param id the URN of a ViPR network system.
     * @param fabricId The name of the VSAN or fabric as returned by
     * @param zoneName - only returns zone with zone name matched the given name. Return all zones, if not specified.
     * @param excludeMembers - true, do not include members with zone. Include members, if not specified.
     * @param excludeAliases - true, do not include aliases with zone. Include aliases, if not specified.
     * @prereq none
     * @brief List active zones in a network system fabric or VSAN
     * @return A list of the active zones and their members. If zone name is specified, and there is a match, then only one zone is
     *         returned.
     *         If excludeMembers is true, then only zone name is present.
     * @throws InternalException
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/san-fabrics/{fabricId}/san-zones")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public SanZonesRestRep getSanZones(@PathParam("id") URI id, @PathParam("fabricId") String fabricId,
            @QueryParam("zone-name") String zoneName, @QueryParam("exclude-members") boolean excludeMembers,
            @QueryParam("exclude-aliases") boolean excludeAliases) throws InternalException {
        SanZonesRestRep szones = new SanZonesRestRep();
        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        NetworkSystem device = queryResource(id);

        String fabricWwn = null;
        if (WWNUtility.isValidWWN(fabricId)) {
            fabricWwn = fabricId;
            fabricId = fabricId.replaceAll(":", "");
        }

        NetworkController controller = getNetworkController(device.getSystemType());
        List<Zoneset> zonesets = controller.getZonesets(device.getId(), fabricId, fabricWwn, zoneName, excludeMembers, excludeAliases);
        for (Zoneset zoneset : zonesets) {
            for (Zone zone : zoneset.getZones()) {
                SanZoneRestRep sz = new SanZoneRestRep();
                sz.setName(zone.getName());
                for (ZoneMember member : zone.getMembers()) {

                    // convert zone member to xml aware. Only fill in alias if member is an alias type
                    sz.getMembers().add(new SanZoneMemberRestRep(
                            member.getAddress(),
                            member.isAliasType() ? member.getAlias() : null));
                }
                szones.getZones().add(sz);
            }
        }
        return szones;
    }

    /**
     * Returns true if valid zone name.
     * 
     * @param name
     * @return
     */
    private void validateZoneName(String name) {
        if (name.matches("[a-zA-Z0-9_]+")) {
            return;
        }
        throw APIException.badRequests.illegalZoneName(name);
    }

    /**
     * Returns true if valid zone name.
     * Throw exception if zone name is invalid based on device type
     *
     * @param name
     * @return
     */
    private boolean validateZoneName(String name, String deviceType) {
        boolean validZoneName = false;
        if(name != null && name.length() > ZONE_NAME_LENGTH) {
            _log.info("Zone name {} is not valid for device type {}", name, deviceType);
            throw APIException.badRequests.nameZoneLongerThanAllowed(name, ZONE_NAME_LENGTH);
        }
        if(deviceType != null) {
            if (deviceType.equalsIgnoreCase(Type.brocade.toString())) {
                if (name.matches(BROCADE_ZONE_NAME_EXP)) {
                    validZoneName = true;
                }
            } else if (deviceType.equalsIgnoreCase(Type.mds.toString())) {
                if (name.matches(CISCO_ZONE_NAME_EXP)) {
                    validZoneName = true;
                }
            }
        }

        if(!validZoneName) {
            _log.info("Zone name {} is not valid for device type {}", name, deviceType);
            throw APIException.badRequests.illegalZoneName(name);
        }
        _log.info("Zone name {} is valid for device type {}", name, deviceType);
        return validZoneName;
    }

    /**
     * Throw exception if alias is invalid
     * 
     * @param alias
     * @param foradd - valid address if validation is for add operation or address is not empty.
     * @return
     */
    private void validateAlias(WwnAliasParam alias, boolean forAdd) {
        if (forAdd || !StringUtils.isEmpty(alias.getAddress())) {
            validateWWN(alias.getAddress());
        }

        validateWWNAlias(alias.getName());

    }

    /**
     * Throw exception if alias of wwn is invalid
     * 
     * @param alias
     * @return
     */
    private void validateWWNAlias(String alias) {
        if (!WWNUtility.isValidWWNAlias(alias)) {
            throw APIException.badRequests.illegalWWNAlias(alias);
        }
    }

    /**
     * Throw exception if wwn is invalid
     * 
     * @param wwn
     * @return
     */
    private void validateWWN(String wwn) {
        if (!WWNUtility.isValidWWN(wwn)) {
            throw APIException.badRequests.illegalWWN(wwn);
        }
    }

    /**
     * Adds one or more SAN zones to the active zoneset of the VSAN or fabric specified on a network system.
     * This is an asynchronous call.
     * 
     * @param sanZones A parameter structure listing the zone(s) to be added and their members.
     * @param id the URN of a ViPR network system.
     * @param fabricId The name of the VSAN or fabric as returned by
     *            /vdc/network-systems/{id}/san-fabrics or the VSAN or fabric WWN
     * @prereq none
     * @brief Add SAN zones to network system VSAN or fabric
     * @return A task description structure.
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/san-fabrics/{fabricId}/san-zones")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep addSanZones(SanZoneCreateParam sanZones, @PathParam("id") URI id,
            @PathParam("fabricId") String fabricId) throws InternalException {
        String task = UUID.randomUUID().toString();
        String fabricWwn = null;
        if (WWNUtility.isValidWWN(fabricId)) {
            fabricWwn = fabricId;
            fabricId = fabricId.replaceAll(":", "");
        }

        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        NetworkSystem device = queryResource(id);

        List<Zone> zones = new ArrayList<Zone>();
        for (SanZone sz : sanZones.getZones()) {
            Zone zone = new Zone(sz.getName());
            validateZoneName(sz.getName(), device.getSystemType());
            zones.add(zone);
            for (String szm : sz.getMembers()) {
                ZoneMember member = createZoneMember(szm);
                zone.getMembers().add(member);
            }

            ArgValidator.checkFieldNotEmpty(zone.getMembers(), "zone members");

            auditOp(OperationTypeEnum.ADD_SAN_ZONE, true, AuditLogManager.AUDITOP_BEGIN, zone.getName(),
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL(),
                    device.getVersion(), device.getUptime());
        }

        ArgValidator.checkFieldNotEmpty(zones, "zones");

        Operation op = _dbClient.createTaskOpStatus(NetworkSystem.class, device.getId(),
                task, ResourceOperationTypeEnum.ADD_SAN_ZONE);
        NetworkController controller = getNetworkController(device.getSystemType());
        controller.addSanZones(device.getId(), fabricId, fabricWwn, zones, false, task);
        return toTask(device, task, op);
    }

    /**
     * Deletes one or more zone(s) from the active zoneset of the VSAN or fabric specified in
     * the network system. This is an asynchronous call.
     * 
     * @param sanZones A list of Zones and their zone members that should be deleted from
     *            the active zoneset. Note: the zone members must be included (deletion of a zone is based
     *            on matching both the name and the zone members).
     * @param id the URN of a ViPR Network System
     * @param fabricId The name of the VSAN or fabric as returned by
     *            /vdc/network-systems/{id}/san-fabrics or the VSAN or fabric WWN
     * @prereq none
     * @brief Delete zones from network system VSAN or fabric
     * @return A task description structure.
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/san-fabrics/{fabricId}/san-zones/remove")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep removeSanZones(SanZonesDeleteParam sanZones, @PathParam("id") URI id,
            @PathParam("fabricId") String fabricId) throws InternalException {

        String task = UUID.randomUUID().toString();

        String fabricWwn = null;
        if (WWNUtility.isValidWWN(fabricId)) {
            fabricWwn = fabricId;
            fabricId = fabricId.replaceAll(":", "");
        }

        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        NetworkSystem device = queryResource(id);
        Operation op = _dbClient.createTaskOpStatus(NetworkSystem.class, device.getId(),
                task, ResourceOperationTypeEnum.REMOVE_SAN_ZONE);

        List<Zone> zones = new ArrayList<Zone>();
        for (SanZone sz : sanZones.getZones()) {
            Zone zone = new Zone(sz.getName());
            zones.add(zone);
            for (String szm : sz.getMembers()) {
                ZoneMember member = createZoneMember(szm);
                zone.getMembers().add(member);
            }

            auditOp(OperationTypeEnum.REMOVE_SAN_ZONE, true, AuditLogManager.AUDITOP_BEGIN, zone.getName(),
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL(),
                    device.getVersion(), device.getUptime());
        }

        ArgValidator.checkFieldNotEmpty(zones, "zones");

        NetworkController controller = getNetworkController(device.getSystemType());
        controller.removeSanZones(device.getId(), fabricId, fabricWwn, zones, false, task);
        return toTask(device, task, op);
    }

    /**
     * For given network system's fabric, update zones via add and/or remove their pwwns or aliases.
     * This is an asynchronous call.
     * 
     * @param updateSanZones A parameter structure listing the zone(s) to be added and their members.
     * @param id the URN of a ViPR network system.
     * @param fabricId The name of the VSAN or fabric as returned by /vdc/network-systems/{id}/san-fabrics
     *            or the VSAN or fabric WWN
     * @prereq Updating zones must be exist in network system with given <code>id</code>
     * @brief Update SAN zones details for network system VSAN or fabric
     * @return A task description structure.
     * @throws InternalException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/san-fabrics/{fabricId}/san-zones")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep updateSanZones(SanZoneUpdateParams updateSanZones, @PathParam("id") URI id,
            @PathParam("fabricId") String fabricId) throws InternalException {
        String task = UUID.randomUUID().toString();

        String fabricWwn = null;
        if (WWNUtility.isValidWWN(fabricId)) {
            fabricWwn = fabricId;
            fabricId = fabricId.replaceAll(":", "");
        }

        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        NetworkSystem device = queryResource(id);

        List<ZoneUpdate> updateZones = new ArrayList<ZoneUpdate>();
        for (SanZoneUpdateParam sz : updateSanZones.getUpdateZones()) {
            ZoneUpdate updateZone = new ZoneUpdate(sz.getName());
            validateZoneName(sz.getName(), device.getSystemType());

            for (String szm : sz.getAddMembers()) {
                if (StringUtils.isEmpty(szm)) {
                    continue;
                }

                ZoneMember member = createZoneMember(szm);
                updateZone.getAddZones().add(member);
            }
            for (String szm : sz.getRemoveMembers()) {
                if (StringUtils.isEmpty(szm)) {
                    continue;
                }

                ZoneMember member = createZoneMember(szm);
                updateZone.getRemoveZones().add(member);
            }

            updateZones.add(updateZone);

            auditOp(OperationTypeEnum.UPDATE_SAN_ZONE, true, AuditLogManager.AUDITOP_BEGIN, updateZone.getName(),
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL(),
                    device.getVersion(), device.getUptime());
        }

        ArgValidator.checkFieldNotEmpty(updateZones, "zones");

        Operation op = _dbClient.createTaskOpStatus(NetworkSystem.class, device.getId(),
                task, ResourceOperationTypeEnum.UPDATE_SAN_ZONE);

        NetworkController controller = getNetworkController(device.getSystemType());
        controller.updateSanZones(device.getId(), fabricId, fabricWwn, updateZones, false, task);
        return toTask(device, task, op);
    }

    /**
     * Activate current active zoneset of the given fabric specified on a network system.
     * This is an asynchronous call.
     * 
     * @param sanZones A parameter structure listing the zone(s) to be added and their members.
     * @param id the URN of a ViPR network system.
     * @param fabricId The name of the VSAN or fabric as returned by
     *            /vdc/network-systems/{id}/san-fabrics or the WWN of the VSAN or fabric
     * @prereq none
     * @brief Activate the current active zoneset of the VSA or fabric which effect all
     *        zoning changes made since the last activation.
     * @return A task description structure.
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/san-fabrics/{fabricId}/san-zones/activate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep activateSanZones(@PathParam("id") URI id,
            @PathParam("fabricId") String fabricId) throws InternalException {
        String task = UUID.randomUUID().toString();

        String fabricWwn = null;
        if (WWNUtility.isValidWWN(fabricId)) {
            fabricWwn = fabricId;
            fabricId = fabricId.replaceAll(":", "");
        }

        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        NetworkSystem device = queryResource(id);

        Operation op = _dbClient.createTaskOpStatus(NetworkSystem.class, device.getId(),
                task, ResourceOperationTypeEnum.ACTIVATE_SAN_ZONE);

        auditOp(OperationTypeEnum.ACTIVATE_SAN_ZONE, true, AuditLogManager.AUDITOP_BEGIN,
                device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL(),
                device.getVersion(), device.getUptime());

        NetworkController controller = getNetworkController(device.getSystemType());
        controller.activateSanZones(device.getId(), fabricId, fabricWwn, task);
        return toTask(device, task, op);
    }

    /**
     * List data of network systems based on input ids.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data of network systems
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public NetworkSystemBulkRep getBulkResources(BulkIdParam param) {
        return (NetworkSystemBulkRep) super.getBulkResources(param);
    }

    /**
     * Returns a list of aliases for the specified network system. For Brocade, aliases
     * can be retrieved per fabric only and fabric-id is a required parameter. For MDS,
     * the full list of device aliases for the network system is returned and fabric-id is
     * ignored if provided.
     * 
     * Note: This is a synchronous call to the device and may take a while to receive
     * a response.
     * 
     * @param id the URN of a ViPR network system.
     * @param fabricId The name of the fabric as returned by
     *            /vdc/network-systems/{id}/san-fabrics or the WWN of the fabric
     * @prereq none
     * @brief List aliases in a network system or a fabric
     * @return A list of aliases.
     * @throws InternalException
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/san-aliases")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public WwnAliasesParam getAliases(@PathParam("id") URI id, @QueryParam("fabric-id") String fabricId) throws InternalException {
        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        NetworkSystem device = queryResource(id);

        if (Type.brocade.toString().equals(device.getSystemType())) {
            ArgValidator.checkFieldNotEmpty(fabricId, "fabric-id");
        }

        String fabricWwn = null;
        if (WWNUtility.isValidWWN(fabricId)) {
            fabricWwn = fabricId;
            fabricId = fabricId.replaceAll(":", "");
        }

        NetworkController controller = getNetworkController(device.getSystemType());
        List<WwnAliasParam> aliases = new ArrayList<WwnAliasParam>(controller.getAliases(device.getId(), fabricId, fabricWwn));

        return new WwnAliasesParam(aliases);
    }

    /**
     * Adds one or more aliases to the specified network system. For Brocade the fabric where
     * the aliases will be added must be specified. For MDS, this input is ignored if provided.
     * <p>
     * This is an asynchronous call.
     * 
     * @param aliases A parameter structure listing the aliases to be added
     * @param id the URN of a ViPR network system.
     * @prereq none
     * @brief Add aliases to a network system
     * @return A task description structure.
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/san-aliases/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep addAliases(WwnAliasesCreateParam aliases, @PathParam("id") URI id) throws InternalException {
        String task = UUID.randomUUID().toString();

        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        ArgValidator.checkFieldNotEmpty(aliases.getAliases(), "aliases");

        NetworkSystem device = queryResource(id);

        String fabricId = aliases.getFabricId();

        if (Type.brocade.toString().equals(device.getSystemType())) {
            ArgValidator.checkFieldNotEmpty(fabricId, "fabric-id");
        }

        String fabricWwn = null;
        if (WWNUtility.isValidWWN(fabricId)) {
            fabricWwn = fabricId;
            fabricId = fabricId.replaceAll(":", "");
        }
        Operation op = _dbClient.createTaskOpStatus(NetworkSystem.class, device.getId(),
                task, ResourceOperationTypeEnum.ADD_ALIAS);

        List<ZoneWwnAlias> zoneAliases = new ArrayList<ZoneWwnAlias>();
        for (WwnAliasParam alias : aliases.getAliases()) {
            ArgValidator.checkFieldNotEmpty(alias.getAddress(), "address");

            validateAlias(alias, true);

            zoneAliases.add(new ZoneWwnAlias(alias.getName(), alias.getAddress()));
            auditOp(OperationTypeEnum.ADD_ALIAS, true, AuditLogManager.AUDITOP_BEGIN, alias.getName(),
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL(),
                    device.getVersion(), device.getUptime());
        }

        NetworkController controller = getNetworkController(device.getSystemType());
        controller.addAliases(device.getId(), fabricId, fabricWwn, zoneAliases, task);
        return toTask(device, task, op);
    }

    /**
     * Removes one or more aliases from the specified network system. For Brocade the fabric from where
     * the aliases will be removed must be specified. For MDS, this input is ignored if provided.
     * 
     * @param aliases A parameter structure listing the aliases to be removed. The alias member is an
     *            optional parameter and when provided, the alias membership is checked prior to removing it.
     * @param id the URN of a ViPR network system.
     * @prereq none
     * @brief Remove aliases to network system to VSAN or fabric
     * @return A task description structure.
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/san-aliases/remove")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep removeAliases(WwnAliasesDeleteParam aliases,
            @PathParam("id") URI id) throws InternalException {
        String task = UUID.randomUUID().toString();

        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        ArgValidator.checkFieldNotEmpty(aliases.getAliases(), "aliases");

        NetworkSystem device = queryResource(id);

        String fabricId = aliases.getFabricId();

        if (Type.brocade.toString().equals(device.getSystemType())) {
            ArgValidator.checkFieldNotEmpty(fabricId, "fabric-id");
        }

        String fabricWwn = null;
        if (WWNUtility.isValidWWN(fabricId)) {
            fabricWwn = fabricId;
            fabricId = fabricId.replaceAll(":", "");
        }

        Operation op = _dbClient.createTaskOpStatus(NetworkSystem.class, device.getId(),
                task, ResourceOperationTypeEnum.REMOVE_ALIAS);

        List<ZoneWwnAlias> zoneAliases = new ArrayList<ZoneWwnAlias>();
        for (WwnAliasParam alias : aliases.getAliases()) {
            validateAlias(alias, false);

            zoneAliases.add(new ZoneWwnAlias(alias.getName(), alias.getAddress()));
            auditOp(OperationTypeEnum.REMOVE_ALIAS, true, AuditLogManager.AUDITOP_BEGIN, alias.getName(),
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL(),
                    device.getVersion(), device.getUptime());
        }

        NetworkController controller = getNetworkController(device.getSystemType());
        controller.removeAliases(device.getId(), fabricId, fabricWwn, zoneAliases, task);
        return toTask(device, task, op);
    }

    /**
     * Changes the WWN member of one or more aliases on the specified network system. For Brocade
     * the fabric of the aliases will be removed must be specified.
     * For MDS, this input is ignored if provided.
     * <p>
     * Current address WWN is optional; however, if provided, it must match the one in system before update. If not, exception will be
     * thrown.
     * <p>
     * This is an asynchronous call.
     * 
     * @param aliases A parameter structure listing the aliases to be updated
     * @param id the URN of a ViPR network system.
     * @param fabricId The name of the VSAN or fabric. This parameter is ignored
     *            if network system is an MDS
     * @prereq none
     * @brief Update aliases in network system
     * @return A task description structure.
     * @throws InternalException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/san-aliases")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep updateAliases(WwnAliasUpdateParams updateAliasParam, @PathParam("id") URI id) throws InternalException {
        String task = UUID.randomUUID().toString();
        ArgValidator.checkFieldUriType(id, NetworkSystem.class, "id");
        ArgValidator.checkFieldNotEmpty(updateAliasParam.getUpdateAliases(), "aliases");

        NetworkSystem device = queryResource(id);

        String fabricId = updateAliasParam.getFabricId();

        if (Type.brocade.toString().equals(device.getSystemType())) {
            ArgValidator.checkFieldNotEmpty(fabricId, "fabric-id");
        }

        String fabricWwn = null;
        if (WWNUtility.isValidWWN(fabricId)) {
            fabricWwn = fabricId;
            fabricId = fabricId.replaceAll(":", "");
        }
        Operation op = _dbClient.createTaskOpStatus(NetworkSystem.class, device.getId(),
                task, ResourceOperationTypeEnum.UPDATE_ALIAS);

        List<ZoneWwnAliasUpdate> zoneAliasesUpdate = new ArrayList<ZoneWwnAliasUpdate>();
        for (WwnAliasUpdateParam updateAlias : updateAliasParam.getUpdateAliases()) {
            validateAlias(updateAlias, false);

            // validate new address
            if (!StringUtils.isEmpty(updateAlias.getNewAddress())) {
                validateWWN(updateAlias.getNewAddress());
            }

            // validate new name
            if (!StringUtils.isEmpty(updateAlias.getNewName())) {
                validateWWNAlias(updateAlias.getNewName());
            }

            zoneAliasesUpdate.add(new ZoneWwnAliasUpdate(updateAlias.getName(), updateAlias.getNewName(), updateAlias.getNewAddress(),
                    updateAlias.getAddress()));

            auditOp(OperationTypeEnum.UPDATE_ALIAS, true, AuditLogManager.AUDITOP_BEGIN, updateAlias.getName(),
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL(),
                    device.getVersion(), device.getUptime());
        }

        NetworkController controller = getNetworkController(device.getSystemType());
        controller.updateAliases(device.getId(), fabricId, fabricWwn, zoneAliasesUpdate, task);
        return toTask(device, task, op);
    }

    @Override
    protected NetworkSystem queryResource(URI id) {
        ArgValidator.checkUri(id);
        NetworkSystem device = _dbClient.queryObject(NetworkSystem.class, id);
        ArgValidator.checkEntityNotNull(device, id, isIdEmbeddedInURL(id));

        return device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.NETWORK_SYSTEM;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<NetworkSystem> getResourceClass() {
        return NetworkSystem.class;
    }

    @Override
    public NetworkSystemBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<NetworkSystem> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new NetworkSystemBulkRep(BulkList.wrapping(_dbIterator, MapNetworkSystem.getInstance()));
    }

    @Override
    public NetworkSystemBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    private ZoneMember createZoneMember(String address) {
        ZoneMember member = new ZoneMember(ConnectivityMemberType.WWPN);
        if (EndpointUtility.isValidEndpoint(address, EndpointType.WWN)) {
            member.setAddress(EndpointUtility.changeCase(address));
        } else if (WWNUtility.isValidWWNAlias(address)) {
            member.setAlias(address);
        } else {
            throw APIException.badRequests.illegalZoneMember(address);
        }

        return member;
    }

    private List<FCEndpointRestRep> getFCEndPointRestReps(List<URI> fcEndpointURIs, String fabricId) {
        List<FCEndpointRestRep> fcEndpointRestRepList = new ArrayList<FCEndpointRestRep>();
        if (fcEndpointRestRepList != null) {
            for (URI uri : fcEndpointURIs) {
                FCEndpoint fcEndpoint = _dbClient.queryObject(FCEndpoint.class, uri);

                // add to list if fabric id was not specified, or endpoint fabric id is the specified id
                if (fcEndpoint != null && (StringUtils.isEmpty(fabricId) || fabricId.equals(fcEndpoint.getFabricId()))) {
                    fcEndpointRestRepList.add(map(fcEndpoint));
                }
            }
        }
        return fcEndpointRestRepList;
    }

}
