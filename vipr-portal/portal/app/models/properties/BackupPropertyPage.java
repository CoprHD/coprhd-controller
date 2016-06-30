/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import util.ConfigProperty;

import java.util.Map;

public class BackupPropertyPage extends CustomPropertyPage {
    private Property externalLocationUrl;
    private Property externalLocationType;
    private Property externalLocationDomain;
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
        externalLocationType = addCustomProperty(properties,ConfigProperty.BACKUP_EXTERNAL_TYPE);
        externalLocationUrl = addCustomProperty(properties, ConfigProperty.BACKUP_EXTERNAL_URL);
        externalLocationDomain = addCustomProperty(properties,ConfigProperty.BACKUP_EXTERNAL_DOMAIN);
        externalLocationUsername = addCustomProperty(properties, ConfigProperty.BACKUP_EXTERNAL_USERNAME);
        externalLocationPassword = addCustomPasswordProperty(properties, ConfigProperty.BACKUP_EXTERNAL_PWD);
        schedulerEnabled = addCustomBooleanProperty(properties, ConfigProperty.BACKUP_SCHEDULER_ENABLE);
        schedulerTime = addCustomProperty(properties, ConfigProperty.BACKUP_SCHEDULER_TIME);
        schedulerInterval = addCustomProperty(properties, ConfigProperty.BACKUP_SCHEDULER_INTERVAL);
        copiesToKeep = addCustomProperty(properties, ConfigProperty.BACKUP_SCHEDULER_COPIES);
        maxManualCopies = addCustomProperty(properties, ConfigProperty.BACKUP_MAX_MANUAL_COPIES);
    }

    public Property getExternalLocationUrl() {
        return externalLocationUrl;
    }

    public Property getExternalLocationType() {
        return externalLocationType;
    }

    public Property getExternalLocationDomain() {
        return externalLocationDomain;
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