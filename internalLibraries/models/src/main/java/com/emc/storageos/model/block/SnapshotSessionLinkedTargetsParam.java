/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.block;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 *
 */
public class SnapshotSessionLinkedTargetsParam {
    SnapshotSessionNewTargetsParam newTargets;
    List<SnapshotSessionExistingTargetParam> existingTargets;
    
    SnapshotSessionLinkedTargetsParam() {
    }
    
    SnapshotSessionLinkedTargetsParam(SnapshotSessionNewTargetsParam newTargets, List<SnapshotSessionExistingTargetParam> existingTargets) {
        this.newTargets = newTargets;
        this.existingTargets = existingTargets;
    }
    
    /**
     * The new targets to be created and linked to the block snapshot session.
     * 
     * @valid none
     */
    @XmlElement(name = "new_targets", required = false)
    public SnapshotSessionNewTargetsParam getNewTargets() {
        return newTargets;
    }

    public void setNewTargets(SnapshotSessionNewTargetsParam newTargets) {
        this.newTargets = newTargets;
    }
    
    /**
     * The existing targets to be linked to the block snapshot session.
     * This parameter is ignored when new targets are specified.
     * 
     * @valid none
     */
    @XmlElementWrapper(name = "existing_targets", required = false)
    @XmlElement(name = "target")
    public List<SnapshotSessionExistingTargetParam> getExistingTargets() {
        return existingTargets;
    }

    public void setExistingTargets(List<SnapshotSessionExistingTargetParam> existingTargets) {
        this.existingTargets = existingTargets;
    }
}
