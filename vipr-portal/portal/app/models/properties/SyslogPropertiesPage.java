/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Map;
import util.ConfigProperty;

public class SyslogPropertiesPage extends CustomPropertyPage {
    private Property logRetentionDays;
    private Property syslogRemoteServersPorts;
    private Property syslogEnable;
    private Property syslogTransportProtocol;
    private Property syslogDriverCa;

    public SyslogPropertiesPage(Map<String, Property> properties) {
        super("Log");
        setRenderTemplate("syslogPage.html");
        logRetentionDays = addCustomProperty(properties, ConfigProperty.SYSTEM_LOG_RETENTION_DAYS);
        syslogRemoteServersPorts = addCustomProperty(properties, ConfigProperty.SYSLOG_REMOTE_SERVERS_PORTS);
        syslogEnable = addCustomProperty(properties, ConfigProperty.SYSLOG_ENABLE);
        syslogDriverCa = addCustomProperty(properties, ConfigProperty.SYSLOG_DRIVER_CA);
        syslogTransportProtocol = addCustomProperty(properties, ConfigProperty.SYSLOG_TRANSPORT_PROTOCOL);
    }

    public Property getLogRetentionDays() {
        return logRetentionDays;
    }

    public Property getSyslogRemoteServersPorts() {
        return syslogRemoteServersPorts;
    }

    public Property getSyslogEnable() {
        return syslogEnable;
    }

    public Property getSyslogTransportProtocol() {
        return syslogTransportProtocol;
    }

    public Property getSyslogDriverCa() {
        return syslogDriverCa;
    }
}
