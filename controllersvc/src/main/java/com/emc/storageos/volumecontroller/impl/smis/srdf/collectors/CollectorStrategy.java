/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.collectors;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;

import javax.cim.CIMObjectPath;
import java.util.Collection;

/**
 * Implementations of this interface must provide a strategy for collecting synchronization instances from SMI-S
 * based on the SRDF configuration of a given ViPR target volume.
 * 
 * Created by bibbyi1 on 3/23/2015.
 * 
 */
public interface CollectorStrategy {
    Collection<CIMObjectPath> collect(StorageSystem provider, Volume targetVolume);
}
