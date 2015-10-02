/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.BulkRestRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_consistency_groups")
public class BlockConsistencyGroupBulkRep extends BulkRestRep {
    private List<BlockConsistencyGroupRestRep> consistencyGroups;

    /**
     * A block consistency group
     * 
     */
    @XmlElement(name = "consistency_group")
    public List<BlockConsistencyGroupRestRep> getConsistencyGroups() {
        if (consistencyGroups == null) {
            consistencyGroups = new ArrayList<BlockConsistencyGroupRestRep>();
        }
        return consistencyGroups;
    }

    public void setConsistencyGroups(List<BlockConsistencyGroupRestRep> consistencyGroups) {
        this.consistencyGroups = consistencyGroups;
    }

    public BlockConsistencyGroupBulkRep() {
    }

    public BlockConsistencyGroupBulkRep(List<BlockConsistencyGroupRestRep> consistencyGroups) {
        this.consistencyGroups = consistencyGroups;
    }
}
