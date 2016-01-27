/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

@Service("DetachApplicationFullCopy")
public class DetachApplicationFullCopyService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.COPY_NAME)
    protected String fullCopyName;
    
    protected URI volumeId;

    @Override
    public void execute() throws Exception {
        Tasks<? extends DataObjectRestRep> tasks = execute(new DetachApplicationFullCopy(applicationId, volumeId, fullCopyName));
        addAffectedResources(tasks);
    }
    
    @Override
    public void precheck() throws Exception {
        NamedVolumesList volList = getClient().application().getFullCopiesByApplication(applicationId);
        if (volList != null && volList.getVolumes() != null && !volList.getVolumes().isEmpty()) {
            for (NamedRelatedResourceRep volId : volList.getVolumes()) {
                VolumeRestRep vol = getClient().blockVolumes().get(volId.getId());
                if (vol != null && vol.getProtection() != null && vol.getProtection().getFullCopyRep() != null && 
                        fullCopyName.equals(vol.getProtection().getFullCopyRep().getFullCopySetName())) {
                    volumeId = vol.getId();
                }
            }
        }
        if (volumeId == null) {
            ExecutionUtils.fail("failTask.DetachApplicationFullCopyService.volumeId.precheck", new Object[] {});
        }
    }
}
