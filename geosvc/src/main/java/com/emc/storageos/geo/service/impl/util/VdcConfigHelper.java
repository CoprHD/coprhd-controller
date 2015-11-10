/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.service.impl.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.cassandra.cli.CliMain;
import org.apache.cassandra.cli.CliOptions;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.CustomConfig;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.PasswordHistory;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Token;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.VirtualDataCenter.ConnectionStatus;
import com.emc.storageos.db.client.model.VirtualDataCenter.GeoReplicationStatus;
import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.server.impl.SchemaUtil;
import com.emc.storageos.geo.vdccontroller.impl.InternalDbClient;
import com.emc.storageos.geomodel.VdcCertListParam;
import com.emc.storageos.geomodel.VdcCertParam;
import com.emc.storageos.geomodel.VdcConfig;
import com.emc.storageos.geomodel.VdcNodeCheckParam;
import com.emc.storageos.geomodel.VdcNodeCheckResponse;
import com.emc.storageos.geomodel.VdcPostCheckParam;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.security.geo.GeoServiceJob;
import com.emc.storageos.security.geo.exceptions.GeoException;
import com.emc.storageos.security.keystore.impl.CertificateVersionHelper;
import com.emc.storageos.security.keystore.impl.CoordinatorConfigStoringHelper;
import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import com.emc.storageos.security.keystore.impl.KeystoreEngine;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.vipr.model.sys.ClusterInfo;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

public class VdcConfigHelper {
    private static final Logger log = LoggerFactory.getLogger(VdcConfigHelper.class);

    public static final String ENCRYPTION_CONFIG_KIND = "encryption";
    public static final String ENCRYPTION_CONFIG_ID = "geoid";
    public static final String LOCAL_HOST = "127.0.0.1";

    private final static int NODE_CHECK_TIMEOUT = 60 * 1000; // one minute

    private static final int NODE_REACHABLE_TIMEOUT = 30 * 1000; // 30 seconds
    private static final int NODE_REACHABLE_PORT = 4443;

    // CTRL-2859,3393 Add reboot delay to allow sync process to succeed
    private ScheduledExecutorService wakeupExecutor = Executors.newScheduledThreadPool(1);
    private static final int WAKEUP_DELAY = 15; // seconds

    @Autowired
    private CoordinatorClient coordinatorClient;

    @Autowired
    private InternalDbClient dbClient;

    @Autowired
    private CoordinatorClient coordinator;

    @Autowired
    private GeoClientCacheManager geoClientCache;

    @Autowired
    private CoordinatorConfigStoringHelper coordConfigStoringHelper;

    @Autowired
    private CertificateVersionHelper certificateVersionHelper;

