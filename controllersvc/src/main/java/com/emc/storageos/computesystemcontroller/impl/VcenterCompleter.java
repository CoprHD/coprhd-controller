/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class VcenterCompleter extends ComputeSystemCompleter {
	
	private static final Logger _logger = LoggerFactory
            .getLogger(VcenterCompleter.class);
    
    public VcenterCompleter(URI id, boolean deactivateOnComplete, String opId) {
        super(Vcenter.class, id, deactivateOnComplete, opId);
    }
    
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        super.complete(dbClient,  status,  coded);
        switch (status) {
            case error:
                dbClient.error(Vcenter.class, this.getId(), getOpId(), coded);
                break;
            default:
                dbClient.ready(Vcenter.class, this.getId(), getOpId());
        }

        if (deactivateOnComplete && status.equals(Status.ready)) {            
            Vcenter vcenter = dbClient.queryObject(Vcenter.class, this.getId());
            
            List<NamedElementQueryResultList.NamedElement> datacenterUris = ComputeSystemHelper.listChildren(dbClient, vcenter.getId(),
                    VcenterDataCenter.class, "label", "vcenter");
            for (NamedElementQueryResultList.NamedElement datacenterUri : datacenterUris) {
                VcenterDataCenter dataCenter = dbClient.queryObject(VcenterDataCenter.class, datacenterUri.id);
                if (dataCenter != null && !dataCenter.getInactive()) {
                    ComputeSystemHelper.doDeactivateVcenterDataCenter(dbClient, dataCenter);
                }
            }
            vcenter.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
            dbClient.markForDeletion(vcenter);
            _logger.info("Deactivating Vcenter: " + this.getId());
        }
    }
}
