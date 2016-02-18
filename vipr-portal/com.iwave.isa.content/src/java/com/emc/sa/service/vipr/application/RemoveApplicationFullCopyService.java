/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;

@Service("RemoveApplicationFullCopy")
public class RemoveApplicationFullCopyService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.COPY_NAME)
    protected String name;
    
    private List<URI> fullCopyIds = new ArrayList<URI>();

    @Override
    public void execute() throws Exception {
        BlockStorageUtils.removeBlockResources(fullCopyIds, VolumeDeleteTypeEnum.FULL);
    }
    
    @Override
    public void precheck() throws Exception {
        List<VolumeRestRep> allCopyVols = getClient().blockVolumes()
                .getByRefs(getClient().application().getFullCopiesByApplication(applicationId).getVolumes());
        List<VolumeRestRep> filtered = new ArrayList<VolumeRestRep>();
        if (allCopyVols != null) {
            for (VolumeRestRep vol : allCopyVols) {
                if (vol != null && vol.getProtection() != null && vol.getProtection().getFullCopyRep() != null
                        && name.equals(vol.getProtection().getFullCopyRep().getFullCopySetName())) {
                    filtered.add(vol);
                }
            }
        }

        if (filtered == null || filtered.isEmpty()) {
            ExecutionUtils.fail("failTask.RemoveApplicationFullCopyService.volumeId.precheck", new Object[] {});
        }

        for (VolumeRestRep vol : filtered) {
            fullCopyIds.add(vol.getId());
        }
    }
}
