/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Map;
import util.ConfigProperty;

public class SyslogPropertiesPage extends CustomPropertyPage {
    private Property externalLocationUrl;

    public SyslogPropertiesPage(Map<String, Property> properties) {
        super("Syslog");
        setRenderTemplate("syslogPage.html");
        externalLocationUrl = addCustomProperty(properties, ConfigProperty.BACKUP_EXTERNAL_URL);
    }

    public Property getExternalLocationUrl() {
        return externalLocationUrl;
    }
}