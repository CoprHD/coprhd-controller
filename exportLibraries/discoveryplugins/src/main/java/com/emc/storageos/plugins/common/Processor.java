/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.common;

import java.util.List;
import java.util.Map;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.google.common.collect.Iterables;

/**
 * All Processors should extend this abstract Class. If the output got from
 * SMIProviders is CIMArgument[], then we need a CIMArg processor, whose
 * responsibility is to only handle CIMArg[] values, and if the output is
 * CIMPath, then we need a CIMPath processor which can handle CIMPath
 * outputs.The idea is to follow the principle
 * "Class with Single Responsibility"
 */
public abstract class Processor {
    private Logger _logger = LoggerFactory
            .getLogger(Processor.class);
    protected static final String CreationClassNamestr = "CreationClassName";
    protected static final String SystemCreationClassNamestr = "SystemCreationClassName";
    protected static final String SystemNamestr = "SystemName";
    protected static final String DeviceIDstr = "DeviceID";
    protected static final String _symm = "SYMM";
    protected static final String _clar = "CLAR";
    protected static final String _symmetrix = "SYMMETRIX";
    protected static final String _clariion = "CLARIION";
    protected static final String _symmvolume = "Symm_StorageVolume";
    protected static final String _clarvolume = "Clar_StorageVolume";
    protected static final String _symmsystem = "Symm_StorageSystem";
    protected static final String _clarsystem = "Clar_StorageSystem";
    protected static final String _volume = "Volume";
    protected static final String _spaceConsumed = "AFSPSpaceConsumed";
    protected static final String _spaceLimit = "SpaceLimit";
    protected static final String _SEVEN = "7";
    protected static final String _VOL = "VOLUME";
    protected static final String _plusDelimiter = "+";
    protected static final String _SVSystemName = "SVSystemName";
    protected static final String _SVDeviceID = "SVDeviceID";
    protected static final String _emcspaceConsumed = "EMCSpaceConsumed";

    /**
     * Process the result got from data sources.
     * 
     * @param operation
     *            : Domain Logic operation.
     * @param resultObj
     *            : Result got from 3rd party Instances.
     * @param keyMap
     *            : common datastructure to hold values.
     * @return
     * @throws SMIPluginException
     *             ex.
     */
    public abstract void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException;

