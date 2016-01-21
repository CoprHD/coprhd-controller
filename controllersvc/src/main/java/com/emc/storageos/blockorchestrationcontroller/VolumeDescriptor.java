/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.blockorchestrationcontroller;

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

import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

@SuppressWarnings("serial")
public class VolumeDescriptor implements Serializable {
    public enum Type {
        /* ******************************
         * The ordering of these are important for the sortByType() method,
         * be mindful when adding/removing/changing the list.
         * Especially the RP Values, keep them in sequential order.
         * ******************************
         */
        BLOCK_DATA(1),      // user's data volume
        BLOCK_MIRROR(2),    // array level mirror
        BLOCK_SNAPSHOT(3),  // array level snapshot
        RP_EXISTING_PROTECTED_SOURCE(4), // RecoverPoint existing source volume that has protection already
        RP_EXISTING_SOURCE(5), // RecoverPoint existing source volume
        RP_VPLEX_VIRT_SOURCE(6), // RecoverPoint + VPLEX Virtual source
        RP_SOURCE(7),       // RecoverPoint source
        RP_TARGET(8),       // RecoverPoint target
        RP_VPLEX_VIRT_TARGET(9), // RecoverPoint + VPLEX Virtual target
        RP_VPLEX_VIRT_JOURNAL(10), // RecoverPoint + VPLEX Virtual journal
        RP_JOURNAL(11),      // RecoverPoint journal
        VPLEX_VIRT_VOLUME(12),  // VPLEX Virtual Volume
        VPLEX_LOCAL_MIRROR(13), // VPLEX local mirror
        VPLEX_IMPORT_VOLUME(14),  // VPLEX existing Volume to be imported
        SRDF_SOURCE(15),     // SRDF remote mirror source
        SRDF_TARGET(16),     // SRDF remote mirror target
        SRDF_EXISTING_SOURCE(17),  // SRDF existing source volume
        VPLEX_MIGRATE_VOLUME(18);

        private final int order;

        private Type(int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }
    };

    private Type _type;              // The type of this volume
    private URI _deviceURI;          // Device this volume will be created on
    private URI _volumeURI;          // The volume id or BlockObject id to be created
    private URI _poolURI;            // The pool id to be used for creation
    private VirtualPoolCapabilityValuesWrapper _capabilitiesValues;  // Non-volume-specific RP policy is stored in here
    private URI _consistencyGroup;   // The consistency group this volume belongs to
    private Long _volumeSize;        // Used to separate multi-volume create requests
    private URI _migrationId;        // Reference to the migration object for this volume

    // Layer/device specific parameters (key/value) for this volume (serializable!)
    private Map<String, Object> parameters = new HashMap<String, Object>();

    public static final String PARAM_VARRAY_CHANGE_NEW_VAARAY_ID = "varrayChangeNewVArrayId";
    public static final String PARAM_VPOOL_CHANGE_VOLUME_ID = "vpoolChangeVolumeId";
    public static final String PARAM_VPOOL_CHANGE_VPOOL_ID = "vpoolChangeVpoolId";
    public static final String PARAM_VPOOL_OLD_VPOOL_ID = "vpoolOldVpoolId";
    public static final String PARAM_IS_COPY_SOURCE_ID = "isCopySourceId";
    public static final String PARAM_DO_NOT_DELETE_VOLUME = "doNotDeleteVolume";

    public VolumeDescriptor(Type type,
            URI deviceURI, URI volumeURI, URI poolURI, URI consistencyGroupURI,
            VirtualPoolCapabilityValuesWrapper capabilities, Long volumeSize) {
        this(type, deviceURI, volumeURI, poolURI, consistencyGroupURI, capabilities);
        _volumeSize = volumeSize;
    }

    public VolumeDescriptor(Type type,
            URI deviceURI, URI volumeURI, URI poolURI, URI consistencyGroupURI, URI migrationId,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        this(type, deviceURI, volumeURI, poolURI, consistencyGroupURI, capabilities);
        setMigrationId(migrationId);
    }

    public VolumeDescriptor(Type type,
            URI deviceURI, URI volumeURI, URI poolURI, URI consistencyGroupURI,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        _type = type;
        _deviceURI = deviceURI;
        _volumeURI = volumeURI;
        _poolURI = poolURI;
        _capabilitiesValues = capabilities;
        _consistencyGroup = consistencyGroupURI;
    }

    public VolumeDescriptor(Type type,
            URI deviceURI, URI volumeURI, URI poolURI,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        this(type, deviceURI, volumeURI, poolURI, null, capabilities);
    }

    /**
     * Returns all the descriptors of a given type.
     * 
     * @param descriptors List<VolumeDescriptor> input list
     * @param type enum Type
     * @return returns list elements matching given type
     */
    static public List<VolumeDescriptor> getDescriptors(List<VolumeDescriptor> descriptors, Type type) {
        List<VolumeDescriptor> list = new ArrayList<VolumeDescriptor>();
        for (VolumeDescriptor descriptor : descriptors) {
            if (descriptor._type == type) {
                list.add(descriptor);
            }
        }
        return list;
    }

