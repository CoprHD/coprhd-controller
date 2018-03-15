/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.lang.reflect.Method;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExport;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExportMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBFileShare;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBShareMap;
import com.emc.storageos.util.ExportUtils;

public class PropertySetterUtil {

    private static final Logger _logger = LoggerFactory.getLogger(PropertySetterUtil.class);

    private static final String SLASH = "/";

    /**
     * Mapping between Key present in VolumeInfo's StringSetMap
     * and Method Name used in Model Object.
     * e.g.ALLOCATED_CAPACITY("setAllocatedCapacity")
     * setAllocatedCapacity is the method Name used in Volume Model
     * to persist Allocated Capacity.
     * 
     * This logic helps me to avoid writing lot of lines of code
     * for each property within Volume Object.
     * 
     */
    public enum VolumeObjectProperties {
        ALLOCATED_CAPACITY("setAllocatedCapacity"),
        PROVISIONED_CAPACITY("setProvisionedCapacity"),
        TOTAL_CAPACITY("setCapacity"),
        STORAGE_POOL("setPool"),
        WWN("setWWN"),
        IS_THINLY_PROVISIONED("setThinlyProvisioned"),
        NATIVE_ID("setNativeId"),
        NAME("setAlternateName");

        private String _methodName;

        VolumeObjectProperties(String methodName) {
            _methodName = methodName;
        }

        public String getMethodName() {
            return _methodName;
        }
    }

    /**
     * Mapping between Key present in FileSystemInfo's StringSetMap
     * and Method Name used in Model Object.
     * e.g.ALLOCATED_CAPACITY("setAllocatedCapacity")
     * setAllocatedCapacity is the method Name used in FileSystem Model
     * to persist Allocated Capacity.
     * 
     * This logic helps me to avoid writing lot of lines of code
     * for each property within FileSystem Object.
     * 
     */
    public enum FileSystemObjectProperties {
        ALLOCATED_CAPACITY("setAllocatedCapacity"),
        PROVISIONED_CAPACITY("setProvisionedCapacity"),
        TOTAL_CAPACITY("setCapacity"),
        STORAGE_POOL("setPool"),
        IS_THINLY_PROVISIONED("setThinlyProvisioned"),
        NATIVE_ID("setNativeId"),
        NAME("setAlternateName");

        private String _methodName;

        FileSystemObjectProperties(String methodName) {
            _methodName = methodName;
        }

        public String getMethodName() {
            return _methodName;
        }
    }

    /**
     * TypeCast based on the argument type
     * In reality, the setter Method in Model Object will be always one
     * and also , we would be having 2 -3 different datatypes within a Model.
     * This logic helps to avoid lot of coding within Block Service, while creating
     * a Model Object.
     * 
     * @param value
     * @param method
     * @return
     */
    public static Object typeCast(String value, Method method) {
        Class[] parameterTypes = method.getParameterTypes();
        if (parameterTypes[0].toString().contains("Long")) {
            return Long.parseLong(value);
        } else if (parameterTypes[0].toString().contains("String")) {
            return value;
        } else if (parameterTypes[0].toString().contains("Boolean")) {
            return Boolean.parseBoolean(value);
        }
        return null;
    }

    /**
     * get Method Name
     * 
     * @param methodName
     * @param instance
     * 
     * @return Method
     */
    public static Method getMethod(String methodName, Object instance) {
        Method[] methods = instance.getClass().getMethods();
        for (Method m : methods) {
            if (m.getName().equalsIgnoreCase(methodName)) {
                return m;
            }
        }
        return null;
    }

    /**
     * VolumeObjectProperties, an enum, associating key with its MethodName
     * e.g. invoke volume.setAllocatedCapacity(allocatedCapacity).
     * 
     * In the above example, AllocatedCapacity is the key , and its methodName is
     * setAllocatedCapacity. This relation is maintained in VolumeObjectproperties Enum.
     * 
     * The code looks clumsy, if we get each property from set, and invoke its
     * associated Method on Volume instance.
     * 
     * Instead, the idea here is to use reflection to invoke the right Method on volume
     * Instance, based on the key Provided.
     * 
     * @param volumeObjectproperties
     * @param value
     * @param instance
     */
    public static void addPropertyIntoObject(VolumeObjectProperties key, String value, Object instance) throws Exception {
        if (null != value) {
            Method method = PropertySetterUtil.getMethod(key.getMethodName(), instance);
            if (null == method) {
                return;
            }
            Object typeCastedValue = PropertySetterUtil.typeCast(value, method);
            if (null == typeCastedValue) {
                return;
            }
            Object[] args = new Object[] { typeCastedValue };
            if (null != method) {
                method.invoke(instance, args);
            }
        }
    }

