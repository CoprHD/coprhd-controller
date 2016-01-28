/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;

@Service("RemoveApplicationFullCopy")
public class RemoveApplicationFullCopyService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.COPY_NAME)
    protected String name;
    
    protected URI volumeId;

    @Override
    public void execute() throws Exception {
        NamedVolumesList allFullCopies = getClient().application().getFullCopiesByApplication(applicationId);
        Set<URI> fullCopyIds = new HashSet<URI>();
        for (NamedRelatedResourceRep fullCopy : allFullCopies.getVolumes()) {
            fullCopyIds.add(fullCopy.getId());
        }
        
        BlockStorageUtils.removeBlockResources(fullCopyIds, VolumeDeleteTypeEnum.FULL);
    }
    
    @Override
    public void precheck() throws Exception {
        NamedVolumesList volList = getClient().application().getFullCopiesByApplication(applicationId);
        if (volList != null && volList.getVolumes() != null && !volList.getVolumes().isEmpty()) {
            for (NamedRelatedResourceRep volId : volList.getVolumes()) {
                VolumeRestRep vol = getClient().blockVolumes().get(volId.getId());
                if (vol != null && vol.getProtection() != null && vol.getProtection().getFullCopyRep() != null && 
                        name.equals(vol.getProtection().getFullCopyRep().getFullCopySetName())) {
                    volumeId = vol.getId();
                }
            }
        }
        if (volumeId == null) {
            ExecutionUtils.fail("failTask.RemoveApplicationFullCopyService.volumeId.precheck", new Object[] {});
        }
    }
}
