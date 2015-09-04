/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;


/**
 * Info for a VPlex storage volume
 */
public class VPlexStorageVolumeInfo extends VPlexResourceInfo {

    // The id of the VPlex cluster to which the storage volume belongs.
    private String clusterId;
    
    // List of initiator+target+lun items for this volume
    private List<String> itls = new ArrayList<>();
    private boolean isItlsFormatted = false;

	/**
     * Getter for the storage system cluster id.
     * 
     * @return The storage system cluster id.
     */
    public String getClusterId() {
        return clusterId;
    }

    /**
     * Setter for the storage system cluster id.
     * 
     * @param id The storage system cluster id.
     */
    public void setClusterId(String id) {
        clusterId = id;
    }
    
    /**
     * Gets the formatted ITLs
     * 
     * @return
     */
    public List<String> getItls() {

        if (!isItlsFormatted) {
            if (null != itls && !itls.isEmpty()) {
                /*
                 * Converting from "0x5000144260037d13\/0x5006016036601854\/2"
                 * to "5000144260037d13-5006016036601854-2" (<initiator-wwn>-<targte-wwn>-<lun-id>)
                 */
                List<String> formatttedItls = new ArrayList<String>(itls.size());
                for (String itl : itls) {
                    String[] itlParts = itl.split("/");

                    String part1 = itlParts[0];
                    String part2 = itlParts[1];
                    String part3 = itlParts[2];

                    String formattedItl = part1.substring(2) + "-"
                            + part2.substring(2) + "-"
                            + part3;
                    formatttedItls.add(formattedItl);
                }
                isItlsFormatted = true;

                // replace the original list with formatted itls
                itls.clear();
                itls.addAll(formatttedItls);
            }
        }

        return itls;
    }

    /**
     * Set the list of ITLs fetched from the response
     * 
     * @param initiatorTargetLunList
     */
    public void setItls(List<String> initiatorTargetLunList) {
        itls.clear();
        for (String itl : initiatorTargetLunList) {
            itls.add(itl);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("StorageVolumeInfo ( ");
        str.append(super.toString());
        str.append(", clusterId: " + clusterId);
        str.append(", itls: ");

        List<String> itls = getItls();
        if (null != itls) {
            for (String itl : getItls()) {
                str.append(itl);
                str.append(", ");
            }
        }
        str.append(" )");
        return str.toString();
    }
}
