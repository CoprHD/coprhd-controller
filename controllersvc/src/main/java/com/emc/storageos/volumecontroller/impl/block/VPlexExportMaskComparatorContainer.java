package com.emc.storageos.volumecontroller.impl.block;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;

public class VPlexExportMaskComparatorContainer {
    
    public ExportMask exportMask;
    
    public ExportGroup exportGroup;
    
    public VPlexExportMaskComparatorContainer(ExportMask exportMask, ExportGroup exportGroup) {
        this.exportMask = exportMask;
        this.exportGroup = exportGroup;
    }
    
}
