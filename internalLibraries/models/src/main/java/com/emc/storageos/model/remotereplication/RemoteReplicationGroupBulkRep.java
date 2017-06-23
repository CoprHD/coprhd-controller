package com.emc.storageos.model.remotereplication;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_remotereplicationgroups")
public class RemoteReplicationGroupBulkRep extends BulkRestRep {
    private List<RemoteReplicationGroupRestRep> groups;

    /**
     * List of remote replication groups
     * 
     * @return List of remote replication groups
     */
    @XmlElement(name = "remotereplicationgroup")
    public List<RemoteReplicationGroupRestRep> getGroups() {
        if (groups == null) {
            groups = new ArrayList<RemoteReplicationGroupRestRep>();
        }
        return groups;
    }

    public void setGroups(List<RemoteReplicationGroupRestRep> groups) {
        this.groups = groups;
    }

    public RemoteReplicationGroupBulkRep() {
    }

    public RemoteReplicationGroupBulkRep(List<RemoteReplicationGroupRestRep> groups) {
        this.groups = groups;
    }
}
