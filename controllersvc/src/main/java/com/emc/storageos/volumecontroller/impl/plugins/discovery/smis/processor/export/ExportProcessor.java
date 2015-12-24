/*
 *  Copyright (c) 2008-2014 EMC Corporation
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
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.TransportType;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.ZoneInfo;
import com.emc.storageos.db.client.model.ZoneInfoMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;

/**
 * Processor used for retrieving masking constructs and creating UnManagedExportMask objects.
 *
 */
public class ExportProcessor extends Processor {

    private Logger _logger = LoggerFactory.getLogger(ExportProcessor.class);
    protected Map<String, Object> _keyMap;
    protected Set<URI> _vplexPortInitiators;
    protected Set<URI> _rpPortInitiators;
    protected DbClient _dbClient;
    protected List<Object> _args;

    private final String ISCSI_PATTERN = "^(iqn|IQN|eui).*$";
    protected static int BATCH_SIZE = 10;
    protected static final String UNMANAGED_EXPORT_MASK = "UnManagedExportMask";
    
    private Set<URI> _allCurrentUnManagedExportMaskUris = null;
    private Map <String, Set<UnManagedExportMask>> _volumeToExportMasksMap = null;
    private List<UnManagedExportMask> _unManagedExportMasksToCreate = null;
    private List<UnManagedExportMask> _unManagedExportMasksToUpdate = null;

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
     * Initialize the Processor.  Child classes should call
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
        
        _vplexPortInitiators = 
                (Set<URI>) _keyMap.get(Constants.UNMANAGED_EXPORT_MASKS_VPLEX_INITS_SET);
        if (_vplexPortInitiators == null) {
            _vplexPortInitiators = VPlexUtil.getBackendPortInitiators(_dbClient);
            _keyMap.put(Constants.UNMANAGED_EXPORT_MASKS_VPLEX_INITS_SET, _vplexPortInitiators);
        }

        _rpPortInitiators = 
                (Set<URI>) _keyMap.get(Constants.UNMANAGED_EXPORT_MASKS_RECOVERPOINT_INITS_SET);
        if (_rpPortInitiators == null) {
            _rpPortInitiators = RPHelper.getBackendPortInitiators(_dbClient);
            _keyMap.put(Constants.UNMANAGED_EXPORT_MASKS_RECOVERPOINT_INITS_SET, _rpPortInitiators);
        }
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
        WBEMClient client = (WBEMClient) keyMap.get(Constants._cimClient);
        StringSet knownIniSet = new StringSet();
        StringSet knownNetworkIdSet = new StringSet();
        StringSet knownPortSet = new StringSet();
        StringSet knownVolumeSet = new StringSet();
        
