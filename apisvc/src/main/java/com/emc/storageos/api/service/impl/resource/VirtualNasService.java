/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.functions.MapStoragePort;
import com.emc.storageos.api.mapper.functions.MapVirtualNas;
import com.emc.storageos.api.service.impl.resource.utils.PurgeRunnable;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.TransportType;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import com.emc.storageos.model.pools.VirtualArrayAssignments;
import com.emc.storageos.model.ports.StoragePortBulkRep;
import com.emc.storageos.model.ports.StoragePortList;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.ports.StoragePortUpdate;
import com.emc.storageos.model.valid.Endpoint.EndpointType;
import com.emc.storageos.model.varray.NetworkEndpointParam;
import com.emc.storageos.model.vnas.VirtualNASList;
import com.emc.storageos.model.vnas.VirtualNASRestRep;
import com.emc.storageos.networkcontroller.impl.NetworkAssociationHelper;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;

/**
 * StoragePort resource implementation
 */
@Path("/vdc/vnas-servers")
@DefaultPermissions(read_roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        write_roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class VirtualNasService extends TaggedResource {

    private static Logger _log = LoggerFactory.getLogger(VirtualNasService.class);

    protected static final String EVENT_SERVICE_SOURCE = "VirtualNasService";

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private NetworkService networkSvc;

    private static final String EVENT_SERVICE_TYPE = "virtualNAS";

    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    // how many times to retry a procedure before returning failure to the user.
    // Is used with "system delete" operation.
    private int _retry_attempts;

    public void setRetryAttempts(int retries) {
        _retry_attempts = retries;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * Gets the virtual NAS with the passed id from the database.
     * 
     * @param id the URN of a ViPR virtual NAS.
     * 
     * @return A reference to the registered VirtualNAS.
     * 
     * @throws BadRequestException When the vNAS is not registered.
     */
    protected VirtualNAS queryRegisteredResource(URI id) {
        ArgValidator.checkUri(id);
        VirtualNAS vNas = _dbClient.queryObject(VirtualNAS.class, id);
        ArgValidator.checkEntity(vNas, id, isIdEmbeddedInURL(id));

        if (!RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
        		vNas.getRegistrationStatus())) {
            throw APIException.badRequests.resourceNotRegistered(
            		VirtualNAS.class.getSimpleName(), id);
        }

        return vNas;
    }

    @Override
    protected VirtualNAS queryResource(URI id) {
        ArgValidator.checkUri(id);
        VirtualNAS vNas = _dbClient.queryObject(VirtualNAS.class, id);
        ArgValidator.checkEntity(vNas, id, isIdEmbeddedInURL(id));
        return vNas;
    }

    /**
     * Gets the ids and self links for all virtual NAS.
     * 
     * @brief List virtual NAS servers
     * @return A VirtualNASList reference specifying the ids and self links for
     *         the virtual NAS servers.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public VirtualNASList getVirtualNasServers() {

    	VirtualNASList vNasList = new VirtualNASList();
        List<URI> ids = _dbClient.queryByType(VirtualNAS.class, true);
        for (URI id : ids) {
        	VirtualNAS vNas = _dbClient.queryObject(VirtualNAS.class, id);
            if ((vNas != null)) {
            	vNasList.getVNASServers().add(
                        toNamedRelatedResource(vNas, vNas.getNativeGuid()));
            }
        }

        return vNasList;
    }

    /**
     * Gets the details of virtual NAS.
     * 
     * @param id the URN of a ViPR virtual NAS.
     * 
     * @brief Show virtual NAS
     * @return A VirtualNASRestRep reference specifying the data for the
     *         virtual NAS with the passed id.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public VirtualNASRestRep getVirtualNasServer(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VirtualNAS.class, "id");
        VirtualNAS vNas = queryResource(id);
        return MapVirtualNas.getInstance(_dbClient).toVirtualNasRestRep(vNas);
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VIRTUAL_NAS;
    }

}
