/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import com.emc.vipr.client.core.Truststore;
import com.emc.vipr.model.keystore.TruststoreSettingsChanges;
import play.jobs.Job;

public class SaveCertificateSettingsJob extends Job {
    private Truststore api;
    private TruststoreSettingsChanges certificateSettings;

    public SaveCertificateSettingsJob(Truststore api, TruststoreSettingsChanges certificateSettings) {
        this.api = api;
        this.certificateSettings = certificateSettings;
    }

    @Override
    public void doJob() {
        api.updateTruststoreSettings(certificateSettings);
    }
}
