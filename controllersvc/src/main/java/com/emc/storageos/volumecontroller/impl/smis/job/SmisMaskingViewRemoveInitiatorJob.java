/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.volumecontroller.TaskCompleter;

import javax.cim.CIMObjectPath;
import java.net.URI;

/**
 * A VMAX Remove Initiator job
 */
public class SmisMaskingViewRemoveInitiatorJob extends SmisJob
{
    public SmisMaskingViewRemoveInitiatorJob(CIMObjectPath cimJob,
                                             URI storageSystem,
                                             TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "RemoveInitiatorFromMaskingView");
    }
}