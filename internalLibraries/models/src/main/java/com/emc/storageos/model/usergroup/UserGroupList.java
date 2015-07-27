/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
 * @valid none
 */

@XmlRootElement(name = "user_groups")
public class UserGroupList {
    private List<NamedRelatedResourceRep> userGroupList;

    public UserGroupList() {}

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
