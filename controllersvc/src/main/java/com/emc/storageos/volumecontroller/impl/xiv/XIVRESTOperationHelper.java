package com.emc.storageos.volumecontroller.impl.xiv;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.xiv.api.XIVApiFactory;
import com.emc.storageos.xiv.api.XIVRESTExportOperations;
import com.emc.storageos.xiv.api.XIVRestException;
import com.emc.storageos.xiv.api.XIVRESTExportOperations.HOST_STATUS;

public class XIVRESTOperationHelper {

    private static Logger _log = LoggerFactory.getLogger(XIVRESTOperationHelper.class);

    private DbClient _dbClient;
    private XIVApiFactory _restAPIFactory;

    private static final int MAXIMUM_LUN = 511;
    private static final String INVALID_LUN_ERROR_MSG = "Logical unit number provided (%d) is larger than allowed (%d).";

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setRestAPIFactory(XIVApiFactory factory) {
        _restAPIFactory = factory;
    }

    private XIVRESTExportOperations getRestClient(StorageSystem storage) {
        XIVRESTExportOperations restExportOpr = null;
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, storage.getActiveProviderURI());
        String providerUser = provider.getSecondaryUsername();
        String providerPassword = provider.getSecondaryPassword();
        String providerURL = provider.getElementManagerURL();

        if (StringUtils.isNotEmpty(providerURL) && StringUtils.isNotEmpty(providerPassword) && StringUtils.isNotEmpty(providerUser)) {
            restExportOpr = _restAPIFactory.getRESTClient(URI.create(providerURL), providerUser, providerPassword);
        }
        return restExportOpr;
    }

    public boolean isClusteredHost(StorageSystem storage, List<Initiator> initiatorList) {
        boolean isClusteredHost = false;
        if (null != initiatorList && !initiatorList.isEmpty()) {
            Host host = _dbClient.queryObject(Host.class, initiatorList.get(0).getHost());
            URI clusterURI = host.getCluster();
            if (null != clusterURI && !clusterURI.toString().isEmpty()) {
                XIVRESTExportOperations restExportOpr = getRestClient(storage);
                if (null != restExportOpr) {
                    HOST_STATUS hostStatus = null;
                    String hostName = host.getLabel();
                    try {
                        hostStatus = restExportOpr.getHostStatus(storage.getIpAddress(), hostName);
                    } catch (Exception e) {
                        _log.error("Unable to validate host {} information on array : {} ", hostName, storage.getLabel(), e);
                    }

                    if (null != hostStatus) {
                        if (HOST_STATUS.HOST_NOT_PRESENT.equals(hostStatus)) {
                            _log.info("Host {} not present on Array {}. Creating a new instance!", hostName, storage.getLabel());
                            isClusteredHost = true;
                        } else if (HOST_STATUS.CLUSTER_HOST.equals(hostStatus)) {
                            _log.info("Identified Host {} as a Clustered Host Array {}.", hostName, storage.getLabel());
                            isClusteredHost = true;
                        } else if (HOST_STATUS.STANDALONE_HOST.equals(hostStatus)) {
                            _log.info("Host {} identified as a Standalone host on Array {}. Using SMIS for provisioning!", hostName,
                                    storage.getLabel());
                            isClusteredHost = false;
                        }
                    }
                }
            }
        }
        return isClusteredHost;
    }

    public void createRESTExportMask(StorageSystem storage, URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter) {

        try {
            XIVRESTExportOperations restExportOpr = getRestClient(storage);
            Host host = _dbClient.queryObject(Host.class, initiatorList.get(0).getHost());
            Cluster cluster = _dbClient.queryObject(Cluster.class, host.getCluster());
            final String storageIP = storage.getSmisProviderIP();
            final String clusterName = cluster.getLabel();
            final String hostName = host.getLabel();

            // Create Cluster if not exist
            restExportOpr.createCluster(storageIP, clusterName);

            // Create Host if not exist
            restExportOpr.createHost(storageIP, clusterName, hostName);

            // Add Initiators to Host.
            if (initiatorList != null && !initiatorList.isEmpty()) {
                for (Initiator initiator : initiatorList) {
                    restExportOpr.createHostPort(storageIP, hostName, Initiator.normalizePort(initiator.getInitiatorPort()),
                            initiator.getProtocol().toLowerCase());
                }
            }

            // Export volume to Cluster
            if (volumeURIHLUs != null && volumeURIHLUs.length > 0) {
                for (VolumeURIHLU volumeURIHLU : volumeURIHLUs) {
                    final String lunName = getBlockObjectAlternateName(volumeURIHLU.getVolumeURI());
                    final String volumeHLU = volumeURIHLU.getHLU();
                    if (volumeHLU != null && !volumeHLU.equalsIgnoreCase(ExportGroup.LUN_UNASSIGNED_STR)) {
                        int hluDec = Integer.parseInt(volumeHLU, 16);
                        if (hluDec > MAXIMUM_LUN) {
                            String errMsg = String.format(INVALID_LUN_ERROR_MSG, hluDec, MAXIMUM_LUN);
                            _log.error(errMsg);
                            throw new Exception(errMsg);
                        } else {
                            restExportOpr.exportVolumeToCluster(storageIP, clusterName, lunName, volumeHLU);
                        }
                    }
                }
            }

            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: createRESTExportMask failed.", e);
            ServiceError error = XIVRestException.exceptions.methodFailed("createExportMask", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    /**
     * This method will take a URI and return alternateName for the BlockObject object to which the
     * URI applies.
     *
     * @param uri
     *            - URI
     * @return Returns a nativeId String value
     * @throws XIVRestException.exceptions.notAVolumeOrBlocksnapshotUri
     *             if URI is not a Volume/BlockSnapshot URI
     */
    private String getBlockObjectAlternateName(URI uri) throws Exception {
        String nativeId;
        if (URIUtil.isType(uri, Volume.class)) {
            Volume volume = _dbClient.queryObject(Volume.class, uri);
            nativeId = volume.getLabel();
        } else if (URIUtil.isType(uri, BlockSnapshot.class)) {
            BlockSnapshot blockSnapshot = _dbClient.queryObject(BlockSnapshot.class, uri);
            nativeId = blockSnapshot.getLabel();
        } else if (URIUtil.isType(uri, BlockMirror.class)) {
            BlockMirror blockMirror = _dbClient.queryObject(BlockMirror.class, uri);
            nativeId = blockMirror.getLabel();
        } else {
            throw XIVRestException.exceptions.notAVolumeOrBlocksnapshotUri(uri);
        }
        return nativeId;
    }

}
