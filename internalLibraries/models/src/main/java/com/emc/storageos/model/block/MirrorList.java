/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * All volume mirrors.
 * 
 */
@XmlRootElement(name = "mirrors")
public class MirrorList {

    private List<NamedRelatedResourceRep> mirrorList;

    public MirrorList() {
    }

    public MirrorList(List<NamedRelatedResourceRep> mirrorList) {
        this.mirrorList = mirrorList;
    }

    /**
     * The list of all the volume mirrors.
     * 
     */
    @XmlElement(name = "mirror")
    public List<NamedRelatedResourceRep> getMirrorList() {
        if (mirrorList == null) {
            mirrorList = new ArrayList<NamedRelatedResourceRep>();
        }
        return mirrorList;
    }

    public void setMirrorList(List<NamedRelatedResourceRep> mirrorList) {
        this.mirrorList = mirrorList;
    }
}
