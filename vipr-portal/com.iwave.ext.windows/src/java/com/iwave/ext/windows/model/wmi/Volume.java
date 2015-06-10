/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.model.wmi;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class Volume {
    private String name;
    private String caption;
    private String deviceId;
    private String driveLetter;
    private String driveLabel;
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getCaption() {
        return caption;
    }
    public void setCaption(String caption) {
        this.caption = caption;
    }
    public String getDeviceId() {
        return deviceId;
    }
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    public String getDriveLetter() {
        return driveLetter;
    }
    public void setDriveLetter(String driveLetter) {
        this.driveLetter = driveLetter;
    }
    public String getDriveLabel() {
        return driveLabel;
    }
    public void setDriveLabel(String driveLabel) {
        this.driveLabel = driveLabel;
    }

    @Override
    public String toString() {
        ToStringBuilder sb = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        sb.append("name", name);
        sb.append("caption", caption);
        sb.append("deviceId", deviceId);
        sb.append("driveLetter", driveLetter);
        sb.append("driveLabel", driveLabel);
        return sb.toString();
    }

}
