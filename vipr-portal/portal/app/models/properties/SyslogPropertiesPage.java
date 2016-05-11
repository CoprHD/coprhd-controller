/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Map;

public class SyslogPropertiesPage extends CustomPropertyPage {

    public SyslogPropertiesPage(Map<String, Property> properties) {
        super("Syslog");
        setRenderTemplate("syslogPage.html");

    }
}