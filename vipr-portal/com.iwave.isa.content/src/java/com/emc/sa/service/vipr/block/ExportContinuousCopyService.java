/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.COPIES;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("ExportContinuousCopy")
public class ExportContinuousCopyService extends ViPRService {
    
    @Param(value = VOLUMES, required = false)
    protected String volumeId;
    
    @Param(value = COPIES, required = false)
    protected List<String> continuousCopyIds;

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
        helper.exportBlockResources(uris(continuousCopyIds), uri(volumeId));
    }

}
