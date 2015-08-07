/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * Map to keep track of which VDCs borrowed a given token
 * Used in single logout functionality.
 */
@NoInactiveIndex
@Cf("RequestedTokenMap")
public class RequestedTokenMap extends DataObject {

    private String tokenID;
    private StringSet VDCIDs;

    @AlternateId("AltIdIndex")
    @Name("tokenId")
    public String getTokenID() {
        return tokenID;
    }

    public void setTokenID(String id) {
        tokenID = id;
        setChanged("tokenId");
    }

    @Name("vdcIds")
    public StringSet getVDCIDs() {
        if (VDCIDs == null) {
            setVDCIDs(new StringSet());
        }
        return VDCIDs;
    }

    public void setVDCIDs(StringSet ids) {
        VDCIDs = ids;
    }

    public void addVDCID(String shortId) {
        getVDCIDs().add(shortId);
    }

    public void removeVDCID(String shortId) {
        getVDCIDs().remove(shortId);
    }

}
