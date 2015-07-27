/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import play.Play;
import play.jobs.Job;
import util.ConfigPropertyUtils;

/**
 * Job to seed the database with initial production and development data.
 * 
 * @author Chris Dail
 */
public class LoadInitialData extends Job<String> {
    public void doJob() {
        if (Play.mode.isDev() && !Play.runingInTestMode()) {
            // Load any required development assets here
        }
        
        ConfigPropertyUtils.loadCoordinatorProperties();
    }
}
