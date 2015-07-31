/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import com.emc.vipr.client.ViPRSystemClient;
import play.jobs.Job;

public class PoweroffJob extends Job {
    private ViPRSystemClient client;

    public PoweroffJob(ViPRSystemClient client) {
        this.client = client;
    }

    @Override
    public void doJob() throws Exception {
        client.control().powerOffCluster();
    }
}
