/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.windows.model.wmi;

import org.apache.commons.lang.StringUtils;

public class WindowsVersion {

    private String version;

    private String caption;

    public WindowsVersion() {
    }

    public WindowsVersion(String version) {
        this.version = version;
    }

    public WindowsVersion(String version, String caption) {
        this.version = version;
        this.caption = caption;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    @Override
    public String toString() {
        if (StringUtils.isNotBlank(caption)) {
            return String.format("%s %s", version, caption);
        }
        else {
            return StringUtils.defaultString(version);
        }
    }
}
