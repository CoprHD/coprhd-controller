/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.usergroup;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that encapsulates the REST representation of a list of
 * user groups for the /bulk POST api request.
 */

@XmlRootElement(name = "bulk_user_groups")
public class UserGroupBulkRestRep extends BulkRestRep {
    private List<UserGroupRestRep> userGroups;

    /**
     * List of user groups
     * 
     * @valid none
     */
    @XmlElement(name = "user_group")
    public List<UserGroupRestRep> getUserGroups() {
        if (userGroups == null) {
            userGroups = new ArrayList<UserGroupRestRep>();
        }
        return userGroups;
    }

    public void setUserGroups(List<UserGroupRestRep> userGroups) {
        this.userGroups = userGroups;
    }

    public UserGroupBulkRestRep() {
    }

    public UserGroupBulkRestRep(List<UserGroupRestRep> userGroups) {
        this.userGroups = userGroups;
    }
}
