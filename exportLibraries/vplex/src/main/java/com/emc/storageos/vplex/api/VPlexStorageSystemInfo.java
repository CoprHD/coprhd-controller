/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

/**
 * Info for a storage system accessible by the VPlex
 */
public class VPlexStorageSystemInfo extends VPlexResourceInfo {

    // Constants used in forming the native guids for storage systems.
    private static final String VPLEX_NAME_DELIM = "-";

    // The unique id for the storage system
    private String uniqueId;

    // The id of the VPlex cluster to which the array is attached.
    private String clusterId;

    /**
     * Getter for the storage system unique id, which could be the
     * serial number if it could be determined, or the whole nativeId
     * string from the VPLEX API if it could not be determined.
     * 
     * @return a unique id for the storage system.
     */
    public String getUniqueId() {
        return uniqueId;
    }

    /**
     * Check if the storageSystemNativeGuid matches with Vplex
     * storage systems unique Id.
     * 
     * @param storageSystemNativeGuid
     * @return
     */
    public boolean matches(String storageSystemNativeGuid) {

        String vplexStorageSystemId = getUniqueId();
        s_logger.info(String.format("Matching the storageSystemNativeGuid %s with %s",
                storageSystemNativeGuid, vplexStorageSystemId));
        if (storageSystemNativeGuid.contains(vplexStorageSystemId.trim())) {
            return true;
        }

        // For IBM XIV there needs a conversion between what is shown on VPLEX console
        // to what is present in ViPR console, to pass the match.
        if (storageSystemNativeGuid.startsWith("IBMXIV+IBM")) {

            int decimalNum = 0;
            try {
                // Convert vplexStorageSystemId to decimal integer
                decimalNum = Integer.parseInt(vplexStorageSystemId);
            } catch (NumberFormatException nfe) {
                // If this is not a decimal number, then consider it as mismatch
                return false;

            }

            // Convert the decimal to hex
            String hexString = Integer.toHexString(decimalNum);

            // Remove first 2 digits/chars from the hex string
            String subHexString = hexString.substring(1, hexString.length());

            // Convert it to decimal, this should be the serial number of the XIV array
            String decimalString = Integer.toString(Integer.parseInt(subHexString, 16));
            if (storageSystemNativeGuid.endsWith(decimalString)) {
                return true;
            }

        }

        return false;
    }

    /**
     * Creates a unique id for the storage system based on the VPlex system
     * name. If the serial number can be determined (the information after the
     * last "-" character), it will be used. Otherwise, the whole nativeId
     * from the VPLEX will be used.
     */
    public void buildUniqueId() throws VPlexApiException {
        String name = getName();

        if (!name.contains(VPLEX_NAME_DELIM)) {
            s_logger.warn("unexpected native guid format: " + name);
            uniqueId = name;
            return;
        }

        int lastDelimIndex = name.lastIndexOf(VPLEX_NAME_DELIM);
        String suffix = name.substring(lastDelimIndex + 1);
        s_logger.info("setting unique id for {} to {}", name, suffix);
        uniqueId = suffix;
    }

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
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("StorageSystemInfo ( ");
        str.append(super.toString());
        str.append(", nativeGuid: ").append(uniqueId);
        str.append(", clusterId: ").append(clusterId);
        str.append(" )");
        return str.toString();
    }
}
