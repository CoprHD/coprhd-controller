/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import com.emc.vipr.client.ViPRSystemClient;
import play.jobs.Job;
import util.ConfigPropertyUtils;
import java.util.Map;

public class UpdatePropertiesJob extends Job {
    private ViPRSystemClient client;
    private Map<String, String> properties;

    public UpdatePropertiesJob(ViPRSystemClient client, Map<String, String> properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void doJob() throws Exception {
        ConfigPropertyUtils.saveProperties(client, properties);
    }
}
