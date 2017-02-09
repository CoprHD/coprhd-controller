/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

/**
 * Info for a VPlex Storage View.
 */
public class VPlexStorageViewInfo extends VPlexResourceInfo {

    // Enumerates the storage view attributes we are interested in and
    // parse from the VPlex storage view response. There must be a setter
    // method for each attribute specified. The format of the setter
    // method must be as specified by the base class method
    // getAttributeSetterMethodName.
    public static enum StorageViewAttribute {
        VOLUMES("virtual-volumes"),
        PORTS("ports"),
        INITIATORS("initiators");

        // The VPlex name for the attribute.
        private String _name;

        /**
         * Constructor.
         * 
         * @param name The VPlex attribute name.
         */
        StorageViewAttribute(String name) {
            _name = name;
        }

        /**
         * Getter for the VPlex name for the attribute.
         * 
         * @return The VPlex name for the attribute.
         */
        public String getAttributeName() {
            return _name;
        }

        /**
         * Returns the enum whose name matches the passed name, else null when
         * not found.
         * 
         * @param name The name to match.
         * 
         * @return The enum whose name matches the passed name, else null when
         *         not found.
         */
        public static StorageViewAttribute valueOfAttribute(String name) {
            StorageViewAttribute[] storageViewAtts = values();
            for (int i = 0; i < storageViewAtts.length; i++) {
                if (storageViewAtts[i].getAttributeName().equals(name)) {
                    return storageViewAtts[i];
                }
            }
            return null;
        }
    };

    // The cluster id;
    private String clusterId;

    // The virtual volume info for the storage view.
    private List<String> virtualVolumes = new ArrayList<String>();

    // A map of the storage view virtual volume names to WWN.
    private Map<String, String> virtualVolumeWWNMap = new HashMap<String, String>();

    // A map of the storage view virtual volume names to HLU.
    private Map<String, Integer> virtualVolumeHLUMap = new HashMap<String, Integer>();

    // The target port info for the storage view.
    private List<String> ports = new ArrayList<String>();

    // The host initiator info for the storage view.
    private List<String> initiators = new ArrayList<String>();

    // The initiators PWWN for the storage view.
    private List<String> initiatorPwwns = new ArrayList<String>();

    /**
     * Getter for the cluster id.
     * 
     * @return The cluster id.
     */
    public String getClusterId() {
        return clusterId;
    }

    /**
     * Setter for the cluster id.
     * 
     * @param strVal The cluster id.
     */
    public void setClusterId(String strVal) {
        clusterId = strVal;
    }

    /**
     * Getter for the storage view virtual volumes.
     * 
     * @return The virtual volumes for the storage view.
     */
    public List<String> getVirtualVolumes() {
        return virtualVolumes;
    }
    
    /**
     * Setter for the virtual volume for the storage view.
     * Comma separated list.
     * 
     * @param strVals The virtual volumes for the storage view.
     */
    public void setVirtualVolumes(List<String> strVals) {
        virtualVolumes.clear();
        virtualVolumes.addAll(strVals);
        refreshMaps();
    }
    
    /**
     * Refreshes the virtualVolumeWWNMap and virtualVolumeHLUMap
     * based on the data in the virtualVolumes collection. This is
     * required because the GSON parser bypasses accessor methods.
     */
    public void refreshMaps() {
        virtualVolumeWWNMap.clear();
        virtualVolumeHLUMap.clear();
        for (String volumeInfoStr : virtualVolumes) {
            StringTokenizer tokenizer = new StringTokenizer(volumeInfoStr, ",");
            String hluStr = tokenizer.nextToken();
            Integer volumeHLU = VPlexApiConstants.LUN_UNASSIGNED;
            hluStr = hluStr.substring(1); // skips the opening "("
            if (null != hluStr && !VPlexApiConstants.NULL_ATT_VAL.equals(hluStr)) {
                try {
                    volumeHLU = Integer.valueOf(hluStr);
                } catch (NumberFormatException ex) {
                    s_logger.error("could not parse HLU from '{}', will be set to -1", hluStr);
                }
            }
            String volumeName = tokenizer.nextToken();
            String vpdId = tokenizer.nextToken();
            int indexColon = vpdId.indexOf(':');
            String volumeWWN = vpdId.substring(indexColon + 1).toUpperCase();
            virtualVolumeWWNMap.put(volumeName, volumeWWN);
            virtualVolumeHLUMap.put(volumeName, volumeHLU);
        }
    }

