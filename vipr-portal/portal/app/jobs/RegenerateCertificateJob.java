/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import play.jobs.Job;

import com.emc.vipr.client.core.Keystore;
import com.emc.vipr.model.keystore.KeyAndCertificateChain;

public class RegenerateCertificateJob extends Job {
    private final Keystore api;

    public RegenerateCertificateJob(Keystore api) {
        this.api = api;
    }

    @Override
    public void doJob() {
        api.regenerateKeyAndCertificate();
    }
}
