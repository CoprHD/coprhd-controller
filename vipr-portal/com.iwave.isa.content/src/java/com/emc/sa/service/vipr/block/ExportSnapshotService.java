/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.SNAPSHOTS;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("ExportSnapshot")
public class ExportSnapshotService extends ViPRService {

    @Param(value=SNAPSHOTS, required=false)
    protected List<String> snapshotIds;
    
    @Param(HOST)
    protected URI hostId;
    
    @Bindable
    protected ExportBlockVolumeHelper helper = new ExportBlockVolumeHelper();
		
    @Override
    public void precheck() throws Exception {
        helper.precheck();
    }
	
    @Override
    public void execute() throws Exception {
        helper.exportBlockResources(uris(snapshotIds));
    }
}
