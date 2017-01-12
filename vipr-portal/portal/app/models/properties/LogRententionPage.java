/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import controllers.infra.ConfigProperties;
import util.ConfigProperty;

import java.util.Map;

public class LogRententionPage extends CustomPropertyPage {
    private Property logRetentionDays;

    public LogRententionPage(Map<String, Property> properties) {
        super("Log Retention");
        setRenderTemplate("logRetentionPage.html");
        logRetentionDays = addCustomProperty(properties, ConfigProperty.SYSTEM_LOG_RETENTION_DAYS);
    }

    public Property getLogRetentionDays() {
        return logRetentionDays;
    }
}
