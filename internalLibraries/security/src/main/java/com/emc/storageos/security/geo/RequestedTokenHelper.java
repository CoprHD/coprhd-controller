/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */


package com.emc.storageos.security.geo;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.RequestedTokenMap;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.security.authentication.TokenEncoder;
import com.emc.storageos.security.exceptions.RetryableSecurityException;
import com.emc.storageos.security.exceptions.SecurityException;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import com.sun.jersey.api.client.ClientResponse;

/**
 * Helper class to manage adding and removing VDC ids to the list of
 * VDCs that borrowed a particular token.
 * Also manages intervdc notifications of logged out tokens.
 */
public class RequestedTokenHelper {
    private static final Logger log = LoggerFactory.getLogger(RequestedTokenHelper.class);
    
    @Autowired
    private DbClient dbClient;
    
    @Autowired
    private CoordinatorClient coordinator;
    
    @Autowired
    protected GeoClientCacheManager geoClientCacheMgt;
    
    @Autowired
    protected TokenEncoder tokenEncoder;  
    
    /**
     * Sets the field called 'dbClient' to the given value.
     * @param dbClient The dbClient to set.
     */
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * Sets the field called 'coordinator' to the given value.
     * @param coordinator The coordinator to set.
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }
    
    private static final String TOKEN_MAP_LOCK_PREFIX = "TokenMap_%s";
    
    public enum Operation { ADD_VDC, REMOVE_VDC };

    /**
     * Adds or removes a VDCid to the list of VDCids that requested the given token
     * @param op ADD_VDC or REMOVE_VDC
     * @param tokenId the token for which the map should be updated
     * @param requestingVDC the short vdcid of the vdc to add or remove
     */
    public void addOrRemoveRequestingVDC(Operation op, String tokenId, String requestingVDC) {
        InterProcessLock tokenLock = null;
        String lockName = String.format(TOKEN_MAP_LOCK_PREFIX, tokenId.toString());
        try {
            tokenLock = coordinator.getLock(lockName);
            if(tokenLock == null) {
                log.error("Could not acquire lock for requested token map update");
                throw SecurityException.fatals.couldNotAcquireRequestedTokenMapCaching();
            }
            tokenLock.acquire();
            if (op == Operation.ADD_VDC) {
                addRequestingVDC(tokenId, requestingVDC);
            } else {
                removeRequestingVDC(tokenId, requestingVDC);
            }
        } catch (Exception ex) {
            log.error("Could not acquire lock while trying to update requested token map.", ex);
        } 
        finally {
            try {
                if(tokenLock != null) {
                    tokenLock.release();
                }
            } catch(Exception ex) {
                log.error("Unable to release requested token map lock", ex);
            }
        }
    }
    
    private void addRequestingVDC(String tokenId, String requestingVDC) {
        RequestedTokenMap map = getTokenMap(tokenId);
        if (map == null) {
            map = new RequestedTokenMap();
            map.setId(URIUtil.createId(RequestedTokenMap.class));
            map.setTokenID(tokenId);
        }
        if (!map.getVDCIDs().contains(requestingVDC)) {
            map.addVDCID(requestingVDC);
            log.debug("Adding shortId {}", requestingVDC);
            dbClient.persistObject(map);
        }
    }
    
    private void removeRequestingVDC(String tokenId, String requestingVDC) {
        RequestedTokenMap map = getTokenMap(tokenId);
        if (map != null) {
            if (map.getVDCIDs().contains(requestingVDC)) {
                map.removeVDCID(requestingVDC);
                if (map.getVDCIDs().isEmpty()) {
                    log.info("Last vdcid entry removed from requested token map.  Removing map.");
                    dbClient.removeObject(map);   
                } else {
                    dbClient.persistObject(map);
                }
            }
        }
    }

    /**
     * Retrieves the list of vdcid that have requested a copy of this token
     * @param tokenId
     * @return
     */
    public RequestedTokenMap getTokenMap(String tokenId) {
        URIQueryResultList maps = new URIQueryResultList();
        List<URI> mapsURI = new ArrayList<URI>();     
        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getRequestedTokenMapTokenIdConstraint(tokenId.toString()), maps);
        if (maps == null) {
            log.info("No requested token map found.  No map.");
            return null;
        }
        while (maps.iterator().hasNext()) {
            mapsURI.add(maps.iterator().next());
        }
        List<RequestedTokenMap> objects = dbClient.queryObject(RequestedTokenMap.class, mapsURI);
        if (objects == null || objects.size() != 1) {
            log.info("No requested token map found.  Empty map.");
            return null;
        }
        return objects.get(0);   
    }

    /**
     * Notify the originatorVDC of the token or follows the map of VDCs that have a copy of this token
     * depending on whether or not the passed in token is from this VDC or not.
     * @param tokenId token URI for lookups in the requested token map
     */
    public void notifyExternalVDCs(String rawToken) {       
        String tokenId = tokenEncoder.decode(rawToken).getTokenId().toString();
        // If this is a token this VDC did not create, it needs to call back the
        // originator
        String originatorVDCId = URIUtil.parseVdcIdFromURI(tokenId);
        if (!VdcUtil.getLocalShortVdcId().equals(originatorVDCId)) {
            // Call originator.  If this fails, this is a problem.
            log.info("Calling token originator to propagate deletion of token");
            boolean failed = false;
            try {
                ClientResponse resp = geoClientCacheMgt.getGeoClient(originatorVDCId).logoutToken(rawToken, null, false);            
                if (resp.getStatus() != ClientResponse.Status.OK.getStatusCode()) {
                    failed = true;
                }
            } catch (Exception ex) {
                failed = true;
            }
            if (failed) {
                throw RetryableSecurityException.retryables.unableToNotifyTokenOriginatorForLogout(originatorVDCId);
            }
        }
        // Else, if this VDC created this token, go through the list of VDCs
        // that may have a copy and notify them.
        RequestedTokenMap map = getTokenMap(tokenId);
        if (map == null || map.getVDCIDs().isEmpty()) {
            return;
        }
       
        log.info("This token had potential copies still active in other VDCs.  Notifying...");
        for (String shortId : map.getVDCIDs()) {
            try {
                ClientResponse resp = geoClientCacheMgt.getGeoClient(shortId).logoutToken(rawToken, null, false);
                // whether this succeeded or not, we remote the entry from the map.  We log a warning,
                // but the remote VDC will expire the copy of the token automatically in less than 10 minutes.
                // The remove logout notification is a best effort attempt to remove the remote token quicker.
                if (resp.getStatus() != ClientResponse.Status.OK.getStatusCode()) {
                    log.warn("Unable to successfully verify that remote copy of token was deleted.  It will expire is less than 10 minutes.");
                }
            } catch (Exception e) {
                log.error("Could not contact remote VDC to invalidate token: {}", shortId);
            }
            removeRequestingVDC(tokenId, shortId); // remove from the requested map whether logout success or not.
        }
    }

    /**
     * Iterates through the list of VDCs, skips the local vdc, and sends a logout?force=true request to
     * each vdc found, with optionally the ?username= parameter if supplied.
     * @param rawToken
     * @param username optional
     */
    public void broadcastLogoutForce(String rawToken, String username) {
        List<URI> vdcIds = dbClient.queryByType(VirtualDataCenter.class, true);
        Iterator<VirtualDataCenter> vdcIter = dbClient.queryIterativeObjects(VirtualDataCenter.class, vdcIds);
        while (vdcIter.hasNext()) {
            VirtualDataCenter vdc = vdcIter.next();
            if (vdc.getShortId().equals(VdcUtil.getLocalShortVdcId())) {
                log.info("Skipping local vdc.  Already proceeded logout?force locally");
                continue;
            }
            try {
                ClientResponse resp = geoClientCacheMgt.getGeoClient(vdc.getShortId()).
                        logoutToken(rawToken, username, true);
            } catch (Exception e) {
                log.error("Could not contact remote VDC to invalidate token with force option: {}", vdc.getShortId());
            }
        }
    }

}
