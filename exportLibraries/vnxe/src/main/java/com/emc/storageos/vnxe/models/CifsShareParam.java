/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class CifsShareParam {
    private String description;
    private boolean isReadOnly;
    private boolean isEncryptionEnabled;
    //Indicates whether continuous availability for SMB 3.0 is enabled for the CIFS share.
    private boolean isContinuousAvailabilityEnabled;
    // Indicates whether the CIFS share access-level permissions are enabled.
    private boolean isACEEnabled;
    /*
     * list of associated access-level permissions for CIFS shares, as defined 
     * by the cifsShareACE resource type
     */
    private List<CifsShareACE> addACE;
    /*
     * list of user, domain user, or group Security Identifiers (SIDs) to remove
     *  from the access list
     */
    private List<String> removeSID;
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public boolean getIsReadOnly() {
        return isReadOnly;
    }
    public void setIsReadOnly(boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }
    public boolean getIsEncryptionEnabled() {
        return isEncryptionEnabled;
    }
    public void setIsEncryptionEnabled(boolean isEncryptionEnabled) {
        this.isEncryptionEnabled = isEncryptionEnabled;
    }
    public boolean getIsContinuousAvailabilityEnabled() {
        return isContinuousAvailabilityEnabled;
    }
    public void setIsContinuousAvailabilityEnabled(
            boolean isContinuousAvailabilityEnabled) {
        this.isContinuousAvailabilityEnabled = isContinuousAvailabilityEnabled;
    }
    public boolean getIsACEEnabled() {
        return isACEEnabled;
    }
    public void setIsACEEnabled(boolean isACEEnabled) {
        this.isACEEnabled = isACEEnabled;
    }
    public List<CifsShareACE> getAddACE() {
        return addACE;
    }
    public void setAddACE(List<CifsShareACE> addACE) {
        this.addACE = addACE;
    }
    public List<String> getRemoveSID() {
        return removeSID;
    }
    public void setRemoveSID(List<String> removeSID) {
        this.removeSID = removeSID;
    }
    
    
    
}
