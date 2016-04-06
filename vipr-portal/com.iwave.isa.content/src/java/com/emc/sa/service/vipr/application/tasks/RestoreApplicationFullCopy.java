/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupFullCopyRestoreParam;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

// TODO move to tasks package
public class RestoreApplicationFullCopy extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final List<String> applicationSubGroup;
    private final String copyName;
    private List<VolumeRestRep> allRPSourceVols;
    private Map<URI, VolumeRestRep> fcTargetToSourceMap = new HashMap<URI, VolumeRestRep>();

    public RestoreApplicationFullCopy(URI applicationId, List<String> applicationSubGroup, String copyName) {
        this.applicationId = applicationId;
        this.applicationSubGroup = applicationSubGroup;
        this.copyName = copyName;
        provideDetailArgs(applicationId, copyName);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        List<URI> volumeIds = new ArrayList<URI>();

        List<VolumeRestRep> allCopyVols = getClient().blockVolumes()
                .getByRefs(getClient().application().getFullCopiesByApplication(applicationId).getVolumes());
        List<VolumeRestRep> volsForCopy = filterByCopyName(allCopyVols);
        if (volsForCopy != null && !volsForCopy.isEmpty()) {
            for (String subGroup : applicationSubGroup) {
                URI volInSubGroup = findVolumeInSubGroup(volsForCopy, subGroup);
                if (volInSubGroup != null) {
                    volumeIds.add(volInSubGroup);
                }
            }
        }

        if (volumeIds.isEmpty()) {
            ExecutionUtils.fail("failTask.RestoreApplicationFullCopyService.volumeId.precheck", new Object[] {});
        }

        VolumeGroupFullCopyRestoreParam input = new VolumeGroupFullCopyRestoreParam(true, volumeIds);
        TaskList taskList = getClient().application().restoreApplicationFullCopy(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }

    /**
     * filters all full copy volumes for an application down to the list of volumes for just one point in time copy
     * 
     * @param allCopyVols
     *            all copy volumes from the application; could be from multiple point in time copies
     * @return
     */
    private List<VolumeRestRep> filterByCopyName(List<VolumeRestRep> allCopyVols) {
        List<VolumeRestRep> filtered = new ArrayList<VolumeRestRep>();
        if (allCopyVols != null) {
            for (VolumeRestRep vol : allCopyVols) {
                if (vol != null && vol.getProtection() != null && vol.getProtection().getFullCopyRep() != null
                        && copyName.equals(vol.getProtection().getFullCopyRep().getFullCopySetName())) {
                    filtered.add(vol);
                }
            }
        }
        return filtered;
    }

    /**
     * get one full copy volume from the application sub group
     * 
     * @param volumesInCopy
     *            volumes in this full copy (could be from multile sub groups
     * @param subGroup
     * @return
     */
    private URI findVolumeInSubGroup(List<VolumeRestRep> volumesInCopy, String subGroup) {
        for (VolumeRestRep fullCopyTargetVol : volumesInCopy) {
            VolumeRestRep fullCopySourceVol = fcTargetToSourceMap.get(fullCopyTargetVol.getId());
            if (fullCopySourceVol == null && fullCopyTargetVol.getProtection() != null
                    && fullCopyTargetVol.getProtection().getFullCopyRep() != null
                    && fullCopyTargetVol.getProtection().getFullCopyRep().getAssociatedSourceVolume() != null) {
                fullCopySourceVol = getClient().blockVolumes()
                        .get(fullCopyTargetVol.getProtection().getFullCopyRep().getAssociatedSourceVolume());
            }
            if (fullCopySourceVol == null) {
                continue;
            }
            // if rp it could be either source, target or swapped source or target
            if (fullCopySourceVol.getProtection() != null && fullCopySourceVol.getProtection().getRpRep() != null) {
                String tgtSubGroup = String.format("%s-RPTARGET", subGroup);
                if (fullCopySourceVol.getReplicationGroupInstance() != null
                        && (fullCopySourceVol.getReplicationGroupInstance().equals(subGroup)
                                || fullCopySourceVol.getReplicationGroupInstance().equals(tgtSubGroup))) {
                    return fullCopyTargetVol.getId();
                }
            } else {
                if (fullCopySourceVol.getReplicationGroupInstance() != null
                        && fullCopySourceVol.getReplicationGroupInstance().equals(subGroup)) {
                    return fullCopyTargetVol.getId();
                }
            }
        }
        return null;
    }
}
