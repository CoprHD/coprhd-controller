/*
 * Copyright (c) 2012-2017 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;


import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileShareRestRep;

public class CheckFileSystemReductionSize extends ViPRExecutionTask<Void> {
	
	private final List<FileShareRestRep> fileSystems;
    private final Double sizeInGb;

    public CheckFileSystemReductionSize(List<FileShareRestRep> fileSystems, Double sizeInGb) {
        this.fileSystems = fileSystems;
        this.sizeInGb = sizeInGb;
        provideDetailArgs(fileSystems, sizeInGb);
    }

    @Override
    public void execute() throws Exception {
        for (FileShareRestRep fs : fileSystems) {
            if (Double.parseDouble(fs.getCapacity()) <= sizeInGb.doubleValue()) {
                throw stateException("file.reduce.size.larger.than.existing", sizeInGb.toString(),
                        fs.getCapacity());
            }
        }
    }
}
