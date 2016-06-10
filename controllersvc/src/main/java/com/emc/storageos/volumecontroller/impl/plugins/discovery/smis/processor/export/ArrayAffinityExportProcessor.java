/*
 *  Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/**
 * Processor used for retrieving masking constructs and populating data structures for array affinity.
 */
public class ArrayAffinityExportProcessor extends Processor {

    private final Logger _logger = LoggerFactory.getLogger(ArrayAffinityExportProcessor.class);
    protected Map<String, Object> _keyMap;
    protected DbClient _dbClient;
    protected List<Object> _args;

    private final String ISCSI_PATTERN = "^(iqn|IQN|eui).*$";
    protected static int MAX_OBJECT_COUNT = 100;

    private static final String HOST = "Host";
    private static final int BATCH_SIZE = 100;

    private Map<URI, Set<String>> _hostToExportMasksMap = null;
    private Map<String, Integer> _exportMaskToHostCountMap = null;
    private Map<String, Set<URI>> _maskToStoragePoolsMap = null;

    private PartitionManager _partitionManager;

    /**
     * Method for setting the partition manager via injection.
     *
     * @param partitionManager the partition manager instance
     */
    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    /**
     * Initialize the Processor. Child classes should call
     * super.initialize if they want the various convenience getter
     * methods to work.
     *
     * @param operation
     * @param resultObj
     * @param keyMap
     */
    protected void initialize(Operation operation, Object resultObj,
            Map<String, Object> keyMap) {
        _keyMap = keyMap;
        _dbClient = (DbClient) keyMap.get(Constants.dbClient);
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.plugins.common.Processor#processResult(com.emc.storageos.plugins.common.domainmodel.Operation,
     * java.lang.Object, java.util.Map)
     */
    @SuppressWarnings("unchecked")
    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {

        initialize(operation, resultObj, keyMap);
        CloseableIterator<CIMInstance> it = null;
        EnumerateResponse<CIMInstance> response = null;
        List<Initiator> matchedInitiators = new ArrayList<Initiator>();
        List<StoragePort> matchedPorts = new ArrayList<StoragePort>();
        WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
        StringSet knownIniSet = new StringSet();
        StringSet knownNetworkIdSet = new StringSet();
        StringSet knownPortSet = new StringSet();
        StringSet knownVolumeSet = new StringSet();

        try {
            // get lun masking view CIM path
            CIMObjectPath path = getObjectPathfromCIMArgument(_args, keyMap);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            if (StringUtils.contains(path.getKeyValue(SmisConstants.CP_SYSTEM_NAME).toString(), profile.getserialID())) {
                return;
            }

            _logger.info("looking at lun masking view: " + path.toString());
            response = (EnumerateResponse<CIMInstance>) resultObj;
            processVolumesAndInitiatorsPaths(response.getResponses(), path.toString(), matchedInitiators, matchedPorts, knownIniSet,
                    knownNetworkIdSet, knownPortSet, knownVolumeSet, client);

            while (!response.isEnd()) {
                _logger.info("Processing next Chunk");
                response = client.getInstancesWithPath(Constants.MASKING_PATH, response.getContext(),
                        new UnsignedInteger32(MAX_OBJECT_COUNT));
                processVolumesAndInitiatorsPaths(response.getResponses(), path.toString(), matchedInitiators, matchedPorts, knownIniSet,
                        knownNetworkIdSet, knownPortSet, knownVolumeSet, client);
            }
        } catch (Exception e) {
            _logger.error("Processing lun maksing view failed", e);
        } finally {
            if (it != null) {
                it.close();
            }

            wrapUp();

            if (response != null) {
                try {
                    client.closeEnumeration(Constants.MASKING_PATH, response.getContext());
                } catch (Exception e) {
                    _logger.debug("Exception occurred while closing enumeration", e);
                }
            }
        }
    }

    /**
     * Gets the Map of hosts to maskingViewPaths that is being tracked in the keyMap.
     *
     * @return a Map of hosts to maskingViewPaths
     */
    private Map<URI, Set<String>> getHostToExportMasksMap() {
        // find or create the Host -> maskingViewPaths tracking data structure in the key map
        _hostToExportMasksMap = (Map<URI, Set<String>>) _keyMap.get(Constants.HOST_EXPORT_MASKS_MAP);
        if (_hostToExportMasksMap == null) {
            _hostToExportMasksMap = new HashMap<URI, Set<String>>();
            _keyMap.put(Constants.HOST_EXPORT_MASKS_MAP, _hostToExportMasksMap);
        }

        return _hostToExportMasksMap;
    }

    /**
     * Gets the Map of maskingViewPath to host count that is being tracked in the keyMap.
     *
     * @return a Map of maskingViewPath to host count
     */
    private Map<String, Integer> getExportMaskToHostCountMap() {
        // find or create the maskingViewPath -> host count tracking data structure in the key map
        _exportMaskToHostCountMap = (Map<String, Integer>) _keyMap.get(Constants.HOST_EXPORT_MASKS_MAP);
        if (_exportMaskToHostCountMap == null) {
            _exportMaskToHostCountMap = new HashMap<String, Integer>();
            _keyMap.put(Constants.HOST_EXPORT_MASKS_MAP, _exportMaskToHostCountMap);
        }

        return _exportMaskToHostCountMap;
    }

