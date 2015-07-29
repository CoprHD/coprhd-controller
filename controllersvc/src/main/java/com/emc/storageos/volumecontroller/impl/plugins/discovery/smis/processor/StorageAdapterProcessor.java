/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.StorageHADomain.HADomainType;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

/**
 * Processor responsible for discovering Storage Adapters
 */
public class StorageAdapterProcessor extends Processor {
    private static final String NAME = "Name";
    private static final String EMCNUMPORTS = "EMCNumPorts";
    private static final String EMCPROTOCOL = "EMCProtocol";
    private static final String EMCSERIALNUMBER = "EMCSerialNumber";
    private static final String EMCSLOTNUMBER = "EMCSlotNumber";
    private static final String EMCADAPTERNAME = "EMCAdapterName";
    private Logger _logger = LoggerFactory.getLogger(StorageAdapterProcessor.class);
    private DbClient _dbClient;
    private AccessProfile profile = null;
    private List<StorageHADomain> _storageAdapterList = null;
    private static final String ROLES = "Roles";

    /**
     * {@inheritDoc}
     */
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            _storageAdapterList = new ArrayList<StorageHADomain>();
            StorageSystem device = _dbClient.queryObject(StorageSystem.class, profile.getSystemId());
            while (it.hasNext()) {
                CIMInstance adapterInstance = null;
                try {
                    adapterInstance = it.next();
                    StorageHADomain adapter = checkStorageAdapterExistsInDB(adapterInstance, device);
                    createStorageAdapter(adapter, adapterInstance, profile);
                    addPath(keyMap, operation.getResult(), adapterInstance.getObjectPath());
                } catch (Exception e) {
                    _logger.warn("Adapter Discovery failed for {}-->{}", "",
                            getMessage(e));
                }
            }
            _dbClient.createObject(_storageAdapterList);
        } catch (Exception e) {
            _logger.error("Adapter Discovery failed -->{}", getMessage(e));
        } finally {
            _storageAdapterList = null;
        }
    }

    /**
     * create StorageAdapter Record, if not present already, else update only the properties.
     * 
     * @param adapter
     * @param adapterInstance
     * @throws URISyntaxException
     * @throws IOException
     */
    private void createStorageAdapter(
            StorageHADomain adapter, CIMInstance adapterInstance, AccessProfile profile)
            throws URISyntaxException, IOException {
        if (null == adapter) {
            adapter = new StorageHADomain();
            adapter.setId(URIUtil.createId(StorageHADomain.class));
            adapter.setStorageDeviceURI(profile.getSystemId());
            adapter.setName(getCIMPropertyValue(adapterInstance, NAME));
            adapter.setAdapterName(getCIMPropertyValue(adapterInstance, EMCADAPTERNAME));
            // Don't change the order of setting this nativeGuid as nativeGuid depends on adapter name.
            adapter.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(_dbClient, adapter));
        }
        adapter.setNumberofPorts(getCIMPropertyValue(adapterInstance, EMCNUMPORTS));
        adapter.setProtocol(getCIMPropertyValue(adapterInstance, EMCPROTOCOL));
        adapter.setSlotNumber(getCIMPropertyValue(adapterInstance, EMCSLOTNUMBER));
        String[] roles = (String[]) adapterInstance.getPropertyValue(ROLES);
        adapter.setAdapterType(HADomainType.getHADomainTypeName(roles[0]));

        _storageAdapterList.add(adapter);
    }

    /**
     * Check if Adapter exists in DB.
     * 
     * @param adapterInstance
     * @return
     * @throws IOException
     */
    private StorageHADomain checkStorageAdapterExistsInDB(CIMInstance adapterInstance, StorageSystem device)
            throws IOException {
        StorageHADomain adapter = null;
        String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                device, adapterInstance.getPropertyValue(EMCADAPTERNAME)
                        .toString(), NativeGUIDGenerator.ADAPTER);
        @SuppressWarnings("deprecation")
        List<URI> adapterURIs = _dbClient
                .queryByConstraint(AlternateIdConstraint.Factory
                        .getStorageHADomainByNativeGuidConstraint(adapterNativeGuid));
        if (!adapterURIs.isEmpty()) {
            adapter = _dbClient.queryObject(StorageHADomain.class,
                    adapterURIs.get(0));
        }
        return adapter;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> arg0)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
