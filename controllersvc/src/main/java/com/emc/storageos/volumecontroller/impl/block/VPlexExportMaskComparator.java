package com.emc.storageos.volumecontroller.impl.block;

import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class VPlexExportMaskComparator implements Comparator<VPlexExportMaskComparatorContainer>{
    
    private static final Logger log = LoggerFactory.getLogger(VPlexExportMaskComparator.class);

    @Override
    public int compare(VPlexExportMaskComparatorContainer e1, VPlexExportMaskComparatorContainer e2) {
        
        /**
         * Filtered export masks can return more than one mask.
         * VPlex system might have an exclusive storage view with only 1 host initiators and shared storage view with all initiators.
         * VPlex system might have multiple exclusive storage views for the same host.
         * VPlex system might have only one big storage view with all clsuter initiators.
         * This new code should be intelligent enough to pick the right export mask, in some cases it can drop all the masks too.
         * 
         * Export Mask preference order:
         * 1. If cluster export and mask contains all the initiators prefer the view, and discard the exclusive views.
         * 2. Otherwise Prefer the exclusive views for simplicity
         * 
         */
        
        Integer e1IniCount = e1.exportMask.getInitiators() != null ? e1.exportMask.getInitiators().size() : 0;
        Integer e2IniCount = e2.exportMask.getInitiators() != null ? e2.exportMask.getInitiators().size() : 0;

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
        
        Integer e1Count = e1.exportMask.returnTotalVolumeCount();
        Integer e2Count = e2.exportMask.returnTotalVolumeCount();
        int result = e1Count.compareTo(e2Count);
        log.info(String.format("Comparing %s (#vols: %d) to %s (#vols: %d) result = %d", e1.exportMask.getMaskName(), e1Count,
                e2.exportMask.getMaskName(), e2Count, result));
        return result;
        
       
    }
    
}
