/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

@Cf("FilePolicyProfile")
public class FilePolicyProfile extends DataObject {

    // Name of the profile
    private String profileName;

    // List of policies associated to this profile currently only one policy per profile later will extrend this..
    private StringSet profilePolicies;

    @Name("profileName")
    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
        setChanged("profileName");
    }

    @Name("profilePolicies")
    public StringSet getProfilePolicies() {
        return profilePolicies;
    }

    public void setProfilePolicies(StringSet profilePolicies) {
        this.profilePolicies = profilePolicies;
        setChanged("profilePolicies");
    }

}