    public void setDbClient(InternalDbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setGeoClientCacheManager(GeoClientCacheManager clientCache) {
        this.geoClientCache = clientCache;
    }

    public void setCoordinatorClient(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    private KeyStore keystore;

    private void initKeyStore() {
        if (keystore == null) {
            try {
                keystore = KeyStoreUtil.getViPRKeystore(coordinatorClient);
            } catch (Exception e) {
                log.error("Failed to load the VIPR keystore", e);
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * This method should be invoked by VDC controller on the same node, since the VDC controller
     * cannot clean up the db of the current VDC, just need to sync the VDC records.
     * 
     * @param newVdcConfigList a list of VDC records to sync up with.
     */
    public void syncVdcConfig(List<VdcConfig> newVdcConfigList) {
        syncVdcConfig(newVdcConfigList, null);
    }

    /**
     * Sync Vdc config to local db. It writes new vdc list to local db and triggers
     * vdc config properties update on each node.
     * 
     * @param newVdcConfigList - new vdc config list
     * @param assignedVdcId - vdc short id for a newly joined vdc. Otherwise null
     */
    public void syncVdcConfig(List<VdcConfig> newVdcConfigList, String assignedVdcId) {
        syncVdcConfig(newVdcConfigList, assignedVdcId, false);
    }

    public void syncVdcConfig(List<VdcConfig> newVdcConfigList, String assignedVdcId, boolean isRecover) {
        // query existing vdc list from db
        // The new queryByType method returns an iterative list, convert it to a "real"
        // list first
        List<URI> vdcIdList = new ArrayList<URI>();
        for (URI vdcId : dbClient.queryByType(VirtualDataCenter.class, true)) {
            vdcIdList.add(vdcId);
        }

        // compare newVdcConfigList with what in local db. finally vdcIdList contains
        // vdc that are going to be removed from local db
        for (VdcConfig config : newVdcConfigList) {
            if (vdcIdList.contains(config.getId())) {
                vdcIdList.remove(config.getId());
                mergeVdcConfig(config, isRecover);
            } else {
                // not contains in vdcIdList - it is a new vdc and we should insert to db
                VirtualDataCenter newVdc = fromConfigParam(config);
                if (config.getId().toString().equals(assignedVdcId)) {
                    newVdc.setLocal(true);
                }
                dbClient.createObject(newVdc);
                if (newVdc.getLocal()) {
                    VdcUtil.invalidateVdcUrnCache();
                }
            }
        }

        // check vdc that are going to be removed
        ArrayList<String> obsoletePeers = new ArrayList<String>();
        for (URI removeVdcId : vdcIdList) {
            log.warn("vdc config {} is being removed", removeVdcId);
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, removeVdcId);
            ConnectionStatus connStatus = vdc.getConnectionStatus();
            if (!isRecover && connStatus.equals(ConnectionStatus.CONNECT_FAILED)) {
                log.info("Ignore vdc record {} with status {}", removeVdcId, connStatus);
                continue;
            }
            dbClient.markForDeletion(vdc);
            Map<String, String> addressesMap = vdc.queryHostIPAddressesMap();
            if (!addressesMap.isEmpty()) {
                // obsolete peers ip in cassandra system table
                obsoletePeers.addAll(addressesMap.values());
                log.info("add {} peers to obsolete list", addressesMap.size());
            }

            dbClient.removeVdcNodesFromBlacklist(vdc);
        }

        if (!obsoletePeers.isEmpty()) {
            // update peer ip to ZK so that geodbsvc could get it
            notifyDbSvcWithObsoleteCassandraPeers(Constants.GEODBSVC_NAME, obsoletePeers);
            log.info("notify geodbsvc with {} obsolete cassandra peers", obsoletePeers.size());
        }

        if (assignedVdcId != null) {
            // Persist a flag to notify geodbsvc on all the nodes in the current vdc
            log.info("reset db needed, set the flag for all db to look it up");
            updateDbSvcConfig(Constants.GEODBSVC_NAME, Constants.REINIT_DB, String.valueOf(true));
        }

        // trigger syssvc to update the vdc config to all the nodes in the current vdc
        // add a small deley so that sync process can finish
        wakeupExecutor.schedule(new Runnable() {
            public void run() {
                for (Service syssvc : coordinator.locateAllServices(
                        ((CoordinatorClientImpl) coordinator).getSysSvcName(),
                        ((CoordinatorClientImpl) coordinator).getSysSvcVersion(), null, null)) {
                    try {
                        log.info("waking up node: {}", syssvc.getNodeId());
                        SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(
                                syssvc.getEndpoint());
                        sysClient.setCoordinatorClient(coordinator);
                        sysClient.post(SysClientFactory.URI_WAKEUP_PROPERTY_MANAGER, null, null);
                    } catch (Exception e) {
                        log.error("Error waking up node: {} Cause:", syssvc.getNodeId(), e);
                    }
                }
            }
        }, WAKEUP_DELAY, TimeUnit.SECONDS);
    }

    public void syncVdcConfigPostSteps(VdcPostCheckParam checkParam) {
        // TODO: verify network strategy
        String type = checkParam.getConfigChangeType();
        VdcConfig.ConfigChangeType configChangeType = VdcConfig.ConfigChangeType.valueOf(type);

        switch (configChangeType) {
            case CONNECT_VDC:
                connectVdcPostCheck(checkParam);
                break;
            case DISCONNECT_VDC:
                disconnectVdcPostCheck(checkParam);
                break;
            case RECONNECT_VDC:
                reconnectVdcPostCheck(checkParam);
                break;
            default:
                log.error("Post check is not supported for {}", configChangeType);
                return;
        }
    }

    private void connectVdcPostCheck(VdcPostCheckParam checkParam) {
        URI root = getVdcRootTenantId();
        if (root != null) {
            if (!root.equals(checkParam.getRootTenantId())) {
                throw new IllegalStateException("root tenant id is different, sync vdc config must failed");
            }
        } else {
            throw new IllegalStateException("root tenant not exist, sync vdc config must failed");
        }
        log.info("Post check: Vdc root tenant id {} check passed", root);

        // Verify if sync vdc config apply successfully
        List<URI> serverVdcList = checkParam.getVdcList();
        List<URI> vdcIdList = dbClient.queryByType(VirtualDataCenter.class, true);
        List<VirtualDataCenter> connectedVdc = new ArrayList<>();
        for (URI vdcId : vdcIdList) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcId);
            if (!serverVdcList.contains(vdcId)) {
                // Not in the list of connected vdc
                // Some records not exist in server side, something must be wrong
                if ((vdc.getConnectionStatus() != null) &&
                        (vdc.getConnectionStatus() == ConnectionStatus.ISOLATED ||
                        vdc.getConnectionStatus() == ConnectionStatus.CONNECTED)) {
                    throw new IllegalStateException("some connected vdc status is not correct, sync vdc config must failed");
                }
            } else {
                serverVdcList.remove(vdcId);
                connectedVdc.add(vdc);
            }
        }
        if (!serverVdcList.isEmpty()) {
            // Some records not exist in this site, something must be wrong
            throw new IllegalStateException("some connected vdc not exist, sync vdc config must failed");
        }

        log.info("Post check: number of connected vdc {} ", connectedVdc.size());
        // Update the status
        for (VirtualDataCenter vdc : connectedVdc) {
            // mark as CONNECTED if currently not
            if ((vdc.getConnectionStatus() == null) ||
                    (vdc.getConnectionStatus() != ConnectionStatus.CONNECTED)) {
                vdc.setConnectionStatus(ConnectionStatus.CONNECTED);
                vdc.setRepStatus(GeoReplicationStatus.REP_ALL);
                dbClient.updateAndReindexObject(vdc);
                log.info("Post check: update vdc status to connected {} ", vdc.getShortId());
            }
        }
    }

    private void disconnectVdcPostCheck(VdcPostCheckParam checkParam) {
    }

    private void reconnectVdcPostCheck(VdcPostCheckParam checkParam) {
    }

    private VirtualDataCenter getDisconnectedVirtualDataCenter(VdcPostCheckParam checkParam) {
        List<URI> vdcIds = checkParam.getVdcList();

        if (vdcIds.size() != 1) {
            String errMsg = String.format("There are more than one disconnected VDCs:%s", vdcIds.toString());
            throw new IllegalArgumentException(errMsg);
        }

        URI vdcId = vdcIds.get(0);

        VirtualDataCenter disconnectedVdc = dbClient.queryObject(VirtualDataCenter.class, vdcId);

        if (disconnectedVdc == null) {
            throw GeoException.fatals.disconnectVdcFailed(vdcId, new Exception("The disconnected vdc can't be found"));
        }
        return disconnectedVdc;
    }

    public URI getVdcRootTenantId() {
        URIQueryResultList tenants = new URIQueryResultList();

        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getTenantOrgSubTenantConstraint(URI.create(TenantOrg.NO_PARENT)),
                tenants);
        if (tenants.iterator().hasNext()) {
            return tenants.iterator().next();
        }
        return null;
    }

    public void resetStaleLocalObjects() {
        Class[] resetClasses = new Class[] { Token.class, StorageOSUserDAO.class, PasswordHistory.class, CustomConfig.class,
                TenantOrg.class };
        for (Class clazz : resetClasses) {
            List<URI> idList = dbClient.queryByType(clazz, true);
            log.info("Reset data object {}", clazz);
            for (URI key : idList) {
                DataObject obj = dbClient.queryObject((Class<? extends DataObject>) clazz, key);
                dbClient.removeObject(obj);
                log.info("Remove {}", key);
            }
        }
    }

    public String getGeoEncryptionKey() {
        Configuration config = coordinator.queryConfiguration(ENCRYPTION_CONFIG_KIND,
                ENCRYPTION_CONFIG_ID);
        if (config == null) {
            throw new IllegalStateException("Geo encryption key not generated yet.");
        }

        return config.getConfig(ENCRYPTION_CONFIG_KIND);
    }

    public void setGeoEncryptionKey(String geoEncryptionKey) {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(ENCRYPTION_CONFIG_KIND);
        config.setId(ENCRYPTION_CONFIG_ID);
        config.setConfig(ENCRYPTION_CONFIG_KIND, geoEncryptionKey);
        coordinator.persistServiceConfiguration(config);
    }

    private void mergeVdcConfig(VdcConfig targetVdc) {
        mergeVdcConfig(targetVdc, false);
    }

    private void mergeVdcConfig(VdcConfig targetVdc, boolean isRecover) {
        VirtualDataCenter srcVdc = dbClient.queryObject(VirtualDataCenter.class,
                targetVdc.getId());

        if (!isRecover && targetVdc.getVersion() < srcVdc.getVersion()) {
            log.info("VDC config for {} is older than that from the local db and is " +
                    "ignored.", targetVdc.getId());
            return;
        }

        boolean isChanged = false;

        log.info("mergeVdcConfig - Vdc connection status {}, new status {}",
                srcVdc.getConnectionStatus(), targetVdc.getConnectionStatus());
        if (!isEqual(srcVdc.getConnectionStatus(), targetVdc.getConnectionStatus())) {
            isChanged = true;
            if (srcVdc.getLocal()) {
                log.warn("The local VDC connection status changes from {} to {} " +
                        "according to remote VDC config.", srcVdc.getConnectionStatus(),
                        targetVdc.getConnectionStatus());
            }
            if (targetVdc.getConnectionStatus() != null) {
                srcVdc.setConnectionStatus(Enum.valueOf(ConnectionStatus.class, targetVdc.getConnectionStatus()));
            }
        }

        if (!isEqual(srcVdc.getRepStatus(), targetVdc.getRepStatus())) {
            isChanged = true;
            if (srcVdc.getLocal()) {
                log.warn("The local VDC rep status changes from {} to {} " +
                        "according to remote VDC config.", srcVdc.getRepStatus(),
                        targetVdc.getRepStatus());
            }
            if (targetVdc.getRepStatus() != null) {
                srcVdc.setRepStatus(Enum.valueOf(GeoReplicationStatus.class, targetVdc.getRepStatus()));
            }
        }

        if (!isEqual(srcVdc.getVersion(), targetVdc.getVersion())) {
            isChanged = true;
            if (srcVdc.getLocal()) {
                log.warn("The local VDC version changes from {} to {} according to " +
                        "remote VDC config.", srcVdc.getVersion(), targetVdc.getVersion());
            }
            srcVdc.setVersion(targetVdc.getVersion());
        }

        if (!isEqual(srcVdc.getHostCount(), targetVdc.getHostCount())) {
            isChanged = true;
            if (srcVdc.getLocal()) {
                log.warn("The local VDC host count changes from {} to {} according to " +
                        "remote VDC config.", srcVdc.getHostCount(),
                        targetVdc.getHostCount());
            }
            srcVdc.setHostCount(targetVdc.getHostCount());
        }

        // TODO: need to revisit this logic
        HashMap<String, String> tgtHostList = targetVdc.getHostIPv4AddressesMap();
        if (!isEqual(srcVdc.getHostIPv4AddressesMap(), tgtHostList)) {
            if (srcVdc.getLocal()) {
                log.warn("The local VDC host list changes from {} to {} according to remote " +
                        " VDC config.", srcVdc.getHostIPv4AddressesMap(), targetVdc.getHostIPv4AddressesMap());
            }
            srcVdc.getHostIPv4AddressesMap().replace(tgtHostList);
            log.info("after merge src IPv4={}", srcVdc.getHostIPv4AddressesMap());
            isChanged = true;
        }

        tgtHostList = targetVdc.getHostIPv6AddressesMap();
        if (!isEqual(srcVdc.getHostIPv6AddressesMap(), tgtHostList)) {
            if (srcVdc.getLocal()) {
                log.warn("The local VDC host list changes from {} to {} according to remote " +
                        " VDC config.", srcVdc.getHostIPv6AddressesMap(), targetVdc.getHostIPv6AddressesMap());
            }
            srcVdc.getHostIPv6AddressesMap().replace(tgtHostList);
            isChanged = true;
        }

        if (!isEqual(srcVdc.getLabel(), targetVdc.getName())) {
            isChanged = true;
            if (srcVdc.getLocal()) {
                log.warn("The local VDC label changes from {} to {} according to remote " +
                        " VDC config.", srcVdc.getLabel(), targetVdc.getName());
            }
            srcVdc.setLabel(targetVdc.getName());
        }

        if (!isEqual(srcVdc.getDescription(), targetVdc.getDescription())) {
            isChanged = true;
            if (srcVdc.getLocal()) {
                log.warn("The local VDC description changes from {} to {} according to " +
                        "remote VDC config.", srcVdc.getDescription(),
                        targetVdc.getDescription());
            }
            srcVdc.setDescription(targetVdc.getDescription());
        }

        if (!isEqual(srcVdc.getApiEndpoint(), targetVdc.getApiEndpoint())) {
            isChanged = true;
            if (srcVdc.getLocal()) {
                log.warn("The local VDC API endpoint changes from {} to {} according to " +
                        "remote VDC config.", srcVdc.getApiEndpoint(),
                        targetVdc.getApiEndpoint());
            }
            srcVdc.setApiEndpoint(targetVdc.getApiEndpoint());
        }

        if (!isEqual(srcVdc.getSecretKey(), targetVdc.getSecretKey())) {
            srcVdc.setSecretKey(targetVdc.getSecretKey());
            isChanged = true;
            if (srcVdc.getLocal()) {
                log.warn("The local VDC security key changes from {} to {} according to " +
                        "remote VDC config.", srcVdc.getSecretKey(),
                        targetVdc.getSecretKey());
            }
        }

        if (!isEqual(srcVdc.getShortId(), targetVdc.getShortId())) {
            isChanged = true;
            log.warn("Short id of VDC {} changes from {} to {} according to remote VDC " +
                    "config.", new Object[] { srcVdc.getId(), srcVdc.getShortId(),
                    targetVdc.getShortId() });
            srcVdc.setShortId(targetVdc.getShortId());
        }

        if (!isEqual(srcVdc.getGeoCommandEndpoint(), targetVdc.getGeoCommandEndpoint())) {
            isChanged = true;
            log.warn("GeoCommandEndpoint of VDC {} changes from {} to {} according to remote VDC " +
                    "config.", new Object[] { srcVdc.getGeoCommandEndpoint(), srcVdc.getGeoCommandEndpoint(),
                    targetVdc.getGeoCommandEndpoint() });
            srcVdc.setGeoCommandEndpoint(targetVdc.getGeoCommandEndpoint());
        }

        if (!isEqual(srcVdc.getGeoDataEndpoint(), targetVdc.getGeoDataEndpoint())) {
            isChanged = true;
            log.warn("GeoDataEndpoint of VDC {} changes from {} to {} according to remote VDC " +
                    "config.", new Object[] { srcVdc.getGeoDataEndpoint(), srcVdc.getGeoDataEndpoint(),
                    targetVdc.getGeoDataEndpoint() });
            srcVdc.setGeoDataEndpoint(targetVdc.getGeoDataEndpoint());
        }

        // If this vdc is isolated, should then reset repstatus to rep_none
        log.info("Checking if Vdc {} is isolated ...{}", targetVdc.getId(), targetVdc.getConnectionStatus());
        if (targetVdc.getConnectionStatus().equals(ConnectionStatus.ISOLATED.toString())) {
            log.info("Vdc {} is isolated, setting georep state to not replicated.", targetVdc.getShortId());
            isChanged = true;
            srcVdc.setRepStatus(GeoReplicationStatus.REP_NONE);
        }

        if (isChanged) {
            dbClient.updateAndReindexObject(srcVdc);
        }
    }

    private VirtualDataCenter fromConfigParam(VdcConfig config) {
        VirtualDataCenter vdc = new VirtualDataCenter();

        vdc.setId(config.getId());
        if (config.getConnectionStatus() != null) {
            vdc.setConnectionStatus(Enum.valueOf(ConnectionStatus.class, config.getConnectionStatus()));
        }

        if (config.getRepStatus() != null) {
            vdc.setRepStatus(Enum.valueOf(GeoReplicationStatus.class, config.getRepStatus()));
        }

        vdc.setVersion(config.getVersion());
        vdc.setShortId(config.getShortId());
        vdc.setHostCount(config.getHostCount());

        final HashMap<String, String> tgtHostIPv4AddressesMap = config.getHostIPv4AddressesMap();
        StringMap addrMap = new StringMap();
        addrMap.putAll(tgtHostIPv4AddressesMap);
        vdc.setHostIPv4AddressesMap(addrMap);

        final HashMap<String, String> tgtHostIPv6AddressesMap = config.getHostIPv6AddressesMap();
        addrMap = new StringMap();
        addrMap.putAll(tgtHostIPv6AddressesMap);
        vdc.setHostIPv6AddressesMap(addrMap);

        vdc.setLabel(config.getName());
        vdc.setDescription(config.getDescription());
        vdc.setApiEndpoint(config.getApiEndpoint());
        vdc.setSecretKey(config.getSecretKey());
        vdc.setLocal(false);

        vdc.setGeoCommandEndpoint(config.getGeoCommandEndpoint());
        vdc.setGeoDataEndpoint(config.getGeoDataEndpoint());

        return vdc;
    }

    private boolean isEqual(Object src, Object tgt) {
        if (src == null) {
            return tgt == null;
        }

        return src.equals(tgt);
    }

    /**
     * Notify dbsvc with a list of obsolete cassandra peers, so that it could remove them before start
     * 
     * @param peerList
     */
    private void notifyDbSvcWithObsoleteCassandraPeers(String svcName, List<String> peerList) {
        String result = StringUtils.join(peerList.iterator(), ",");
        updateDbSvcConfig(svcName, Constants.OBSOLETE_CASSANDRA_PEERS, result);
    }

    public void updateDbSvcConfig(String svcName, String key, String value) {
        String kind = coordinator.getDbConfigPath(svcName);
        try {
            List<Configuration> configs = coordinator.queryAllConfiguration(kind);
            if (configs == null) {
                String errMsg = "No " + svcName + " config found in the current vdc";
                log.error(errMsg);
                throw new IllegalStateException(errMsg);
            }

            for (Configuration config : configs) {
                if (config.getId() == null) {
                    // version Znodes, e.g., /config/dbconfig/1.1
                    continue;
                }
                if (config.getId().equals(Constants.GLOBAL_ID)) {
                    continue;
                }
                config.setConfig(key, value);
                coordinator.persistServiceConfiguration(config);
            }
        } catch (CoordinatorException e) {
            throw new IllegalStateException(e);
        }
    }

    public void persistVdcCert(KeyStore keystore, String alias, String certstr, Boolean certchain) {
        // put vdc cert into trust-store
        log.info("Persisting cert of vdc {} into local trust-store ...", alias);
        try {
            if (certchain) {
                Certificate[] chain = null;
                chain = KeyCertificatePairGenerator.getCertificateChainFromString(certstr);
                if (ArrayUtils.isEmpty(chain)) {
                    throw APIException.badRequests.failedToLoadCertificateFromString(certstr);
                }
                keystore.setCertificateEntry(alias, chain[0]);
            } else {
                Certificate cert = KeyCertificatePairGenerator.getCertificateFromString(certstr);
                if (cert == null) {
                    throw APIException.badRequests.failedToLoadCertificateFromString(certstr);
                }
                keystore.setCertificateEntry(alias, cert);
            }
        } catch (CertificateException e) {
            log.error(e.getMessage(), e);
            throw APIException.badRequests.failedToLoadCertificateFromString(certstr, e);
        } catch (KeyStoreException e) {
            log.error(e.getMessage(), e);
            throw APIException.badRequests.failedToStoreCertificateInKeyStore(e);
        }
    }

    public void syncVdcCerts(VdcCertListParam vdcCertListParam) {
        initKeyStore();

        try {
            if (vdcCertListParam.getCmd().equals(VdcCertListParam.CMD_UPDATE_CERT)) {
                persistVdcCert(keystore, vdcCertListParam.getTargetVdcId(), vdcCertListParam.getTargetVdcCert(), false);
                if (!certificateVersionHelper.updateCertificateVersion()) {
                    throw SecurityException.fatals.failedToUpdateKeyCertificateEntry();
                }
            } else if (vdcCertListParam.getCmd().equals(VdcCertListParam.CMD_ADD_CERT)) {
                Boolean newVdc = false;
                VirtualDataCenter vdc = VdcUtil.getLocalVdc();
                log.info("current status of local vdc is {}", vdc.getConnectionStatus().toString());
                Certificate cert = KeyCertificatePairGenerator.getCertificateFromString(vdcCertListParam.getTargetVdcCert());
                if (KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS == keystore.getCertificateAlias(cert) ||
                        vdc.getConnectionStatus().equals(ConnectionStatus.RECONNECTING)) {
                    log.info("syncing certs to new vdc or a vdc need to be reconnected back to the federation...");
                    newVdc = true;
                } else {
                    log.info("syncing new vdc's cert to existed vdc ...");
                }

                if (newVdc) {
                    // add all connected vdcs' certs into target vdc trust-store
                    for (VdcCertParam certParam : vdcCertListParam.getVdcCerts()) {
                        persistVdcCert(keystore, certParam.getVdcId().toString(), certParam.getCertificate(), false);
                    }
                } else {
                    // add target vdc's cert into current vdc trust-store
                    persistVdcCert(keystore, vdcCertListParam.getTargetVdcId(), vdcCertListParam.getTargetVdcCert(), false);
                }
            }
        } catch (CertificateException e) {
            log.error(e.getMessage(), e);
            throw APIException.badRequests.failedToLoadCertificateFromString(
                    vdcCertListParam.getTargetVdcCert(), e);
        } catch (KeyStoreException e) {
            log.error(e.getMessage(), e);
            throw APIException.badRequests.failedToStoreCertificateInKeyStore(e);
        }
    }

    public void setKeyCertchain(Boolean selfsigned, byte[] key, Certificate[] chain) {
        initKeyStore();

        try {
            KeyStoreUtil.setSelfGeneratedCertificate(coordConfigStoringHelper, selfsigned);
            keystore.setKeyEntry(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS, key, chain);
            if (!certificateVersionHelper.updateCertificateVersion()) {
                throw SecurityException.fatals.failedToUpdateKeyCertificateEntry();
            }
        } catch (KeyStoreException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    public boolean isCompatibleVersion(SoftwareVersion remoteVer) {
        log.info("Remote version is {}", remoteVer);

        VirtualDataCenter localVdc = VdcUtil.getLocalVdc();
        String viprVersion = getViPRVersion(localVdc.getShortId());
        log.info("My vipr version is {}", viprVersion);

        SoftwareVersion myViprVersion = new SoftwareVersion(viprVersion);

        if (myViprVersion.compareTo(remoteVer) >= 0 ) {
            log.info("version compatible");
            return true;
        }

        log.info("version not compatible");
        return false;
    }

    /**
     * Cehck to see if the local cluster is stable
     * 
     * @return true if state is STABLE
     */
    public boolean isClusterStable() {
        log.info("Checking if local cluster is stable...");
        return ((((DbClientImpl) dbClient).getCoordinatorClient().getControlNodesState() == ClusterInfo.ClusterState.STABLE) && isGeodbServiceStable());
    }

    /**
     * Check to see if all nodes' geodbsvc is up and running
     * 
     * @return true if service count equals node count
     */
    public boolean isGeodbServiceStable() {
        List<Service> services = coordinator.locateAllServices(Constants.GEODBSVC_NAME, dbClient.getSchemaVersion(), null, null);
        log.info("Checking if all geosvcs are up, geosvc count-{}, node count-{}", services.size(),
                ((CoordinatorClientImpl) coordinator).getNodeCount());
        return (((CoordinatorClientImpl) coordinator).getNodeCount() == services.size());
    }

    /**
     * Build VdcConfig for a vdc for SyncVdcConfig call
     * 
     * @param vdc
     * @return
     */
    public VdcConfig toConfigParam(VirtualDataCenter vdc) {
        log.info("copy {} to the sync config param", vdc.getShortId());
        VdcConfig vdcConfig = new VdcConfig();

        vdcConfig.setId(vdc.getId());
        vdcConfig.setShortId(vdc.getShortId());
        vdcConfig.setSecretKey(vdc.getSecretKey());

        if ((vdc.getLabel() != null) && (!vdc.getLabel().isEmpty())) {
            vdcConfig.setName(vdc.getLabel());
        }
        if ((vdc.getDescription() != null) && (!vdc.getDescription().isEmpty())) {
            vdcConfig.setDescription(vdc.getDescription());
        }
        if (vdc.getApiEndpoint() != null) {
            vdcConfig.setApiEndpoint(vdc.getApiEndpoint());
        }

        vdcConfig.setHostCount(vdc.getHostCount());

        vdcConfig.setHostIPv4AddressesMap(vdc.getHostIPv4AddressesMap());
        vdcConfig.setHostIPv6AddressesMap(vdc.getHostIPv6AddressesMap());

        vdcConfig.setVersion(vdc.getVersion());
        vdcConfig.setConnectionStatus(vdc.getConnectionStatus().toString());
        vdcConfig.setRepStatus(vdc.getRepStatus().toString());
        vdcConfig.setGeoCommandEndpoint(vdc.getGeoCommandEndpoint());
        vdcConfig.setGeoDataEndpoint(vdc.getGeoDataEndpoint());

        return vdcConfig;
    }

    /**
     * Build VdcConfig for a vdc for SyncVdcConfig call
     * 
     * @param vdcInfo
     * @return
     */
    public VdcConfig toConfigParam(Properties vdcInfo) {
        log.info("copy {} to the sync config param", vdcInfo.getProperty(GeoServiceJob.VDC_SHORT_ID));
        VdcConfig vdcConfig = new VdcConfig();
        vdcConfig.setId(URIUtil.uri(vdcInfo.getProperty(GeoServiceJob.OPERATED_VDC_ID)));

        vdcConfig.setShortId(vdcInfo.getProperty(GeoServiceJob.VDC_SHORT_ID));
        vdcConfig.setSecretKey(vdcInfo.getProperty(GeoServiceJob.VDC_SECRETE_KEY));

        String name = vdcInfo.getProperty(GeoServiceJob.VDC_NAME);
        if ((name != null) && (!name.isEmpty())) {
            vdcConfig.setName(name);
        }
        String description = vdcInfo.getProperty(GeoServiceJob.VDC_DESCRIPTION);
        if ((description != null) && (!description.isEmpty())) {
            vdcConfig.setDescription(description);
        }
        String endPnt = vdcInfo.getProperty(GeoServiceJob.VDC_API_ENDPOINT);
        if (endPnt != null) {
            vdcConfig.setApiEndpoint(endPnt);
        }
        vdcConfig.setGeoCommandEndpoint(vdcInfo.getProperty(GeoServiceJob.VDC_GEOCOMMAND_ENDPOINT));
        vdcConfig.setGeoDataEndpoint(vdcInfo.getProperty(GeoServiceJob.VDC_GEODATA_ENDPOINT));

        return vdcConfig;
    }

    /**
     * tries to connect to each node in each vdc
     * 
     * @param vdcList
     * @return true if all nodes in all vdc's are reachable
     */
    public boolean areNodesReachable(List<VdcConfig> vdcList, boolean isAllNotReachable) {
        if (vdcList == null || vdcList.isEmpty()) {
            throw new IllegalStateException("No vdc's passed in node reachable check request");
        }
        for (VdcConfig vdc : vdcList) {
            if (!areNodesReachable(vdc.getShortId(), vdc.getHostIPv4AddressesMap(), vdc.getHostIPv6AddressesMap(), isAllNotReachable)) {
                return false;
            }
        }
        return true;
    }

    /**
     * tries to connect to each node in each vdc
     * 
     * @param vdcId - the Id of the target VDC
     * @return true if all nodes of the target Vdc is reachable
     */
    public boolean areNodesReachable(URI vdcId) {
        if (vdcId == null) {
            throw new IllegalArgumentException("The target Vdc short ID should not be null or empty");
        }

        VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcId);

        if (areNodesReachable(vdc.getShortId(), vdc.getHostIPv4AddressesMap(), vdc.getHostIPv6AddressesMap(), false)) {
            return true;
        }

        return false;
    }

    /**
     * tries to connect to each node in either the ipv4 list or the ipv6 list (ipv4 is default)
     * 
     * @param vdcShortId short id of vdc where node ip's are from
     * @param ipv4
     * @param ipv6
     * @return true if all nodes are reachable
     */
    public boolean areNodesReachable(String vdcShortId, Map<String, String> ipv4, Map<String, String> ipv6, boolean isAllNotReachable) {
        List<String> ips = new ArrayList<String>();
        if (ipv4 != null && !ipv4.isEmpty()) {
            ips.addAll(ipv4.values());
        } else if (ipv6 != null && !ipv6.isEmpty()) {
            ips.addAll(ipv6.values());
        } else {
            throw new IllegalStateException("Cannot perform node reachable check on vdc " + vdcShortId
                    + " no nodes were found on VdcConfig object");
        }

        for (String host : ips) {
            log.info("Testing connection to ip address : " + host);

            Socket socket = null;
            try {
                socket = new Socket();
                InetSocketAddress endpoint = new InetSocketAddress(InetAddress.getByName(host), NODE_REACHABLE_PORT);
                socket.connect(endpoint, NODE_REACHABLE_TIMEOUT);
                if (isAllNotReachable) {
                    return true;
                }
            } catch (IOException e) {
                if (isAllNotReachable) {
                    log.info("Disconnect node check, could NOT access node {}, will continue check.", host);
                    continue;
                }
                log.error("Could not connect to server {}", host);
                log.error(e.getMessage(), e);
                return false;
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        log.error("Exception closing socket");
                        log.error(e.getMessage(), e);
                    }
                }
            }

            log.info("ip address " + host + " is reachable");
        }
        // For disconnect vdc, isAllNotReachable will be true, if the code reaches here, means all the host can not access
        // and for disconnect ops, can not reachable means all the nodes can not access.
        if (isAllNotReachable) {
            log.info("All nodes are not reachable.");
            return false;
        } else {
            return true;
        }
    }

