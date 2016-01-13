/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.vpool.ProtectionType;

@XmlRootElement(name = "volume_full_copy_create")
public class VolumeFullCopyCreateParam {

    // Currently only full_copy type is supported
    private String type = ProtectionType.full_copy.toString();;
    private String name;
    private Integer count;
    private Boolean createInactive;

    // fields for Application API
    /** By default, consider clones to be created for all array groups in Application */
    private Boolean partial = Boolean.FALSE;
    /** Volume list will be considered only when it is partial. List has to have one Volume from each Array Group */
    private List<URI> volumes;

    public VolumeFullCopyCreateParam() {
    }

    public VolumeFullCopyCreateParam(String type, String name, Integer count,
            Boolean createInactive) {
        this.type = type;
        this.name = name;
        this.count = count;
        this.createInactive = createInactive;
    }

    public VolumeFullCopyCreateParam(String type, String name, Integer count,
            Boolean createInactive, Boolean partial, List<URI> volumes) {
        this.type = type;
        this.name = name;
        this.count = count;
        this.createInactive = createInactive;
        this.partial = partial;
        this.volumes = volumes;
    }

    /**
     * Type of copy requested. Currently
     * only a full-copy is supported.
     * 
     * @valid full_copy
     */
    @XmlElement(required = false)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Name of the copy.
     * 
     * @valid none
     */
    @XmlElement(required = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Number of copies requested.
     * 
     * @valid none
     */
    @XmlElement(required = false)
    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    /**
     * If create_inactive is set to true, then the operation will create
     * the full copy, but not activate the synchronization between source
     * and target volumes. The activation would have to be done using the
     * block volume activate operation.
     * 
     * The default value for the parameter is false. That is, the operation
     * will create and activate the synchronization for the full copy.
     * 
     * @valid true
     * @valid false
     */
    @XmlElement(name = "create_inactive", required = false, defaultValue = "false")
    public Boolean getCreateInactive() {
        return createInactive;
    }

    public void setCreateInactive(Boolean createInactive) {
        this.createInactive = createInactive;
    }

    /**
     * Boolean which indicates whether we need to take clone for the entire Application or for subset.
     * By default it is set to false, and consider that clones to be created for all array replication groups in an Application.
     * If set to true, volumes list should be provided with volumes one from each Array replication group.
     * 
     * @valid true
     * @valid false
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
     * @valid example:  list of valid URIs
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
