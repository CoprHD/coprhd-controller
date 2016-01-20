package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_data_status")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteDataStatus {
    @XmlElement(name = "data_synced")
    private boolean dataSynced = false;
    @XmlElement(name = "last_sync_time")
    private long lastSyncTime;

    public boolean isDataSynced() {
        return dataSynced;
    }
    public void setDataSynced(boolean dataSynced) {
        this.dataSynced = dataSynced;
    }
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
