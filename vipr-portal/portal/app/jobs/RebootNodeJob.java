/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import com.emc.vipr.client.ViPRSystemClient;
import play.jobs.Job;

public class RebootNodeJob extends Job {
    private ViPRSystemClient client;
    private String nodeId;

    public RebootNodeJob(ViPRSystemClient client, String nodeId) {
        this.client = client;
        this.nodeId = nodeId;
    }

    @Override
    public void doJob() throws Exception {
        client.control().rebootNodeByNodeId(nodeId);
    }
}