    /**
     * Send node check request to target vdc.
     * 
     * @param sendToVdc vdc to send msg to
     * @param vdcsToCheck list of vdc's with nodes to check
     * @return
     * @throws Exception
     */
    public VdcNodeCheckResponse sendVdcNodeCheckRequest(VirtualDataCenter sendToVdc, Collection<VirtualDataCenter> vdcsToCheck) {
        List<VdcConfig> virtualDataCenters = new ArrayList<VdcConfig>();
        for (VirtualDataCenter vdc : vdcsToCheck) {
            VdcConfig vdcConfig = new VdcConfig();
            vdcConfig.setId(vdc.getId());
            vdcConfig.setShortId(vdc.getShortId());
            if (vdc.getHostIPv4AddressesMap() != null && !vdc.getHostIPv4AddressesMap().isEmpty()) {
                vdcConfig.setHostIPv4AddressesMap(vdc.getHostIPv4AddressesMap());
            } else if (vdc.getHostIPv6AddressesMap() != null && !vdc.getHostIPv6AddressesMap().isEmpty()) {
                vdcConfig.setHostIPv6AddressesMap(vdc.getHostIPv6AddressesMap());
            } else {
                throw new IllegalStateException("Cannot perform node reachable check on vdc " + vdc.getShortId()
                        + " no nodes were found on VirtualDataCenter object");
            }
            virtualDataCenters.add(vdcConfig);
        }
        return sendVdcNodeCheckRequest(sendToVdc, virtualDataCenters);
    }

