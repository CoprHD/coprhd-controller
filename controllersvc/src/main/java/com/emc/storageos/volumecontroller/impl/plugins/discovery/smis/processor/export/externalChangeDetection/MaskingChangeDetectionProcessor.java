/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export.externalChangeDetection;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.util.EventUtils;
import com.emc.storageos.db.client.model.util.EventUtils.EventCode;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.utils.ExportChangeDetectionProperties;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public class MaskingChangeDetectionProcessor extends Processor {
    
    private DbClient _dbClient;
    private List<Object> _args;
    private EnumerateResponse<CIMInstance> responses;
    private Logger _logger = LoggerFactory.getLogger(MaskingChangeDetectionProcessor.class);
    
    @SuppressWarnings({ "unchecked", "deprecation" })
    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap) throws BaseCollectionException {
        /**
         * Algorithm : 1. Get Export Masks in ViPR belonging to the given
         * masking view. 2. Store all Storage Ports and initiators. 3. storage
         * System. 2. For each mask in ViPR database, get the masking view from
         * Array. 3. Find out differences and raise Events.
         */
        
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
            
            // Construct Map of MaskName and CIMInstance & MaskName and
            // ExportMask object
            CIMObjectPath path = this.getObjectPathfromCIMArgument(_args, keyMap);
            String maskName = getCIMPropertyValue(path, SmisConstants.CP_DEVICE_ID);
            
            @SuppressWarnings("deprecation")
            List<URI> maskUriList = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getExportMaskByNameConstraint(maskName));
            if (null == maskUriList || maskUriList.isEmpty()) {
                _logger.warn("Storage System doesn't have any export masks in ViPR database");
                return;
            }
            
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, maskUriList.get(0));
            
            Set<String> initiatorsFromArray = new HashSet<String>();
            Set<String> storagePortsFromArray = new HashSet<String>();
            responses = (EnumerateResponse<CIMInstance>) resultObj;
            processingStoragePortsAndInitiators(responses.getResponses(), initiatorsFromArray, storagePortsFromArray);
            
            while (!responses.isEnd()) {
                _logger.info("Processing next Chunk");
                responses = client
                        .getInstancesWithPath(Constants.MASKING_PATH, responses.getContext(), new UnsignedInteger32(50));
                processingStoragePortsAndInitiators(responses.getResponses(), initiatorsFromArray, storagePortsFromArray);
            }
            
            Set<String> storagePortsInMask = new HashSet<String>();
            
            if (null != exportMask.getStoragePorts()) {
                List<StoragePort> storagePorts = _dbClient.queryObject(StoragePort.class,
                        StringSetUtil.stringSetToUriList(exportMask.getStoragePorts()));
                for (StoragePort stPort : storagePorts) {
                    storagePortsInMask.add(stPort.getPortNetworkId());
                }
            }
            
            Set<String> initiatorPortsInMask = new HashSet<String>();
            URI initiatorToSelectExportGroup = null; 
            if (null != exportMask.getInitiators()) {
                List<Initiator> initiators = _dbClient.queryObject(Initiator.class,
                        StringSetUtil.stringSetToUriList(exportMask.getInitiators()));
                if (null == initiatorToSelectExportGroup) {
                    initiatorToSelectExportGroup = initiators.get(0).getId();
                }
                if (null != exportMask.getExistingInitiators()) {
                    initiatorPortsInMask.addAll(exportMask.getExistingInitiators());
                }
                for (Initiator iniPort : initiators) {
                    initiatorPortsInMask.add(iniPort.getInitiatorPort());
                }
                
            }
            
          
            
            _logger.info("Storage Ports in Array : {} , Database : {}", Joiner.on("@@").join(storagePortsFromArray),
                    Joiner.on("@@").join(storagePortsInMask));
            _logger.info("Initiator Ports in Array : {} , Database : {}", Joiner.on("@@").join(initiatorsFromArray),
                    Joiner.on("@@").join(initiatorPortsInMask));
            
            //TODO : Storage Ports have :format, butportsFromArray doesn't have the same,
            //Right now not fixing this in order to raise events by default.
            SetView<String> storagePortsDiff = Sets.difference(storagePortsInMask, storagePortsFromArray);
            SetView<String> initiatorPortsDiff = Sets.difference(initiatorPortsInMask, initiatorsFromArray);
            
            // If any unknown storage Ports, then create Event.
            // Else, if there is a change in # storage Ports, raise events.
            if (!storagePortsDiff.isEmpty() || storagePortsInMask.size() != storagePortsFromArray.size()
                    || !initiatorPortsDiff.isEmpty() || initiatorPortsInMask.size() != initiatorsFromArray.size()) {
                
                List<URI> exportGroups = _dbClient.queryByConstraint(AlternateIdConstraint.
                        Factory.getExportGroupInitiatorConstraint(initiatorToSelectExportGroup.toString()));
                
                if (null != exportGroups && !exportGroups.isEmpty()) {
                    ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroups.get(0));
                    
                    String message = ExportChangeDetectionProperties.getMessage("Masking.storagePortChanged", maskName, storagePortsInMask,
                            storagePortsFromArray, initiatorPortsInMask, initiatorsFromArray);
                    //Host belongs to a tenant, hence taking the first export Group
                    EventUtils.createActionableEvent(_dbClient, EventCode.MASKING_PORTS_CHANGED,exportGroup.getTenant().getURI() , maskName, message, "warning",
                            exportMask, maskUriList, "refreshExports", new Object[] { maskUriList,
                                    StorageSystem.Discovery_Namespaces.RESOLVE.name()});
                }
                
            }
            
        } catch (Exception e) {
            _logger.error("External Export Change Detection failed", e);
        }
        
    }
    
    /**
     * Process each Storage Port and Initiator and add to the temporary data
     * structure.
     * 
     * @param responses
     * @param initiatorsFromArray
     * @param storagePortsFromArray
     */
    private void processingStoragePortsAndInitiators(CloseableIterator<CIMInstance> responses, Set<String> initiatorsFromArray,
            Set<String> storagePortsFromArray) {
        while (responses.hasNext()) {
            CIMInstance instance = responses.next();
            switch (instance.getClassName()) {
            
            case SmisConstants.CP_SYMM_FCSCSI_PROTOCOL_ENDPOINT:
            case SmisConstants.CP_SYMM_ISCSI_PROTOCOL_ENDPOINT:
                String portNetworkId = this.getCIMPropertyValue(instance, SmisConstants.CP_NAME);
                if (portNetworkId == null) {
                    portNetworkId = this.getCIMPropertyValue(instance, SmisConstants.CP_PERMANENT_ADDRESS);
                }
                _logger.info("Found Storage Port {}", portNetworkId);
                storagePortsFromArray.add(portNetworkId);
                break;
            
            case SmisConstants.CP_SE_STORAGE_HARDWARE_ID:
                String initiatorNetworkId = this.getCIMPropertyValue(instance, SmisConstants.CP_STORAGE_ID);
                _logger.info("Found Initiator ID :{}", initiatorNetworkId);
                initiatorsFromArray.add(initiatorNetworkId);
                break;
            default:
                break;
            }
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
