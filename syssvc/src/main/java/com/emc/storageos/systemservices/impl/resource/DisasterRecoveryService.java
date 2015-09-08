/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.SiteMapper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.TaggedResource;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Site;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/site")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class DisasterRecoveryService extends TaggedResource {

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryService.class);
    private CoordinatorClient coordinator = null;
    private InternalApiSignatureKeyGenerator apiSignatureGenerator;
    private SiteMapper siteMapper;
    
    public DisasterRecoveryService() {
        siteMapper = new SiteMapper();
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteRestRep addStandby(SiteAddParam param) {
        log.info("Begin to add standby site");

        Site standbySite = new Site();
        standbySite.setId(URIUtil.createId(Site.class));
        standbySite.setUuid(param.getUuid());
        standbySite.setName(param.getName());
        standbySite.setVip(param.getVip());
        standbySite.getHostIPv4AddressMap().putAll(new StringMap(param.getHostIPv4AddressMap()));
        standbySite.getHostIPv6AddressMap().putAll(new StringMap(param.getHostIPv6AddressMap()));

        if (log.isDebugEnabled()) {
            log.debug(standbySite.toString());
        }
        
        VirtualDataCenter vdc = queryLocalVDC();
        vdc.getSiteIDs().add(standbySite.getId().toString());

        log.info("Persist standby site to DB");
        _dbClient.createObject(standbySite);
        
        log.info("Update VCD to persist new standby site ID");
        _dbClient.persistObject(vdc);

        return siteMapper.map(standbySite);
    }

    
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteList getAllStandby() {
        log.info("Begin to list all standby sites of local VDC");
        SiteList standbyList = new SiteList();
        
        VirtualDataCenter vdc = queryLocalVDC();
        Collection<String> standbyIds = getStandbyIds(vdc.getSiteIDs());

        List<URI> ids = _dbClient.queryByType(Site.class, true);
        Iterator<Site> sites = _dbClient.queryIterativeObjects(Site.class, ids);
        while (sites.hasNext()) {
            Site standby = sites.next();
            if (standbyIds.contains(standby.getId().toString())) {
                standbyList.getSites().add(siteMapper.map(standby));
            }
        }
        
        return standbyList;
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public SiteRestRep getStandby(@PathParam("id") String id) {
        log.info("Begin to get standby site by uuid");
        
        VirtualDataCenter vdc = queryLocalVDC();
        
        List<URI> ids = _dbClient.queryByType(Site.class, true);

        Iterator<Site> sites = _dbClient.queryIterativeObjects(Site.class, ids);
        while (sites.hasNext()) {
            Site standby = sites.next();
            if (vdc.getSiteIDs().contains(standby.getId().toString())) {
                if (standby.getUuid().equals(id)) {
                    return siteMapper.map(standby);
                }
            }
        }

        return null;
    }

    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public SiteRestRep removeStandby(@PathParam("id") String id) {
        log.info("Begin to remove standby site from local vdc");
        
        VirtualDataCenter vdc = queryLocalVDC();
        Collection<String> standbyIds = getStandbyIds(vdc.getSiteIDs());
        
        List<URI> ids = _dbClient.queryByType(Site.class, true);

        Iterator<Site> sites = _dbClient.queryIterativeObjects(Site.class, ids);
        while (sites.hasNext()) {
            Site standby = sites.next();
            if (standbyIds.contains(standby.getId().toString())) {
                if (standby.getUuid().equals(id)) {
                    log.info("Find standby site in local VDC and remove it");
                    vdc.getSiteIDs().remove(standby.getId());
                    _dbClient.persistObject(vdc);
                    _dbClient.markForDeletion(standby);
                    return siteMapper.map(standby);
                }
            }
        }
        
        return null;
    }
    
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/standby/config")
    public SiteRestRep getStandbyConfig() {
        log.info("Begin to get standby config");
        String siteId = this.coordinator.getSiteId();
        VirtualDataCenter vdc = queryLocalVDC();
        SecretKey key = apiSignatureGenerator.getSignatureKey(SignatureKeyType.INTERVDC_API);

        Site localSite = new Site();

        localSite.setUuid(siteId);
        localSite.setVip(vdc.getApiEndpoint());
        localSite.getHostIPv4AddressMap().putAll(vdc.getHostIPv4AddressesMap());
        localSite.getHostIPv6AddressMap().putAll(vdc.getHostIPv6AddressesMap());
        localSite.setSecretKey(new String(Base64.encodeBase64(key.getEncoded()), Charset.forName("UTF-8")));

        log.info("localSite: {}", localSite);
        return siteMapper.map(localSite);
    }
    
    @POST()
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/standby/config")
    public SiteRestRep addPrimary(SiteAddParam param) {
        log.info("Begin to add primary site");

        Site primarySite = new Site();
        primarySite.setId(URIUtil.createId(Site.class));
        primarySite.setUuid(param.getUuid());
        primarySite.setName(param.getName());
        primarySite.setVip(param.getVip());
        primarySite.getHostIPv4AddressMap().putAll(new StringMap(param.getHostIPv4AddressMap()));
        primarySite.getHostIPv6AddressMap().putAll(new StringMap(param.getHostIPv6AddressMap()));

        VirtualDataCenter vdc = queryLocalVDC();

        if (vdc.getSiteIDs() == null) {
            vdc.setSiteIDs(new StringSet());
        }

        vdc.getSiteIDs().add(primarySite.getId().toString());

        log.info("Persist primary site to DB");
        _dbClient.createObject(primarySite);

        log.info("Update VCD to persist new site ID");
        _dbClient.persistObject(vdc);

        return siteMapper.map(primarySite);
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

    private Collection<String> getStandbyIds(Set<String> siteIds) {
        Set<String> standbyIds = new HashSet<String>();
        String primarySiteId = this.coordinator.getPrimarySiteId();

        for (String siteId : siteIds){
            if (siteId != null && !siteId.equals(primarySiteId)) {
                standbyIds.add(siteId);
            }
        }
        return Collections.unmodifiableCollection(standbyIds);
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.SITE;
    }
    
    // encapsulate the get local VDC operation for easy UT writing because VDCUtil.getLocalVdc is static method
    VirtualDataCenter queryLocalVDC() {
        return VdcUtil.getLocalVdc();
    }
    
    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public InternalApiSignatureKeyGenerator getApiSignatureGenerator() {
        return apiSignatureGenerator;
    }

    public void setApiSignatureGenerator(InternalApiSignatureKeyGenerator apiSignatureGenerator) {
        this.apiSignatureGenerator = apiSignatureGenerator;
    }
    
    public void setSiteMapper(SiteMapper siteMapper) {
        this.siteMapper = siteMapper;
    }
}
