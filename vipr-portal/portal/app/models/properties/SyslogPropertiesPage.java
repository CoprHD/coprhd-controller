/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Map;
import util.ConfigProperty;

public class SyslogPropertiesPage extends CustomPropertyPage {
    private Property syslogRemoteServersPorts;
    private Property syslogServices;
    //private StringOption logOptions;

    public SyslogPropertiesPage(Map<String, Property> properties) {
        super("Syslog Forwarder");
        setRenderTemplate("syslogPage.html");
        syslogRemoteServersPorts = addCustomProperty(properties, ConfigProperty.SYSLOG_REMOTE_SERVERS_PORTS);
        syslogServices = addCustomProperty(properties, ConfigProperty.SYSLOG_SERVICES);
    }

    public Property getSyslogRemoteServersPorts() {
        return syslogRemoteServersPorts;
    }
    public Property getSyslogServices() {
        return syslogServices;
    }
    //public StringOption getLogOptions() {
    //    return logOptions;
    //}
}