    /**
     * set the required arguments for processor to process Result. to-Do :for
     * future use
     * 
     * @param inputArgs
     *            : List of arguments. To-Do : for future purpose
     * @return
     */
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }

    /**
     * create the nativeID key from CIMObjectPath
     * 
     * @param sourcePath
     * @return String
     */
    protected String createKeyfromPath(CIMObjectPath sourcePath) {
        String systemName = sourcePath.getKey(SystemNamestr).getValue().toString()
                .replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
        String key = systemName.toUpperCase()
                + _plusDelimiter + _VOL + _plusDelimiter
                + sourcePath.getKey(DeviceIDstr).getValue().toString();
        _logger.debug("Key Created :{} ", key);
        return key;
    }

    /**
     * Add CIMObject Paths to Map.
     * 
     * @param keyMap
     * @param operation
     * @param path
     * @throws SMIPluginException
     */
    @SuppressWarnings("serial")
    protected void addPath(
            Map<String, Object> keyMap, String key, CIMObjectPath path)
            throws BaseCollectionException {
        try {
            Object result = keyMap.get(key);
            if (keyMap.containsKey(key) && result instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<CIMObjectPath> cimPathList = (List<CIMObjectPath>) keyMap
                        .get(key);
                cimPathList.add(path);
                keyMap.put(key, cimPathList);
            } else {
                keyMap.put(key, path);
            }
        } catch (Exception ex) {
            throw new BaseCollectionException(
                    "Error while adding CIMObject Path to Map : " + path, ex) {
                @Override
                public int getErrorCode() {
                    // To-Do errorCode
                    return -1;
                }
            };
        }
    }

    /**
     * Add CIMObject Paths to Map.
     * 
     * @param keyMap
     * @param operation
     * @param path
     * @throws SMIPluginException
     */
    @SuppressWarnings("serial")
    protected void addInstance(
            Map<String, Object> keyMap, String key, CIMInstance instance)
            throws BaseCollectionException {
        try {
            Object result = keyMap.get(key);
            if (keyMap.containsKey(key) && result instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<CIMInstance> cimInstanceList = (List<CIMInstance>) keyMap
                        .get(key);

                cimInstanceList.add(instance);
                keyMap.put(key, cimInstanceList);
            } else {
                keyMap.put(key, instance);
            }
        } catch (Exception ex) {
            throw new BaseCollectionException(
                    "Error while adding CIMInstance to Map : " + instance.getObjectPath(), ex) {
                @Override
                public int getErrorCode() {
                    // To-Do errorCode
                    return -1;
                }
            };
        }
    }

    /**
     * Get Volume Metrics Object from Map.
     * 
     * @param keyMap
     * @param key
     * @return Stats
     * 
     *         To-Do: move this to util
     * @throws SMIPluginException
     */
    @SuppressWarnings("serial")
    protected Object getMetrics(Map<String, Object> keyMap, String key)
            throws BaseCollectionException {
        Object metrics = null;
        if (keyMap.containsKey(key)) {
            metrics = (Object) keyMap.get(key);
        }
        return metrics;
    }

    /*
     * Create key from CIMProperties
     * 
     * @param volumeInstance
     * 
     * @return String
     */
    protected String createKeyfromProps(CIMInstance volumeInstance) {

        String SystemName = volumeInstance.getPropertyValue(_SVSystemName).toString().toUpperCase();

        String DeviceID = volumeInstance.getPropertyValue(_SVDeviceID).toString().toUpperCase();
        return createVolumeIdKeyForArray(SystemName, DeviceID);
    }

    /**
     * Calculate Provisioned Capacity
     * 
     * @param volumeInstance
     * @return long
     */
    protected long returnProvisionedCapacity(CIMInstance volumeInstance,
            Map<String, Object> keyMap) {
        long blocksize, blocks;
        if (keyMap.containsKey(Constants.IS_NEW_SMIS_PROVIDER)
                && Boolean.valueOf(keyMap.get(Constants.IS_NEW_SMIS_PROVIDER).toString())) {
            blocksize = Long.parseLong(volumeInstance.getPropertyValue(
                    Constants.BLOCK_SIZE).toString());
            blocks = Long.parseLong(volumeInstance.getPropertyValue(
                    Constants.NUMBER_OF_BLOCKS).toString());
        } else {
            blocksize = Long.parseLong(volumeInstance.getPropertyValue(
                    Constants.SV_BLOCK_SIZE).toString());
            blocks = Long.parseLong(volumeInstance.getPropertyValue(
                    Constants.SV_NUMBER_BLOCKS).toString());
        }
        return blocksize * blocks;
    }

    /**
     * get Property Value;
     * 
     * @param poolInstance
     * @param propName
     * @return
     */
    protected String getCIMPropertyValue(CIMInstance instance, String propName) {
        String value = null;
        try {
            value = instance.getPropertyValue(propName).toString();
            if (propName.equals(Constants.INSTANCEID) && value.contains(Constants.SMIS80_DELIMITER)) {
                value = value.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
            }
        } catch (Exception e) {
            _logger.debug("Property {} Not found in returned Instance {}", propName, instance.getObjectPath());
        }
        return value;
    }

    /**
     * get Property Value;
     * 
     * @param poolInstance
     * @param propName
     * @return
     */
    protected String getCIMPropertyValue(CIMObjectPath path, String propName) {
        String value = null;
        try {
            value = path.getKey(propName).getValue().toString();
            if (value.contains(Constants.SMIS80_DELIMITER)) {
                value = value.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
            }

        } catch (Exception e) {
            _logger.warn("Property {} Not found in returned Instance {}", propName, path);
        }
        return value;
    }

    /**
     * get property array value;
     * 
     * @param instance
     * @param propName
     * @return
     */
    protected String[] getCIMPropertyArrayValue(CIMInstance instance, String propName) {
        String[] value = null;
        try {
            value = (String[]) instance.getPropertyValue(propName);
        } catch (Exception e) {
            _logger.warn("Property {} Not found in returned Instance {}", propName, instance.getObjectPath());
        }
        return value;
    }

    protected boolean checkForNull(Object obj) {
        if (null == obj) {
            return true;
        }
        return false;
    }

    /**
     * get Message.
     * 
     * @param ex WBEMException.
     * @return String.
     */
    protected String getMessage(final Exception ex) {
        String cause = ex.getCause() != null ? ex.getCause().toString() : "";
        String message = ex.getMessage() != null ? ex.getMessage() : "";
        String error = "";
        if (!cause.isEmpty()) {
            error = cause;
        }
        if (!message.isEmpty()) {
            error = error + "-" + message;
        }
        return error;
    }

    protected int getPartitionSize(Map<String, Object> keyMap) {
        @SuppressWarnings("unchecked")
        Map<String, String> props = (Map<String, String>) keyMap.get(Constants.PROPS);
        int size = Constants.DEFAULT_PARTITION_SIZE;
        if (null != props.get(Constants.METERING_RECORDS_PARTITION_SIZE)) {
            size = Integer.parseInt(props.get(Constants.METERING_RECORDS_PARTITION_SIZE));
        }
        return size;
    }

    /**
     * return 1st Argument in inputArguments used to
     * call this SMI-S call.
     * 
     * @return
     */
    protected CIMObjectPath getObjectPathfromCIMArgument(List<Object> args) {
        Object[] arguments = (Object[]) args.get(0);
        return (CIMObjectPath) arguments[0];
    }

    /**
     * return 1st Argument in inputArguments used to
     * call this SMI-S call.
     * 
     * @return
     */
    protected CIMObjectPath getObjectPathfromCIMArgument(List<Object> args, Map<String, Object> keyMap) {
        Object[] arguments = (Object[]) args.get(0);
        Boolean using80Delimiters = (Boolean) keyMap.get(Constants.USING_SMIS80_DELIMITERS);
        if (null != using80Delimiters && using80Delimiters) {
            return (CIMObjectPath) Util.normalizedWriteArgs(keyMap, arguments)[0];
        }
        return (CIMObjectPath) arguments[0];
    }

    /**
     * Return the 2nd argument in the input arguments,
     * which should be the current command object index
     * for this processor.
     * 
     * @return the current command object index
     */
    protected Integer getCurrentCommandIndex(List<Object> args) {
        int currentCommandIndex = (int) args.get(1);
        return currentCommandIndex;
    }

    protected String getValueAtGivenPositionFromTierId(Iterable<String> itr, int position) {
        return Iterables.get(itr, position);
    }

    public Object getFromOutputArgs(CIMArgument[] outputArguments, String key) {
        Object element = null;
        if (outputArguments != null) {
            for (CIMArgument outArg : outputArguments) {
                if (outArg != null && null != outArg.getName()) {
                    if (outArg.getName().equals(key)) {
                        element = outArg.getValue();
                        break;
                    }
                } else {
                    _logger.info("Provider returned unexpected values");
                }
            }
        }
        return element;
    }

    /**
     * @param instance CIMInstance to be checked
     * @return true if name space match, otherwise false
     */
    protected boolean isIBMInstance(CIMInstance instance) {
        return Constants.IBM_NAMESPACE.equals(instance.getObjectPath().getNamespace())
                || instance.getClassName().startsWith(Constants.IBMXIV_CLASS_PREFIX);
    }

    /**
     * Create key from CIMProperties
     * 
     * @param volumeInstance {@link CIMInstance} volume details.
     * 
     * @return String
     */
    protected String createKeyfor8x(CIMInstance volumeInstance) {
        String SystemName = volumeInstance.getPropertyValue(SystemNamestr)
                .toString().toUpperCase();

        String DeviceID = volumeInstance.getPropertyValue(DeviceIDstr)
                .toString().toUpperCase();

        String keyString = (createVolumeIdKeyForArray(SystemName, DeviceID))
                .replaceAll(Constants.SMIS_80_STYLE, Constants.SMIS_PLUS_REGEX);
        return keyString;
    }

    /**
     * Method to create key Array+SerialNumber+VOLUME+VOLUMEID dynamically
     * from the returned Volume Instance, so that we can use this key to get
     * back its corresponding Stat Object
     * 
     * @param systemName {@link String} Systemname or Array name
     * @param deviceId {@link String} device id
     * @return
     */
    private String createVolumeIdKeyForArray(String systemName, String deviceId)
    {
        // Creating the key - Array+SerialNumber+VOLUME+VOLUMEID dynamically
        // from the returned Volume Instance, so that we can use this key to get
        // back its corresponding Stat Object
        StringBuilder key = new StringBuilder(systemName);
        key.append(Constants._plusDelimiter).append(_VOL);
        key.append(Constants._plusDelimiter).append(deviceId);
        return key.toString();
    }
}
