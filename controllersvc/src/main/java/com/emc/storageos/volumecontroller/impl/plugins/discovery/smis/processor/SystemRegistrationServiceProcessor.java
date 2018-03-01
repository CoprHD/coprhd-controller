/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.util.Iterator;
import java.util.Map;

import javax.cim.CIMArgument;
import javax.cim.CIMDataType;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger32;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/**
 * Use the SystemRegistrationService Instance to perform the VMAXSystemRefresh
 * 
 */
public class SystemRegistrationServiceProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(SystemRegistrationServiceProcessor.class);

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
	@Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {   	
            String serialID = (String) keyMap.get(Constants._serialID);
            try {
                final Iterator<?> it = (Iterator<?>) resultObj;
                while (it.hasNext()) {
                    final CIMObjectPath systemRegistrationService = (CIMObjectPath) it.next();
                    if (operation.getMethod().contains(Constants._enum)) {
                        CIMProperty<?> ccprop = systemRegistrationService.getKey(Constants._CreationClassName);
                        String ccName = (String) ccprop.getValue();
                        _logger.debug("CCName :" + ccName);
                        if (ccName.contains(Constants.SYSTEMREGISTRATIONSERVICE)) {
                        	//There should only be one....                    	
                            addPath(keyMap, operation.getResult(), systemRegistrationService);
                        	refreshVMAXStorageSystem(serialID, systemRegistrationService, keyMap);	 
                            break;                 	
                        }
                    }
                }
            } catch (SMIPluginException e) {
                throw e;
            } catch (Exception e) {
                String errMsg = String.format("An error occurred while processing the SystemRegistrationService for refreshing the VMAX system: %s", e.getMessage());
                throw new SMIPluginException(SMIPluginException.ERRORCODE_OPERATIONFAILED, e, errMsg);
            }           
      	
    }
 
    /**
     * Performs the StorageSystem Refresh for VMAX arrays...
     * 
     * @param serialID: VMAX system Serial ID
     * @param systemRegistrationService The CIMObjectpath of SystemRegistrationService used for performign RefreshSystem
     * @param keymap - The keymap holing the CIMObjectpath of the VMAX system 
     * @return NONE
     */
    public void refreshVMAXStorageSystem( String serialID, CIMObjectPath systemRegistrationService, Map<String, Object> keyMap) {
        try {      
                _logger.info("Invoking performStorageSystemRefresh for {System:}", serialID);      	
            	WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
            	CIMObjectPath vmaxSystem = (CIMObjectPath) keyMap.get(Constants._computerSystem);
                CIMObjectPath[] systems = new CIMObjectPath[] { vmaxSystem};            	
                UnsignedInteger32[] syncType = new UnsignedInteger32[] {
                        new UnsignedInteger32(SmisConstants.REPLICATION_DATA_SYNC_TYPE),
                        new UnsignedInteger32(SmisConstants.DEVICES_SYNC_TYPE),
                        new UnsignedInteger32(SmisConstants.MASKING_SYNC_TYPE)
                };
                CIMArgument[] refreshArgs = new CIMArgument[] {
                		new CIMArgument<>(SmisConstants.CP_SYNC_TYPE, CIMDataType.UINT32_ARRAY_T, syncType),
                		new CIMArgument<>(SmisConstants.CP_SYSTEMS, CIMDataType.getDataType(systems), systems)
                };
                long start = System.nanoTime();
                Object obj = client.invokeMethod(systemRegistrationService, SmisConstants.EMC_REFRESH_SYSTEM, refreshArgs, new CIMArgument[5]);
                String total = String.format("%2.6f", ((System.nanoTime() - start) / 1000000000.0));
                _logger.info("SMI-S Provider: Successfully Completed EMCRefreshSystem on VMAX StorageSytem {} with Object Path {} in time {} seconds.", serialID, vmaxSystem, total );
        }catch (Exception e) {
        	_logger.error("Failed to refresh StorageSystem for serialID {} ", serialID, e);
        }
    }     
}
