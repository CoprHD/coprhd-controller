/*
 * Copyright (c) 2018 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.model.sys.diagutil.DiagutilParam;
import play.jobs.Job;

import java.util.List;

/**
 * 
 * Class for collecting diagutil data Job.
 *
 */
public class CollectDiagutilDataJob extends Job {
    private ViPRSystemClient client;
    private List<String> options;
    private DiagutilParam param;

    /**
     * Constructor for CollectDiagUtilDataJob
     * @param client
     *         ViPRSystemClient object
     * @param options
     *         list of options
     * @param param
     *         Diagutil parameters
     */
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
