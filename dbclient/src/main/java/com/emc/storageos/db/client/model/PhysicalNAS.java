/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.model.valid.EnumType;

/**
 * PhysicalNAS Server will contain the details of NAS server depending on StorageArray type
 * e.g. DataMover.
 * It will hold information about the IP interfaces, CIFS Server & NFS servers mapped to NasServer
 * 
 * @author ganeso
 * 
 */

@Cf("PhysicalNAS")
public class PhysicalNAS extends NASServer {

    // Placeholder for storing list of Physical NAS servers
    private StringSet containedVirtualNASservers;

    @Name("containedVirtualNASServers")
    public StringSet getContainedVirtualNASservers() {
        return containedVirtualNASservers;
    }

    public void setContainedVirtualNASservers(StringSet containedVirtualNASservers) {
        this.containedVirtualNASservers = containedVirtualNASservers;
        setChanged("containedVirtualNASservers");
    }

    // Defines different States of the Physical NAS server.
    public static enum NasState {
        Active("Active"),
        Passive("Passive"),
        UNKNOWN("N/A");

        private final String nasState;

        private NasState(String state) {
            nasState = state;
        }

        public String getNasState() {
            return nasState;
        }

        private static NasState[] copyValues = values();

        public static String getNasState(String name) {
            for (NasState type : copyValues) {
                if (type.getNasState().equalsIgnoreCase(name)) {
                    return type.name();
                }
            }
            return UNKNOWN.toString();
        }
    };

    @EnumType(NasState.class)
    @Name("pNasState")
    public String getPNasState() {
        return this.getNasState();
    }

    public void setPNasState(String nasState) {
        this.setNasState(nasState);
        setChanged("pNasState");
    }

}
