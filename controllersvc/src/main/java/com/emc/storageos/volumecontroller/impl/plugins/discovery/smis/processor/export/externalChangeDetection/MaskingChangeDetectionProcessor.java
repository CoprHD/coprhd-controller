package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export.externalChangeDetection;

import java.net.URI;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.util.EventUtils;
import com.emc.storageos.db.client.model.util.EventUtils.EventCode;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.model.systems.StorageSystemRefreshParam;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.plugins.ServiceOptions;
import com.emc.storageos.volumecontroller.impl.plugins.ServiceOptions.serviceParameters;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.utils.ExportChangeDetectionProperties;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public class MaskingChangeDetectionProcessor extends Processor {
    
    
    protected DbClient _dbClient;
    
    private List<Object> _args;
    
    private EnumerateResponse<CIMInstance> responses;
    
    private final String ISCSI_PATTERN = "^(iqn|IQN|eui).*$";
    
    private Logger _logger = LoggerFactory.getLogger(MaskingChangeDetectionProcessor.class);
    
    @SuppressWarnings("unchecked")
    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap) throws BaseCollectionException {
        /**
         * Algorithm : 1. Get Export Masks in ViPR belonging to the given
         * storage System. 2. For each mask in ViPR database, get the masking
         * view from Array. 3. Find out differences and raise Events.
         */
        
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            
           
            
            WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
            
            // Construct Map of MaskName and CIMInstance & MaskName and
            // ExportMask object
            CIMObjectPath path = this.getObjectPathfromCIMArgument(_args, keyMap);
            
            CIMInstance lunMaskingView = client.getInstance(path, false, false, null);
            
            String maskName = CIMPropertyFactory.getPropertyValue(lunMaskingView, SmisConstants.CP_NAME);
            
            @SuppressWarnings("deprecation")
            List<URI> maskUriList = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getExportMaskByNameConstraint(maskName));
            
            if (null == maskUriList || maskUriList.isEmpty()) {
                _logger.warn("Storage System doesn't have any export masks in ViPR database");
                return;
            }
            
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, maskUriList.get(0));
            
            responses = (EnumerateResponse<CIMInstance>) resultObj;
            
            Set<String> initiatorIdsFromArray = new HashSet<String>();
            
            Set<String> storagePortIdsFromArray = new HashSet<String>();
            
            while (responses.getResponses().hasNext()) {
                CIMInstance instance = responses.getResponses().next();
                
                switch (instance.getClassName()) {
                
                case SmisConstants.CP_SYMM_FCSCSI_PROTOCOL_ENDPOINT:
                case SmisConstants.CP_SYMM_ISCSI_PROTOCOL_ENDPOINT:
                    String portNetworkId = this.getCIMPropertyValue(instance, SmisConstants.CP_NAME);
                    _logger.info("Storage Port  id " + portNetworkId);
                    if (portNetworkId == null) {
                        portNetworkId = this.getCIMPropertyValue(instance, SmisConstants.CP_PERMANENT_ADDRESS);
                    }
                    
                    if (WWNUtility.isValidNoColonWWN(portNetworkId)) {
                        portNetworkId = WWNUtility.getWWNWithColons(portNetworkId);
                        
                    } else if (WWNUtility.isValidWWN(portNetworkId)) {
                        portNetworkId = portNetworkId.toUpperCase();
                        
                    } else if (portNetworkId.matches(ISCSI_PATTERN)
                            && (iSCSIUtility.isValidIQNPortName(portNetworkId) || iSCSIUtility.isValidEUIPortName(portNetworkId))) {
                        // comes from SMI-S in the following format (just want
                        // the
                        // first part)
                        // "iqn.1992-04.com.emc:50000973f0065980,t,0x0001"
                        portNetworkId = portNetworkId.split(",")[0];
                        
                    } else {
                        
                        continue;
                    }
                    
                    // check if a storage port exists for this id in ViPR
                    // if so, add to _storagePorts
                    StoragePort knownStoragePort = NetworkUtil.getStoragePort(portNetworkId, _dbClient);
                    storagePortIdsFromArray.add(knownStoragePort.getId().toString());
                    break;
                
                case SmisConstants.CP_SE_STORAGE_HARDWARE_ID:
                    String initiatorNetworkId = getCIMPropertyValue(instance, SmisConstants.CP_STORAGE_ID);
                    _logger.info("Initiator network id " + initiatorNetworkId);
                    
                    initiatorIdsFromArray.add(initiatorNetworkId);
                    break;
                default:
                    break;
                }
            }
            
            Set<String> storagePortsInMask = new HashSet<String>();
            
            if (null != exportMask.getStoragePorts()) {
                storagePortsInMask.addAll(exportMask.getStoragePorts());
            }
            
            _logger.info("Storage Ports in Array : {} , Database : {}", Joiner.on("@@").join(storagePortIdsFromArray),
                    Joiner.on("@@").join(storagePortsInMask));
            
            SetView<String> removedPorts = Sets.difference(storagePortsInMask, storagePortIdsFromArray);
            
            SetView<String> addedPorts = Sets.difference(storagePortIdsFromArray, storagePortsInMask);
            
            String message = ExportChangeDetectionProperties.getMessage("Masking.storagePortChanged", maskName, addedPorts,
                    removedPorts);
            
            if (!storagePortIdsFromArray.isEmpty()) {
                EventUtils.createActionableEvent(_dbClient, EventCode.STORAGE_PORT_CHANGED, null, maskName, message, "warning",
                        exportMask, maskUriList, EventUtils.refreshExportMasks, new Object[] { maskUriList, StorageSystem.Discovery_Namespaces.RESOLVE_MASKING_CHANGES });
            }
            
        } catch (Exception e) {
            _logger.error("External Export Change Detection failed", e);
        }
        
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.plugins.common.Processor#setPrerequisiteObjects(java
     * .util.List)
     */
    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
        this._args = inputArgs;
    }
    
}
