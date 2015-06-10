/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.modelclient.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.IndexByKey;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.Relation;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.StringSet;

/**
 * @author cgarber
 *
 */
@Cf("ExportGroup")
public class ExportGroup extends DataObject {
    
    private StringSet snapshots;
    private Set<BlockSnapshot> snapshotSet;
    private StringSet _exportMasks; 
    private List<ExportMask> _exportMaskSet;
    private StringSet exportMaskLabels;
    private List<ExportMask> exportMasksFromLabels;

    @RelationIndex(cf = "RelationIndex", type = ExportMask.class)
    @IndexByKey
    @Name("exportMasks")
    public StringSet getExportMasks() {
        return _exportMasks;
    }

    public void setExportMasks(StringSet exportMasks) {
        _exportMasks = exportMasks;
    }

    public void addExportMask(URI maskUri) {
        addExportMask(maskUri.toString());
    }

    public void addExportMask(String maskUriStr) {
        if (_exportMasks == null) {
            _exportMasks = new StringSet();
        }
        if (!_exportMasks.contains(maskUriStr)) {
            _exportMasks.add(maskUriStr);
        }
    }

    public void removeExportMask(URI maskUri) {
        removeExportMask(maskUri.toString());
    }

    public void removeExportMask(String maskUriStr) {
        if (_exportMasks != null) {
            _exportMasks.remove(maskUriStr);
        }
    }

    /**
     * @return the _exportMaskSet
     */
    @Relation(type=ExportMask.class, mappedBy="exportMasks")
    @Name("exportMaskSet")
    public List<ExportMask> getExportMaskSet() {
        return _exportMaskSet;
    }

    /**
     * @param _exportMaskSet the _exportMaskSet to set
     */
    public void setExportMaskSet(List<ExportMask> _exportMaskSet) {
        this._exportMaskSet = _exportMaskSet;
    }
    
    public boolean addExportMask(ExportMask mask) {
        if (_exportMaskSet == null) _exportMaskSet = new ArrayList<ExportMask>();
        return (_exportMaskSet.contains(mask)) ? false : _exportMaskSet.add(mask);
    }
    public boolean removeExportMask(ExportMask mask) {
        return (_exportMaskSet == null) ? false : _exportMaskSet.remove(mask);
    }
    
    /**
     * @return the snapshots
     */
    @RelationIndex(cf = "RelationIndex", type = BlockSnapshot.class)
    @IndexByKey
    @Name("snapshots")
    public StringSet getSnapshots() {
        return snapshots;
    }

    /**
     * @param snapshots the snapshots to set
     */
    public void setSnapshots(StringSet snapshots) {
        this.snapshots = snapshots;
    }

    /**
     * @return the snapshotSet
     */
    @Relation(type=BlockSnapshot.class, mappedBy="snapshots")
    @Name("snashotSet")
    public Set<BlockSnapshot> getSnapshotSet() {
        return snapshotSet;
    }

    /**
     * @param snapshotSet the snapshotSet to set
     */
    public void setSnapshotSet(Set<BlockSnapshot> snapshotSet) {
        this.snapshotSet = snapshotSet;
    }

    /**
     * @return the exportMaskLabels
     */
    @Name("exportMasksByLabel")
    public StringSet getExportMaskLabels() {
        return exportMaskLabels;
    }

    /**
     * @param exportMaskLabels the exportMaskLabels to set
     */
    public void setExportMaskLabels(StringSet exportMaskLabels) {
        this.exportMaskLabels = exportMaskLabels;
    }

    /**
     * @return the exportMasksFromLabels
     */
    @Relation(type=BlockSnapshot.class, mappedBy="exportMasksByLabel")
    @Name("exportMaskListFromLabels")
    public List<ExportMask> getExportMasksFromLabels() {
        return exportMasksFromLabels;
    }

    /**
     * @param exportMasksFromLabels the exportMasksFromLabels to set
     */
    public void setExportMasksFromLabels(List<ExportMask> exportMasksFromLabels) {
        this.exportMasksFromLabels = exportMasksFromLabels;
    }


}