    /**
     * Gets the Map of maskingViewPaths to StoragePools that is being tracked in the keyMap.
     *
     * @return a Map of maskingViewPaths to StoragePools
     */
    private Map<String, Set<URI>> getMaskToStoragePoolsMap() {
        // find or create the maskingViewPath -> StoragePools tracking data structure in the key map
        _maskToStoragePoolsMap = (Map<String, Set<URI>>) _keyMap.get(Constants.EXPORT_MASK_STORAGE_POOLS_MAP);
        if (_maskToStoragePoolsMap == null) {
            _maskToStoragePoolsMap = new HashMap<String, Set<URI>>();
            _keyMap.put(Constants.EXPORT_MASK_STORAGE_POOLS_MAP, _maskToStoragePoolsMap);
        }

        return _maskToStoragePoolsMap;
    }

    /**
     * Update preferredPoolIds for hosts
     */
    protected void wrapUp() {

        Integer currentCommandIndex = this.getCurrentCommandIndex(_args);
        List maskingViews = (List) _keyMap.get(Constants.MASKING_VIEWS);
        _logger.info("ExportProcessor current index is " + currentCommandIndex);
        _logger.info("ExportProcessor maskingViews size is " + maskingViews.size());
        if ((maskingViews != null) && (maskingViews.size() == (currentCommandIndex + 1))) {
            _logger.info("this is the last time ArrayAffinityExportProcessor will be called, cleaning up...");
            updatePreferredPoolIds();
        } else {
            _logger.info("no need to wrap up yet...");
        }
    }

