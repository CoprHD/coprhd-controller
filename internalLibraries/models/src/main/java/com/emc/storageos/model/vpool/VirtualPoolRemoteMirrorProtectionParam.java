/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class VirtualPoolRemoteMirrorProtectionParam {

    public VirtualPoolRemoteMirrorProtectionParam() {

    }

    /**
     * The remote protection virtual array settings for a virtual pool.
     * 
     */
    private List<VirtualPoolRemoteProtectionVirtualArraySettingsParam> remoteCopySettings;

    @XmlElementWrapper(name = "remote_copy_settings")
    @XmlElement(name = "remote_copy_setting", required = false)
    public List<VirtualPoolRemoteProtectionVirtualArraySettingsParam> getRemoteCopySettings() {
        if (null == remoteCopySettings) {
            remoteCopySettings = new ArrayList<VirtualPoolRemoteProtectionVirtualArraySettingsParam>();
        }
        return remoteCopySettings;
    }

    public VirtualPoolRemoteMirrorProtectionParam(
            List<VirtualPoolRemoteProtectionVirtualArraySettingsParam> remoteCopySettings) {
        this.remoteCopySettings = remoteCopySettings;
    }

    public void setRemoteCopySettings(List<VirtualPoolRemoteProtectionVirtualArraySettingsParam> remoteCopySettings) {
        this.remoteCopySettings = remoteCopySettings;
    }

}
