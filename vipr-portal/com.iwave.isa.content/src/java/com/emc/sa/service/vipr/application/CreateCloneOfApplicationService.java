/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.CreateCloneOfApplication;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

@Service("CreateCloneOfApplication")
public class CreateCloneOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.NAME)
    private String name;

    @Param(value = ServiceParams.APPLICATION_SITE, required = false)
    private URI virtualArrayId;

    private URI virtualPoolId;

    @Override
    public void execute() throws Exception {
        Tasks<? extends DataObjectRestRep> tasks = execute(
                new CreateCloneOfApplication(applicationId, name, virtualArrayId, virtualPoolId));
        addAffectedResources(tasks);
    }
    
    @Override
    public void precheck() throws Exception {
        NamedVolumesList volList = getClient().application().getVolumeByApplication(applicationId);
        if (volList == null || volList.getVolumes() == null || volList.getVolumes().isEmpty()) {
            ExecutionUtils.fail("failTask.CreateCloneOfApplicationService.volumeId.precheck", new Object[] {});
        }
        VolumeRestRep firstVol = getClient().blockVolumes().get(volList.getVolumes().get(0));
        boolean isRP = firstVol.getProtection() != null && firstVol.getProtection().getRpRep() != null;
        if (virtualArrayId == null && isRP) {
            ExecutionUtils.fail("failTask.CreateCloneOfApplicationService.virtualArrayId.precheck", new Object[] {});
        }
        if (isRP) {
            String personality = "SOURCE";
            if (StringUtils.split(virtualArrayId.toString(), ':')[0].equals("tgt")) {
                personality = "TARGET";
                virtualArrayId = URI.create(StringUtils.substringAfter(virtualArrayId.toString(), ":"));
            }
            List<VolumeRestRep> appVols = getClient().blockVolumes().getByRefs(getClient().application().listVolumes(applicationId));
            for (VolumeRestRep vol : appVols) {
                if (vol.getProtection() != null && vol.getProtection().getRpRep() != null
                        && vol.getProtection().getRpRep().getPersonality() != null
                        && vol.getProtection().getRpRep().getPersonality().equals(personality)
                        && vol.getVirtualArray().getId().equals(virtualArrayId)) {
                    virtualPoolId = vol.getVirtualPool().getId();
                    break;
                }
            }
            if (virtualPoolId == null) {
                ExecutionUtils.fail("failTask.CreateCloneOfApplicationService.virtualArrayId.precheck", new Object[] {});
            }
        }
    }
}