    /**
     * @param sendToVdc
     * @param virtualDataCenters
     * @return
     */
    private VdcNodeCheckResponse sendVdcNodeCheckRequest(VirtualDataCenter sendToVdc, List<VdcConfig> virtualDataCenters) {
        log.info("sending {} vdcs to {} to be checked", virtualDataCenters.size(), sendToVdc.getShortId());
        VdcNodeCheckParam param = new VdcNodeCheckParam();
        param.setVirtualDataCenters(virtualDataCenters);
        try {
            GeoServiceClient client = resetGeoClientCacheTimeout(sendToVdc.getShortId(), null, NODE_CHECK_TIMEOUT);
            return client.vdcNodeCheck(param);
        } finally {
            // clear the client cache to reset timeouts that were altered above
            geoClientCache.clearCache();
        }
    }

    /*
     * Use this method to reset GeoServiceClient timeout value
     * For add vdc, it should be 1 min
     * For disconnect node reachable check, should be 3 mins
     * For reconnect node reachable check, should be 1 min
     */
    public GeoServiceClient resetGeoClientCacheTimeout(String vdcShortId, Properties vdcInfo, int nodeCheckTimeout_ms) {
        // clear the client cache to ensure that we create a new connection with a longer timeout
        // timeout for makeing the geosvc socket connection is 30 seconds, so this timeout needs to be longer than that
        geoClientCache.clearCache();
        GeoServiceClient client;
        if (vdcInfo == null) {
            client = geoClientCache.getGeoClient(vdcShortId);
        } else {
            client = geoClientCache.getGeoClient(vdcInfo);
        }
        client.setClientConnectTimeout(nodeCheckTimeout_ms);
        client.setClientReadTimeout(nodeCheckTimeout_ms);
        return client;
    }