    /**
     * filesystemObjectProperties, an enum, associating key with its MethodName
     * e.g. invoke FileSystem.setAllocatedCapacity(allocatedCapacity).
     * 
     * In the above example, AllocatedCapacity is the key , and its methodName is
     * setAllocatedCapacity. This relation is maintained in FileSystemObjectProperties Enum.
     * 
     * 
     * Instead, the idea here is to use reflection to invoke the right Method on filesystem
     * Instance, based on the key Provided.
     * 
     * @param filesystemObjectproperties
     * @param value
     * @param instance
     */
    public static void addPropertyIntoObject(FileSystemObjectProperties key, String value, Object instance) throws Exception {
        if (null != value) {
            Method method = PropertySetterUtil.getMethod(key.getMethodName(), instance);
            if (null == method) {
                return;
            }
            Object typeCastedValue = PropertySetterUtil.typeCast(value, method);
            if (null == typeCastedValue) {
                return;
            }
            Object[] args = new Object[] { typeCastedValue };
            if (null != method) {
                method.invoke(instance, args);
            }
        }
    }

    /**
     * extract value from a String Set
     * This method is used, to get value from a StringSet of size 1.
     * 
     * @param key
     * @param volumeInformation
     * @return String
     */
    public static String extractValueFromStringSet(String key, StringSetMap volumeInformation) {
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
     * extract values from a String Set
     * This method is used, to get value from a StringSet of variable size.
     * 
     * @param key
     * @param volumeInformation
     * @return String[]
     */
    public static StringSet extractValuesFromStringSet(String key, StringSetMap volumeInformation) {
        try {
            StringSet returnSet = new StringSet();
            StringSet availableValueSet = volumeInformation.get(key);
            if (null != availableValueSet) {
                for (String value : availableValueSet) {
                    returnSet.add(value);
                }
            }
            return returnSet;
        } catch (Exception e) {
            _logger.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * VolumeObjectProperties, an enum, associating key with its MethodName
     * e.g. invoke volume.setAllocatedCapacity(allocatedCapacity).
     * 
     * In the above example, AllocatedCapacity is the key , and its methodName is
     * setAllocatedCapacity. This relation is maintained in VolumeObjectproperties Enum.
     * 
     * The code looks clumsy, if we get each property from set, and invoke its
     * associated Method on Volume instance.
     * 
     * Instead, the idea here is to use reflection to invoke the right Method on volume
     * Instance, based on the key Provided.
     * 
     * @param volumeInformation
     * @param volume
     * @param key
     */
    private static void addVolumeDetail(
            StringSetMap volumeInformation, Volume volume, VolumeObjectProperties key)
            throws Exception {
        String value = PropertySetterUtil.extractValueFromStringSet(key.toString(), volumeInformation);
        PropertySetterUtil.addPropertyIntoObject(key, value, volume);
    }

    /**
     * FileSystemObjectProperties, an enum, associating key with its MethodName
     * e.g. invoke filesystem.setAllocatedCapacity(allocatedCapacity).
     * 
     * In the above example, AllocatedCapacity is the key , and its methodName is
     * setAllocatedCapacity. This relation is maintained in FileSystemObjectproperties Enum.
     * 
     * The code looks clumsy, if we get each property from set, and invoke its
     * associated Method on FileSystem instance.
     * 
     * Instead, the idea here is to use reflection to invoke the right Method on filesystem
     * Instance, based on the key Provided.
     * 
     * @param filesystemInformation
     * @param filesystem
     * @param key
     */
    private static void addFileSystemDetail1(
            StringSetMap filesystemInformation, FileShare filesystem, FileSystemObjectProperties key)
            throws Exception {
        String value = PropertySetterUtil.extractValueFromStringSet(key.toString(), filesystemInformation);
        PropertySetterUtil.addPropertyIntoObject(key, value, filesystem);
    }

    /**
     * Extract Details from UnManaged Volume Object and add to Volume Object.
     * 
     * @param volumeInformation
     * @param volumeInfo
     * @param volume
     * @return
     */
    public static Volume addVolumeDetails(StringSetMap volumeInformation, Volume volume)
            throws Exception {
        for (VolumeObjectProperties volumeObjectProp : VolumeObjectProperties.values()) {
            addVolumeDetail(volumeInformation, volume, volumeObjectProp);
        }
        return volume;

    }

    /**
     * Extract Details from UnManaged filesystem Object and add to filesystem Object.
     * 
     * @param filesystemInformation
     * @param filesystemInfo
     * @param filesystem
     * @return
     */
    public static FileShare addFileSystemDetails(StringSetMap filesystemInformation, FileShare filesystem)
            throws Exception {
        for (FileSystemObjectProperties filesystemObjectProp : FileSystemObjectProperties.values()) {
            addFileSystemDetail1(filesystemInformation, filesystem, filesystemObjectProp);
        }
        return filesystem;

    }

    /**
     * Extract SMB data from UnManaged to Managed
     * 
     * @param unManagedSMBShareMap
     * @return
     */
    public static SMBShareMap convertUnManagedSMBMapToManaged(
            UnManagedSMBShareMap unManagedSMBShareMap, StoragePort storagePort, StorageHADomain dataMover) {

        SMBShareMap smbShareMap = new SMBShareMap();
        if (unManagedSMBShareMap == null) {
            return smbShareMap;
        }
        SMBFileShare smbshare = null;
        for (UnManagedSMBFileShare unManagedSMBFileShare : unManagedSMBShareMap.values()) {
            smbshare = new SMBFileShare();

            smbshare.setName(unManagedSMBFileShare.getName());
            smbshare.setNativeId(unManagedSMBFileShare.getNativeId());
            smbshare.setDescription(unManagedSMBFileShare.getDescription());
            if (storagePort != null) {
                smbshare.setMountPoint("\\\\" + storagePort.getPortNetworkId() + "\\" + unManagedSMBFileShare.getName());
            } else {
                smbshare.setMountPoint(unManagedSMBFileShare.getMountPoint());
            }
            smbshare.setPath(unManagedSMBFileShare.getPath());
            // need to removed
            smbshare.setMaxUsers(unManagedSMBFileShare.getMaxUsers());
            smbshare.setPermission(unManagedSMBFileShare.getPermission());
            smbshare.setPermissionType(unManagedSMBFileShare.getPermissionType());
            smbshare.setPortGroup(storagePort.getPortGroup());
            // share name
            smbShareMap.put(unManagedSMBFileShare.getName(), smbshare);
        }
        return smbShareMap;
    }

    /**
     * This method converts unmanaged Fs export map to managed Fs export map
     * 
     * @param unManagedFSExportMap
     * @param storagePort
     * @param dataMover
     * @param fsPath
     * @return
     */
    public static FSExportMap convertUnManagedExportMapToManaged(
            UnManagedFSExportMap unManagedFSExportMap, StoragePort storagePort, StorageHADomain dataMover, String fsPath) {

        FSExportMap fsExportMap = new FSExportMap();

        if (unManagedFSExportMap == null) {
            return fsExportMap;
        }

        for (UnManagedFSExport export : unManagedFSExportMap.values()) {
            FileExport fsExport = new FileExport();

            if (null != export.getIsilonId()) {
                fsExport.setIsilonId(export.getIsilonId());
            }

            if (null != export.getNativeId()) {
                fsExport.setNativeId(export.getNativeId());
            }

            if (null != storagePort) {
                fsExport.setStoragePort(storagePort.getPortName());
                if ((export.getMountPath() != null) && (export.getMountPath().length() > 0)) {
                    fsExport.setMountPoint(ExportUtils.getFileMountPoint(storagePort.getPortNetworkId(), export.getMountPath()));
                } else {
                    fsExport.setMountPoint(ExportUtils.getFileMountPoint(storagePort.getPortNetworkId(), export.getPath()));
                }
            } else if (null != export.getStoragePort()) {
                fsExport.setStoragePort(export.getStoragePort());
                if (null != export.getMountPoint()) {
                    fsExport.setMountPoint(export.getMountPoint());
                }
            }

            if (null != dataMover) {
                fsExport.setStoragePortName(dataMover.getName());
            } else if (null != storagePort) {
                fsExport.setStoragePortName(storagePort.getPortName());
            } else if (null != export.getStoragePortName()) {
                fsExport.setStoragePortName(export.getStoragePortName());
            }

            if (null != export.getMountPath()) {
                fsExport.setMountPath(export.getMountPath());
            }

            String subDir = getSubDirectory(export.getPath(), fsPath);
            if (null != subDir) {
                fsExport.setSubDirectory(subDir);
            }

            fsExport.setPath(export.getPath());
            fsExport.setPermissions(export.getPermissions());
            fsExport.setProtocol(export.getProtocol());
            fsExport.setRootUserMapping(export.getRootUserMapping());
            fsExport.setSecurityType(export.getSecurityType());
            fsExport.setClients(export.getClients());
            fsExportMap.put(fsExport.getFileExportKey(), fsExport);
        }

        return fsExportMap;
    }

    /**
     * Method to extract sub directory name from the export path using file system path
     * 
     * @param exportPath
     * @param fsPath
     * @return
     */
    private static String getSubDirectory(String exportPath, String fsPath) {

        String fsPathWithSlash = fsPath + SLASH;
        if (StringUtils.isNotEmpty(exportPath) && exportPath.contains(fsPathWithSlash)) {
            String[] pathArray = exportPath.split(fsPathWithSlash);
            if (pathArray.length > 0) {
                String subDirName = pathArray[pathArray.length - 1];
                if (subDirName.contains(SLASH)) {
                    subDirName = subDirName.split(SLASH)[0];
                }
                _logger.info("Subdirectroy : {}", subDirName);
                return subDirName;
            }
        }
        _logger.info("Subdirectory is null for export path {} and File system path {}.", exportPath, fsPath);
        return null;
    }
}