    /**
     * Return a map of device URI to a list of descriptors in that device.
     * 
     * @param descriptors List<VolumeDescriptors>
     * @return Map of device URI to List<VolumeDescriptors> in that device
     */
    static public Map<URI, List<VolumeDescriptor>> getDeviceMap(List<VolumeDescriptor> descriptors) {
        HashMap<URI, List<VolumeDescriptor>> poolMap = new HashMap<URI, List<VolumeDescriptor>>();
        for (VolumeDescriptor desc : descriptors) {
            if (poolMap.get(desc._deviceURI) == null) {
                poolMap.put(desc._deviceURI, new ArrayList<VolumeDescriptor>());
            }
            poolMap.get(desc._deviceURI).add(desc);
        }
        return poolMap;
    }

    /**
     * Return a map of pool URI to a list of descriptors in that pool.
     * 
     * @param descriptors List<VolumeDescriptors>
     * @return Map of pool URI to List<VolumeDescriptors> in that pool
     */
    static public Map<URI, List<VolumeDescriptor>> getPoolMap(List<VolumeDescriptor> descriptors) {
        HashMap<URI, List<VolumeDescriptor>> poolMap = new HashMap<URI, List<VolumeDescriptor>>();
        for (VolumeDescriptor desc : descriptors) {
            if (poolMap.get(desc._poolURI) == null) {
                poolMap.put(desc._poolURI, new ArrayList<VolumeDescriptor>());
            }
            poolMap.get(desc._poolURI).add(desc);
        }
        return poolMap;
    }

    /**
     * Return a map of pool URI to a list of descriptors in that pool of each size.
     * 
     * @param descriptors List<VolumeDescriptors>
     * @return Map of pool URI to a map of identical sized volumes to List<VolumeDescriptors> in that pool of that size
     */
    static public Map<URI, Map<Long, List<VolumeDescriptor>>> getPoolSizeMap(List<VolumeDescriptor> descriptors) {
        Map<URI, Map<Long, List<VolumeDescriptor>>> poolSizeMap = new HashMap<URI, Map<Long, List<VolumeDescriptor>>>();
        for (VolumeDescriptor desc : descriptors) {

            // If the outside pool map doesn't exist, create it.
            if (poolSizeMap.get(desc._poolURI) == null) {
                poolSizeMap.put(desc._poolURI, new HashMap<Long, List<VolumeDescriptor>>());
            }

            // If the inside size map doesn't exist, create it.
            if (poolSizeMap.get(desc._poolURI).get(desc.getVolumeSize()) == null) {
                poolSizeMap.get(desc._poolURI).put(desc.getVolumeSize(), new ArrayList<VolumeDescriptor>());
            }

            // Add volume to the list
            poolSizeMap.get(desc._poolURI).get(desc.getVolumeSize()).add(desc);
        }

        return poolSizeMap;
    }

    /**
     * Return a List of URIs for the volumes.
     * 
     * @param descriptors List<VolumeDescriptors>
     * @return List<URI> of volumes in the input list
     */
    public static List<URI> getVolumeURIs(List<VolumeDescriptor> descriptors) {
        List<URI> volumeURIs = new ArrayList<URI>();
        for (VolumeDescriptor desc : descriptors) {
            volumeURIs.add(desc._volumeURI);
        }
        return volumeURIs;
    }

    /**
     * Filter a list of VolumeDescriptors by type(s).
     * 
     * @param descriptors -- Original list.
     * @param inclusive -- Types to be included (or null if not used).
     * @param exclusive -- Types to be excluded (or null if not used).
     * @return List<VolumeDescriptor>
     */
    public static List<VolumeDescriptor> filterByType(
            List<VolumeDescriptor> descriptors,
            Type[] inclusive, Type[] exclusive) {
        List<VolumeDescriptor> result = new ArrayList<VolumeDescriptor>();
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
        for (VolumeDescriptor desc : descriptors) {
            if (excluded.contains(desc._type)) {
                continue;
            }
            if (included.isEmpty() || included.contains(desc._type)) {
                result.add(desc);
            }
        }
        return result;
    }

    public static List<VolumeDescriptor> filterByType(
            List<VolumeDescriptor> descriptors,
            Type... inclusive) {
        return filterByType(descriptors, inclusive, null);
    }

