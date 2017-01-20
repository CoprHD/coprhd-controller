package com.emc.storageos.model.remotereplication;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "remote_replication_mode_update")
public class RemoteReplicationModeChange {
    private String replicationMode;

    @XmlElement(name = "replication_mode")
    public String getNewMode() {
        return replicationMode;
    }

    public void setNewMode(String newMode) {
        this.replicationMode = newMode;
    }
}
