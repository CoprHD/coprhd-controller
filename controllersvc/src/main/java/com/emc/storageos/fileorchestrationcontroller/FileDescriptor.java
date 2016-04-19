/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class FileDescriptor implements Serializable {

    public FileDescriptor(Type type, URI deviceURI, URI fsURI, URI poolURI,
            Long fileSize,
            VirtualPoolCapabilityValuesWrapper capabilitiesValues,
            URI migrationId, String suggestedNativeFsId) {
        super();
        this._type = type;
        this._deviceURI = deviceURI;
        this._fsURI = fsURI;
        this._poolURI = poolURI;
        this._fileSize = fileSize;
        this._capabilitiesValues = capabilitiesValues;
        this._migrationId = migrationId;
        this._suggestedNativeFsId = suggestedNativeFsId;

    }

    public FileDescriptor(Type type, URI deviceURI, URI fsURI, URI poolURI,
            String deletionType, boolean forceDelete) {
        super();

        this._type = type;
        this._deviceURI = deviceURI;
        this._fsURI = fsURI;
        this._poolURI = poolURI;
        this.deleteType = deletionType;
        this.forceDelete = forceDelete;
    }

    public FileDescriptor(Type type, URI deviceURI, URI fsURI, URI poolURI,
            String deletionType, boolean forceDelete, Long fileSize) {
        this(type, deviceURI, fsURI, poolURI, deletionType, forceDelete);

        this._fileSize = fileSize;
    }

    public FileDescriptor(Type type, URI deviceURI, URI fsURI, URI poolURI,
            String deletionType, boolean forceDelete, boolean deleteTargetOnly) {
        super();
        this._type = type;
        this._deviceURI = deviceURI;
        this._fsURI = fsURI;
        this._poolURI = poolURI;
        this.deleteType = deletionType;
        this.forceDelete = forceDelete;
        this.deleteTargetOnly = deleteTargetOnly;
    }

    public enum Type {
        /* ******************************
         * The ordering of these are important for the sortByType() method,
         * be mindful when adding/removing/changing the list.
         */
        FILE_DATA(1),                   // user's data filesystem
        FILE_LOCAL_MIRROR_TARGET(2),    // array level mirror
        FILE_SNAPSHOT(3),               // array level snapshot
        FILE_EXISTING_SOURCE(4),        // existing source file
        FILE_MIRROR_SOURCE(5),          // remote mirror source
        FILE_MIRROR_TARGET(6),          // remote mirror target
        FILE_EXISTING_MIRROR_SOURCE(7); // change vpool of filesystem
        private final int order;

        private Type(int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }
    };

    public enum DeleteType {
        FULL,
        VIPR_ONLY
    }

    private Type _type;                  // The type of this file
    private URI _deviceURI;              // Device this file will be created on
    private URI _fsURI;                  // The file id or FileObject or FileShare id to be created
    private URI _poolURI;                // The pool id to be used for creation
    private Long _fileSize;              // Used to separate multi-file create requests
    private VirtualPoolCapabilityValuesWrapper _capabilitiesValues;  // mirror policy is stored in here
    private URI _migrationId;            // Reference to the migration object for this file
    private String _suggestedNativeFsId; // user suggested native id
    private String deleteType;           // delete type either FULL or VIPR_ONLY

    private boolean deleteTargetOnly;

    public String getDeleteType() {
        return deleteType;
    }

    public void setDeleteType(String deleteType) {
        this.deleteType = deleteType;
    }

    public boolean isForceDelete() {
        return forceDelete;
    }

    public void setForceDelete(boolean forceDelete) {
        this.forceDelete = forceDelete;
    }

    private boolean forceDelete;

    public String getSuggestedNativeFsId() {
        return _suggestedNativeFsId;
    }

    public void setSuggestedNativeFsId(String suggestedNativeFsId) {
        this._suggestedNativeFsId = suggestedNativeFsId;
    }

    public Type getType() {
        return _type;
    }

    public void setType(Type type) {
        this._type = type;
    }

    public URI getDeviceURI() {
        return _deviceURI;
    }

    public void setDeviceURI(URI deviceURI) {
        this._deviceURI = deviceURI;
    }

    public URI getFsURI() {
        return _fsURI;
    }

    public void setFsURI(URI fsURI) {
        this._fsURI = fsURI;
    }

    public URI getPoolURI() {
        return _poolURI;
    }

    public void setPoolURI(URI poolURI) {
        this._poolURI = poolURI;
    }

    public VirtualPoolCapabilityValuesWrapper getCapabilitiesValues() {
        return _capabilitiesValues;
    }

    public void setCapabilitiesValues(VirtualPoolCapabilityValuesWrapper capabilitiesValues) {
        this._capabilitiesValues = capabilitiesValues;
    }

    public Long getFileSize() {
        return _fileSize;
    }

    public void setFileSize(Long fileSize) {
        this._fileSize = fileSize;
    }

    public URI getMigrationId() {
        return _migrationId;
    }

    public void setMigrationId(URI migrationId) {
        this._migrationId = migrationId;
    }

    public boolean isDeleteTargetOnly() {
        return deleteTargetOnly;
    }

    public void setDeleteTargetOnly(boolean deleteTargetOnly) {
        this.deleteTargetOnly = deleteTargetOnly;
    }

    /**
     * Sorts the descriptors using the natural order of the enum type
     * defined at the top of the class.
     * 
     * @param descriptors FileDescriptors to sort
     */
    public static void sortByType(List<FileDescriptor> descriptors) {
        Collections.sort(descriptors, new Comparator<FileDescriptor>() {
            @Override
            public int compare(FileDescriptor vd1, FileDescriptor vd2) {
                return vd1.getType().getOrder() - vd2.getType().getOrder();
            }
        });
    }

    /**
     * Return a map of device URI to a list of descriptors in that device.
     * 
     * @param descriptors List<FileDescriptors>
     * @return Map of device URI to List<FileDescriptors> in that device
     */
    static public Map<URI, List<FileDescriptor>> getDeviceMap(List<FileDescriptor> descriptors) {
        HashMap<URI, List<FileDescriptor>> poolMap = new HashMap<URI, List<FileDescriptor>>();
        for (FileDescriptor desc : descriptors) {
            if (poolMap.get(desc._deviceURI) == null) {
                poolMap.put(desc._deviceURI, new ArrayList<FileDescriptor>());
            }
            poolMap.get(desc._deviceURI).add(desc);
        }
        return poolMap;
    }

    /**
     * Return a map of pool URI to a list of descriptors in that pool.
     * 
     * @param descriptors List<FileDescriptors>
     * @return Map of pool URI to List<FileDescriptors> in that pool
     */
    static public Map<URI, List<FileDescriptor>> getPoolMap(List<FileDescriptor> descriptors) {
        HashMap<URI, List<FileDescriptor>> poolMap = new HashMap<URI, List<FileDescriptor>>();
        for (FileDescriptor desc : descriptors) {
            if (poolMap.get(desc._poolURI) == null) {
                poolMap.put(desc._poolURI, new ArrayList<FileDescriptor>());
            }
            poolMap.get(desc._poolURI).add(desc);
        }
        return poolMap;
    }

    /**
     * Returns all the descriptors of a given type.
     * 
     * @param descriptors List<FileDescriptor> input list
     * @param type enum Type
     * @return returns list elements matching given type
     */
    static public List<FileDescriptor> getDescriptors(List<FileDescriptor> descriptors, Type type) {
        List<FileDescriptor> list = new ArrayList<FileDescriptor>();
        for (FileDescriptor descriptor : descriptors) {
            if (descriptor._type == type) {
                list.add(descriptor);
            }
        }
        return list;
    }

    /**
     * Return a map of pool URI to a list of descriptors in that pool of each size.
     * 
     * @param descriptors List<FileDescriptors>
     * @return Map of pool URI to a map of identical sized filesystems to List<FileDescriptors> in that pool of that size
     */
    static public Map<URI, Map<Long, List<FileDescriptor>>> getPoolSizeMap(List<FileDescriptor> descriptors) {
        Map<URI, Map<Long, List<FileDescriptor>>> poolSizeMap = new HashMap<URI, Map<Long, List<FileDescriptor>>>();
        for (FileDescriptor desc : descriptors) {

            // If the outside pool map doesn't exist, create it.
            if (poolSizeMap.get(desc._poolURI) == null) {
                poolSizeMap.put(desc._poolURI, new HashMap<Long, List<FileDescriptor>>());
            }

            // If the inside size map doesn't exist, create it.
            if (poolSizeMap.get(desc._poolURI).get(desc.getFileSize()) == null) {
                poolSizeMap.get(desc._poolURI).put(desc.getFileSize(), new ArrayList<FileDescriptor>());
            }

            // Add file to the list
            poolSizeMap.get(desc._poolURI).get(desc.getFileSize()).add(desc);
        }

        return poolSizeMap;
    }

    /**
     * Return a List of URIs for the filesystems.
     * 
     * @param descriptors List<FileDescriptors>
     * @return List<URI> of filesystems in the input list
     */
    public static List<URI> getFileSystemURIs(List<FileDescriptor> descriptors) {
        List<URI> fileURIs = new ArrayList<URI>();
        for (FileDescriptor desc : descriptors) {
            fileURIs.add(desc._fsURI);
        }
        return fileURIs;
    }

    /**
     * Filter a list of FileDescriptors by type(s).
     * 
     * @param descriptors -- Original list.
     * @param inclusive -- Types to be included (or null if not used).
     * @param exclusive -- Types to be excluded (or null if not used).
     * @return List<FileDescriptor>
     */
    public static List<FileDescriptor> filterByType(
            List<FileDescriptor> descriptors,
            Type[] inclusive, Type[] exclusive) {
        List<FileDescriptor> result = new ArrayList<FileDescriptor>();
        if (descriptors == null) {
            return result;
        }

        HashSet<Type> included = new HashSet<Type>();
        if (inclusive != null) {
            included.addAll(Arrays.asList(inclusive));
        }
        HashSet<Type> excluded = new HashSet<Type>();
        if (exclusive != null) {
            excluded.addAll(Arrays.asList(exclusive));
        }
        for (FileDescriptor desc : descriptors) {
            if (excluded.contains(desc._type)) {
                continue;
            }
            if (included.isEmpty() || included.contains(desc._type)) {
                result.add(desc);
            }
        }
        return result;
    }

    public static List<FileDescriptor> filterByType(
            List<FileDescriptor> descriptors,
            Type... inclusive) {
        return filterByType(descriptors, inclusive, null);
    }

}
