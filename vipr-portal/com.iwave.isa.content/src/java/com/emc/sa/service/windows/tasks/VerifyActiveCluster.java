/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

public class VerifyActiveCluster extends WindowsExecutionTask<Void> {

    @Override
    public void execute() throws Exception {
        if (!getTargetSystem().hasActiveClusters()) {
            throw stateException("illegalState.VerifyActiveCluster.noTargetSystem");
        }

        String url = getTargetSystem().getTarget().getUrl().toExternalForm();
        provideDetailArgs(url);
    }
}
