/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class VcenterDataCenterCompleter extends ComputeSystemCompleter {

	private static final Logger _logger = LoggerFactory.getLogger(VcenterDataCenterCompleter.class);
	
	public VcenterDataCenterCompleter(URI id, boolean deactivateOnComplete, String opId) {
		super(VcenterDataCenter.class, id, deactivateOnComplete, opId);
	}

	@Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        super.complete(dbClient,  status,  coded);
        switch (status) {
            case error:
                dbClient.error(VcenterDataCenter.class, this.getId(), getOpId(), coded);
                break;
            default:
                dbClient.ready(VcenterDataCenter.class, this.getId(), getOpId());
        }

        if (deactivateOnComplete && status.equals(Status.ready)) {            
        	VcenterDataCenter datacenter = dbClient.queryObject(VcenterDataCenter.class, this.getId());
                
            if (datacenter != null && !datacenter.getInactive()) {
                ComputeSystemHelper.doDeactivateVcenterDataCenter(dbClient, datacenter);
                _logger.info("Deactivating datacenter: " + this.getId());
            }
        }
    }
}
