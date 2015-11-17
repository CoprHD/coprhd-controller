/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi.request;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * Parameters to create volume snapshot
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class ScaleIOSnapshotVolumes {
    private List<ScaleIOSnapshotDef> snapshotDefs = new ArrayList<ScaleIOSnapshotDef>();

    public List<ScaleIOSnapshotDef> getSnapshotDefs() {
        return snapshotDefs;
    }

    public void setSnapshotDefs(List<ScaleIOSnapshotDef> snapshotDefs) {
        this.snapshotDefs = snapshotDefs;
    }

    public void addSnapshot(String volId, String snapName) {
        ScaleIOSnapshotDef def = new ScaleIOSnapshotDef();
        def.setVolumeId(volId);
        def.setSnapshotName(snapName);
        snapshotDefs.add(def);
    }

    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    public class ScaleIOSnapshotDef {
        private String volumeId;
        private String snapshotName;

        public String getVolumeId() {
            return volumeId;
        }

        public void setVolumeId(String volumeId) {
            this.volumeId = volumeId;
        }

        public String getSnapshotName() {
            return snapshotName;
        }

        public void setSnapshotName(String snapshotName) {
            this.snapshotName = snapshotName;
        }

    }

}
