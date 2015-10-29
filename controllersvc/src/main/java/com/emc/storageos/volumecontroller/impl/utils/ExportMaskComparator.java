/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.utils;

import com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

/**
 * Class encapsulates a way to compare ExportMasks using a wrapper class ExportMaskComparatorContainer
 * 
 */
class ExportMaskComparator implements Comparator<ExportMaskComparatorContainer> {
    private static final Logger log = LoggerFactory.getLogger(ExportMaskComparator.class);

    public int compare(ExportMaskComparatorContainer e1, ExportMaskComparatorContainer e2) {
        // CTRL-8982 , existing initiators are initiators which are not part of userAdded
        // it could be null, I think we need to check the initiators instead of existingInitiators()
        // Rule 1: Prefer masks that contain all initiators over partial or incomplete masks
        Integer e1IniCount = e1.mask.getInitiators() != null ? e1.mask.getInitiators().size() : 0;
        Integer e2IniCount = e2.mask.getInitiators() != null ? e2.mask.getInitiators().size() : 0;

        // CTRL-9709 - If Cluster, then cluster MV should get more preference than Host MV
        if (e1.exportGroup.forCluster()) {
            // Descending order
            if (e1IniCount < e2IniCount) {
                return 1;
            } else if (e1IniCount > e2IniCount) {
                return -1;
            }

        } else {
            // if Host or initiator mode - ascending order
            if (e1IniCount > e2IniCount) {
                return 1;
            } else if (e1IniCount < e2IniCount) {
                return -1;
            }
        }

        // Rule 2: COP-16877 Prefer REGULAR masks to Phantom
        if (e1.policy.getExportType().equals(ExportMaskPolicy.EXPORT_TYPE.REGULAR.name()) &&
                !e2.policy.getExportType().equals(ExportMaskPolicy.EXPORT_TYPE.REGULAR.name())) {
            return -1;
        } else if (!e1.policy.getExportType().equals(ExportMaskPolicy.EXPORT_TYPE.REGULAR.name()) &&
                e2.policy.getExportType().equals(ExportMaskPolicy.EXPORT_TYPE.REGULAR.name())) {
            return 1;
        }

        // Rule 3: Prefer masks that have cascaded groups
        if (e1.policy.isSimpleMask() && !e2.policy.isSimpleMask()) {
            return 1;
        } else if (!e1.policy.isSimpleMask() && e2.policy.isSimpleMask()) {
            return -1;
        }

        // Rule 4: Prefer masks that are less utilized
        Integer e1Count = e1.mask.returnTotalVolumeCount();
        Integer e2Count = e2.mask.returnTotalVolumeCount();
        int result = e1Count.compareTo(e2Count);
        log.info(String.format("Comparing %s (#vols: %d) to %s (#vols: %d) result = %d", e1.mask.getMaskName(), e1Count,
                e2.mask.getMaskName(), e2Count, result));
        return result;
    }
}
