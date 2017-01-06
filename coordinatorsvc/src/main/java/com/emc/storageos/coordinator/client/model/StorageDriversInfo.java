/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.util.HashSet;
import java.util.Set;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

/**
 * This model is to hold all installed drivers at /config/storagedrivers/global.
 * Data change would notify all nodes to download/remove their local driver
 * files to keep aligned with this target list.
 */
public class StorageDriversInfo implements CoordinatorSerializable {

    private static final String ENCODING_SEPERATOR = "\0";

    public static final String ID = "global";
    public static final String KIND = "storagedrivers";
    public static final String ATTRIBUTE = "storagedrivers";

    private Set<String> installedDrivers = new HashSet<String>();

    public Set<String> getInstalledDrivers() {
        return installedDrivers;
    }

    public void setInstalledDrivers(Set<String> installedDrivers) {
        this.installedDrivers = installedDrivers;
    }

    @Override
    public String encodeAsString() {
        if (installedDrivers == null || installedDrivers.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String driver : installedDrivers) {
            builder.append(driver).append(ENCODING_SEPERATOR);
        }
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public StorageDriversInfo decodeFromString(String infoStr) throws FatalCoordinatorException {
        if (infoStr == null || infoStr.isEmpty()) {
            return new StorageDriversInfo();
        }
        StorageDriversInfo info = new StorageDriversInfo();
        Set<String> drivers = new HashSet<String>();
        String[] driverStrs = infoStr.split(ENCODING_SEPERATOR);
        for (String driverStr : driverStrs) {
            if (driverStr == null || driverStr.isEmpty()) {
                continue;
            }
            drivers.add(driverStr);
        }
        info.setInstalledDrivers(drivers);
        return info;
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(ID, KIND, ATTRIBUTE);
    }
}