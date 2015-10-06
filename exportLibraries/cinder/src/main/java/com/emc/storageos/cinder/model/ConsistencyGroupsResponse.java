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
package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Pojo class for detail list of consistency group
 * @author singhc1
 *
 */
public class ConsistencyGroupsResponse {
    
    private List<ConsistencyGroupDetail> consistencyGroupList = new ArrayList<ConsistencyGroupDetail>();

    @JsonProperty(value = "consistencygroups")
    @XmlElement(name = "consistencygroups")
    public List<ConsistencyGroupDetail> getConsistencyGroups() {
        return consistencyGroupList;
    }

    public void addConsistencyGroup(ConsistencyGroupDetail cgDetail) {
        if (null != cgDetail) {
            consistencyGroupList.add(cgDetail);
        }
    }

}