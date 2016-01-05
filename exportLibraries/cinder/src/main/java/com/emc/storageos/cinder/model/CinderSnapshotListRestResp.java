/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value = "snapshots")
@XmlRootElement(name = "snapshots")
public class CinderSnapshotListRestResp {
    private List<CinderSnapshot> snapshots;

    /**
     * List of snapshots that make up this entry. Used primarily to report to cinder.
     */

    @XmlElement(name = "snapshot")
    public List<CinderSnapshot> getSnapshots() {
        if (snapshots == null) {
            snapshots = new ArrayList<CinderSnapshot>();
        }
        return snapshots;
    }

    public void setSnapshots(List<CinderSnapshot> snapshots) {
        this.snapshots = snapshots;
    }

}
