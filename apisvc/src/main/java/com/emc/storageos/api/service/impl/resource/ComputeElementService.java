/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.ComputeMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.ComputeMapper;
import com.emc.storageos.api.service.impl.resource.utils.ComputeSystemUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeVirtualPool;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.compute.ComputeElementBulkRep;
import com.emc.storageos.model.compute.ComputeElementList;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.google.common.base.Function;

@Path("/vdc/compute-elements")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ComputeElementService extends TaskResourceService {
    private static final String EVENT_SERVICE_TYPE = "ComputeElement";
    private static final String EVENT_SERVICE_SOURCE = "ComputeElementService";

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private ComputeVirtualPoolService computeVirtualPoolService;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    private static final Logger _log = LoggerFactory.getLogger(ComputeElementService.class);

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ComputeElement queryResource(URI id) {
        ArgValidator.checkUri(id);
        ComputeElement ce = _dbClient.queryObject(ComputeElement.class, id);
        ArgValidator.checkEntity(ce, id, isIdEmbeddedInURL(id));
        return ce;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.COMPUTE_ELEMENT;
    }

    /**
     * Gets the compute element with the passed id from the database.
     * 
     * @param id the URN of a ViPR compute element.
     * 
     * @return A reference to the registered compute element.
     * 
     * @throws BadRequestException When the compute element is not registered.
     */
    protected ComputeElement queryRegisteredResource(URI id) {
        ArgValidator.checkUri(id);
        ComputeElement ce = _dbClient.queryObject(ComputeElement.class, id);
        ArgValidator.checkEntityNotNull(ce, id, isIdEmbeddedInURL(id));

        if (!RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(ce.getRegistrationStatus())) {
            throw APIException.badRequests.resourceNotRegistered(ComputeElement.class.getSimpleName(), id);
        }

        return ce;
    }

    /**
     * Gets the ids and self links for all compute elements.
     * 
     * @brief List compute elements
     * @return A ComputeElementList reference specifying the ids and self links for
     *         the compute elements.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ComputeElementList getComputeElements() {
        ComputeElementList computeElements = new ComputeElementList();
        List<URI> ids = _dbClient.queryByType(ComputeElement.class, true);

        for (URI id : ids) {
            ComputeElement computeElement = _dbClient.queryObject(ComputeElement.class, id);
            if (computeElement != null && !computeElement.getInactive()) {
                computeElements.getComputeElements().add(toNamedRelatedResource(computeElement));
            }
        }

        return computeElements;
    }

    /**
     * Gets the data for a compute element.
     * 
     * @param id the URN of a ViPR compute element.
     * 
     * @brief Show compute element
     * @return A ComputeElementRestRep reference specifying the data for the
     *         compute element with the passed id.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ComputeElementRestRep getComputeElement(@PathParam("id") URI id) {

        ArgValidator.checkFieldUriType(id, ComputeElement.class, "id");
        ComputeElement ce = queryResource(id);
        ArgValidator.checkEntity(ce, id, isIdEmbeddedInURL(id));

        Host associatedHost = getAssociatedHost(ce, _dbClient);
        Cluster cluster = null;
        if (associatedHost!=null && !NullColumnValueGetter.isNullURI(associatedHost.getCluster())){
            cluster = _dbClient.queryObject(Cluster.class, associatedHost.getCluster());
        }
        return ComputeMapper.map(ce, associatedHost, cluster);
    }

    /**
     * Allows the user to deregister a registered compute element so that it is no
     * longer used by the system. This simply sets the registration_status of
     * the compute element to UNREGISTERED.
     * 
     * @param id the URN of a ViPR compute element to deregister.
     * 
     * @brief Unregister compute element
     * @return Status indicating success or failure.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deregister")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public ComputeElementRestRep deregisterComputeElement(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, ComputeElement.class, "id");
        ComputeElement ce = queryResource(id);

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getHostComputeElementConstraint(ce.getId()), uris);
        List<Host> hosts = _dbClient.queryObject(Host.class, uris, true);
        if (!hosts.isEmpty()) {
            throw APIException.badRequests.unableToDeregisterProvisionedComputeElement(ce.getLabel(), hosts.get(0).getHostName());
        }

        if (RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(ce.getRegistrationStatus())) {
            ce.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
            _dbClient.persistObject(ce);
            // Remove the element being deregistered from all CVPs it is part of.
            URIQueryResultList cvpList = new URIQueryResultList();
            _log.debug("Looking for CVPs this blade is in");
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getMatchedComputeElementComputeVirtualPoolConstraint(id), cvpList);
            Iterator<URI> cvpListItr = cvpList.iterator();
            while (cvpListItr.hasNext()) {
                ComputeVirtualPool cvp = _dbClient.queryObject(ComputeVirtualPool.class, cvpListItr.next());
                _log.debug("Found cvp:" + cvp.getLabel() + "containing compute element being deregistered");
                StringSet currentElements = new StringSet();
                if (cvp.getMatchedComputeElements() != null) {
                    currentElements.addAll(cvp.getMatchedComputeElements());
                    currentElements.remove(ce.getId().toString());
                }
                cvp.setMatchedComputeElements(currentElements);
                _dbClient.updateAndReindexObject(cvp);
                _log.debug("Removed ce from cvp");
            }
            // Record the compute element deregister event.
            // recordComputeElementEvent(OperationTypeEnum.DEREGISTER_COMPUTE_ELEMENT,
            // COMPUTE_ELEMENT_DEREGISTERED_DESCRIPTION, ce.getId());

            recordAndAudit(ce, OperationTypeEnum.DEREGISTER_COMPUTE_ELEMENT, true, null);
        }

        return ComputeMapper.map(ce,null, null);
    }

    /**
     * Manually register the discovered compute element with the passed id on the
     * registered compute system with the passed id.
     * 
     * @param computeElementId The id of the compute element.
     * 
     * @brief Register compute system compute element
     * @return A reference to a ComputeElementRestRep specifying the data for the
     *         registered compute element.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/{id}/register")
    public ComputeElementRestRep registerComputeElement(@PathParam("id") URI id) {

        ArgValidator.checkFieldUriType(id, ComputeElement.class, "id");
        ComputeElement ce = _dbClient.queryObject(ComputeElement.class, id);
        ArgValidator.checkEntity(ce, id, isIdEmbeddedInURL(id));

        if (ce == null) {
            throw APIException.badRequests.computeElementNotFound(id);
        }

        if (ce.getComputeSystem() == null) {
            throw APIException.badRequests.computeElementNotBelongingToSystem(id, null);
        } else {
            ComputeSystemUtils.queryRegisteredSystem(ce.getComputeSystem(), _dbClient, isIdEmbeddedInURL(ce.getComputeSystem()));
        }

        // if not registered, registered it. Otherwise, dont do anything
        if (RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(ce.getRegistrationStatus())) {
            registerComputeElement(ce);
            List<URI> cvpIds = _dbClient.queryByType(ComputeVirtualPool.class, true);
            Iterator<ComputeVirtualPool> iter = _dbClient.queryIterativeObjects(ComputeVirtualPool.class, cvpIds);
            while (iter.hasNext()) {
                ComputeVirtualPool cvp = iter.next();
                if (cvp.getUseMatchedElements()) {
                    _log.debug("Compute pool " + cvp.getLabel() + " configured to use dynamic matching -- refresh matched elements");
                    computeVirtualPoolService.getMatchingCEsforCVPAttributes(cvp);
                    _dbClient.updateAndReindexObject(cvp);
                }
            }

        }

        return map(ce,null,null);
    }

    private void registerComputeElement(ComputeElement ce) {
        ce.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        _dbClient.updateAndReindexObject(ce);

        recordAndAudit(ce, OperationTypeEnum.REGISTER_COMPUTE_ELEMENT, true, null);
    }

    /**
     * Retrieves resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List data of compute element resources
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public ComputeElementBulkRep getBulkResources(BulkIdParam param) {
        return (ComputeElementBulkRep) super.getBulkResources(param);
    }

    @Override
    public ComputeElementBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<ComputeElement> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new ComputeElementBulkRep(BulkList.wrapping(_dbIterator, new Function<ComputeElement, ComputeElementRestRep>() {
            @Override
            public ComputeElementRestRep apply(ComputeElement ce) {
                Host associatedHost = getAssociatedHost(ce, _dbClient);
                Cluster cluster = null;
                if (associatedHost!=null && !NullColumnValueGetter.isNullURI(associatedHost.getCluster())){
                   cluster = _dbClient.queryObject(Cluster.class, associatedHost.getCluster());
                }
                ComputeElementRestRep restRep = ComputeMapper.map(ce, associatedHost, cluster);
                return restRep;
            }
        }));
    }

    private Host getAssociatedHost(ComputeElement ce, DbClient dbClient) {
        Host associatedHost = null;
        URIQueryResultList uris = new URIQueryResultList();

        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getHostComputeElementConstraint(ce.getId()), uris);
        List<Host> hosts = _dbClient.queryObject(Host.class, uris, true);

        // we expect to find just one host that uses this CE
        if (hosts!=null && !hosts.isEmpty()){
            associatedHost = hosts.get(0);
        }

        return associatedHost;

    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<ComputeElement> getResourceClass() {
        return ComputeElement.class;
    }

    /**
     * Record ViPR Event for the completed operations
     * 
     * @param computeElement
     * @param type
     * @param description
     */
    private void recordComputeEvent(ComputeElement computeElement, OperationTypeEnum typeEnum, boolean status) {
        RecordableBourneEvent event = new RecordableBourneEvent(
                /* String */typeEnum.getEvType(status),
                /* tenant id */null,
                /* user id ?? */URI.create("ViPR-User"),
                /* project ID */null,
                /* CoS */null,
                /* service */EVENT_SERVICE_TYPE,
                /* resource id */computeElement.getId(),
                /* description */typeEnum.getDescription(),
                /* timestamp */System.currentTimeMillis(),
                /* extensions */null,
                /* native guid */computeElement.getNativeGuid(),
                /* record type */RecordType.Event.name(),
                /* Event Source */EVENT_SERVICE_SOURCE,
                /* Operational Status codes */"",
                /* Operational Status Descriptions */"");
        try {
            _evtMgr.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}. Error: {}.",
                    typeEnum.getDescription(), ex);
        }
    }

    private void recordAndAudit(ComputeElement ce, OperationTypeEnum typeEnum, boolean status, String operationalStage) {

        recordComputeEvent(ce, typeEnum, status);

        auditOp(typeEnum, status, operationalStage,
                ce.getId().toString(), ce.getLabel(), ce.getNativeGuid(), ce.getUuid(), ce.getOriginalUuid());

    }
}
