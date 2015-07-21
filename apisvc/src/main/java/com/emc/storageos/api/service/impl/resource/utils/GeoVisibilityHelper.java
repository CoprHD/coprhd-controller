/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class GeoVisibilityHelper {
    
    private DbClient dbClient;
    private GeoClientCacheManager geoClientCache;
    
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
    public GeoClientCacheManager getGeoClientCache() {
        return geoClientCache;
    }
    public void setGeoClientCache(GeoClientCacheManager geoClientCache) {
        this.geoClientCache = geoClientCache;
    }        
    
    /**
     * Convenience method for pulling a GeoClient instance from the cache
     * 
     * @param shortVdcId the vdc id
     * @return the matching client
     */
    public GeoServiceClient getClient(String shortVdcId) {
        return geoClientCache.getGeoClient(shortVdcId);
    }
    
    /**
     * Verify the provided parameter maps to a valid Virtual Data Center
     * 
     * @param shortVdcId
     */
    public void verifyVdcId(String shortVdcId) {
        //TODO: we may want to also check the connection status of this VDC
        if (StringUtils.isNotBlank(shortVdcId) && (VdcUtil.getVdcUrn(shortVdcId) == null)) {
            throw APIException.badRequests.badVdcId(shortVdcId);
        }        
    }
    
    /**
     * @param id the URI of an object
     * @return true if the embedded VDC identifier matches the local VDC
     */
    public boolean isLocalURI(URI id) {
        String vdcShortId = URIUtil.parseVdcIdFromURI(id);
        vdcShortId = StringUtils.isNotBlank(vdcShortId) ? vdcShortId : VdcUtil.getFirstVdcId();
        return isLocalVdcId(vdcShortId);
    }
    
    /**
     * Is the provided short VDC id this VDC's id (or empty)
     * 
     * @param shortVdcId
     * @return true if yes
     */
    public boolean isLocalVdcId(String shortVdcId) {
        return StringUtils.isBlank(shortVdcId) || StringUtils.equals(shortVdcId, VdcUtil.getLocalShortVdcId());
    }
}
