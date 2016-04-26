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

public class VolumeGroupFullCopyOperationParam {

    // fields for Application API
    /** By default, consider clone operation for all array replication groups in Application */
    private Boolean partial = Boolean.FALSE;
    private List<URI> fullCopies;
    // alternative to passing a list of full copy volumes
    private String copySetName;
    // alternative to passing partial flag and list of full copy volumes
    private List<String> subGroups;

    public VolumeGroupFullCopyOperationParam() {
    }

    public VolumeGroupFullCopyOperationParam(Boolean partial, List<URI> fullCopies) {
        this.partial = partial;
        this.fullCopies = fullCopies;
    }

    /**
     * Boolean which indicates whether we need to operate on clone for the entire Application or for set of array replication groups.
     * By default it is set to false, and consider that clones to be operated for all array replication groups in an Application.
     * If set to true, volumes list should be provided with full copy URIs one from each Array replication group.
     * In any case, minimum of one full copy URI needs to be specified in order to identify the clone set.
     * 
     */
    @XmlElement(name = "partial", required = false, defaultValue = "false")
    public Boolean getPartial() {
        return partial;
    }

    public void setPartial(Boolean partial) {
        this.partial = partial;
    }

    @XmlElementWrapper(required = true, name = "volumes")
    /**
     * List of Full copy IDs.
     * 
     * If full, Clones will be operated for the entire Application. A Full copy URI needs to be specified in order to identify the clone set.
     * 
     * If partial, Clones need not be operated for the entire Application, instead operate on clones for the specified array replication groups.
     * List can have full copy URIs one from each Array replication group.
     * 
     * example:  list of valid URIs
     */
    @XmlElement(required = false, name = "volume")
    public List<URI> getFullCopies() {
        if (fullCopies == null) {
            fullCopies = new ArrayList<URI>();
        }
        return fullCopies;
    }

    public void setFullCopies(List<URI> fullCopies) {
        this.fullCopies = fullCopies;
    }

    /**
     * @return the copySetName
     */
    @XmlElement(name = "copy_set_name", required = false)
    public String getCopySetName() {
        return copySetName;
    }

    /**
     * @param copySetName the copySetName to set
     */
    public void setCopySetName(String copySetName) {
        this.copySetName = copySetName;
    }

    /**
     * @return the subGroups
     */
    @XmlElementWrapper(required = true, name = "subgroups")
    @XmlElement(required = false, name = "subgroup")
    public List<String> getSubGroups() {
        return subGroups;
    }

    /**
     * @param subGroups the subGroups to set
     */
    public void setSubGroups(List<String> subGroups) {
        this.subGroups = subGroups;
    }
}
