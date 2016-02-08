/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.virtualpool;

import static com.emc.vipr.client.core.util.ResourceUtils.asString;
import static com.emc.vipr.client.core.util.ResourceUtils.name;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Required;
import play.data.validation.Validation;
import util.VirtualArrayUtils;
import util.VirtualPoolUtils;

import com.emc.storageos.model.vpool.VirtualPoolRemoteProtectionVirtualArraySettingsParam;

public class ReplicationCopyForm {

    @Required
    public String virtualArray;
    public String virtualArrayName;
    public String virtualPool;
    public String virtualPoolName;

    public boolean isEnabled() {
        return StringUtils.isNotBlank(virtualArray);
    }

    public void validate(String formName) {
        Validation.valid(formName, this);
    }

    public void load(VirtualPoolRemoteProtectionVirtualArraySettingsParam copy) {
        virtualArray = asString(copy.getVarray());
        virtualArrayName = name(VirtualArrayUtils.getVirtualArray(virtualArray));
        virtualPool = asString(copy.getVpool());
        virtualPoolName = name(VirtualPoolUtils.getFileVirtualPool(virtualPool));
    }

    public VirtualPoolRemoteProtectionVirtualArraySettingsParam write(String remoteCopyMode) {
        VirtualPoolRemoteProtectionVirtualArraySettingsParam param = new VirtualPoolRemoteProtectionVirtualArraySettingsParam();
        param.setVarray(uri(virtualArray));
        param.setVpool(uri(virtualPool));
        param.setRemoteCopyMode(remoteCopyMode);
        return param;
    }
}
