/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Map;
import util.ConfigProperty;
import util.StringOption;

public class SyslogPropertiesPage extends CustomPropertyPage {
    private Property externalLocationUrl;
    private Property maxManualCopies;
    private StringOption logOptions;

    public SyslogPropertiesPage(Map<String, Property> properties) {
        super("Syslog");
        setRenderTemplate("syslogPage.html");
        externalLocationUrl = addCustomProperty(properties, ConfigProperty.BACKUP_EXTERNAL_URL);
        maxManualCopies = addCustomProperty(properties, ConfigProperty.BACKUP_MAX_MANUAL_COPIES);
    }

    public Property getExternalLocationUrl() {
        return externalLocationUrl;
    }
    public Property getMaxManualCopies() {
        return maxManualCopies;
    }
    public Property getLogOptions() {
        return logOptions;
    }
}