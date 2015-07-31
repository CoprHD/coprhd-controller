/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * Virtual Storage Pool settings regarding protection for a specific varray
 */
@Cf("VpoolProtectionVarraySettings")
public class VpoolProtectionVarraySettings extends DataObject {
    // Vpool this was created from
    private NamedURI _parent;

    // protection type (probably not necessary, stored in the CoS)
    private String _type;

    // protection VirtualPool
    private URI _virtualPool;

    // RP target journal size policy
    private String _journalSize;

    // RP target journal virtual array
    private URI _journalVarray;

    // RP target journal virtual pool
    private URI _journalVpool;

    // Do not use RelatedIndex on VirtualPool fields since VirtualPool already has a dependency reference to this object.
    @Name("parent")
    public NamedURI getParent() {
        return _parent;
    }

    public void setParent(NamedURI parent) {
        _parent = parent;
        setChanged("parent");
    }

    @Name("type")
    public String getType() {
        return _type;
    }

    public void setType(String type) {
        _type = type;
        setChanged("type");
    }

    @Name("virtualPool")
    @AlternateId("AltIdIndex")
    public URI getVirtualPool() {
        return _virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        _virtualPool = virtualPool;
        setChanged("virtualPool");
    }

    public void setJournalSize(String journalSize) {
        _journalSize = journalSize;
        setChanged("journalSize");
    }

    @Name("journalSize")
    public String getJournalSize() {
        return _journalSize;
    }

    @Name("journalVarray")
    public URI getJournalVarray() {
        return _journalVarray;
    }

    public void setJournalVarray(URI journalVarray) {
        this._journalVarray = journalVarray;
        setChanged("journalVarray");
    }

    @Name("journalVpool")
    public URI getJournalVpool() {
        return _journalVpool;
    }

    public void setJournalVpool(URI journalVpool) {
        this._journalVpool = journalVpool;
        setChanged("journalVpool");
    }
}
