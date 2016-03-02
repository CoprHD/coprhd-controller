/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.block.VolumeFullCopyCreateParam;

@XmlRootElement(name = "volume_group_full_copy_create")
public class VolumeGroupFullCopyCreateParam extends VolumeFullCopyCreateParam {

    // fields for Application API
    /** By default, consider clones to be created for all array groups in Application */
    private Boolean partial = Boolean.FALSE;
    /** Volume list will be considered only when it is partial. List has to have one Volume from each Array Group */
    private List<URI> volumes;

    public VolumeGroupFullCopyCreateParam() {
    }

    public VolumeGroupFullCopyCreateParam(String type, String name, Integer count,
            Boolean createInactive, Boolean partial, List<URI> volumes) {
        super(type, name, count, createInactive);
        this.partial = partial;
        this.volumes = volumes;
    }

    /**
     * Boolean which indicates whether we need to take clone for the entire Application or for subset.
     * By default it is set to false, and consider that clones to be created for all array replication groups in an Application.
     * If set to true, volumes list should be provided with volumes one from each Array replication group.
     * 
     */
    @XmlElement(name = "partial", required = false, defaultValue = "false")
    public Boolean getPartial() {
        return partial;
    }

    public void setPartial(Boolean partial) {
        this.partial = partial;
    }

    @XmlElementWrapper(required = false, name = "volumes")
    /**
     * List of Volume IDs.
     * This field is applicable only if partial is set to true,
     * meaning Clones need not be created for the entire Application, instead create clones for the specified array replication groups.
     * List can have volumes one from each Array replication group.
     * 
     * example:  list of valid URIs
     */
    @XmlElement(required = false, name = "volume")
    public List<URI> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<URI>();
        }
        return volumes;
    }

    public void setVolumes(List<URI> volumes) {
        this.volumes = volumes;
    }
}
