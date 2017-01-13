/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import util.ConfigProperty;

import java.util.Map;

public class LogRententionPropertyPage extends CustomPropertyPage {
    private Property logRetentionDays;

    public LogRententionPropertyPage(Map<String, Property> properties) {
        super("Log Retention");
        setRenderTemplate("logRetentionPage.html");
        logRetentionDays = addCustomProperty(properties, ConfigProperty.SYSTEM_LOG_RETENTION_DAYS);
    }

    public Property getLogRetentionDays() {
        return logRetentionDays;
    }
}