    /**
     * Gets the WWN for the volume in the storage view.
     * 
     * @param volumeName The volume name.
     * 
     * @return The WWN of the volume.
     */
    public String getWWNForStorageViewVolume(String volumeName) {
        return virtualVolumeWWNMap.get(volumeName);
    }

    /**
     * Gets the volume name for the wwn in the storage view.
     * 
     * @param volumeName The WWN of the volume
     * 
     * @return The volume name, or null if not found.
     */
    public String getVolumeNameForWWN(String wwn) {
        if (wwn != null) {
            for (Entry<String, String> entry : virtualVolumeWWNMap.entrySet()) {
                if (wwn.equalsIgnoreCase(entry.getValue())) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }

    /**
     * Gets the HLU for the volume in the storage view.
     * 
     * @param volumeName The volume name.
     * 
     * @return The HLU of the volume.
     */
    public Integer getHLUForStorageViewVolume(String volumeName) {
        // if the LUN value is not set for this volume name, return LUN_UNASSIGNED
        Integer hlu = virtualVolumeHLUMap.get(volumeName);
        return hlu != null ? hlu : VPlexApiConstants.LUN_UNASSIGNED;
    }

    /**
     * Gets a Map of virtual volume WWN to HLU
     */
    public Map<String, Integer> getWwnToHluMap() {
        Map<String, Integer> map = new HashMap<String, Integer>();

        for (Entry<String, String> entry : virtualVolumeWWNMap.entrySet()) {
            String volumeName = entry.getKey();
            Integer volumeHlu = virtualVolumeHLUMap.get(volumeName);
            String volumeWwn = entry.getValue();
            map.put(volumeWwn, volumeHlu);
        }

        return map;
    }

    /**
     * Getter for the storage view target ports.
     * 
     * @return The target ports for the storage view.
     */
    public List<String> getPorts() {
        return ports;
    }

    /**
     * Setter for the target ports in the storage view.
     * Comma separated list.
     * 
     * @param strVals The target ports for the storage view.
     */
    public void setPorts(List<String> strVals) {
        ports.clear();
        ports.addAll(strVals);
    }

    /**
     * Getter for the storage view initiators.
     * 
     * @return The initiators for the storage view.
     */
    public List<String> getInitiators() {
        return initiators;
    }

    /**
     * Setter for the initiators in the storage view.
     * Comma separated list.
     * 
     * @param strVals The initiators for the storage view.
     */
    public void setInitiators(List<String> strVals) {
        initiators.clear();
        for (String initiator : strVals) {
            initiators.add(initiator);
        }
    }

    /**
     * Getter for the initiator PWWNs in the storage view.
     * 
     * @return The initiator PWWNs in the storage view.
     */
    public List<String> getInitiatorPwwns() {
        return initiatorPwwns;
    }

    /**
     * Setter for the initiator PWWNs in the storage view.
     * 
     * @param initiatorPwwns The initiators PWWN in the storage view.
     */
    public void setInitiatorPwwns(List<String> initiatorPwwns) {
        this.initiatorPwwns = initiatorPwwns;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAttributeFilters() {
        List<String> attFilters = new ArrayList<String>();
        for (StorageViewAttribute att : StorageViewAttribute.values()) {
            attFilters.add(att.getAttributeName());
        }
        return attFilters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("StorageViewInfo ( ");
        str.append(super.toString());
        str.append(", clusterId: ").append(clusterId);
        str.append(", virtualVolumes: ").append(virtualVolumes.toString());
        str.append(", initiators: ").append(initiators.toString());
        str.append(", initiator PWWNs: ").append(initiatorPwwns.toString());
        str.append(", target ports: ").append(ports.toString());
        str.append(", wwn to hlu map: ").append(getWwnToHluMap().toString());
        str.append(" )");
        return str.toString();
    }
}
