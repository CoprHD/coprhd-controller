/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Info for a VPlex Consistency Group.
 */
public class VPlexConsistencyGroupInfo extends VPlexResourceInfo {

    // Enumerates the consistency group attributes we are interested in and
    // parse from the VPlex response. There must be a setter
    // method for each attribute specified. The format of the setter
    // method must be as specified by the base class method
    // getAttributeSetterMethodName.
    public static enum CGAttribute {
        VOLUMES("virtual-volumes"),
        VISIBILITY("visibility"),
        STORAGE_AT_CLUSTER("storage-at-clusters");

        // The VPlex name for the attribute.
        private String _name;

        /**
         * Constructor.
         * 
         * @param name The VPlex attribute name.
         */
        CGAttribute(String name) {
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
        public static CGAttribute valueOfAttribute(String name) {
            CGAttribute[] cgAtts = values();
            for (int i = 0; i < cgAtts.length; i++) {
                if (cgAtts[i].getAttributeName().equals(name)) {
                    return cgAtts[i];
                }
            }
            return null;
        }
    };

    // The cluster name;
    private String clusterName;

    // The virtual volume names
    private List<String> virtualVolumes = new ArrayList<String>();

    // The clusters for which the CG has visibility
    private List<String> visibleClusters = new ArrayList<String>();

    // The cluster at which the CG has storage
    private List<String> storageAtClusters = new ArrayList<String>();

    /**
     * Getter for the cluster name.
     * 
     * @return The cluster name.
     */
    public String getClusterName() {
        return clusterName;
    }

    /**
     * Setter for the cluster name.
     * 
     * @param strVal The cluster name.
     */
    public void setClusterName(String strVal) {
        clusterName = strVal;
    }

    /**
     * Getter for the virtual volume names.
     * 
     * @return The virtual volume names.
     */
    public List<String> getVirtualVolumes() {
        return virtualVolumes;
    }

    /**
     * Setter for the virtual volume names.
     * 
     * @param strVals The virtual volume names.
     */
    public void setVirtualVolumes(List<String> strVals) {
        virtualVolumes.addAll(strVals);
    }

    /**
     * Getter for the clusters for which the CG has visibility.
     * 
     * @return The clusters for which the CG has visibility.
     */
    public List<String> getVisibility() {
        return visibleClusters;
    }

    /**
     * Setter for the clusters for which the CG has visibility.
     * 
     * @param strVals The clusters for which the CG has visibility.
     */
    public void setVisibility(List<String> strVals) {
        visibleClusters.addAll(strVals);
    }

    /**
     * Getter for the clusters at which the CG has storage.
     * 
     * @return The clusters at which the CG has storage
     */
    public List<String> getStorageAtClusters() {
        return storageAtClusters;
    }

    /**
     * Setter for the clusters at which the CG has storage.
     * 
     * @param strVals The clusters at which the CG has storage.
     */
    public void setStorageAtClusters(List<String> strVals) {
        storageAtClusters.addAll(strVals);
    }

    /**
     * Determines if the consistency group has the visibility specified by the
     * passed list of cluster ids.
     * 
     * @param clusterIds A list of cluster ids.
     * 
     * @return true when the consistency group has visibility to all passed
     *         clusters
     */
    public boolean hasClusterVisibility(List<String> clusterIds) {
        return visibleClusters.containsAll(clusterIds);
    }

    /**
     * If the consistency group is visible on both clusters, it's distributed.
     * 
     * @return true if distributed, false otherwise.
     */
    public boolean isDistributed() {
        return visibleClusters.size() > 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAttributeFilters() {
        List<String> attFilters = new ArrayList<String>();
        for (CGAttribute att : CGAttribute.values()) {
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
        str.append("ConsistencyGroupInfo ( ");
        str.append(super.toString());
        str.append(", clusterName: ").append(clusterName);
        str.append(", virtualVolumes: ").append(virtualVolumes.toString());
        str.append(", visibleClusters: ").append(visibleClusters.toString());
        str.append(", storageAtClusters: ").append(storageAtClusters.toString());
        str.append(" )");
        return str.toString();
    }
}
