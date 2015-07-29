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

    public BackupPropertyPage(Map<String, Property> properties) {
        super("Backup");
        setRenderTemplate("backupPage.html");
        externalLocationUrl = addCustomProperty(properties, "backup_external_location_url");
        externalLocationUsername = addCustomProperty(properties, "backup_external_location_username");
        externalLocationPassword = addCustomPasswordProperty(properties, "backup_external_location_password");
        schedulerEnabled = addCustomBooleanProperty(properties, "backup_scheduler_enable");
        schedulerTime = addCustomProperty(properties, "backup_scheduler_time");
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
}