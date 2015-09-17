/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.db.client.DbClient;

/**
 * Object that identifies a protection in the system
 * 
 */
@SuppressWarnings("serial")
@Cf("ProtectionSet")
public class ProtectionSet extends DataObject {

    // project
    private URI _project;

    // protection system
    private URI _protectionSystem;

    // protection ID, such as a CG ID
    private String _protectionId;

    // protection status
    private String _protectionStatus;

    // Set of volume URIs
    private StringSet _volumes;

    // Subtasks of this protection, used by Workflow
    private StringMap _subtaskMap;

    // unique ID to be indexed - this field is not exposed to client
    private String _nativeGuid;

    public static enum ProtectionStatus {
        ENABLED,
        DISABLED,
        DELETED,
        MIXED,
        PAUSED
    }

    @XmlElement
    @RelationIndex(cf = "RelationIndex", type = ProtectionSystem.class)
    @Name("protectionSystem")
    public URI getProtectionSystem() {
        return _protectionSystem;
    }

    public void setProtectionSystem(URI _protectionSystem) {
        this._protectionSystem = _protectionSystem;
        setChanged("protectionSystem");
    }

    @AlternateId("AltIdIndex")
    @Name("protectionId")
    public String getProtectionId() {
        return _protectionId;
    }

    public void setProtectionId(String _protectionId) {
        this._protectionId = _protectionId;
        setChanged("protectionId");
    }

    @Name("volumes")
    public StringSet getVolumes() {
        return _volumes;
    }

    public void setVolumes(StringSet volumes) {
        this._volumes = volumes;
        setChanged("volumes");
    }

    @Name("protectionStatus")
    public String getProtectionStatus() {
        return _protectionStatus;
    }

    public void setProtectionStatus(String _protectionStatus) {
        this._protectionStatus = _protectionStatus;
        setChanged("protectionStatus");
    }

    @RelationIndex(cf = "RelationIndex", type = Project.class)
    @Name("project")
    public URI getProject() {
        return _project;
    }

    public void setProject(URI project) {
        _project = project;
        setChanged("project");
    }

    @Name("subtaskMap")
    public StringMap getSubtaskMap() {
        return _subtaskMap;
    }

    public void setSubtaskMap(StringMap subtaskMap) {
        _subtaskMap = subtaskMap;
        setChanged("subtaskMap");
    }

    @AlternateId("AltIdIndex")
    @Name("nativeGuid")
    public String getNativeGuid() {
        return _nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        _nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }

    @Override
    public String toString() {
        return _protectionSystem.toString() + ":" + _protectionId + ":" + _protectionStatus + ":" + _volumes.toString();
    }

    /**
     * Given an internal site name and a source volume, tell us the target volume for that
     * replication set.
     * 
     * @param _dbClient DB Client proxy
     * @param protectionSet Protection Set
     * @param volume A Source Volume in the protection set
     * @param emInternalSiteName Internal site name of the target to find.
     * @return A volume corresponding to the target for that source and internal site name
     * @throws URISyntaxException
     */
    public static Volume getTargetVolumeFromSourceAndInternalSiteName(DbClient _dbClient,
            ProtectionSet protectionSet, Volume sourceVolume, String emInternalSiteName) throws URISyntaxException {
        for (String volumeStr : protectionSet.getVolumes()) {
            Volume volume = _dbClient.queryObject(Volume.class, new URI(volumeStr));
            // Find the volume that is from the specified site and the source volume's replication set
            if ((volume.getRSetName() != null) && // removes any journals. journals aren't in a replication set.
                    (volume.getRSetName().equals(sourceVolume.getRSetName()) &&
                            (volume.getInternalSiteName().equals(emInternalSiteName)) &&
                    (!volume.getId().equals(sourceVolume.getId())))) {
                return volume;
            }
        }

        return null;
    }

    /**
     * Given a target volume, find the source volume.
     * 
     * @param _dbClient DB Client proxy
     * @param protectionSet Protection Set
     * @param volume A target Volume in the protection set
     * @return A volume corresponding to the source for that target
     * @throws URISyntaxException
     */
    public static Volume getSourceVolumeFromTargetVolume(DbClient _dbClient,
            ProtectionSet protectionSet, Volume sourceVolume) {
        for (String volumeStr : protectionSet.getVolumes()) {
            Volume volume = _dbClient.queryObject(Volume.class, URI.create(volumeStr));
            if ((volume.getRSetName() != null) && // removes any journals. journals aren't in a replication set.
                    (volume.getRSetName().equals(sourceVolume.getRSetName()) &&
                            (volume.getPersonality().toString().equalsIgnoreCase(Volume.PersonalityTypes.SOURCE.name())) &&
                    (!volume.getId().equals(sourceVolume.getId())))) {
                return volume;
            }
        }

        return null;
    }
}
