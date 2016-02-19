/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import static com.emc.storageos.db.client.util.NullColumnValueGetter.isNullURI;

/**
 * Class represents an array snapshot point-in-time copy.
 */
@SuppressWarnings("serial")
@Cf("BlockSnapshotSession")
public class BlockSnapshotSession extends DataObject implements ProjectResourceSnapshot {

    // Enum defines copy modes for array snapshot sessions.
    public enum CopyMode {
        copy,
        nocopy
    }

    // The id of the source consistency group, if any.
    private URI consistencyGroup;

    // The id of source Volume or BlockSnapshot for the array
    // snapshot session.
    private NamedURI _parent;

    // The project id.
    private NamedURI _project;

    // The ids of the BlockSnapshot instances associated with
    // the array snapshot session. The BlockSnapshot instances
    // represent the target volumes linked to the array snapshot.
    private StringSet _linkedTargets;

    // The session label specified by the user when creating a
    // new BlockSnapshotSession instance. Maybe be different
    // from the "label" when the source is in a consistency
    // group, in which case the BlockSnapshotSession instance
    // for each source in the consistency group will have the
    // same session label but a different, unique label.
    private String _sessionLabel;

    // Reference to snapshot session on the array for example an
    // CIMObjectPath for a SynchronizationAspectForSource or
    // SynchronizationAspectForSourceGroup. It is necessary to
    // maintain this reference because it may not be navigable
    // using the API.
    private String _sessionInstance;

    // Name reference of replication group that the object belong to.
    // There can be multiple array replication groups within an Application,
    // this property shows the replication group for which this session was created within a CG.
    private String _replicationGroupInstance;

    // Snapshot Session set name which user provided while creating sessions for volumes/CGs in an Application.
    // There can be multiple array replication groups within an Application.
    private String _sessionSetName;

    @RelationIndex(cf = "RelationIndex", type = BlockConsistencyGroup.class)
    @Name("consistencyGroup")
    public URI getConsistencyGroup() {
        return consistencyGroup;
    }

    public void setConsistencyGroup(URI consistencyGroup) {
        this.consistencyGroup = consistencyGroup;
        setChanged("consistencyGroup");
    }

    @NamedRelationIndex(cf = "NamedRelationIndex", type = BlockObject.class)
    @Name("parent")
    @Override
    public NamedURI getParent() {
        return _parent;
    }

    public void setParent(NamedURI parent) {
        _parent = parent;
        setChanged("parent");
    }

    @Override
    public Class<? extends DataObject> parentClass() {
        return BlockObject.class;
    }

    @Override
    @NamedRelationIndex(cf = "NamedRelationIndex", type = Project.class)
    @Name("project")
    public NamedURI getProject() {
        return _project;
    }

    public void setProject(NamedURI project) {
        _project = project;
        setChanged("project");
    }

    @RelationIndex(cf = "LinkedTargetsIndex", type = BlockSnapshot.class)
    @IndexByKey
    @Name("linkedTargets")
    public StringSet getLinkedTargets() {
        return _linkedTargets;
    }

    public void setLinkedTargets(StringSet linkedTargets) {
        _linkedTargets = linkedTargets;
        setChanged("linkedTargets");
    }

    @AlternateId("AltIdIndex")
    @Name("sessionLabel")
    public String getSessionLabel() {
        return _sessionLabel;
    }

    public void setSessionLabel(String sessionLabel) {
        _sessionLabel = sessionLabel;
        setChanged("sessionLabel");
    }

    @AlternateId("SessionInstanceAltIdIndex")
    @Name("sessionInstance")
    public String getSessionInstance() {
        return _sessionInstance;
    }

    public void setSessionInstance(String sessionInstance) {
        _sessionInstance = sessionInstance;
        setChanged("sessionInstance");
    }

    @AlternateId("AltIdIndex")
    @Name("replicationGroupInstance")
    public String getReplicationGroupInstance() {
        return _replicationGroupInstance;
    }

    public void setReplicationGroupInstance(String replicaGroupInstance) {
        _replicationGroupInstance = replicaGroupInstance;
        setChanged("replicationGroupInstance");
    }

    @AlternateId("AltIdIndex")
    @Name("sessionSetName")
    public String getSessionSetName() {
        return _sessionSetName;
    }

    public void setSessionSetName(String sessionSetName) {
        this._sessionSetName = sessionSetName;
        setChanged("sessionSetName");
    }

    public boolean hasConsistencyGroup() {
        return !isNullURI(consistencyGroup);
    }
}
