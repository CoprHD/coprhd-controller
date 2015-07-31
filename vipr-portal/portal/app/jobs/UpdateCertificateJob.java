/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import play.jobs.Job;

import com.emc.vipr.client.core.Keystore;
import com.emc.vipr.model.keystore.KeyAndCertificateChain;

public class UpdateCertificateJob extends Job {
    private final Keystore api;
    private final KeyAndCertificateChain keyAndCert;

    public UpdateCertificateJob(Keystore api, KeyAndCertificateChain keyAndCert) {
        this.api = api;
        this.keyAndCert = keyAndCert;
    }

    @Override
    public void doJob() {
        api.setKeyAndCertificateChain(keyAndCert);
    }
}
