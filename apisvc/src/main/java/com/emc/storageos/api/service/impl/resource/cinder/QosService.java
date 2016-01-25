/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */

package com.emc.storageos.api.service.impl.resource.cinder;

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.cinder.model.CinderQosListRestResp;
import com.emc.storageos.cinder.model.QosAssociationsRestResp;
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
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = { ACL.OWN, ACL.ALL },
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = { ACL.OWN, ACL.ALL })
@SuppressWarnings({ "unchecked", "rawtypes" })
public class QosService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(QosService.class);
    private static final String EVENT_SERVICE_TYPE = "block";

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
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public CinderQosListRestResp getQosList(@PathParam("tenant_id") String openstack_tenant_id) {
        CinderQosListRestResp qosListResp = new CinderQosListRestResp();
        return qosListResp;
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
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{qos_id}/associations")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public QosAssociationsRestResp getQosAssociations(@PathParam("tenant_id") String openstack_tenant_id) {
        _log.info("START get qos associations");
        QosAssociationsRestResp objQosRestResp = new QosAssociationsRestResp();
        return objQosRestResp;
    }

    static String date(Long timeInMillis) {
        return new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").format(new java.util.Date(timeInMillis));
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
    protected ResourceTypeEnum getResourceType() {
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
