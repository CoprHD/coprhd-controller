/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Map;

public class BackupPropertyPage extends CustomPropertyPage {
    private Property externalLocationUrl;
    private Property externalLocationUsername;
    private Property externalLocationPassword;
    private Property schedulerEnabled;
    private Property schedulerTime;
    private Property schedulerInterval;
    private Property copiesToKeep;
    private Property maxManualCopies;

    public BackupPropertyPage(Map<String, Property> properties) {
        super("Backup");
        setRenderTemplate("backupPage.html");
        externalLocationUrl = addCustomProperty(properties, "backup_external_location_url");
        externalLocationUsername = addCustomProperty(properties, "backup_external_location_username");
        externalLocationPassword = addCustomPasswordProperty(properties, "backup_external_location_password");
        schedulerEnabled = addCustomBooleanProperty(properties, "backup_scheduler_enable");
        schedulerTime = addCustomProperty(properties, "backup_scheduler_time");
        schedulerInterval = addCustomProperty(properties, "backup_scheduler_interval");
        copiesToKeep = addCustomProperty(properties, "backup_scheduler_copies_to_keep");
        maxManualCopies = addCustomProperty(properties, "backup_max_manual_copies");
    }

    public Property getExternalLocationUrl() {
        return externalLocationUrl;
    }

    public Property getExternalLocationUsername() {
        return externalLocationUsername;
    }

    public Property getExternalLocationPassword() {
        return externalLocationPassword;
    }

    public Property getSchedulerEnabled() {
        return schedulerEnabled;
    }

    public Property getSchedulerTime() {
        return schedulerTime;
    }

    public Property getSchedulerInterval() {
        return schedulerInterval;
    }

    public Property getCopiesToKeep() {
        return copiesToKeep;
    }

    public Property getMaxManualCopies() {
        return maxManualCopies;
    }
}