    /**
     * Helper method to retrieve the vpool change volume hiding in the volume descriptors
     * 
     * @param descriptors list of volumes
     * @return URI of the vpool change volume
     */
    public static URI getVirtualPoolChangeVolume(List<VolumeDescriptor> descriptors) {
        if (descriptors != null) {
            for (VolumeDescriptor volumeDescriptor : descriptors) {
                if (volumeDescriptor.getParameters() != null) {
                    if ((URI) volumeDescriptor.getParameters().get(VolumeDescriptor.PARAM_VPOOL_CHANGE_VOLUME_ID) != null) {
                        return (URI) volumeDescriptor.getParameters().get(VolumeDescriptor.PARAM_VPOOL_CHANGE_VOLUME_ID);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Helper method to find the change vpool using a single descriptor
     * 
     * @param descriptor Volume descriptor to use
     * @return URI of the change vpool
     */
    public static URI getVirtualPoolChangeVolume(VolumeDescriptor descriptor) {
        if (descriptor != null) {
            List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();
            descriptors.add(descriptor);
            return getVirtualPoolChangeVolume(descriptors);
        }
        return null;
    }

    /**
     * Helper method to retrieve the source vpool change volume hiding in the volume descriptors.
     * 
     * @param descriptors list of volumes
     * @return Map<URI,URI> of the vpool change volume and the old vpool associated to it.
     */
    public static Map<URI, URI> getAllVirtualPoolChangeSourceVolumes(List<VolumeDescriptor> descriptors) {
        Map<URI, URI> sourceVolumes = new HashMap<URI, URI>();
        if (descriptors != null) {
            for (VolumeDescriptor volumeDescriptor : descriptors) {
                if (volumeDescriptor.getParameters() != null) {
                    if (volumeDescriptor.getParameters().get(VolumeDescriptor.PARAM_VPOOL_CHANGE_VOLUME_ID) != null) {
                        URI volumeURI = (URI) volumeDescriptor.getParameters().get(VolumeDescriptor.PARAM_VPOOL_CHANGE_VOLUME_ID);
                        URI oldVpoolURI = (URI) volumeDescriptor.getParameters().get(VolumeDescriptor.PARAM_VPOOL_OLD_VPOOL_ID);
                        sourceVolumes.put(volumeURI, oldVpoolURI);
                    }
                }
            }
        }
        return sourceVolumes;
    }

    /**
     * Sorts the descriptors using the natural order of the enum type
     * defined at the top of the class.
     * 
     * @param descriptors VolumeDescriptors to sort
     */
    public static void sortByType(List<VolumeDescriptor> descriptors) {
        Collections.sort(descriptors, new Comparator<VolumeDescriptor>() {
            @Override
            public int compare(VolumeDescriptor vd1, VolumeDescriptor vd2) {
                return vd1.getType().getOrder() - vd2.getType().getOrder();
            }
        });
    }
    
    /**
     * Returns all descriptors that have the PARAM_DO_NOT_DELETE_VOLUME flag set to true.
     * 
     * @param descriptors List of descriptors to check
     * @return all descriptors that have the PARAM_DO_NOT_DELETE_VOLUME flag set to true
     */
    public static List<VolumeDescriptor> getDoNotDeleteDescriptors(List<VolumeDescriptor> descriptors) {
        List<VolumeDescriptor> doNotDeleteDescriptors = new ArrayList<VolumeDescriptor>();
        if (descriptors != null && !descriptors.isEmpty()) {
            for (VolumeDescriptor descriptor : descriptors) {
                if (descriptor.getParameters() != null
                        && descriptor.getParameters().get(VolumeDescriptor.PARAM_DO_NOT_DELETE_VOLUME) != null
                        && descriptor.getParameters().get(VolumeDescriptor.PARAM_DO_NOT_DELETE_VOLUME).equals(Boolean.TRUE)) {                
                    doNotDeleteDescriptors.add(descriptor);
                }
            } 
        }
        return doNotDeleteDescriptors;
    }

    @Override
    public String toString() {
        return "VolumeDescriptor [_type=" + _type + ", _deviceURI="
                + _deviceURI + ", _volumeURI=" + _volumeURI + ", _poolURI="
                + _poolURI + ", _consistencyGroup=" + _consistencyGroup +
                ", _capabilitiesValues=" + _capabilitiesValues + ", parameters="
                + parameters + ", size=" + _volumeSize + "]";
    }

    public String toString(Volume volume) {
        return "VolumeDescriptor [_type=" + _type + ", _deviceURI="
                + _deviceURI + ", _poolURI="
                + _poolURI + ", _consistencyGroup=" + _consistencyGroup +
                ", _capabilitiesValues=" + _capabilitiesValues
                + ", parameters=" + parameters + ", volume=" +
                volume.toString() + ", size=" + _volumeSize + "]";
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

    public URI getVolumeURI() {
        return _volumeURI;
    }

    public void setVolumeURI(URI volumeURI) {
        this._volumeURI = volumeURI;
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

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public URI getConsistencyGroupURI() {
        return _consistencyGroup;
    }

    public void setConsistencyGroupURI(URI consistencyGroupURI) {
        this._consistencyGroup = consistencyGroupURI;
    }

    public Long getVolumeSize() {
        return _volumeSize;
    }

    public void setVolumeSize(Long _volumeSize) {
        this._volumeSize = _volumeSize;
    }

    public URI getMigrationId() {
        return _migrationId;
    }

    public void setMigrationId(URI _migrationId) {
        this._migrationId = _migrationId;
    }
}
