package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_data_status")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteDataStatus {
    private boolean dataSynced = false;
    private long lastSyncTime;

    @XmlElement(name = "data_synced")
    public boolean isDataSynced() {
        return dataSynced;
    }
    public void setDataSynced(boolean dataSynced) {
        this.dataSynced = dataSynced;
    }

    @XmlElement(name = "last_sync_time")
    public long getLastSyncTime() {
        return lastSyncTime;
    }
    public void setLastSyncTime(long lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteDataStatus [");
        builder.append("dataSynced=");
        builder.append(dataSynced);
        builder.append(", lastSyncTime=");
        builder.append(lastSyncTime);
        builder.append("]");
        return builder.toString();
    }
}
