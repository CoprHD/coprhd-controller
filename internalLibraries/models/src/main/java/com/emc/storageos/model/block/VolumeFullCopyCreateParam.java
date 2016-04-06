/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.vpool.ProtectionType;

@XmlRootElement(name = "volume_full_copy_create")
public class VolumeFullCopyCreateParam {

    // Currently only full_copy type is supported
    private String type = ProtectionType.full_copy.toString();;
    private String name;
    private Integer count;
    private Boolean createInactive;

    // varray tells us source or target for RP or source or HA for vplex
    private URI varrayId;
    private URI vpoolId;

    public VolumeFullCopyCreateParam() {
    }

    public VolumeFullCopyCreateParam(String type, String name, Integer count,
            Boolean createInactive) {
        this.type = type;
        this.name = name;
        this.count = count;
        this.createInactive = createInactive;
    }

    /**
     * Type of copy requested. Currently
     * only a full-copy is supported.
     * Valid value:
     *      full_copy
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
     */
    @XmlElement(name = "create_inactive", required = false, defaultValue = "false")
    public Boolean getCreateInactive() {
        return createInactive;
    }

    public void setCreateInactive(Boolean createInactive) {
        this.createInactive = createInactive;
    }

    /**
     * @return the varrayId
     */
    @XmlElement(required = false)
    public URI getVarrayId() {
        return varrayId;
    }

    /**
     * @param varrayId the varrayId to set
     */
    public void setVarrayId(URI varrayId) {
        this.varrayId = varrayId;
    }

    /**
     * @return the vpoolId
     */
    @XmlElement(required = false)
    public URI getVpoolId() {
        return vpoolId;
    }

    /**
     * @param vpoolId the vpoolId to set
     */
    public void setVpoolId(URI vpoolId) {
        this.vpoolId = vpoolId;
    }
}
