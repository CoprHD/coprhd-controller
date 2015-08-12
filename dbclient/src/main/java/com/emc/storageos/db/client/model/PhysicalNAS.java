/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.db.client.model.StorageHADomain.HADomainType;
import com.emc.storageos.model.valid.EnumType;

/**
 * PhysicalNAS Server will contain the details of NAS server depending on StorageArray type
 * eg. DataMover. 
 * It will hold information about the Ip interfaces, cifs Server & NFS servers mapped to NasServer
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

    // Defines different States of the NAS server.
    public static enum NasState {
        Active("Active"),
        Passive("Passive"),
        UNKNOWN("N/A");

        private String NasState;

        private NasState(String state) {
            NasState = state;
        }

        public String getNasState() {
            return NasState;
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
    @Name("nasState")
    public String getPNasState() {
        return this.getNasState();
    }

    public void setPNasState(String _nasState) {
        this.setNasState(_nasState);
        setChanged("nasState");
    }

}
