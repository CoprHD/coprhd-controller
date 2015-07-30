/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnx.xmlapi;

import java.util.Map;


public class VNXFileSystem extends VNXBaseClass {
    private String fsName;
    private int fsId;
    private String fsNativeGuid;
    private String type;

    private String storagePool;
    private String storage;
    private String extendSize;
    private String cwormState;
    private String dataMover;
    private String size;
    private String mountPath;
    private String usedCapacity;
    private String totalCapcity;
    private boolean autoExtendEnabled = false;
    private Map<String, String> autoAtts;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setFsName(String fsName) {
        this.fsName = fsName;
    }

    public String getFsName() {
        return fsName;
    }

    public void setFsId(int fsId) {
        this.fsId = fsId;
    }

    public int getFsId() {
        return fsId;
    }

    public void setFsNativeGuid(String fsNativeGuid) {
        this.fsNativeGuid = fsNativeGuid;
    }

    public String getFsNativeGuid() {
        return fsNativeGuid;
    }

    public void setExtendSize(String extendSize) {
        this.extendSize = extendSize;
    }

    public String getExtendSize() {
        return extendSize;
    }

    public void setStoragePool(String storagePoolId) {
        this.storagePool = storagePoolId;
    }

    public String getStoragePool() {
        return storagePool;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public String getStorage() {
        return storage;
    }

    public void setUsedCapcity(String usedCapacity) {
        this.usedCapacity = usedCapacity;

    }

    public String getUsedCapacity() {
        return usedCapacity;
    }

    public void setTotalCapacity(String totalCapacity) {
        this.totalCapcity = totalCapacity;
    }

    public String getTotalCapacity() {
        return totalCapcity;
    }

    public VNXFileSystem() {
    }

    public VNXFileSystem(String fsName, int fsId) {
        this.fsName = fsName;
        this.fsId = fsId;
    }

    public VNXFileSystem(String fsName, int fsId, String type, String cwormState, String dataMover, String size,
            Map<String, String> autoAtts) {
        this.fsName = fsName;
        this.fsId = fsId;
        this.type = type;
        this.cwormState = cwormState;
        this.dataMover = dataMover;
        this.size = size;
        this.autoAtts = autoAtts;
        this.mountPath = "/" + fsName;
    }

    public VNXFileSystem(String fsName, int fsId, String storagePool, String type, String cwormState, String dataMover, String size,
            Map<String, String> autoAtts) {
        this.fsName = fsName;
        this.fsId = fsId;
        this.type = type;
        this.storagePool = storagePool;
        this.cwormState = cwormState;
        this.dataMover = dataMover;
        this.size = size;
        this.autoAtts = autoAtts;
        this.mountPath = "/" + fsName;
    }

    public static String getAllFileSystems() {
        String xml = requestHeader +
                "\t<Query>\n" +
                "\t<FileSystemQueryParams>\n" +
                "\t<AspectSelection fileSystems=\"true\" />\n" +
                "\t</FileSystemQueryParams>\n" +
                "\t</Query>\n" +
                requestFooter;
        return xml;
    }

    public static String getFileSystem(String fsName) {
        String xml = requestHeader +
                "\t<Query>\n" +
                "\t<FileSystemQueryParams   >\n" +
                "\t<AspectSelection fileSystems=\"true\" />\n" +
                "\t<Alias name=\"" + fsName + "\" />\n" +
                "\t</FileSystemQueryParams>\n" +
                "\t</Query>\n" +
                requestFooter;
        return xml;
    }

    public String getCreateXML() {
        String xml = requestHeader +
                "\t<StartTask timeout=\"" + timeout + "\">\n" +
                "\t<NewFileSystem name=\"" + fsName + "\" type = \"" + type + "\" cwormState = \"" + cwormState + "\" >\n" +
                "\t<Mover mover=\"" + dataMover + "\"/>\n" +
                "\t<StoragePool pool=\"" + storagePool + "\" size=\"" + size + "\" mayContainSlices=\"true\" >\n";

        // add Auto Extend attributes
        String autoExtend = (String) autoAtts.get(AUTO_EXTEND_ENABLED_ATTRIBUTE);
        if (Boolean.valueOf(autoExtend).booleanValue() == true) {
            xml += "\t<EnableAutoEx autoExtensionMaxSize=\"" + autoAtts.get(AUTO_EXTEND_MAX_SIZE_ATTRIBUTE) +
                    "\" highWaterMark=\"" + autoAtts.get(AUTO_EXTEND_HWM_ATTRIBUTE) + "\"></EnableAutoExt>";
        }
        xml += "\t</StoragePool>" +
                "\t<Mount path=\"" + mountPath + "\" ></Mount>\n" +
                "\t</NewFileSystem>\n" +
                "\t</StartTask>\n" +
                requestFooter;

        return xml;

    }

    public String getDeleteXML() {
        String xml = requestHeader +
                "\t<StartTask timeout=\"" + timeout + "\">\n" +
                "\t<DeleteFileSystem fileSystem=\"" + fsId + "\"/>\n" +
                "\t</StartTask>\n" +
                requestFooter;
        return xml;
    }

    public String getExpandXML() {
        String xml = requestHeader +
                "\t<StartTask timeout=\"" + timeout + "\">\n" +
                "\t<ExtendFileSystem fileSystem=\"" + fsId + "\">\n" +
                "\t<StoragePool pool=\"" + storagePool + "\" size=\"" + extendSize + "\"/>\n" +
                "\t</ExtendFileSystem>\n" +
                "\t</StartTask>\n" +
                requestFooter;
        return xml;
    }

}