    public void addVdcToCassandraStrategyOptions(List<VdcConfig> vdcConfigs, VirtualDataCenter vdc, boolean wait) throws Exception {
        log.info("add vdc {} to strategy options");
        for (VdcConfig vdcCfg : vdcConfigs) {
            if (vdcCfg.getId().equals(vdc.getId())) {
                log.info("find the vdc cfg {}", vdcCfg);

                // update the VirtualDataCenter object of the vdc
                mergeVdcConfig(vdcCfg);
                vdc = dbClient.queryObject(VirtualDataCenter.class, vdc.getId());

                addStrategyOption(vdc, wait);
                break;
            }
        }
    }

    public void addStrategyOption(VirtualDataCenter vdc, boolean wait) throws Exception {
        String shortVdcId = vdc.getShortId();
        Map<String, String> options = dbClient.getGeoStrategyOptions();

        if (options.containsKey(shortVdcId))
        {
            return; // already added
        }

        options.put(shortVdcId, vdc.getHostCount().toString());

        setCassandraStrategyOptions(options, wait);
    }

    public void removeStrategyOption(String shortVdcId, boolean wait) throws Exception {
        Map<String, String> options = dbClient.getGeoStrategyOptions();

        if (!options.containsKey(shortVdcId))
        {
            return; // already removed
        }

        options.remove(shortVdcId);

        setCassandraStrategyOptions(options, wait);
    }

