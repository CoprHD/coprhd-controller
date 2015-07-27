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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "block_mirror")
public class BlockMirrorRestRep extends VolumeRestRep {
    private NamedRelatedResourceRep source;
    private String syncState;
    private String syncType;

    /**
     * Volume representing the source in a mirror relationship.
     * @valid none
     */
    @XmlElement
    public NamedRelatedResourceRep getSource() {
        return source;
    }

    public void setSource(NamedRelatedResourceRep source) {
        this.source = source;
    }

    /**
     * Synchronization state.
     * @valid UNKNOWN
     * @valid RESYNCHRONIZING
     * @valid SYNCHRONIZED
     * @valid FRACTURED
     */
    @XmlElement
    public String getSyncState() {
        return syncState;
    }

    public void setSyncState(String syncState) {
        this.syncState = syncState;
    }

    /**
     * Synchronization type.
     * @valid none
     */
    @XmlElement
    public String getSyncType() {
        return syncType;
    }

    public void setSyncType(String syncType) {
        this.syncType = syncType;
    }
}
