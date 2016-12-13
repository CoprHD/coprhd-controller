/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * Block Quality of Service 
 * 
 * Contains attributes which are Block specific
 */
/**
 * @author degwea
 */
public class BlockQoS extends QoS{
    // number of multiple paths to access storage, block only
    private Integer _numPaths;
    // minimum number of paths to access storage, block only
    private Integer _minPaths;
    // number of paths to access storage per Initiator, block only
    private Integer _pathsPerInitiator;
    // VMAX Host IO Limits attributes
    private Integer _hostIOLimitBandwidth; // Host Front End limit bandwidth. If not specfied or 0, inidicated unlimited
    private Integer _hostIOLimitIOPs; // Host Front End limit I/O. If not specified or 0, indicated unlimited
}