    private void setCassandraStrategyOptions(Map<String, String> options, boolean wait)
            throws CharacterCodingException, TException, NoSuchFieldException, IllegalAccessException, InstantiationException,
            InterruptedException, ConnectionException, ClassNotFoundException {
        int port = InternalDbClient.DbJmxClient.DEFAULTTHRIFTPORT;

        log.info("The dbclient encrypted={}", dbClient.isGeoDbClientEncrypted());

        if (dbClient.isGeoDbClientEncrypted()) {
            CliOptions cliOptions = new CliOptions();
            List<String> args = new ArrayList<String>();

            args.add("-h");
            args.add(LOCAL_HOST);

            args.add("-p");
            args.add(Integer.toString(port));

            args.add("-ts");
            DbClientContext ctx = dbClient.getGeoContext();
            String geoDBTrustStoreFile = ctx.getTrustStoreFile();
            String trustStorePassword = ctx.getTrustStorePassword();
            args.add(geoDBTrustStoreFile);

            args.add("-tspw");
            args.add(trustStorePassword);

            args.add("-tf");
            args.add(DbConfigConstants.SSLTransportFactoryName);

            String[] cmdArgs = args.toArray(new String[0]);

            cliOptions.processArgs(CliMain.sessionState, cmdArgs);
        }

        CliMain.connect(LOCAL_HOST, port);

        String useGeoKeySpaceCmd = "use " + DbClientContext.GEO_KEYSPACE_NAME + ";";
        CliMain.processStatement(useGeoKeySpaceCmd);

        String command = genUpdateStrategyOptionCmd(options);
        CliMain.processStatement(command);
        CliMain.disconnect();

        if (wait) {
            waitForStrategyOptionsSynced();
        }
    }

