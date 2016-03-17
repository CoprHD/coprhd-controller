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
import com.emc.sa.service.vipr.application.tasks.CreateSnapshotForApplication;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

@Service("CreateSnapshotOfApplication")
public class CreateSnapshotOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_COPY_SETS)
    protected String name;

    @Param(ServiceParams.APPLICATION_SITE)
    protected String virtualArrayParameter;

    @Param(ServiceParams.READ_ONLY)
    protected Boolean readOnly;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<String> subGroups;

    @Override
    public void execute() throws Exception {

        NamedVolumesList volumesToUse = getVolumesBySite();

        List<URI> volumeIds = BlockStorageUtils.getSingleVolumePerSubGroupAndStorageSystem(volumesToUse, subGroups);

        Tasks<? extends DataObjectRestRep> tasks = execute(new CreateSnapshotForApplication(applicationId, volumeIds, name, readOnly));
        addAffectedResources(tasks);
    }

    /**
     * Get volumes by selected virtual array
     * 
     * @return list of volumes
     */
    public NamedVolumesList getVolumesBySite() {
        boolean isTarget = false;
        URI virtualArray = null;
        if (virtualArrayParameter != null && StringUtils.split(virtualArrayParameter, ':')[0].equals("tgt")) {
            virtualArray = URI.create(StringUtils.substringAfter(virtualArrayParameter, ":"));
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
