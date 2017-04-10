/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.common.Constants;

/**
 * Abstract class containing methods common to its children.
 */
public abstract class AbstractCIMObjectPathFactory implements CIMObjectPathFactory {

    private static final Logger log = LoggerFactory.getLogger(AbstractCIMObjectPathFactory.class);

    private String systemNamePrefix;
    private String paramNamePrefix;

    protected CIMArgumentFactory cimArgumentFactory;
    protected CIMPropertyFactory cimPropertyFactory;
    protected CIMConnectionFactory cimConnectionFactory;
    protected DbClient dbClient;

    // Methods called within Creator/Query class has to go through Adapter again.
    protected CIMObjectPathFactoryAdapter cimAdapter;

    /*
     * Getters / Setters
     */

    public CIMArgumentFactory getCimArgumentFactory() {
        return cimArgumentFactory;
    }

    public void setCimArgumentFactory(CIMArgumentFactory cimArgumentFactory) {
        this.cimArgumentFactory = cimArgumentFactory;
    }

    public CIMPropertyFactory getCimPropertyFactory() {
        return cimPropertyFactory;
    }

    public void setCimPropertyFactory(CIMPropertyFactory cimPropertyFactory) {
        this.cimPropertyFactory = cimPropertyFactory;
    }

    public CIMConnectionFactory getCimConnectionFactory() {
        return cimConnectionFactory;
    }