    private void waitForStrategyOptionsSynced() throws InterruptedException, ConnectionException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < SchemaUtil.MAX_SCHEMA_WAIT_MS) {
            Map<String, List<String>> versions = dbClient.getGeoSchemaVersions();

            if (versions.size() == 2) {
                break;
            }

            log.info("waiting for schema change ...");
            Thread.sleep(1000);
        }
    }

    private String genUpdateStrategyOptionCmd(Map<String, String> strategyOptions) {
        // prepare update command
        Set<Map.Entry<String, String>> options = strategyOptions.entrySet();
        StringBuilder updateKeySpaceCmd = new StringBuilder("update keyspace ");
        updateKeySpaceCmd.append(DbClientContext.GEO_KEYSPACE_NAME);
        updateKeySpaceCmd.append(" with strategy_options={");
        boolean isFirst = true;
        for (Map.Entry<String, String> option : options) {
            if (isFirst) {
                isFirst = false;
            } else {
                updateKeySpaceCmd.append(",");
            }

            updateKeySpaceCmd.append(option.getKey());
            updateKeySpaceCmd.append(":");
            updateKeySpaceCmd.append(option.getValue());
        }
        updateKeySpaceCmd.append("};");

        String cmd = updateKeySpaceCmd.toString();
        log.info("update keyspace cmd={}", cmd);
        return cmd;
    }

    public VirtualDataCenter getDisconnectingVdc() {
        List<URI> ids = dbClient.queryByType(VirtualDataCenter.class, true);

        for (URI id : ids) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, id);

            if (vdc.getConnectionStatus() == ConnectionStatus.DISCONNECTING ||
                    vdc.getConnectionStatus() == ConnectionStatus.CONNECT_FAILED) {
                return vdc;
            }
        }

        return null;
    }

    /**
     * Send rest call to get vdc version
     * 
     * @param vdcId vdc short Id
     * @return vdc version in string format
     */
    public String getViPRVersion(String vdcId) {
        return geoClientCache.getGeoClient(vdcId).getViPRVersion();
    }

    /**
     * Send rest call to get vdc version
     * 
     * @param vdcProp vdc short Id
     * @return vdc version in string format
     */
    public String getViPRVersion(Properties vdcProp) {
        return geoClientCache.getGeoClient(vdcProp).getViPRVersion();
    }
}
