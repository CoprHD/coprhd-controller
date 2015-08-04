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
import javax.xml.bind.annotation.XmlRootElement;

/**
 * 
 */
@XmlRootElement(name = "snapshot_session_create")
public class SnapshotSessionCreateParam {

    private String name;
    private Boolean createInactive;
    private SnapshotSessionTargetsParam linkedTargets;

    public SnapshotSessionCreateParam() {
    }

    public SnapshotSessionCreateParam(String name, Boolean createInactive) {
        this.name = name;
        this.createInactive = createInactive;
    }

    /**
     * The snapshot session name.
     * 
     * @valid none
     */
    @XmlElement
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * If create_inactive is set to true, then the
     * operation will create the snapshot session, but
     * not activate the synchronization between source
     * and specified target volumes, if any. The
     * activation would have to be done using the block
     * snapshot session activate API. This parameter
     * only applies when linked targets are specified.
     * 
     * The default value for the parameter is false.
     * That is, the operation will create and activate
     * the synchronization for the snapshot session.
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
     * Specifies the target volumes to linked to the newly created
     * block snapshot session. When not specified, no targets volumes
     * will be linked to the newly created snapshot session.
     * 
     * @valid none
     * 
     */
    @XmlElement(name = "linked_targets", required = false)
    public SnapshotSessionTargetsParam getLinkedTargets() {
        return linkedTargets;
    }

    public void setLinkedTargets(SnapshotSessionTargetsParam linkedTargets) {
        this.linkedTargets = linkedTargets;
    }
}
