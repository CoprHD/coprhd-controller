/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service.impl.resource.cinder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.api.service.impl.resource.VirtualPoolService;
import com.emc.storageos.cinder.model.*;
import com.emc.storageos.db.client.model.QosSpecification;
import com.emc.storageos.db.client.model.VirtualPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/v2/{tenant_id}/qos-specs")
@DefaultPermissions( readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = {ACL.OWN, ACL.ALL},
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = {ACL.OWN, ACL.ALL})
@SuppressWarnings({ "unchecked", "rawtypes" })
public class QosService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(QosService.class);
    private static final String EVENT_SERVICE_TYPE = "block";

    /**
     * Create Qos for the given tenant
     *
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param param POST data containing the QoS creation information.
     *
     * @brief Create Qos
     * @return Created QoS specs
     */
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public CinderQosDetail createQoS(@PathParam("tenant_id") String openstack_tenant_id, CinderQosCreateRequest param, @Context HttpHeaders header) {

        _log.info("START create QoS");

        throw new UnsupportedOperationException();
    }

    /**
     * Get the summary list of all Qos for the given tenant
     *     
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant 
     *
     * @brief List Qos
     * @return Qos list
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public CinderQosListRestResp getQosList(@PathParam("tenant_id") String openstack_tenant_id) {
    	CinderQosListRestResp qosListResp= new CinderQosListRestResp();
        _log.info("START get QoS list");

        List<URI> qosSpecs = _dbClient.queryByType(QosSpecification.class, true);
        for(URI qos:qosSpecs){
            QosSpecification qosSpecification = _dbClient.queryObject(QosSpecification.class, qos);
            if(qosSpecification != null){
                _log.debug("Qos Specification found, id: {}", qosSpecification.getId());
                qosListResp.getQos_specs().add(getDataFromQosSpecification(qosSpecification));
            }
        }

        // TODO: Create a new qos entry in database for each Virtual Pool without a qos specs
        _log.info("END get QoS list");
        return qosListResp;
    }

    /**
     * Get the details of given Qos for the given tenant
     *
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param qos_id the URN of the QoS
     *
     * @brief List Qos in detail
     * @return Qos detailed list
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{qos_id}")
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public CinderQosDetail getQosDetails(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id) {
        CinderQosDetail qosDetailed = new CinderQosDetail();
        _log.info("START get QoS specs detailed");

        QosSpecification qosSpecification = _dbClient.queryObject(QosSpecification.class, URI.create(qos_id));
        if(qosSpecification != null){
            _log.info("Fetched Qos Specification, id: {}", qosSpecification.getId());
            qosDetailed.qos_spec = getDataFromQosSpecification(qosSpecification);
        }

        _log.info("END get QoS specs detailed");
        return qosDetailed;
    }

    /**
     * Sets or unsets keys in a specified QoS specification.
     *
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param qos_id the URN of the QoS specs to update
     *
     * @brief Set or unset key in Qos specs
     * @return Updated Qos specs
     */
    @PUT
    @Path("/{qos_id}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public CinderQosKeyUpdateRequest setUnsetQosKey(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id, CinderQosKeyUpdateRequest data) {

        _log.info("START set or unset QoS keys");
        throw new UnsupportedOperationException();
    }

    /**
     * Delete Qos for the given tenant
     *
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param qos_id the URN of the QoS specs to delete
     *
     * @brief Delete Qos specs
     * @return Task result
     */
    @DELETE
    @Path("/{qos_id}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public Response deleteQoS(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id, @QueryParam("force") String force) {

        _log.info("START delete QoS, force = {}", force);
        throw new UnsupportedOperationException();
    }

    /**
     * Associates a QoS specification with a specified volume type(virtual pool).
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param qos_id the URN of the QoS specs
     * @param volume_id the URN of the volume
     *
     * @brief Associates Qos to a Volume Type
     * @return
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{qos_id}/associate")
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public Response associateQosWithVolumeType(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id, @QueryParam("vol_type_id") String vol_type_id) {
        _log.info("START associate qos with volume type(virtual pool)");
        throw new UnsupportedOperationException();
    }

    /**
     * Disassociates a QoS specification from a specified volume type(virtual pool).
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param qos_id the URN of the QoS specs
     * @param volume_id the URN of the volume
     *
     * @brief Disassociates Qos from a Volume Type
     * @return
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{qos_id}/disassociate")
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public Response disassociateQosFromVolumeType(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id, @QueryParam("vol_type_id") String vol_type_id) {
        _log.info("START disassociate qos from volume type(virtual pool)");
        throw new UnsupportedOperationException();
    }

    /**
     * Disassociates a specified QoS specification from all associations.
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param qos_id the URN of the QoS specs
     *
     * @brief Remove all associations for a given Qos specs
     * @return
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{qos_id}/disassociate_all")
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public Response disassociateQosFromAllAssociations(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id) {
        _log.info("START disassociate qos from all associations");
        throw new UnsupportedOperationException();
    }
    
    
	/**
     * Get the detailed list of all associations for a given qos
     *     
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant 
     *
     * @brief List volumes in detail
     * @return Volume detailed list
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{qos_id}/associations")
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public QosAssociationsRestResp getQosAssociations(@PathParam("tenant_id") String openstack_tenant_id) {
        _log.info("START get qos associations");
        QosAssociationsRestResp objQosRestResp= new QosAssociationsRestResp();
        return objQosRestResp;
    }
    

    //INTERNAL FUNCTIONS
    private CinderQos getDataFromQosSpecification(QosSpecification qosSpecs){
        _log.debug("Fetching data from Qos Specification, id: {}", qosSpecs.getId());
        CinderQos qos = new CinderQos();

        qos.id = qosSpecs.getId().toString();
        qos.consumer = qosSpecs.getConsumer();
        qos.name = qosSpecs.getName();
        qos.specs = qosSpecs.getSpecs();

        return qos;
    }

    static String date(Long timeInMillis){
    	return new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").format(new java.util.Date (timeInMillis));
    }


    @Override
    protected URI getTenantOwner(URI id) {
        Volume volume = (Volume) queryResource(id);
        return volume.getTenant().getURI();
    }

    /**
     * Volume is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected ResourceTypeEnum getResourceType(){
        return ResourceTypeEnum.VOLUME;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }


    /**
     * Get object specific permissions filter
     *
     */
    @Override
    protected ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
                                                                             PermissionsHelper permissionsHelper)
    {
        return new ProjOwnedResRepFilter(user, permissionsHelper, Volume.class);
    }

	@Override
	protected DataObject queryResource(URI id) {
		throw new UnsupportedOperationException();
	}

}
