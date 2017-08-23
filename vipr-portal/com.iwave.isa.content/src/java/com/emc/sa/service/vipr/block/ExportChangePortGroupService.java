/*
 * Copyright (c) 2017  DELL EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.EXPORT;
import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.CHANGE_PORT_GROUP;
import static com.emc.sa.service.ServiceParams.CURRENT_PORT_GROUP;
import static com.emc.sa.service.ServiceParams.STORAGE_SYSTEM;
import static com.emc.sa.service.ServiceParams.SUSPEND_WAIT;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.ExportChangePortGroup;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.Task;

@Service("ExportChangePortGroup")
public class ExportChangePortGroupService extends ViPRService {

    @Param(HOST)
    protected URI hostId;
    
    @Param(VIRTUAL_ARRAY)
    protected URI virtualArrayId;
    
    @Param(EXPORT)
    protected URI exportId;
    
    @Param(STORAGE_SYSTEM)
    protected URI storageSystemId;
    
    @Param(CURRENT_PORT_GROUP)
    protected URI currentPortGroupId;
    
    @Param(value = CHANGE_PORT_GROUP, required = true)
    protected URI newPortGroupId;
    
    @Param(SUSPEND_WAIT)
    protected boolean suspendWait;
    
    @Override
    public void precheck() throws Exception {
        // Current Port Group must be defined
        if (URIUtil.isNull(currentPortGroupId)) {
            ExecutionUtils.fail("failTask.exportChangePortGroup.precheck.noExistingPortGroup", args());
        }
        
        // New Port Group must be defined
        if (URIUtil.isNull(newPortGroupId)) {
            ExecutionUtils.fail("failTask.exportChangePortGroup.precheck.newPortGroupRequired", args());
        }
        
        // The current port group cannot be the same as the intended new port group
        // for the order.
        if (currentPortGroupId.equals(newPortGroupId)) {
            ExecutionUtils.fail("failTask.exportChangePortGroup.precheck.samePortGroup", args());
        }
    }
    
    @Override
    public void execute() throws Exception {
        Task<ExportGroupRestRep> task = execute(new ExportChangePortGroup(exportId, currentPortGroupId, newPortGroupId, suspendWait));        
        addAffectedResource(task);
    }
}
