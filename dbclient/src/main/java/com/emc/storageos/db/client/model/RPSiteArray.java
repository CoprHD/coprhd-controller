/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

/**
 * Object that helps to identify RP systems, their sites and arrays.
 * Helps with placement, replication, and connectivity tables.
 * 
 */
@Cf("RPSiteArray")
public class RPSiteArray extends DataObject {

    // RP Cluster Protection system this entry belongs to
    private URI _rpProtectionSystem;

    // Array Storage System
    private URI _storageSystem;

    // RP site unique identifier
    private String _rpInternalSiteName;

    // RP site Name
    private String _rpSiteName;

    // RP array visible based on Raw Unique ID
    // _storageSystem above, if exists, will also represent the same storage system.
    private String _arraySerialNumber;

    @XmlElement
    @RelationIndex(cf = "RelationIndex", type = ProtectionSystem.class)
    @Name("rpProtectionSystem")
    public URI getRpProtectionSystem() {
        return _rpProtectionSystem;
    }

    public void setRpProtectionSystem(URI rpProtectionSystem) {
        this._rpProtectionSystem = rpProtectionSystem;
        setChanged("rpProtectionSystem");
    }

    @XmlElement
    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageSystem")
    public URI getStorageSystem() {
        return _storageSystem;
    }

    public void setStorageSystem(URI storageSystem) {
        this._storageSystem = storageSystem;
        setChanged("storageSystem");
    }

    @XmlElement
    @Name("rpInternalSiteName")
    public String getRpInternalSiteName() {
        return _rpInternalSiteName;
    }

    public void setRpInternalSiteName(String rpInternalSiteName) {
        this._rpInternalSiteName = rpInternalSiteName;
        setChanged("rpInternalSiteName");
    }

    @XmlElement
    @Name("rpSiteName")
    public String getRpSiteName() {
        return _rpSiteName;
    }

    public void setRpSiteName(String rpSiteName) {
        this._rpSiteName = rpSiteName;
        setChanged("rpSiteName");
    }

    @XmlElement
    @Name("arraySerialNumber")
    public String getArraySerialNumber() {
        return _arraySerialNumber;
    }

    public void setArraySerialNumber(String arraySerialNumber) {
        this._arraySerialNumber = arraySerialNumber;
        setChanged("arraySerialNumber");
    }

    // Identity helper in logs
    public String toString() {
        return _rpProtectionSystem.toString() + ":" + _rpInternalSiteName + ":" + _rpSiteName + ":" + _arraySerialNumber + ":"
                + _storageSystem.toString();
    }

}
