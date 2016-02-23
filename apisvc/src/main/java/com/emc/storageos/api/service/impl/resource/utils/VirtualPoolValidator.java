/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.services.util.StorageDriverManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.vpool.VirtualPoolCommonParam;
import com.emc.storageos.model.vpool.VirtualPoolUpdateParam;

/**
 * Simple Validator framework built around CoS Attributes, using Chain Of Responsibility technique
 * 
 */
public abstract class VirtualPoolValidator<C extends VirtualPoolCommonParam, U extends VirtualPoolUpdateParam> {
    protected StorageDriverManager storageDriverManager = (StorageDriverManager)StorageDriverManager.
            getApplicationContext().getBean(StorageDriverManager.STORAGE_DRIVER_MANAGER);

    protected Logger _logger = LoggerFactory.getLogger(VirtualPoolValidator.class);
    public static final String NONE = "NONE";
    protected VirtualPoolValidator _nextValidator;

    public abstract void setNextValidator(VirtualPoolValidator validator);

    /**
     * extract value from a String Set
     * This method is used, to get value from a StringSet of size 1.
     * 
     * @param key
     * @param volumeInformation
     * @return String
     */
    protected String extractValueFromStringSet(String key, StringSetMap volumeInformation) {
        try {
            StringSet availableValueSet = volumeInformation.get(key);
            if (null != availableValueSet) {
                for (String value : availableValueSet) {
                    return value;
                }
            }
        } catch (Exception e) {
            _logger.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * get Value from String Set
     * 
     * @param key
     * @param volumeInformation
     * @return
     */
    protected StringSet getVolumeInformation(String key, StringSetMap volumeInformation) {
        try {
            StringSet availableValueSet = volumeInformation.get(key);
            return availableValueSet;
        } catch (Exception e) {
            _logger.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * validate CoS Update parameters
     * 
     * @param cos
     * @param updateParam
     * @param dbClient
     * @throws DatabaseException
     */
    public void validateVirtualPoolUpdateParam(VirtualPool cos, U updateParam, DbClient dbClient)
            throws DatabaseException {
        if (isUpdateAttributeOn(updateParam)) {
            validateVirtualPoolUpdateAttributeValue(cos, updateParam, dbClient);
        }
        if (null != _nextValidator) {
            _nextValidator.validateVirtualPoolUpdateParam(cos, updateParam, dbClient);
        }
    }

    protected abstract void validateVirtualPoolUpdateAttributeValue(VirtualPool cos, U updateParam, DbClient dbClient);

    protected abstract boolean isCreateAttributeOn(C createParam);

    /**
     * validate CoS Create parameters
     * 
     * @param createParam
     * @param dbClient
     * @throws DatabaseException
     */
    public void validateVirtualPoolCreateParam(C createParam, DbClient dbClient)
            throws DatabaseException {
        if (isCreateAttributeOn(createParam)) {
            validateVirtualPoolCreateAttributeValue(createParam, dbClient);
        }
        if (null != _nextValidator) {
            _nextValidator.validateVirtualPoolCreateParam(createParam, dbClient);
        }
    }

    protected abstract void validateVirtualPoolCreateAttributeValue(
            C createParam, DbClient dbClient);

    protected abstract boolean isUpdateAttributeOn(U updateParam);
}