    public void setCimConnectionFactory(CIMConnectionFactory cimConnectionFactory) {
        this.cimConnectionFactory = cimConnectionFactory;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public CIMObjectPathFactoryAdapter getCimAdapter() {
        return cimAdapter;
    }

    public void setCimAdapter(CIMObjectPathFactoryAdapter cimAdapter) {
        this.cimAdapter = cimAdapter;
    }

    public String getSystemNamePrefix() {
        return systemNamePrefix;
    }

    public void setSystemNamePrefix(String systemNamePrefix) {
        this.systemNamePrefix = systemNamePrefix;
    }

    public String getParamNamePrefix() {
        return paramNamePrefix;
    }

    public void setParamNamePrefix(String paramNamePrefix) {
        this.paramNamePrefix = paramNamePrefix;
    }

    /*
     * Common CIMObjectPathFactory methods
     */

    public String prefixWithSystemName(String str) {
        return getSystemNamePrefix().concat(str);
    }

    public String prefixWithParamName(String str) {
        return getParamNamePrefix().concat(str);
    }

    public CIMObjectPath getCimObjectPathFromOutputArgs(CIMArgument[] outputArguments, String key) {
        CIMObjectPath cimObjectPath = null;
        for (CIMArgument outArg : outputArguments) {
            if (outArg != null) {
                if (outArg.getName().equals(key)) {
                    cimObjectPath = (CIMObjectPath) outArg.getValue();
                    break;
                }
            }
        }
        return cimObjectPath;
    }

    public Object getFromOutputArgs(CIMArgument[] outputArguments, String key) {
        Object element = null;
        for (CIMArgument outArg : outputArguments) {
            if (outArg != null) {
                if (outArg.getName().equals(key)) {
                    element = outArg.getValue();
                    break;
                }
            }
        }
        return element;
    }

    public CIMObjectPath[] getProtocolControllersFromOutputArgs(CIMArgument[] outputArguments) {
        CIMObjectPath[] protocolControllers = null;
        for (CIMArgument outputArgument : outputArguments) {
            if ((outputArgument != null) && outputArgument.getName().equals(CP_PROTOCOL_CONTROLLERS)) {
                protocolControllers = (CIMObjectPath[]) outputArgument.getValue();
                break;
            }
        }
        return protocolControllers;
    }

    /**
     * This method extracts the group name from the group path.
     * 
     * @param storageDevice The reference to storage device
     * @param groupPath storage group path
     * @return the group name
     */
    public String getMaskingGroupName(StorageSystem storageDevice, CIMObjectPath groupPath) {
        String groupName = null;

        CIMProperty<?>[] keys = groupPath.getKeys();
        for (CIMProperty key : keys) {
            if (key.getName().equals(CP_INSTANCE_ID)) {
                String groupNameProperty = key.getValue().toString();
                int lastDelimIndex = 0;
                if (storageDevice.getUsingSmis80()) {
                    lastDelimIndex = groupNameProperty.lastIndexOf(Constants.SMIS80_DELIMITER);
                    // For V3 provider example:
                    // if groupNameProperty = SYMMETRIX-+-000196700567-+-stdummyhost3_567_SG_BRONZE_DSS_SRP_1
                    // substring will get stdummyhost3_567_SG_BRONZE_DSS_SRP_1 as a groupName
                    groupName = groupNameProperty.substring(lastDelimIndex + 3);
                } else {
                    lastDelimIndex = groupNameProperty.lastIndexOf(Constants.PLUS);
                    // For V2 provider example:
                    // if groupNameProperty = SYMMETRIX+000195701505+stdummyhost3_505_SG_GOLD
                    // substring will get stdummyhost3_505_SG_GOLD as a groupName
                    groupName = groupNameProperty.substring(lastDelimIndex + 1);
                }
            }
        }
        return groupName;
    }

    public CIMObjectPath getStorageGroupObjectPath(String storageGroupName, StorageSystem storage) throws Exception {
        return getMaskingGroupPath(storage, storageGroupName, MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
    }

    /**
     * Get an Iterator for all CIM_StorageSynchronized instances referenced by the given
     * BlockObject.
     * 
     * @param storage
     *            [required] - StorageSystem object representing array
     * @param subject
     *            [required] - BlockObject used for reference
     * @return SE_StorageSynchronized_SV_SV CIMObjectPath object associated with the given
     *         objectPath
     */
    public CloseableIterator<CIMObjectPath> getSyncObjects(StorageSystem storage, BlockObject subject) {
        try {
            CIMObjectPath subjectPath = getBlockObjectPath(storage, storage, subject);
            return cimConnectionFactory.getConnection(storage).getCimClient().
                    referenceNames(subjectPath, SmisConstants.SE_STORAGE_SYNCHRONIZED_SV_SV, null);
        } catch (WBEMException e) {
            log.warn(String.format("Trying to find syncObject for %s failed",
                    subject.getId()));
        }
        return null;
    }

    /**
     * Return a single CIM_StorageSynchronized instance referenced by the given BlockObject.
     * 
     * @deprecated In cases where there may be more than one CIM_StorageSynchronized instance, we
     *             should favor using #getSyncObjects instead and inspect each instances'
     *             [System|Synced] Element property to ensure that we're operating on the correct
     *             one.
     * @param storage
     * @param subject
     * @return
     */
    public CIMObjectPath getSyncObject(StorageSystem storage, BlockObject subject) {
        CloseableIterator<CIMObjectPath> syncReference = null;
        CIMObjectPath syncObjectPath = NULL_CIM_OBJECT_PATH;
        try {
            syncReference = getSyncObjects(storage, subject);
            if (syncReference != null) {
                while (syncReference.hasNext()) {
                    syncObjectPath = syncReference.next();
                    if (syncObjectPath != null) {
                        break;
                    }
                }
            }
        } finally {
            if (syncReference != null) {
                syncReference.close();
            }
        }
        return syncObjectPath;
    }

    public String getSystemName(StorageSystem system) {
        return SmisUtils.translate(system, prefixWithSystemName(system.getSerialNumber()));
    }

    public String getPoolName(StorageSystem system, String poolID) {
        return SmisUtils.translate(system, prefixWithSystemName(system.getSerialNumber()).concat("+").concat(poolID));
    }

    // TODO: check if this method is used at all if not delete it
    public String getProcessorName(StorageSystem system, String processorName) {
        return SmisUtils.translate(system,
                prefixWithSystemName(system.getSerialNumber()).concat("+FA-").concat(processorName.replaceAll(":", "+")));
    }

    /**
     * Creates a CIMInstance object with properties that will be used for calling the modifyInstance
     * CIM operation. We want to set a parameter for the enabling the creating of VDEVs when this
     * setting is applied to a CreateOrModifyElement call.
     * 
     * @param setting
     *            [required] - CIMObjectPath referencing the StorageSetting
     * @return CIMInstance - newly created CIMInstance object with properties
     */
    public CIMInstance getStoragePoolVdevSettings(CIMObjectPath setting) {
        CIMProperty[] properties = new CIMProperty[] {
                cimPropertyFactory.uint16(CP_STORAGE_EXTENT_INITIAL_USAGE, DELTA_REPLICA_TARGET_VALUE),
        };
        return new CIMInstance(setting, properties);
    }

    public abstract CIMObjectPath getBlockObjectPath(StorageSystem storage, StorageSystem storage1,
            BlockObject subject);

    public abstract CIMObjectPath getMaskingGroupPath(StorageSystem storage, String storageGroupName,
            MASKING_GROUP_TYPE se_deviceMaskingGroup) throws Exception;
}
