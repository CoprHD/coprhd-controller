/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@XmlRootElement
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class CreateFileSystemParam extends ParamBase{
    private String description;
    private ReplicationParam replicationParameters;
    private SnapScheduleParam snapScheduleParameters;
    private FileSystemParam fsParameters;
    private CifsFileSystemParam cifsFsParameters;
    private List<NfsShareCreateParam> nfsShareCreate;
    private List<CifsShareCreateParam> cifsShareCreate;
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public ReplicationParam getReplicationParameters() {
        return replicationParameters;
    }
    public void setReplicationParameters(ReplicationParam replicationParameters) {
        this.replicationParameters = replicationParameters;
    }
    public SnapScheduleParam getSnapScheduleParameters() {
        return snapScheduleParameters;
    }
    public void setSnapScheduleParameters(SnapScheduleParam snapScheduleParameters) {
        this.snapScheduleParameters = snapScheduleParameters;
    }
    public FileSystemParam getFsParameters() {
        return fsParameters;
    }
    public void setFsParameters(FileSystemParam fsParameters) {
        this.fsParameters = fsParameters;
    }
    public CifsFileSystemParam getCifsFsParameters() {
        return cifsFsParameters;
    }
    public void setCifsFsParameters(CifsFileSystemParam cifsFsParameters) {
        this.cifsFsParameters = cifsFsParameters;
    }
    public List<NfsShareCreateParam> getNfsShareCreate() {
        return nfsShareCreate;
    }
    public void setNfsShareCreate(List<NfsShareCreateParam> nfsShareCreate) {
        this.nfsShareCreate = nfsShareCreate;
    }
    public List<CifsShareCreateParam> getCifsShareCreate() {
        return cifsShareCreate;
    }
    public void setCifsShareCreate(List<CifsShareCreateParam> cifsShareCreate) {
        this.cifsShareCreate = cifsShareCreate;
    }
    
}
