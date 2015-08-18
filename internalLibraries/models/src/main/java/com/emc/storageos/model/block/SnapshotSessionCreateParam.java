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
 * Class that captures the POST data passed in a request to create
 * a new BlockSnapshotSession instance.
 */
@XmlRootElement(name = "snapshot_session_create")
public class SnapshotSessionCreateParam {

    // The name for the snapshot session.
    private String name;

    // The linked target information.
    private SnapshotSessionTargetsParam linkedTargets;

    /**
     * Default constructor.
     */
    public SnapshotSessionCreateParam() {
    }

    /**
     * Constructor.
     * 
     * @param name The name for the snapshot session.
     * @param linkedTargets A reference to the linked target information.
     */
    public SnapshotSessionCreateParam(String name, SnapshotSessionTargetsParam linkedTargets) {
        this.name = name;
        this.linkedTargets = linkedTargets;
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
