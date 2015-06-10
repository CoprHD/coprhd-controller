/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import play.jobs.Job;
import com.emc.vipr.client.ViPRSystemClient;

public class MinorityNodeRecoveryJob extends Job {
    private ViPRSystemClient client;
    
    public MinorityNodeRecoveryJob(ViPRSystemClient client) {
        this.client = client;
    }
    
    @Override
    public void doJob() throws Exception {
        client.control().recoverMinorityNode();
    }
}
