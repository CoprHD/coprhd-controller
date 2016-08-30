/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * 
 * @author yelkaa
 * 
 */

@XmlRootElement(name = "mount_info_list")
public class MountInfoList {
    private List<MountInfo> mountList;

    public List<MountInfo> getMountList() {
        return mountList;
    }

    @XmlElement(name = "mount_info")
    @JsonProperty("mount_info")
    public void setMountList(List<MountInfo> mountList) {
        this.mountList = mountList;
    }

}
