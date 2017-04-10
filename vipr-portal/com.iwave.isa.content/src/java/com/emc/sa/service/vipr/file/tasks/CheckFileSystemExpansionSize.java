/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileShareRestRep;

public class CheckFileSystemExpansionSize extends ViPRExecutionTask<Void> {

    private final List<FileShareRestRep> fileSystems;
    private final Double sizeInGb;

    public CheckFileSystemExpansionSize(List<FileShareRestRep> fileSystems, Double sizeInGb) {
        this.fileSystems = fileSystems;
        this.sizeInGb = sizeInGb;
        provideDetailArgs(fileSystems, sizeInGb);
    }

    @Override
    public void execute() throws Exception {
        for (FileShareRestRep fs : fileSystems) {
            if (Double.parseDouble(fs.getCapacity()) >= sizeInGb.doubleValue()) {
//                throw stateException("file.expand.size.smaller.than.existing", sizeInGb.toString(),
//                        fs.getCapacity());
            }
        }
    }

}