package com.emc.storageos.model.remotereplication;

import com.emc.storageos.model.DataObjectRestRep;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "remote_replication_set")
public class RemoteReplicationSetRestRep extends DataObjectRestRep {

    // native id of replication set.
    private String nativeId;

    // Device label of this replication set
    private String deviceLabel;

    // If replication set is reachable.
    private Boolean reachable;

    // Type of storage systems in this replication set.
    private String storageSystemType;


    @XmlElement(name = "native_id")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    @XmlElement(name = "name")
    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }

    @XmlElement(name = "reachable")
    public Boolean getReachable() {
        return reachable;
    }

    public void setReachable(Boolean reachable) {
        this.reachable = reachable;
    }


    @XmlElement(name = "storage_system_type")
    public String getStorageSystemType() {
        return storageSystemType;
    }

    public void setStorageSystemType(String storageSystemType) {
        this.storageSystemType = storageSystemType;
    }
}
