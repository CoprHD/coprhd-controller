/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.machinetags.vmware;

import com.emc.sa.machinetags.MachineTag;
import com.emc.sa.machinetags.MultiValueMachineTag;
import com.google.common.collect.Lists;
import java.util.List;

public class DatastoreMachineTag extends MultiValueMachineTag {

    public static final String NAMESPACE = "isa.vc";
    public static final String DATACENTER = "datacenter";
    public static final String VCENTER = "vcenter";
    public static final String MOUNT_POINT = "mountPoint";
    public static final String DATASTORE = "datastore";

    public static final List<String> relatedTags = Lists.newArrayList(DATASTORE, MOUNT_POINT, VCENTER, DATACENTER);

    public DatastoreMachineTag(Integer index, String vcenter, String datacenter, String datastore, String mountpoint) {
        super(new MachineTag(NAMESPACE, DATASTORE, index, datastore),
                new MachineTag(NAMESPACE, VCENTER, index, vcenter),
                new MachineTag(NAMESPACE, DATACENTER, index, datacenter),
                new MachineTag(NAMESPACE, MOUNT_POINT, index, mountpoint));
    }

    public DatastoreMachineTag(MachineTag datastore, MachineTag vcenter, MachineTag datacenter, MachineTag mountpoint) {
        super(datastore, vcenter, datacenter, mountpoint);
    }

    @Override
    public List<String> getRelatedTags() {
        return relatedTags;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    public String getVCenter() {
        return getTagValue(VCENTER);
    }

    public String getDatacenter() {
        return getTagValue(DATACENTER);
    }

    public String getDatastore() {
        return getTagValue(DATASTORE);
    }

    public String getMountPoint() {
        return getTagValue(MOUNT_POINT);
    }

    private String getTagValue(String name) {
        MachineTag tag = getTag(name);
        return tag != null ? tag.value : null;
    }
}
