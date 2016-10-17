/*
 * Copyright 2016 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.driver.dellsc.helpers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.dellsc.DellSCDriverException;
import com.emc.storageos.driver.dellsc.DellSCDriverTask;
import com.emc.storageos.driver.dellsc.scapi.SizeUtil;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPI;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPIException;
import com.emc.storageos.driver.dellsc.scapi.objects.ScCopyMirrorMigrate;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolume;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.model.VolumeMirror;
import com.emc.storageos.storagedriver.model.VolumeMirror.SynchronizationState;

/**
 * Helper for mirroring operations.
 */
public class DellSCMirroring {

    private static final Logger LOG = LoggerFactory.getLogger(DellSCMirroring.class);

    private DellSCConnectionManager connectionManager;

    /**
     * Initialize the instance.
     */
    public DellSCMirroring() {
        this.connectionManager = DellSCConnectionManager.getInstance();
    }

    /**
     * Create volume mirrors.
     *
     * @param mirrors The volume mirrors to create.
     * @return The creation task.
     */
    public DriverTask createVolumeMirror(List<VolumeMirror> mirrors) {
        LOG.info("Creating volume mirror");
        DriverTask task = new DellSCDriverTask("createVolumeMirror");

        StringBuilder errBuffer = new StringBuilder();
        int mirrorsCreated = 0;
        for (VolumeMirror mirror : mirrors) {
            LOG.debug("Creating mirror of volume {}", mirror.getParentId());
            String ssn = mirror.getStorageSystemId();
            try {
                StorageCenterAPI api = connectionManager.getConnection(ssn);

                ScVolume srcVol = api.getVolume(mirror.getParentId());
                ScVolume destVol = api.createVolume(
                        ssn,
                        mirror.getDisplayName(),
                        srcVol.storageType.instanceId,
                        SizeUtil.byteToMeg(
                                SizeUtil.sizeStrToBytes(srcVol.configuredSize)),
                        null);

                ScCopyMirrorMigrate scCmm = api.createMirror(ssn, srcVol.instanceId, destVol.instanceId);

                mirror.setNativeId(scCmm.instanceId);
                mirror.setSyncState(SynchronizationState.COPYINPROGRESS);

                mirrorsCreated++;
                LOG.info("Created volume mirror '{}'", scCmm.instanceId);
            } catch (StorageCenterAPIException | DellSCDriverException dex) {
                String error = String.format("Error creating volume mirror %s: %s", mirror.getDisplayName(), dex);
                LOG.error(error);
                errBuffer.append(String.format("%s%n", error));
            }
        }

        task.setMessage(errBuffer.toString());

        if (mirrorsCreated == mirrors.size()) {
            task.setStatus(TaskStatus.READY);
        } else if (mirrorsCreated == 0) {
            task.setStatus(TaskStatus.FAILED);
        } else {
            task.setStatus(TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }

    /**
     * Delete volume mirror and the destination volume.
     *
     * @param mirror The mirror to delete.
     * @return The delete task.
     */
    public DriverTask deleteVolumeMirror(VolumeMirror mirror) {
        LOG.info("Deleting volume mirror {}", mirror);
        DellSCDriverTask task = new DellSCDriverTask("deleteVolumeMirror");

        try {
            StorageCenterAPI api = connectionManager.getConnection(mirror.getStorageSystemId());
            ScCopyMirrorMigrate cmm = api.getMirror(mirror.getNativeId());
            api.deleteMirror(cmm.instanceId);
            api.deleteVolume(cmm.destinationVolume.instanceId);
            task.setStatus(TaskStatus.READY);
        } catch (StorageCenterAPIException | DellSCDriverException dex) {
            String error = String.format(
                    "Error deleting volume mirror %s: %s", mirror.getNativeId(), dex);
            LOG.error(error);
            task.setFailed(error);
        }
        return task;
    }

    /**
     * Delete volume mirror but leave the destination volume intact.
     *
     * @param mirrors The mirrors to split.
     * @return The split task.
     */
    public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors) {
        LOG.info("Splitting volume mirror");
        DellSCDriverTask task = new DellSCDriverTask("splitVolumeMirror");

        StringBuilder errBuffer = new StringBuilder();
        int mirrorSplit = 0;
        for (VolumeMirror mirror : mirrors) {
            try {
                StorageCenterAPI api = connectionManager.getConnection(mirror.getStorageSystemId());
                api.deleteMirror(mirror.getNativeId());
                task.setStatus(TaskStatus.READY);
                mirrorSplit++;
            } catch (StorageCenterAPIException | DellSCDriverException dex) {
                String error = String.format("Error splitting volume mirror %s: %s", mirror.getDisplayName(), dex);
                LOG.error(error);
                errBuffer.append(String.format("%s%n", error));
            }
        }

        task.setMessage(errBuffer.toString());

        if (mirrorSplit == mirrors.size()) {
            task.setStatus(TaskStatus.READY);
        } else if (mirrorSplit == 0) {
            task.setStatus(TaskStatus.FAILED);
        } else {
            task.setStatus(TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }

    /**
     * Resume volume mirrors. Not supported as once a mirror is split,
     * we no longer have any way of knowing what the target was.
     *
     * @param mirrors The mirrors to resume.
     * @return The mirror task.
     */
    public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors) {
        LOG.info("Resuming volume mirror not supported.");
        DriverTask task = new DellSCDriverTask("resumeVolumeMirror");
        task.setStatus(TaskStatus.FAILED);
        return null;
    }

    /**
     * Restore a volume mirror.
     * 
     * @param mirrors The mirrors.
     * @return The driver task.
     */
    public DriverTask restoreVolumeMirror(List<VolumeMirror> mirrors) {
        LOG.info("Restoring volume mirror not supported");

        // Need to determine what this expects. Mirrors are... mirrored. So
        // nothing to restore to in SC terms.
        DriverTask task = new DellSCDriverTask("restoreVolumeMirror");
        task.setStatus(TaskStatus.FAILED);
        return null;
    }
}
