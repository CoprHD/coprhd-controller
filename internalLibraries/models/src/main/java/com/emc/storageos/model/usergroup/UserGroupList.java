/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.usergroup;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for the REST response for the GET List API
 * of the UserGroup
 * 
 */

@XmlRootElement(name = "user_groups")
public class UserGroupList {
    private List<NamedRelatedResourceRep> userGroupList;

    public UserGroupList() {
    }

    public UserGroupList(List<NamedRelatedResourceRep> userGroupList) {
        this.userGroupList = userGroupList;
    }

    @XmlElement(name = "user_group")
    public List<NamedRelatedResourceRep> getUserGroups() {
        if (userGroupList == null) {
            userGroupList = new ArrayList<NamedRelatedResourceRep>();
        }
        return userGroupList;
    }

    public void setUserGroups(List<NamedRelatedResourceRep> userGroupList) {
        this.userGroupList = userGroupList;
    }
}
