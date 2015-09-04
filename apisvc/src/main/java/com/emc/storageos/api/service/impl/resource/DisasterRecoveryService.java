/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.SiteMapper;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Site;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/site")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class DisasterRecoveryService extends TaggedResource {

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryService.class);

    public DisasterRecoveryService() {

    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteRestRep addStandby(SiteAddParam param) {
        log.info("begin to add standby");

        Site standby = new Site();
        standby.setId(URIUtil.createId(Site.class));
        standby.setUuid(param.getUuid());
        standby.setName(param.getName());
        standby.setVip(param.getVip());
        standby.getHostIPv4AddressMap().putAll(new StringMap(param.getHostIPv4AddressMap()));
        standby.getHostIPv6AddressMap().putAll(new StringMap(param.getHostIPv6AddressMap()));

        _dbClient.createObject(standby);
        
        VirtualDataCenter vdc = queryLocalVDC();
        StringSet standbyIDs = vdc.getStandbyIDs();

        if (standbyIDs == null) {
            standbyIDs = new StringSet();
        }

        standbyIDs.add(standby.getId().toString());

        log.info("update vdc");
        _dbClient.persistObject(vdc);

        return SiteMapper.map(standby);
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteList getAllStandby() {
        SiteList standbyList = new SiteList();

        VirtualDataCenter vdc = queryLocalVDC();

        List<URI> ids = _dbClient.queryByType(Site.class, true);
        Iterator<Site> iter = _dbClient.queryIterativeObjects(Site.class, ids);
        while (iter.hasNext()) {
            Site standby = iter.next();
            if (vdc.getStandbyIDs().contains(standby.getId().toString())) {
                standbyList.getSites().add(toNamedRelatedResource(standby));
            }
        }
        return standbyList;
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public SiteRestRep getStandby(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Site.class, "id");

        Site standby = queryResource(id);
        return SiteMapper.map(standby);
    }

    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public SiteRestRep removeStandby(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Site.class, "id");
        Site standby = queryResource(id);

        VirtualDataCenter vdc = queryLocalVDC();
        StringSet standbyIDs = vdc.getStandbyIDs();
        standbyIDs.remove(standby.getId().toString());

        _dbClient.persistObject(vdc);
        _dbClient.markForDeletion(standby);

        return SiteMapper.map(standby);
    }

    @Override
    protected Site queryResource(URI id) {
        ArgValidator.checkUri(id);
        Site standby = _dbClient.queryObject(Site.class, id);
        ArgValidator.checkEntityNotNull(standby, id, isIdEmbeddedInURL(id));
        return standby;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.STANDBY;
    }

    private VirtualDataCenter queryLocalVDC() {
        List<URI> ids = _dbClient.queryByType(VirtualDataCenter.class, true);
        Iterator<VirtualDataCenter> iter = _dbClient.queryIterativeObjects(VirtualDataCenter.class, ids);
        while (iter.hasNext()) {
            VirtualDataCenter vdc = iter.next();
            if (vdc.getLocal()) {
                log.info("find local vdc instance");
                return vdc;
            }
        }

        return null;
    }
}
