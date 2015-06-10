/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.monitoring.cim.event;

import java.net.URI;

import javax.cim.UnsignedInteger16;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.OperationalStatus;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StoragePortProcessor;
@Component("CIMStoragePortUpdatableDeviceEvent")
@Scope("prototype")
public class CIMStoragePortUpdatableDeviceEvent extends
        CIMInstanceRecordableDeviceEvent implements ApplicationContextAware{
    
    /**
     * Logger to log the debug statements
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(CIMStoragePortUpdatableDeviceEvent.class);
    
    private String newOperationalStatus = StoragePort.OperationalStatus.UNKNOWN.name();
    /**
     * 
     * @param dbClient
     */
    @Autowired
    public CIMStoragePortUpdatableDeviceEvent(DbClient dbClient) {
        super(dbClient);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        _applicationContext = applicationContext;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        if(_eventType == null){
            _eventType =  OperationTypeEnum.UPDATE_STORAGE_PORT.getEvType(true);
        }
        return _eventType;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getExtensions() {
        return String.format("Port's operational status :%s", newOperationalStatus);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getRecordType() {
        return RecordType.Event.name();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getNativeGuid() {

        if (_nativeGuid != null) {
            _logger.debug("Using already computed NativeGuid : {}", _nativeGuid);
        } else {
            try { 
                StoragePort storagePort = getStoargePortFromDBBasedOnPortName();
                if(storagePort!=null){
                    _nativeGuid = storagePort.getNativeGuid();
                }
                logMessage("NativeGuid for storagePort Computed as  : [{}]",
                        new Object[] { _nativeGuid });
            } catch (Throwable e) {
                _logger.error("Unable to compute NativeGuid :", e);
            }
        }

        return _nativeGuid;

    }
    
    /**
     * Returns StoragePort instance from DB based on the indication.
     * @return
     */
    private StoragePort getStoargePortFromDBBasedOnPortName(){
        StoragePort storagePort = null;
        String classSuffix = _indication.get(CIMConstants.SOURCE_INSTANCE_MODEL_PATH_CLASS_SUFFIX_TAG);
        URIQueryResultList results = new URIQueryResultList();
        String sourceInstanceSysName = _indication.get("SourceInstanceSystemName");
        if(CIMConstants.FC_PORT_CLASS_SUFFIX.equalsIgnoreCase(classSuffix)){
            /**
             * PreviousInstancePermanentAddress : 50000973F0065901
             * We need to convert to 50:00:09:73:F0:06:59:01 to generate nativeGuid
             */
            String permamnetAddress = _indication.get("PreviousInstancePermanentAddress");
            _logger.debug("permamnetAddress from indication :{}",permamnetAddress);
            String wwnPermanentAddress = WWNUtility.getWWNWithColons(permamnetAddress);
            _logger.debug("wwnPermanentAddress :{}",wwnPermanentAddress);
            _nativeGuid = NativeGUIDGenerator.generateNativeGuidForStoragePortFromIndication(sourceInstanceSysName, wwnPermanentAddress);
        }else if(CIMConstants.iSCSI_PORT_CLASS_SUFFIX.equalsIgnoreCase(classSuffix)){
            String sourceInstanceModelPathName = _indication.get("SourceInstanceModelPathName");
            /**
             * SourceInstanceModelPathName : iqn.1992-04.com.emc:50000973f0065980,t,0x0001
             * We need only iqn.1992-04.com.emc:50000973f0065980 to generate nativeGuid
             */
            _logger.debug("sourceInstanceModelPathName :{}",sourceInstanceModelPathName);
            String[] spliterArr = sourceInstanceModelPathName.split(",");
            _nativeGuid = NativeGUIDGenerator.generateNativeGuidForStoragePortFromIndication(sourceInstanceSysName, spliterArr[0]);
        }
        _logger.debug("_nativeGuid :{}",_nativeGuid);
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStoragePortByNativeGuidConstraint(_nativeGuid), results);
        if (results.iterator().hasNext()) {
            URI storagePortURI = results.iterator().next();
            _logger.debug("StoragePort's URI :{}",storagePortURI);
            storagePort = _dbClient.queryObject(StoragePort.class, storagePortURI);
            _logger.debug("StoragePort nativeGuid :{}",storagePort.getNativeGuid());
        }else{
            _logger.error("Unable to find StoragePort instance for the given indication");
        }
        return storagePort;
    }
    
    /**
     * Updates Port's operational status based on the indication received from SMI-S provider.
     * @return true if success. 
     */
    public Boolean updateStoragePortOperationalStatus(){
        _logger.info("Updating operationalStatus for the StoragePort initiated");
        boolean updateStatus =false;
        StoragePort storagePort = getStoargePortFromDBBasedOnPortName();
        
        OperationalStatus operationalStatus = StoragePortProcessor.getPortOperationalStatus(getOperationalStatusCodesArray());
        newOperationalStatus = operationalStatus.name();
        storagePort.setOperationalStatus(newOperationalStatus);
        _dbClient.persistObject(storagePort);
        
        updateStatus = true;
        _logger.info("Updating operationalStatus for the StoragePort completed status:{}",updateStatus);
        return updateStatus;
    }
    
    /**
     * Converts {@link String}operationalStatusCode to UnsignedInteger16[] format
     * @return {@link UnsignedInteger16}[] operationalStatusCodes of port
     */
    private UnsignedInteger16[] getOperationalStatusCodesArray(){
        String operationalStatusCode = getOperationalStatusCodes();
        _logger.debug("operationalStatusCode :{}",operationalStatusCode);
        String[] opStausCodeArray = operationalStatusCode.split(",");
        UnsignedInteger16[] unsignedArray = new UnsignedInteger16[opStausCodeArray.length];
        for(int i=0, size = opStausCodeArray.length; i<size; i++){
            unsignedArray[i] = new UnsignedInteger16(opStausCodeArray[i].trim());
        }
        _logger.debug("Unsigned16 array value :{}",unsignedArray);
        return unsignedArray;
    }
    
    /**
     * Log the messages. This method eliminates the logging condition check
     * every time when we need to log a message.
     * 
     * @param msg
     * @param obj
     */
    private void logMessage(String msg, Object[] obj) {
        if (_monitoringPropertiesLoader.isToLogIndications()) {
            _logger.debug("-> " + msg, obj);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<? extends DataObject> getResourceClass() {
        return StoragePort.class;
    }

}
