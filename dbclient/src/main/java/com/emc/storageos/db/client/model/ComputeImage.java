/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.upgrade.CustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.ComputeImageMigration;
import com.emc.storageos.model.valid.EnumType;

@Cf("ComputeImage")
public class ComputeImage extends DataObject {

    // DataObject.label - user given name

    public static final String ESX = "esx";
    public static final String ESXI = "esxi";
    public static final String RHEL = "rhel";
    public static final String CENTOS = "centos";
    public static final String ORACLE = "oracle";

    private String osName;
    private String osVersion;
    private String osUpdate;
    private String osBuild;
    private String osArchitecture;
    private String customName;
    private String pathToDirectory;

    // image name
    private String imageName;
    // image URL - set by the user
    private String imageUrl;
    // ESX, Linux, etc.
    private String imageType;
    // success or failure message
    private String lastImportStatusMessage;

    private String computeImageStatus = ComputeImageStatus.NOT_AVAILABLE.name();

    public static enum ImageType {
        esx, linux
    }

    public static enum ComputeImageStatus {
        AVAILABLE, NOT_AVAILABLE, IN_PROGRESS
    }

    public String fullName() {
        if (osName == null || osVersion == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(osName);
        sb.append('-').append(osVersion);
        if (osUpdate != null && !osUpdate.equals("")) {
            sb.append('.').append(osUpdate);
        }
        if (osBuild != null && !osBuild.equals("")) {
            sb.append('-').append(osBuild);
        }
        if (osArchitecture != null && !osArchitecture.equals("")) {
            sb.append('-').append(osArchitecture);
        }
        if (customName != null && !customName.equals("")) {
            sb.append("-cust-").append(customName);
        }
        return sb.toString();
    }

    @EnumType(ImageType.class)
    @Name("imageType")
    public String getImageType() {
        return imageType;
    }

    public void setImageType(String imageType) {
        this.imageType = imageType;
        setChanged("imageType");
    }

    @CustomMigrationCallback(callback = ComputeImageMigration.class)
    @Name("imageName")
    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
        setChanged("imageName");
    }

    @Name("imageUrl")
    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        setChanged("imageUrl");
    }

    @EnumType(ComputeImageStatus.class)
    @Name("computeImageStatus")
    public String getComputeImageStatus() {
        return computeImageStatus;
    }

    public void setComputeImageStatus(String computeImageStatus) {
        this.computeImageStatus = computeImageStatus;
        setChanged("computeImageStatus");
    }

    @Name("lastImportStatusMessage")
    public String getLastImportStatusMessage() {
        return lastImportStatusMessage;
    }

    public void setLastImportStatusMessage(String lastImportStatusMessage) {
        this.lastImportStatusMessage = lastImportStatusMessage;
        setChanged("lastImportStatusMessage");
    }

    @Name("osName")
    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
        setChanged("osName");
    }

    @Name("osVersion")
    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
        setChanged("osVersion");
    }

    @Name("osUpdate")
    public String getOsUpdate() {
        return osUpdate;
    }

    public void setOsUpdate(String osUpdate) {
        this.osUpdate = osUpdate;
        setChanged("osUpdate");
    }

    @Name("osBuild")
    public String getOsBuild() {
        return osBuild;
    }

    public void setOsBuild(String osBuild) {
        this.osBuild = osBuild;
        setChanged("osBuild");
    }

    @Name("osArchitecture")
    public String getOsArchitecture() {
        return osArchitecture;
    }

    public void setOsArchitecture(String osArchitecture) {
        this.osArchitecture = osArchitecture;
        setChanged("osArchitecture");
    }

    @Name("customName")
    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
        setChanged("customName");
    }

    @Name("pathToDirectory")
    public String getPathToDirectory() {
        return pathToDirectory;
    }

    public void setPathToDirectory(String pathToDirectory) {
        this.pathToDirectory = pathToDirectory;
        setChanged("pathToDirectory");
    }

    /**********************************
     * Utility methods
     **********************************/
    public boolean _isEsxi6x() {
        if (osName.equals(ESXI) && osVersion.startsWith("6.")) {
            return true;
        }
        return false;
    }

    public boolean _isEsxi5x() {
        if (osName.equals(ESXI) && osVersion.startsWith("5.")) {
            return true;
        }
        return false;
    }

    public boolean _isEsxi4x() {
        if (osName.equals(ESXI) && osVersion.startsWith("4.")) {
            return true;
        }
        return false;
    }

    public boolean _isEsx4x() {
        if (osName.equals(ESX) && osVersion.startsWith("4.")) {
            return true;
        }
        return false;
    }

    public boolean _isRedhat() {
        if (osName.equals(RHEL)) {
            return true;
        }
        return false;
    }

    public boolean _isCentos() {
        if (osName.equals(CENTOS)) {
            return true;
        }
        return false;
    }

    public boolean _isOracle() {
        if (osName.equals(ORACLE)) {
            return true;
        }
        return false;
    }

    public boolean _isLinux() {
        return _isRedhat() || _isCentos() || _isOracle();
    }
}
