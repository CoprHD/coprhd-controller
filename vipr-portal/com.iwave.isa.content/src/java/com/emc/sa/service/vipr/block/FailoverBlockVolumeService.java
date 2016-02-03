/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.FAILOVER_TARGET;
import static com.emc.sa.service.ServiceParams.IMAGE_TO_ACCESS;
import static com.emc.sa.service.ServiceParams.POINT_IN_TIME;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.VOLUMES;
import static com.emc.vipr.client.core.util.ResourceUtils.stringId;

import java.net.URI;

import com.emc.sa.asset.providers.BlockProvider;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.FailoverBlockConsistencyGroup;
import com.emc.sa.service.vipr.block.tasks.FailoverBlockVolume;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.vipr.client.Tasks;

@Service("FailoverBlockVolume")
public class FailoverBlockVolumeService extends ViPRService {
    // constant representing the RP type.
    private static final String RECOVER_POINT = "rp";

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(VOLUMES)
    protected URI protectionSource;

    @Param(FAILOVER_TARGET)
    protected URI protectionTarget;

    @Param(value = IMAGE_TO_ACCESS, required = false)
    protected String imageToAccess;

    @Param(value = POINT_IN_TIME, required = false)
    protected String pointInTime;

    private String type;

    @Override
    public void precheck() {
        String sourceId = "";
        String targetId = "";
        String targetName = "";
        String sourceName = "";

        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            // The type selected is volume
            BlockObjectRestRep targetVolume = BlockStorageUtils.getVolume(protectionTarget);
            BlockObjectRestRep sourceVolume = BlockStorageUtils.getVolume(protectionSource);
            type = BlockStorageUtils.getFailoverType(targetVolume);
            targetId = stringId(targetVolume);
            targetName = targetVolume.getName();
            sourceId = stringId(sourceVolume);
            sourceName = sourceVolume.getName();
        } else {
            // The type selected is consistency group
            BlockConsistencyGroupRestRep cg = BlockStorageUtils.getBlockConsistencyGroup(protectionSource);
            VirtualArrayRestRep virtualArray = BlockStorageUtils.getVirtualArray(protectionTarget);
            type = ConsistencyUtils.getFailoverType(cg);
            targetId = stringId(virtualArray);
            targetName = virtualArray.getName();
            sourceId = stringId(cg);
            sourceName = cg.getName();
        }

        if (type == null) {
            ExecutionUtils.fail("failTask.FailoverBlockVolumeService", args(sourceId, targetId), args());
        }

        if (type.equals(RECOVER_POINT) && BlockProvider.PIT_IMAGE_OPTION_KEY.equals(imageToAccess) && pointInTime == null) {
            ExecutionUtils.fail("failTask.FailoverBlockVolumeService.pit", new Object[] {}, new Object[] {});
        }

        // TODO: Add new fields
        logInfo("fail.over.block.volume.service", type.toUpperCase(), sourceName, targetName);
    }

    @Override
    public void execute() {
        Tasks<? extends DataObjectRestRep> tasks;

        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            // The type selected is volume
            if (type != null && type.equals(RECOVER_POINT) && (!BlockProvider.LATEST_IMAGE_OPTION_KEY.equals(imageToAccess))) {
                // This is a RP failover request so we need to pass along the copyName and pointInTime values.
                // We only want to do this if the image selected is NOT the latest image (this is handled by
                // the default case) but a specific snapshot or point in time.
                setImageToAccessForRP();

                tasks = execute(new FailoverBlockVolume(protectionSource, protectionTarget, type, imageToAccess, pointInTime));
            } else {
                tasks = execute(new FailoverBlockVolume(protectionSource, protectionTarget, type));
            }
        } else {
            // The type selected is consistency group
            if (type != null && type.equals(RECOVER_POINT) && (!BlockProvider.LATEST_IMAGE_OPTION_KEY.equals(imageToAccess))) {
                // This is a RP failover request so we need to pass along the copyName and pointInTime values.
                // We only want to do this if the image selected is NOT the latest image (this is handled by
                // the default case) but a specific snapshot or point in time.
                setImageToAccessForRP();

                tasks = execute(new FailoverBlockConsistencyGroup(protectionSource, protectionTarget, type, imageToAccess, pointInTime));
            } else {
                tasks = execute(new FailoverBlockConsistencyGroup(protectionSource, protectionTarget, type));
            }
        }
        if (tasks != null) {
            addAffectedResources(tasks);
        }
    }

    /**
     * Determines the appropriate image to access (copy) for RecoverPoint failover. The imageToAccess
     * value will specify one of 3 options: latest image, specific point-in-time, or a snapshot.
     */
    private void setImageToAccessForRP() {
        // This is a RP failover request so we need to pass along the copyName and pointInTime values.
        // We only want to do this if the image selected is NOT the latest image (this is handled by
        // the default case) but a specific snapshot or point in time.
        if (URIUtil.isValid(imageToAccess) && uri(imageToAccess) != null
                && URIUtil.isType(uri(imageToAccess), BlockSnapshot.class)) {
            // If the imageToAccess is a BlockSnapshot, the user is attempting to failover to
            // a specific RP bookmark. Get the name of that bookmark and pass it down.
            BlockSnapshotRestRep snapshot = BlockStorageUtils.getSnapshot(uri(imageToAccess));
            imageToAccess = snapshot.getName();
        }

        if (BlockProvider.PIT_IMAGE_OPTION_KEY.equals(imageToAccess)) {
            // If the image to access is a point-in-time, null out the image access variable otherwise
            // the failover over logic will attempt to look for a bookmark called 'pit'.
            imageToAccess = null;
        }
    }
}
