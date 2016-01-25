/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.geo;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Properties;

import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.security.helpers.ServiceClientRetryFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;

public class GeoClientCacheManager {

    private static final int CLIENT_CONNECT_TIMEOUT = 30 * 1000;
    private static final int CLIENT_READ_TIMEOUT = 30 * 1000;

    private int _clientConnTimeout = CLIENT_CONNECT_TIMEOUT;
    private int _clientReadTimeout = CLIENT_READ_TIMEOUT;

    private static final Logger log = LoggerFactory.getLogger(GeoClientCacheManager.class);
    private CoordinatorClient coordinatorClient;
    private DbClient dbClient;
    // TODO: ultimately we'll need a strategy for cache refresh
    private ConcurrentHashMap<String, GeoServiceClient> clientCache = new ConcurrentHashMap<String, GeoServiceClient>();

    public void setClientConnTimeout(int clientConnTimeout) {
        _clientConnTimeout = clientConnTimeout;
    }

    public void setClientReadTimeout(int clientReadTimeout) {
        _clientReadTimeout = clientReadTimeout;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * Clear the Client Cache (and the underlying dbClient URN cache)
     */
    public void clearCache() {
        VdcUtil.invalidateVdcUrnCache();
        clientCache.clear();
    }

    public GeoServiceClient getGeoClient(String shortVdcId) {
        log.info("db client instance is {}", this.dbClient);
        if (!((DbClientImpl) dbClient).isInitDone()) {
            log.info("db client not inited. starting ...");
            dbClient.start();
            log.info("db client started");
        }
        GeoServiceClient client = null;
        if (clientCache.containsKey(shortVdcId)) {
            client = clientCache.get(shortVdcId);
            log.info("returning existing cached client for vdc {}", shortVdcId);
        } else {
            client = createClient(lookupVdc(shortVdcId));
            clientCache.put(shortVdcId, client);
            log.info("returning new cached client for vdc {}", shortVdcId);
        }
        return client;
    }

    public GeoServiceClient getGeoClient(Properties vdcInfo) {
        GeoServiceClient client = null;
        String shortId = vdcInfo.getProperty(GeoServiceJob.VDC_SHORT_ID);
        if (clientCache.containsKey(shortId)) {
            client = clientCache.get(shortId);
            log.info("returning existing cached client for vdc {}", shortId);
        } else {
            client = createClient(vdcInfo);
            clientCache.put(shortId, client);
            log.info("returning new cached client for vdc {}", shortId);
        }
        return client;
    }

    /**
     * Remove this method once the ugly code needing to use a non-standard
     * port in the add vdc flow is fixed
     * 
     * @param shortVdcId
     * @param nonSharedClient
     * @return
     */
    @Deprecated
    public GeoServiceClient getNonSharedGeoClient(String shortVdcId) {
        GeoServiceClient client = createClient(lookupVdc(shortVdcId));
        log.info("returning non-shared client for {}", shortVdcId);
        return client;
    }

    private VirtualDataCenter lookupVdc(String shortVdcId) {
        URI vdcURN = dbClient.getVdcUrn(shortVdcId);
        // TODO: convert to the appropriate ViPR exception
        if (vdcURN == null) {
            throw new IllegalArgumentException("unknown vdc id " + shortVdcId);
        }
        VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcURN);
        // TODO: convert to the proper ViPR exception
        if (vdc == null) {
            throw new IllegalArgumentException("vdc does not exist: " + vdcURN);
        }
        return vdc;
    }

    private GeoServiceClient createClient(VirtualDataCenter vdc) {
        GeoServiceClient client = new GeoServiceClient();
        client.setCoordinatorClient(coordinatorClient);
        client.setServer(vdc.getApiEndpoint());
        client.setSecretKey(vdc.getSecretKey());
        // set both timeout values to 30s due to CTRL-2779
        client.setClientConnectTimeout(_clientConnTimeout);
        client.setClientReadTimeout(_clientReadTimeout);
        client.addFilter(new ServiceClientRetryFilter(client.getClientMaxRetries(), client.getClientRetryInterval()));
        client.addFilter(new GeoServiceExceptionFilter());
        
        return client;
    }

    private GeoServiceClient createClient(Properties vdcInfo) {
        GeoServiceClient client = new GeoServiceClient();
        client.setCoordinatorClient(coordinatorClient);
        client.setServer(vdcInfo.getProperty(GeoServiceJob.VDC_API_ENDPOINT));
        client.setSecretKey(vdcInfo.getProperty(GeoServiceJob.VDC_SECRETE_KEY));
        // set both timeout values to 30s due to CTRL-2779
        client.setClientConnectTimeout(_clientConnTimeout);
        client.setClientReadTimeout(_clientReadTimeout);
        client.addFilter(new ServiceClientRetryFilter(client.getClientMaxRetries(), client.getClientRetryInterval()));
        client.addFilter(new GeoServiceExceptionFilter());
        return client;
    }
}
