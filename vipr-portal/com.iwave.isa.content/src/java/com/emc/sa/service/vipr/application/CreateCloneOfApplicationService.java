/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.CreateCloneOfApplication;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
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
    private String virtualArrayId;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<String> subGroups;

    @Override
    public void execute() throws Exception {
        NamedVolumesList volumesToUse = getVolumesBySite();

        List<URI> volumeIds = BlockStorageUtils.getSingleVolumePerSubGroup(volumesToUse, subGroups);

        Tasks<? extends DataObjectRestRep> tasks = execute(
                new CreateCloneOfApplication(applicationId, name, volumeIds));
        addAffectedResources(tasks);
    }

    /**
     * Get volumes by selected virtual array
     * 
     * @return list of volumes
     */
    // TODO this is same as snapshot, use common in a helper class?
    public NamedVolumesList getVolumesBySite() {
        boolean isTarget = false;
        URI virtualArray = null;
        if (virtualArrayId != null && StringUtils.split(virtualArrayId, ':')[0].equals("tgt")) {
            virtualArray = URI.create(StringUtils.substringAfter(virtualArrayId, ":"));
            isTarget = true;
        } else {
            isTarget = false;
        }

        NamedVolumesList applicationVolumes = getClient().application().getVolumeByApplication(applicationId);
        NamedVolumesList volumesToUse = new NamedVolumesList();
        for (NamedRelatedResourceRep volumeId : applicationVolumes.getVolumes()) {
            VolumeRestRep volume = getClient().blockVolumes().get(volumeId);
            if (volume.getHaVolumes() != null && !volume.getHaVolumes().isEmpty()) {
                volume = getClient().blockVolumes().get(volume.getHaVolumes().get(0).getId());
            }
            if (isTarget) {
                if (volume.getVirtualArray().getId().equals(virtualArray)) {
                    volumesToUse.getVolumes().add(volumeId);
                }
            } else {
                if (volume.getProtection() == null || volume.getProtection().getRpRep() == null
                        || volume.getProtection().getRpRep().getPersonality() == null
                        || volume.getProtection().getRpRep().getPersonality().equalsIgnoreCase("SOURCE")) {
                    volumesToUse.getVolumes().add(volumeId);
                }
            }
        }
        return volumesToUse;
    }
}
