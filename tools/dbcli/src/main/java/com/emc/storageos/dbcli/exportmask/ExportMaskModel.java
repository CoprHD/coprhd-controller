package com.emc.storageos.dbcli.exportmask;

import java.net.URI;
import java.util.Map;

import com.emc.storageos.db.client.model.StringSet;

public class ExportMaskModel {
    URI id;
    String maskName;
    String nativeId;
    StringSet storagePorts;
    // String initiators;
    Map<String, StringSet> zoningMap;


    @Override
    public String toString() {
        return "id: " + id + " maskName: " + maskName + " nativeId: " + nativeId + " storagePorts: " + storagePorts + " zoningMap: "
                + zoningMap;
    }



    public String getMaskName() {
        return maskName;
    }

    public void setMaskName(String maskName) {
        this.maskName = maskName;
    }

    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    public StringSet getStoragePorts() {
        return storagePorts;
    }

    public void setStoragePorts(StringSet storagePorts) {
        this.storagePorts = storagePorts;
    }

    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    public Map<String, StringSet> getZoningMap() {
        return zoningMap;
    }

    public void setZoningMap(Map<String, StringSet> zoningMap) {
        this.zoningMap = zoningMap;
    }

}