        try {
            
            // set lun masking view CIM path
            CIMObjectPath path = this.getObjectPathfromCIMArgument(_args, keyMap);
            UnManagedExportMask mask = this.getUnManagedExportMask(path);
            mask.setMaskingViewPath(path.toString());
            
            _logger.info("looking at lun masking view: " + path.toString());
            
            CIMInstance lunMaskingView = client.getInstance(path, false, false, null);

            if (lunMaskingView != null) {
                String maskName = CIMPropertyFactory.getPropertyValue(lunMaskingView,
                        SmisConstants.CP_NAME);
                if (maskName != null) {
                    mask.setMaskName(maskName);
                }
                _logger.info("set UnManagedExportMask maskName to " + mask.getMaskName());
            } else {
                
                _logger.info("lunMaskingView was null");
            }
            
            CIMProperty<String> deviceIdProperty =
                    (CIMProperty<String>) path.getKey(SmisConstants.CP_DEVICE_ID);
            if (deviceIdProperty != null) {
                mask.setNativeId(deviceIdProperty.getValue());
            }
            _logger.info("set UnManagedExportMask nativeId to " + mask.getNativeId());
            
            // set storage system id
            URI systemId = (URI) keyMap.get(Constants.SYSTEMID);
            mask.setStorageSystemUri(systemId);

            response = (EnumerateResponse<CIMInstance>) resultObj;
            processVolumesAndInitiatorsPaths(response.getResponses(), mask, matchedInitiators, matchedPorts, knownIniSet,
                    knownNetworkIdSet, knownPortSet, knownVolumeSet);

            while (!response.isEnd()) {
                _logger.info("Processing next Chunk");
                response = client.getInstancesWithPath(Constants.MASKING_PATH, response.getContext(), 
                        new UnsignedInteger32(BATCH_SIZE));
                processVolumesAndInitiatorsPaths(response.getResponses(), mask, matchedInitiators, matchedPorts, knownIniSet,
                        knownNetworkIdSet, knownPortSet, knownVolumeSet);
            }
            
            // CTRL - 8918 - always update the mask with new initiators and volumes.
            mask.replaceNewWithOldResources(knownIniSet, knownNetworkIdSet, knownVolumeSet, knownPortSet);
            
            // get zones and store them?
            updateZoningMap(mask, matchedInitiators, matchedPorts);

            updateVplexBackendVolumes(mask, matchedInitiators);

            updateRecoverPointVolumes(mask, matchedInitiators);
        } catch (Exception e) {
            _logger.error("something failed", e);
        } finally {
            if (it != null) {
                it.close();
            }
            
            wrapUp();

            if (response != null) {
                try {
                    client.closeEnumeration(Constants.MASKING_PATH, response.getContext());
                } catch (Exception e) {
                    _logger.warn("Exception occurred while closing enumeration", e);
                }
            }
        }
    }

    private void updateZoningMap(UnManagedExportMask mask, List<Initiator> initiators, List<StoragePort> storagePorts) {
        NetworkDeviceController networkDeviceController = (NetworkDeviceController) 
                _keyMap.get(Constants.networkDeviceController);
        try {
            ZoneInfoMap zoningMap = networkDeviceController.getInitiatorsZoneInfoMap(initiators, storagePorts);
            for (ZoneInfo zoneInfo : zoningMap.values()) {
                _logger.info("Found zone: {} for initiator {} and port {}", new Object[] {zoneInfo.getZoneName(), 
                        zoneInfo.getInitiatorWwn(), zoneInfo.getPortWwn()});
            }
            mask.setZoningMap(zoningMap);
        } catch (Exception ex) {
            _logger.error("Failed to get the zoning map for mask {}", mask.getMaskName());
            mask.setZoningMap(null);
        }
    }

    /**
     * Marks any VPLEX backend volumes as such by look at the initiators
     * and determining if any of them represent VPLEX backend ports.
     *  
     * @param mask - the UnManagedExportMask
     * @param initiators - the initiators to test for VPLEX backend port status
     */
    private void updateVplexBackendVolumes(UnManagedExportMask mask, List<Initiator> initiators) {
        StringBuilder nonVplexInitiators = new StringBuilder();
        int vplexPortInitiatorCount = 0;
        for (Initiator init : initiators) {
            if (this._vplexPortInitiators.contains(init.getId())) {
                _logger.info("export mask {} contains vplex backend port initiator {}", 
                        mask.getMaskName(), init.getInitiatorPort());
                vplexPortInitiatorCount++;
            } else {
                nonVplexInitiators.append(init.getInitiatorPort()).append(" ");
            }
        }
        
        if (vplexPortInitiatorCount > 0) {
            _logger.info("export mask {} contains {} vplex backend port initiators", 
                    mask.getMaskName(), vplexPortInitiatorCount);
            if (vplexPortInitiatorCount > initiators.size()) {
                _logger.warn("   there are some ports in this mask that are not "
                        + "vplex backend port initiators: " + nonVplexInitiators);
            }
            
            Set<String> unmanagedVplexBackendMasks = 
                    (Set<String>) _keyMap.get(Constants.UNMANAGED_VPLEX_BACKEND_MASKS_SET);
            if (unmanagedVplexBackendMasks == null) {
                unmanagedVplexBackendMasks = new HashSet<String>();
                _keyMap.put(Constants.UNMANAGED_VPLEX_BACKEND_MASKS_SET, unmanagedVplexBackendMasks);
            }
            _logger.info("adding mask {} to unmanaged vplex backend masks list", mask.getMaskName());
            unmanagedVplexBackendMasks.add(mask.getId().toString());
        }
    }

    /**
     * Marks any RecoverPoint volumes as such by looking at the initiators
     * and determining if any of them represent RPA front-end ports
     *  
     * @param mask - the UnManagedExportMask
     * @param initiators - the initiators to test for RPA ports status
     */
    private void updateRecoverPointVolumes(UnManagedExportMask mask, List<Initiator> initiators) {
        StringBuilder nonRecoverPointInitiators = new StringBuilder();
        int rpPortInitiatorCount = 0;
        for (Initiator init : initiators) {
            if (this._rpPortInitiators.contains(init.getId())) {
                _logger.info("export mask {} contains RPA initiator {}", 
                        mask.getMaskName(), init.getInitiatorPort());
                rpPortInitiatorCount++;
            } else {
                nonRecoverPointInitiators.append(init.getInitiatorPort()).append(" ");
            }
        }
        
        if (rpPortInitiatorCount > 0) {
            _logger.info("export mask {} contains {} RPA initiators", 
                    mask.getMaskName(), rpPortInitiatorCount);
            if (rpPortInitiatorCount > initiators.size()) {
                _logger.warn("   there are some ports in this mask that are not "
                        + "RPA initiators: " + nonRecoverPointInitiators);
            }
            
            Set<String> unmanagedRecoverPointMasks = 
                    (Set<String>) _keyMap.get(Constants.UNMANAGED_RECOVERPOINT_MASKS_SET);
            if (unmanagedRecoverPointMasks == null) {
                unmanagedRecoverPointMasks = new HashSet<String>();
                _keyMap.put(Constants.UNMANAGED_RECOVERPOINT_MASKS_SET, unmanagedRecoverPointMasks);
            }
            _logger.info("adding mask {} to unmanaged RP masks list", mask.getMaskName());
            unmanagedRecoverPointMasks.add(mask.getId().toString());
        }
    }
    
    /**
     * Returns an UnManagedExportMask if it exists for the requested
     * CIMObjectPath, or creates a new one if none exists.
     * 
     * @param cimObjectPath the CIMObjectPath for the Unmanaged Export on the storage array
     * @return an UnManagedExportMask object to use
     */
    protected UnManagedExportMask getUnManagedExportMask( CIMObjectPath cimObjectPath ) {
        
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getUnManagedExportMaskPathConstraint(cimObjectPath.toString()), result);

        UnManagedExportMask uem = null;
        Iterator<URI> it = result.iterator();
        if (it.hasNext()) {
            uem = _dbClient.queryObject(UnManagedExportMask.class, it.next());
        }
        if (uem != null && !uem.getInactive()) {
            getUnManagedExportMasksToUpdate().add(uem);
            
            // clean up collections (we'll be refreshing them)
            uem.getKnownInitiatorUris().clear();
            uem.getKnownInitiatorNetworkIds().clear();
            uem.getKnownStoragePortUris().clear();
            uem.getKnownVolumeUris().clear();
            uem.getUnmanagedInitiatorNetworkIds().clear();
            uem.getUnmanagedStoragePortNetworkIds().clear();
            uem.getUnmanagedVolumeUris().clear();
        } else {
            uem = new UnManagedExportMask();
            getUnManagedExportMasksToCreate().add(uem);
        }
        
        return uem;
    }
    
    /**
     * Gets the Map of Volumes to UnManagedExportMasks that is being tracked in the keyMap.
     * 
     * @return a Map of Volumes to UnManagedExportMasks 
     */
    protected Map <String, Set<UnManagedExportMask>> getVolumeToExportMasksMap() {
        
        // find or create the Volume -> UnManagedExportMask tracking data structure in the key map
        _volumeToExportMasksMap = 
                (Map <String, Set<UnManagedExportMask>>) _keyMap.get(Constants.UNMANAGED_EXPORT_MASKS_MAP);
        if (_volumeToExportMasksMap == null) {
            _volumeToExportMasksMap = new HashMap<String, Set<UnManagedExportMask>>();
            _keyMap.put(Constants.UNMANAGED_EXPORT_MASKS_MAP, _volumeToExportMasksMap);
        }
        
        return _volumeToExportMasksMap;
    }
    
    /**
     * Gets the Set of UnManagedExportMask URIs that are being tracked in the keyMap.
     * They represent the any UnManagedExportMasks that are being updated or created
     * in the database during the discovery run.  This collection will be used
     * during clean up to determine which UnManagedExportMasks in the database are
     * orphaned and can be marked inactive.
     * 
     * @return a Set of UnManagedExportMask URIs 
     */
    protected Set<URI> getAllCurrentUnManagedExportMaskUris() {
        
        // find or create the master set of UnManagedExportMasks in the key map
        // this is used for cleaning up the database when we're all done
        _allCurrentUnManagedExportMaskUris =
                (Set<URI>) _keyMap.get(Constants.UNMANAGED_EXPORT_MASKS_SET);
        if (_allCurrentUnManagedExportMaskUris == null) {
            _allCurrentUnManagedExportMaskUris = new HashSet<URI>();
            _keyMap.put(Constants.UNMANAGED_EXPORT_MASKS_SET, _allCurrentUnManagedExportMaskUris);
        }

        return _allCurrentUnManagedExportMaskUris;
    }

    /**
     * Get the list of UnManagedExportMasks to create in the database.
     * This is stored in the keyMap between Processor iterations and flushed
     * by the partition manager according to the BATCH_SIZE
     * 
     * @return the list of UnManagedExportMasks to create on the next partitioning flush
     */
    protected List<UnManagedExportMask> getUnManagedExportMasksToCreate() {
        if (_unManagedExportMasksToCreate == null) {
            _unManagedExportMasksToCreate = 
                    (List <UnManagedExportMask>) _keyMap.get(Constants.UNMANAGED_EXPORT_MASKS_CREATE_LIST);
            if (_unManagedExportMasksToCreate == null) {
                _unManagedExportMasksToCreate = new ArrayList<UnManagedExportMask>();
                _keyMap.put(Constants.UNMANAGED_EXPORT_MASKS_CREATE_LIST, _unManagedExportMasksToCreate);
            }

        }
        
        return _unManagedExportMasksToCreate;
    }
    
    /**
     * Get the list of UnManagedExportMasks to update in the database.
     * This is stored in the keyMap between Processor iterations and flushed
     * by the partition manager according to the BATCH_SIZE
     * 
     * @return the list of UnManagedExportMasks to update on the next partitioning flush
     */
    protected List<UnManagedExportMask> getUnManagedExportMasksToUpdate() {
        if (_unManagedExportMasksToUpdate == null) {
            _unManagedExportMasksToUpdate = 
                    (List <UnManagedExportMask>) _keyMap.get(Constants.UNMANAGED_EXPORT_MASKS_UPDATE_LIST);
            if (_unManagedExportMasksToUpdate == null) {
                _unManagedExportMasksToUpdate = new ArrayList<UnManagedExportMask>();
                _keyMap.put(Constants.UNMANAGED_EXPORT_MASKS_UPDATE_LIST, _unManagedExportMasksToUpdate);
            }

        }
        
        return _unManagedExportMasksToUpdate;
    }
    
    /**
     * Looks at the UnManagedExportMask tracking containers and persists
     * in batches if the batch size has been reached.
     */
    protected void handlePersistence() {
        handlePersistence(false);
    }
    
    /**
     * Looks at the UnManagedExportMask tracking containers and persists
     * in batches if the batch size has been reached, unless the force
     * argument is true, in which case everything will be flushed.
     * 
     * @param force if true, flush everything regardless of batch size
     */
    protected void handlePersistence( Boolean force ) {
        
        // if volumes size reaches BATCH_SIZE for forced flush, then persist to database
        if (force == true) {
            _logger.info("forced UnManagedExportMask flushing has been requested");
        }
        
        if ((getUnManagedExportMasksToCreate().size() >= BATCH_SIZE) || force) {
            _partitionManager.insertInBatches(getUnManagedExportMasksToCreate(),
                    getPartitionSize(_keyMap), _dbClient, UNMANAGED_EXPORT_MASK);
            getUnManagedExportMasksToCreate().clear();
        }

        if ((getUnManagedExportMasksToUpdate().size() >= BATCH_SIZE) || force) {
            _partitionManager.updateInBatches(getUnManagedExportMasksToUpdate(),
                    getPartitionSize(_keyMap), _dbClient, UNMANAGED_EXPORT_MASK);
            getUnManagedExportMasksToUpdate().clear();
        }

    }
    
    /**
     * Cleans up any instances of UnManagedExportMask that are in the database
     * but no longer needed (either because they no longer exist on the storage
     * system or because they are now fully managed by ViPR).
     * 
     * Also cleans up the UnManagedExportMasksToCreate/Update collections.
     */
    protected void wrapUp() {
        
        Integer currentCommandIndex = this.getCurrentCommandIndex(_args);
        List maskingViews = (List) _keyMap.get(Constants.MASKING_VIEWS);
        _logger.info("ExportProcessor current index is " + currentCommandIndex);
        _logger.info("ExportProcessor maskingViews size is " + maskingViews.size());
        if ((maskingViews != null) && (maskingViews.size() == (currentCommandIndex + 1))) {
            
            _logger.info("this is the last time ExportProcessor will be called, cleaning up...");
    
            // force persist leftover UnManagedExportMasks
            handlePersistence(true);
    
            // why does a simple database query have to be so difficult?
            URI storageSystemUri = (URI) _keyMap.get(Constants.SYSTEMID);
            DiscoveryUtils.markInActiveUnManagedExportMask(storageSystemUri, _allCurrentUnManagedExportMaskUris, _dbClient,
                    _partitionManager);
            
        } else {
            _logger.info("no need to wrap up yet...");
        }
    }
    
    private void processVolumesAndInitiatorsPaths(CloseableIterator<CIMInstance> it, UnManagedExportMask mask,
            List<Initiator> matchedInitiators, List<StoragePort> matchedPorts, Set<String> knownIniSet,
            Set<String> knownNetworkIdSet, Set<String> knownPortSet, Set<String> knownVolumeSet) {
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
                // if so, add to _knownInitiators
                // otherwise, add to _unmanagedInitiators
                Initiator knownInitiator = NetworkUtil.getInitiator(initiatorNetworkId, _dbClient);
                if (knownInitiator != null) {
                    _logger.info("   found an initiator in ViPR on host " + knownInitiator.getHostName());
                    knownIniSet.add(knownInitiator.getId().toString());
                    knownNetworkIdSet.add(knownInitiator.getInitiatorPort());
                    if (HostInterface.Protocol.FC.toString().equals(knownInitiator.getProtocol())) {
                        matchedInitiators.add(knownInitiator);
                    }
                } else {
                    _logger.info("   no hosts in ViPR found configured for initiator " + initiatorNetworkId);
                    mask.getUnmanagedInitiatorNetworkIds().add(initiatorNetworkId);
                }

                break;

            // process FC and ISCSI target ports
            case SmisConstants.CP_SYMM_FCSCSI_PROTOCOL_ENDPOINT:
            case SmisConstants.CP_SYMM_ISCSI_PROTOCOL_ENDPOINT:
            case SmisConstants.CP_CLAR_FCSCSI_PROTOCOL_ENDPOINT:
            case SmisConstants.CP_CLAR_ISCSI_PROTOCOL_ENDPOINT:
            case SmisConstants.CP_CLAR_FRONTEND_FC_PORT:

                String portNetworkId = this.getCIMPropertyValue(cimi, SmisConstants.CP_NAME);
                if (portNetworkId == null) {
                    portNetworkId = this.getCIMPropertyValue(cimi, SmisConstants.CP_PERMANENT_ADDRESS);
                }

                _logger.info("looking at storage port network id " + portNetworkId);
                if (WWNUtility.isValidNoColonWWN(portNetworkId)) {
                    portNetworkId = WWNUtility.getWWNWithColons(portNetworkId);
                    _logger.info("   wwn normalized to " + portNetworkId);
                } else if (WWNUtility.isValidWWN(portNetworkId)) {
                    portNetworkId = portNetworkId.toUpperCase();
                    _logger.info("   wwn normalized to " + portNetworkId);
                } else if (portNetworkId.matches(ISCSI_PATTERN)
                        && (iSCSIUtility.isValidIQNPortName(portNetworkId) || iSCSIUtility.isValidEUIPortName(portNetworkId))) {
                    // comes from SMI-S in the following format (just want the
                    // first part)
                    // "iqn.1992-04.com.emc:50000973f0065980,t,0x0001"
                    portNetworkId = portNetworkId.split(",")[0];
                    _logger.info("   iSCSI storage port normalized to " + portNetworkId);
                } else {
                    _logger.warn("   this is not a valid WWN or iSCSI format, skipping");
                    continue;
                }

                // check if a storage port exists for this id in ViPR
                // if so, add to _storagePorts
                StoragePort knownStoragePort = NetworkUtil.getStoragePort(portNetworkId, _dbClient);

                if (knownStoragePort != null) {
                    _logger.info("   found a matching storage port in ViPR " + knownStoragePort.getLabel());
                    knownPortSet.add(knownStoragePort.getId().toString());
                    if (TransportType.FC.toString().equals(knownStoragePort.getTransportType())) {
                        matchedPorts.add(knownStoragePort);
                    }
                } else {
                    _logger.info("   no storage port in ViPR found matching portNetworkId " + portNetworkId);
                    mask.getUnmanagedStoragePortNetworkIds().add(portNetworkId);
                }

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

                Volume volume = null;
                Iterator<URI> volumes = result.iterator();
                if (volumes.hasNext()) {
                    volume = _dbClient.queryObject(Volume.class, volumes.next());
                    if (null != volume) {
                        knownVolumeSet.add(volume.getId().toString());
                    }
                }

                nativeGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(systemName.toUpperCase(), id);
                _logger.info("   nativeGuid for keying UnManagedVolumes is " + nativeGuid);
                // add to map of volume paths to export masks
                Set<UnManagedExportMask> maskSet = getVolumeToExportMasksMap().get(nativeGuid);
                if (maskSet == null) {
                    maskSet = new HashSet<UnManagedExportMask>();
                    _logger.info("   creating maskSet for nativeGuid " + nativeGuid);
                    getVolumeToExportMasksMap().put(nativeGuid, maskSet);
                }
                maskSet.add(mask);

                break;

            default:
                break;
            }
        }
        if (mask.getId() == null) {
            mask.setId(URIUtil.createId(UnManagedExportMask.class));
        }
        
        handlePersistence();
        getAllCurrentUnManagedExportMaskUris().add(mask.getId());
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
}
