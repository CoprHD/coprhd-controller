/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * All volume mirrors.
 *
 */
@XmlRootElement(name = "mirrors")
public class MirrorList {
    
    private List<NamedRelatedResourceRep> mirrorList;

    public MirrorList() {}
    
    public MirrorList(List<NamedRelatedResourceRep> mirrorList) {
        this.mirrorList = mirrorList;
    }

    /** 
     * The list of all the volume mirrors.
     * @valid none
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
