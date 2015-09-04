/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.StringHashMapEntry;

/**
 * Extends the name related resource to add new fields
 * specifying if the a change to the virtual pool is allowed and
 * the reason if not allowed.
 */
public class VirtualPoolChangeRep extends NamedRelatedVirtualPoolRep {

    private Boolean allowed;
    private String notAllowedReason;
    private List<StringHashMapEntry> allowedChangeOperations;

    public VirtualPoolChangeRep() {
    }

    public VirtualPoolChangeRep(URI id, RestLinkRep selfLink, String name, String virtualPoolType,
            String notAllowedReason, List<VirtualPoolChangeOperationEnum> allowedChangeOperationEnums) {
        super(id, selfLink, name, virtualPoolType);
        this.allowed = allowedChangeOperationEnums != null && !allowedChangeOperationEnums.isEmpty();
        this.notAllowedReason = notAllowedReason;

        if (allowedChangeOperationEnums != null) {
            for (VirtualPoolChangeOperationEnum allowedChangeOperationEnum : allowedChangeOperationEnums) {
                getAllowedChangeOperations().add(
                        new StringHashMapEntry(allowedChangeOperationEnum.name(), allowedChangeOperationEnum.toString()));
            }
        }
    }

    /**
     * Specifies whether or not a virtual pool change is allowed.
     * 
     * @valid true
     * @valid false
     */
    @XmlElement(name = "allowed")
    public Boolean getAllowed() {
        return allowed;
    }

    public void setAllowed(Boolean allowed) {
        this.allowed = allowed;
    }

    /**
     * When not allowed, the reason the virtual pool change is not allowed.
     * 
     * @valid none
     */
    @XmlElement(name = "not_allowed_reason")
    public String getNotAllowedReason() {
        return notAllowedReason;
    }

    public void setNotAllowedReason(String notAllowedReason) {
        this.notAllowedReason = notAllowedReason;
    }

    /**
     * Get list of allowed change operations
     * 
     * @return List of allowed change operations
     */
    @XmlElement(name = "allowed_change_operation")
    public List<StringHashMapEntry> getAllowedChangeOperations() {
        if (allowedChangeOperations == null) {
            allowedChangeOperations = new ArrayList<StringHashMapEntry>();
        }
        return allowedChangeOperations;
    }

    public void setAllowedChangeOperations(List<StringHashMapEntry> allowedOperations) {
        this.allowedChangeOperations = allowedOperations;
    }
}
