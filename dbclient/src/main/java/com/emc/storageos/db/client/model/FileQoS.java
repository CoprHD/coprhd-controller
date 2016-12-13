/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
/**
 * File Quality of Service 
 * 
 * Contains attributes which are File specific
 */
/**
 * @author degwea
 */
package com.emc.storageos.db.client.model;

public class FileQoS extends QoS{

    // File Replication attributes.
    // Replication type { Local or Remote}
    private String fileReplicationType;
    // File Replication RPO value
    private Long _frRpoValue;
    // File Replication RPO type
    private String _frRpoType;
    // File Replication RPO type
    private String _fileReplicationCopyMode;
    // File Repilcation copies
    private StringMap _fileRemoteCopySettings;
}