    private void processVolumesAndInitiatorsPaths(CloseableIterator<CIMInstance> it, String maskingViewPath,
            List<Initiator> matchedInitiators, List<StoragePort> matchedPorts, Set<String> knownIniSet,
            Set<String> knownNetworkIdSet, Set<String> knownPortSet, Set<String> knownVolumeSet, WBEMClient client) {
        while (it.hasNext()) {
            CIMInstance cimi = it.next();

            _logger.info("looking at classname: " + cimi.getClassName());
            switch (cimi.getClassName()) {

                // process initiators
                case SmisConstants.CP_SE_STORAGE_HARDWARE_ID:

                    String initiatorNetworkId = this.getCIMPropertyValue(cimi, SmisConstants.CP_STORAGE_ID);
                    _logger.info("looking at initiator network id " + initiatorNetworkId);
                    if (WWNUtility.isValidNoColonWWN(initiatorNetworkId)) {
                        initiatorNetworkId = WWNUtility.getWWNWithColons(initiatorNetworkId);
                        _logger.info("   wwn normalized to " + initiatorNetworkId);
                    } else if (WWNUtility.isValidWWN(initiatorNetworkId)) {
                        initiatorNetworkId = initiatorNetworkId.toUpperCase();
                        _logger.info("   wwn normalized to " + initiatorNetworkId);
                    } else if (initiatorNetworkId.matches(ISCSI_PATTERN)
                            && (iSCSIUtility.isValidIQNPortName(initiatorNetworkId) || iSCSIUtility
                                    .isValidEUIPortName(initiatorNetworkId))) {
                        _logger.info("   iSCSI storage port normalized to " + initiatorNetworkId);
                    } else {
                        _logger.warn("   this is not a valid FC or iSCSI network id format, skipping");
                        continue;
                    }

                    // check if a host initiator exists for this id
                    Initiator knownInitiator = NetworkUtil.getInitiator(initiatorNetworkId, _dbClient);
                    if (knownInitiator != null) {
                        _logger.info("Found an initiator in ViPR on host " + knownInitiator.getHostName());

                        // add to map of host to export masks, and map of mask to hosts
                        URI hostId = knownInitiator.getHost();
                        if (!NullColumnValueGetter.isNullURI(hostId)) {
                            Set<String> maskingViewPaths = getHostToExportMasksMap().get(hostId);
                            if (maskingViewPaths == null) {
                                maskingViewPaths = new HashSet<String>();
                                _logger.info("Creating mask set for host {}" + hostId);
                                getHostToExportMasksMap().put(hostId, maskingViewPaths);
                            }
                            maskingViewPaths.add(maskingViewPath);

                            Integer hostCount = getExportMaskToHostCountMap().get(maskingViewPath);
                            if (hostCount == null) {
                                hostCount = 0;
                                _logger.info("Initial host count for mask {}" + maskingViewPath);
                            }
                            getExportMaskToHostCountMap().put(maskingViewPath, hostCount++);
                        }
                    } else {
                        _logger.info("No hosts in ViPR found configured for initiator " + initiatorNetworkId);
                    }

                    break;

                // process FC and ISCSI target ports
                case SmisConstants.CP_SYMM_FCSCSI_PROTOCOL_ENDPOINT:
                case SmisConstants.CP_SYMM_ISCSI_PROTOCOL_ENDPOINT:
                case SmisConstants.CP_CLAR_FCSCSI_PROTOCOL_ENDPOINT:
                case SmisConstants.CP_CLAR_ISCSI_PROTOCOL_ENDPOINT:
                case SmisConstants.CP_CLAR_FRONTEND_FC_PORT:
                    break;

                // process storage volumes
                case _symmvolume:
                case _clarvolume:

                    CIMObjectPath volumePath = cimi.getObjectPath();
                    _logger.info("volumePath is " + volumePath.toString());

                    String systemName = volumePath.getKey(SmisConstants.CP_SYSTEM_NAME).getValue().toString();
                    systemName = systemName.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
                    String id = volumePath.getKey(SmisConstants.CP_DEVICE_ID).getValue().toString();
                    _logger.info("systemName is " + systemName);
                    _logger.info("id is " + id);
                    String nativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(systemName.toUpperCase(), id);
                    _logger.info("nativeGuid for looking up ViPR volumes is " + nativeGuid);

                    URIQueryResultList result = new URIQueryResultList();
                    _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint(nativeGuid), result);

                    URI poolURI = null;
                    Volume volume = null;
                    Iterator<URI> volumes = result.iterator();
                    if (volumes.hasNext()) {
                        volume = _dbClient.queryObject(Volume.class, volumes.next());
                        if (null != volume) {
                            knownVolumeSet.add(volume.getId().toString());
                            poolURI = volume.getPool();
                        }
                    }

                    if (volume == null) {
                        poolURI = ArrayAffinityDiscoveryUtils.getStoragePool(volumePath, client, _dbClient);
                    }

                    if (!NullColumnValueGetter.isNullURI(poolURI)) {
                        Set<URI> pools = getMaskToStoragePoolsMap().get(maskingViewPath);
                        if (pools == null) {
                            pools = new HashSet<URI>();
                            _logger.info("Creating pool set for mask {}" + maskingViewPath);
                            getMaskToStoragePoolsMap().put(maskingViewPath, pools);
                        }
                        pools.add(poolURI);
                    }

                    break;

                default:
                    break;
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.plugins.common.Processor#setPrerequisiteObjects(java.util.List)
     */
    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        this._args = inputArgs;
    }

    private void updatePreferredPoolIds() {
        Map<URI, Set<String>> hostExportMasks = getHostToExportMasksMap();
        Map<String, Integer> exportMaskHostCount = getExportMaskToHostCountMap();
        Map<String, Set<URI>> maskStroagePools = getMaskToStoragePoolsMap();
        URI systemId = (URI) _keyMap.get(Constants.SYSTEMID);
        List<Host> hostsToUpdate = new ArrayList<Host>();

        try {
            List<URI> hostURIs = _dbClient.queryByType(Host.class, true);
            Iterator<Host> hosts = _dbClient.queryIterativeObjectFields(Host.class, ArrayAffinityDiscoveryUtils.HOST_PROPERTIES, hostURIs);
            while (hosts.hasNext()) {
                Host host = hosts.next();
                if (host != null) {
                    _logger.info("Processing host {}", host.getLabel());
                    Map<String, String> preferredPoolMap = new HashMap<String, String>();
                    Set<String> masks = hostExportMasks.get(host.getId());
                    if (masks != null && !masks.isEmpty()) {
                        for (String mask : masks) {
                            Set<URI> pools = maskStroagePools.get(mask);
                            String exportType = exportMaskHostCount.get(mask) > 1 ? ExportGroup.ExportGroupType.Cluster.name()
                                    : ExportGroup.ExportGroupType.Host.name();
                            if (pools != null && !pools.isEmpty()) {
                                for (URI pool : pools) {
                                    ArrayAffinityDiscoveryUtils.addPoolToPreferredPoolMap(preferredPoolMap, pool.toString(), exportType);
                                }
                            }
                        }
                    }

                    if (ArrayAffinityDiscoveryUtils.updatePreferredPools(host, systemId, _dbClient, preferredPoolMap)) {
                        hostsToUpdate.add(host);
                    }
                }

                // if hostsToUpdate size reaches BATCH_SIZE, persist to db
                if (hostsToUpdate.size() >= BATCH_SIZE) {
                    _partitionManager.updateInBatches(hostsToUpdate, BATCH_SIZE, _dbClient, HOST);
                    hostsToUpdate.clear();
                }
            }

            if (!hostsToUpdate.isEmpty()) {
                _partitionManager.updateInBatches(hostsToUpdate, BATCH_SIZE, _dbClient, HOST);
            }
        } catch (Exception e) {
            _logger.warn("Exception on updatePreferredSystems {}", e.getMessage());
        }
    }
}
