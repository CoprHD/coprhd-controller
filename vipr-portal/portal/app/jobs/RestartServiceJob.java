/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import play.jobs.Job;
import com.emc.vipr.client.ViPRSystemClient;

public class RestartServiceJob extends Job {
    private ViPRSystemClient client;
    private String serviceName;
    private String nodeId;

    public RestartServiceJob(ViPRSystemClient client, String serviceName, String nodeId) {
        this.client = client;
        this.serviceName = serviceName;
        this.nodeId = nodeId;
    }

    @Override
    public void doJob() throws Exception {
        client.control().restartServiceByNodeId(nodeId, serviceName);
    }
}
