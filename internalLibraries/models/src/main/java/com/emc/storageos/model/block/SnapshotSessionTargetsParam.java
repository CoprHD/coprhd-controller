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

import javax.xml.bind.annotation.XmlElement;


/**
 * 
 */
public class SnapshotSessionTargetsParam {
    
    Integer count;
    String copyMode;
    
    public SnapshotSessionTargetsParam() {
    }
    
    public SnapshotSessionTargetsParam(Integer count, String copyMode) {
        this.count = count;
        this.copyMode = copyMode;
    }

    /**
     * The number of new targets to create and link to the snapshot session.
     * 
     * @valid none
     */
    @XmlElement(required = true)
    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    /**
     * The copy mode for the new target volumes to be linked to
     * the block snapshot session. A volume that is linked to a
     * snapshot session using "copy" copy_mode and achieves the 
     * "copied" state will contain a full, usable copy of the 
     * snapshot session source device upon being unlinked from 
     * the session. This is not true for volumes linked in "nocopy" 
     * copy-mode.
     * 
     * @valid copy
     * @valid nocopy
     */
    @XmlElement(name = "copy_mode", required = false, defaultValue = "noCopy")
    public String getCopyMode() {
        return copyMode;
    }

    public void setCopyMode(String copyMode) {
        this.copyMode = copyMode;
    }    
}
