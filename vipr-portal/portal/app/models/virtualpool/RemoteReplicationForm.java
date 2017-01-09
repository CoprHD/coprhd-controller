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

import com.emc.storageos.model.vpool.VirtualPoolRemoteReplicationSettingsParam;

public class RemoteReplicationForm {
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

    public void load(VirtualPoolRemoteReplicationSettingsParam param) {
        virtualArray = asString(param.getVarray());
        virtualArrayName = name(VirtualArrayUtils.getVirtualArray(virtualArray));
        virtualPool = asString(param.getVpool());
        virtualPoolName = name(VirtualPoolUtils.getBlockVirtualPool(virtualPool));
    }

    public VirtualPoolRemoteReplicationSettingsParam write() {
        VirtualPoolRemoteReplicationSettingsParam param = new VirtualPoolRemoteReplicationSettingsParam();
        param.setVarray(uri(virtualArray));
        param.setVpool(uri(virtualPool));
        return param;
    }
}
