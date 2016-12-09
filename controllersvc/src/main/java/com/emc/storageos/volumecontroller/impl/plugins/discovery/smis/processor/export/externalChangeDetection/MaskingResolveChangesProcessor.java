/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export.externalChangeDetection;



import java.net.URI;
import java.util.ArrayList;
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
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;

import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;


public class MaskingResolveChangesProcessor extends Processor {

    private final Logger _logger = LoggerFactory.getLogger(MaskingResolveChangesProcessor.class);
    protected Map<String, Object> _keyMap;
   
    protected DbClient _dbClient;
    protected List<Object> _args;

    private final String ISCSI_PATTERN = "^(iqn|IQN|eui).*$";
    protected static int BATCH_SIZE = 10;
    
    
    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.plugins.common.Processor#processResult(com.emc.storageos.plugins.common.domainmodel.Operation,
     * java.lang.Object, java.util.Map)
     */
    @SuppressWarnings({ "unchecked", "unused" })
    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {

        CloseableIterator<CIMInstance> it = null;
        EnumerateResponse<CIMInstance> response = null;
        WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
        StringSet knownIniSet = new StringSet();
        StringSet knownPortSet = new StringSet();
        StringSet unknownIniSet = new StringSet();
        StringSet unknownPortSet = new StringSet();
        StringMap userAddedIni = new StringMap();
        
        try {
            CIMObjectPath path = this.getObjectPathfromCIMArgument(_args, keyMap);
            
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
            String maskName = getCIMPropertyValue(path, SmisConstants.CP_DEVICE_ID);
            
            @SuppressWarnings("deprecation")
            List<URI> maskUriList = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getExportMaskByNameConstraint(maskName));
            if (null == maskUriList || maskUriList.isEmpty()) {
                _logger.warn("Storage System doesn't have any export masks in ViPR database");
                return;
            }
            response = (EnumerateResponse<CIMInstance>) resultObj;
            processVolumesAndInitiatorsPaths(response.getResponses(), knownIniSet,
                    knownPortSet, userAddedIni,unknownIniSet,
                    unknownPortSet);
              
            while (!response.isEnd()) {
                _logger.info("Processing next Chunk");
                response = client.getInstancesWithPath(Constants.MASKING_PATH, response.getContext(),
                        new UnsignedInteger32(BATCH_SIZE));
                processVolumesAndInitiatorsPaths(response.getResponses(), knownIniSet,
                        knownPortSet, userAddedIni,unknownIniSet,
                        unknownPortSet);
            }
            
            //Replace the storage Ports with export mask storage Ports.
            //Replace the Initiator Ports with export mask initiator Ports.
             ExportMask mask = dbClient.queryObject(ExportMask.class, maskUriList.get(0));
             
            
             //Replace Storage Ports
             List<StoragePort> storagePorts = new ArrayList<StoragePort>();
             if (null == mask.getStoragePorts()) {
                 mask.setStoragePorts(new StringSet());
             }
             
             SetView<String> addedPorts = Sets.difference(knownPortSet, mask.getStoragePorts());
             SetView<String> removedPorts = Sets.difference(mask.getStoragePorts(), knownPortSet);
             
             mask.getStoragePorts().replace(knownPortSet);
             storagePorts = DataObjectUtils.iteratorToList(_dbClient.queryIterativeObjects(StoragePort.class,
                     StringSetUtil.stringSetToUriList(mask.getStoragePorts())));
             
             //Replace initiators
             List<Initiator> initiators = new ArrayList<Initiator>();
             if(null == mask.getInitiators()) {
                 mask.setInitiators(new StringSet());
             }
             
             SetView<String> addedInis = Sets.difference(knownIniSet, mask.getInitiators());
             SetView<String> removedInis= Sets.difference(mask.getInitiators(), knownIniSet);
             
             mask.getInitiators().replace(knownIniSet);
             initiators = DataObjectUtils.iteratorToList(_dbClient.queryIterativeObjects(Initiator.class,
                     StringSetUtil.stringSetToUriList(mask.getInitiators())));
             
             //Replace UserAddedInitiators
             if(null == mask.getUserAddedInitiators()) {
                 mask.setUserAddedInitiators(new StringMap());
             }
             mask.getUserAddedInitiators().replace(userAddedIni);
             //Replace Existing Initiators
             if (null == mask.getExistingInitiators()) {
                 mask.setExistingInitiators(new StringSet());
             }
             mask.getExistingInitiators().replace(unknownIniSet);
             
             if (null != unknownPortSet && !unknownPortSet.isEmpty()) {
                 _logger.info("Unknown Storage Ports {} not able to resolve.");
                 //Raise Event to user, that the re-discovery of the Storage Array didn't bring in the changed ports.
             }
            
             //This takes care of updating the zone map and zone references
             updateZoningMap(mask, removedInis, removedPorts);
            

         
        } catch (Exception e) {
            _logger.error("Exception resolving the masking changes", e);
        } finally {
            if (it != null) {
                it.close();
            }

           if (response != null) {
                try {
                    client.closeEnumeration(Constants.MASKING_PATH, response.getContext());
                } catch (Exception e) {
                    _logger.debug("Exception occurred while closing enumeration", e);
                }
            }
        }
    }

    private void updateZoningMap(ExportMask mask, Set<String> removedInitiators, Set<String> removedPorts) {
        NetworkDeviceController networkDeviceController = (NetworkDeviceController)
                _keyMap.get(Constants.networkDeviceController);
        try {
            networkDeviceController.refreshZoningMap(mask, removedInitiators, removedPorts, true, true);
        } catch (Exception ex) {
            _logger.error("Failed to update  zoning map for mask {}", mask.getMaskName());
        }
    }

    /**
     * 
     * @param it
     * @param knownIniSet
     * @param knownPortSet
     * @param userAddedInitiators
     * @param unknownIniSet
     * @param unknownPortSet
     */
   private void processVolumesAndInitiatorsPaths(CloseableIterator<CIMInstance> it,Set<String> knownIniSet,
           Set<String> knownPortSet, Map<String,String> userAddedInitiators, Set<String> unknownIniSet,
           Set<String> unknownPortSet) {
        while (it.hasNext()) {
            CIMInstance instance = it.next();
            switch (instance.getClassName()) {
                 case SmisConstants.CP_SE_STORAGE_HARDWARE_ID:
                    
                     String initiatorNetworkId = this.getCIMPropertyValue(instance, SmisConstants.CP_STORAGE_ID);
                    if (WWNUtility.isValidNoColonWWN(initiatorNetworkId)) {
                        initiatorNetworkId = WWNUtility.getWWNWithColons(initiatorNetworkId);
                    } else if (WWNUtility.isValidWWN(initiatorNetworkId)) {
                        initiatorNetworkId = initiatorNetworkId.toUpperCase();
                    } else if (initiatorNetworkId.matches(ISCSI_PATTERN)
                            && (iSCSIUtility.isValidIQNPortName(initiatorNetworkId) || iSCSIUtility
                                    .isValidEUIPortName(initiatorNetworkId))) {
                    } else {
                        _logger.warn("Not a valid initiator wwn {}", initiatorNetworkId);
                        continue;
                    }

                    Initiator knownInitiator = NetworkUtil.getInitiator(initiatorNetworkId, _dbClient);
                    if (knownInitiator != null) {
                        knownIniSet.add(knownInitiator.getId().toString());
                        userAddedInitiators.put(knownInitiator.getInitiatorPort(), knownInitiator.getId().toString());
                    } else {
                        _logger.info("Unknown initiator {} found registered on Array");
                        unknownIniSet.add(initiatorNetworkId);
                    }

                    break;

                // process FC and ISCSI target ports
                case SmisConstants.CP_SYMM_FCSCSI_PROTOCOL_ENDPOINT:
                case SmisConstants.CP_SYMM_ISCSI_PROTOCOL_ENDPOINT:
                case SmisConstants.CP_CLAR_FCSCSI_PROTOCOL_ENDPOINT:
                case SmisConstants.CP_CLAR_ISCSI_PROTOCOL_ENDPOINT:
                case SmisConstants.CP_CLAR_FRONTEND_FC_PORT:

                    String portNetworkId = this.getCIMPropertyValue(instance, SmisConstants.CP_NAME);
                    if (portNetworkId == null) {
                        portNetworkId = this.getCIMPropertyValue(instance, SmisConstants.CP_PERMANENT_ADDRESS);
                    }
                    
                    _logger.info("Storage Port id {} discovered : "  + portNetworkId);
                    if (WWNUtility.isValidNoColonWWN(portNetworkId)) {
                        portNetworkId = WWNUtility.getWWNWithColons(portNetworkId);
                    } else if (WWNUtility.isValidWWN(portNetworkId)) {
                        portNetworkId = portNetworkId.toUpperCase();
                    } else if (portNetworkId.matches(ISCSI_PATTERN)
                            && (iSCSIUtility.isValidIQNPortName(portNetworkId) || iSCSIUtility.isValidEUIPortName(portNetworkId))) {
                        portNetworkId = portNetworkId.split(",")[0];
                    } else {
                        _logger.warn("Invalid Storage Port WWN");
                        continue;
                    }

                    // check if a storage port exists for this id in ViPR if so, add to _storagePorts
                    StoragePort knownStoragePort = NetworkUtil.getStoragePort(portNetworkId, _dbClient);
                    if (knownStoragePort != null) {
                        knownPortSet.add(knownStoragePort.getId().toString());
                    } else {
                        _logger.info("Unknown Storage Port {} not discovered in database " + portNetworkId);
                        unknownPortSet.add(portNetworkId);
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
    
}
