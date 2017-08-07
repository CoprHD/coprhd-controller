/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.model.sys.diagutil.DiagutilParam;
import play.jobs.Job;

import java.util.List;

public class CollectDiagutilDataJob extends Job {
    private ViPRSystemClient client;
    private List<String> options;
    private DiagutilParam param;

    public CollectDiagutilDataJob(ViPRSystemClient client, List<String> options, DiagutilParam param) {
        this.client = client;
        this.options = options;
        this.param = param;
    }

    @Override
    public void doJob() {
        client.diagutil().collect(options, param);
    }
}
