/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.exceptions.ServiceErrorsException;

public abstract class LongRunningTasks<T> extends ViPRExecutionTask<Tasks<T>> {
    private boolean waitFor;
    private long timeout = -1;
    private int maxErrorDisplay = -1;

    public void setMaxErrorDisplay(int maxErrorDisplay) {
		this.maxErrorDisplay = maxErrorDisplay;
	}

	public boolean isWaitFor() {
        return waitFor;
    }

    public void setWaitFor(boolean waitFor) {
        this.waitFor = waitFor;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public final Tasks<T> executeTask() throws Exception {
        Tasks<T> tasks = doExecute();
        for (Task<T> task : tasks.getTasks()) {
            addOrderIdTag(task.getTaskResource().getId());
            if (waitFor) {
                info("Waiting for task to complete: %s on resource: %s", task.getOpId(), task.getResourceId());
            }
        }
        if (waitFor) {
            try {
				tasks.waitFor(timeout);
			} catch (ServiceErrorsException e) {
				if(maxErrorDisplay > 0 && e.getServiceErrors().size() > maxErrorDisplay){
	        		throw new ServiceErrorsException(e.getServiceErrors().subList(0, maxErrorDisplay));
	        	}else{
	        		throw e;
	        	}
			}
        }
        return tasks;
    }

    protected abstract Tasks<T> doExecute() throws Exception;
}
