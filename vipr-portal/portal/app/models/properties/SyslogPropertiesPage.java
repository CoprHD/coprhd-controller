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

    private Property syslogEnable;
    private Property syslogEnableTls;
    private Property syslogTransportProtocol;
    private Property syslogEnableTlsCert;
    private Property syslogLogLevel;
    //private StringOption logOptions;

    public SyslogPropertiesPage(Map<String, Property> properties) {
        super("Syslog Forwarder");
        setRenderTemplate("syslogPage.html");
        syslogRemoteServersPorts = addCustomProperty(properties, ConfigProperty.SYSLOG_REMOTE_SERVERS_PORTS);
        syslogServices = addCustomProperty(properties, ConfigProperty.SYSLOG_SERVICES);
        syslogEnable = addCustomProperty(properties, ConfigProperty.SYSLOG_ENABLE);
        syslogEnableTls = addCustomProperty(properties, ConfigProperty.SYSLOG_ENABLE_TLS);
        syslogTransportProtocol = addCustomProperty(properties, ConfigProperty.SYSLOG_TRANSPORT_PROTOCOL);
        syslogEnableTlsCert = addCustomProperty(properties, ConfigProperty.SYSLOG_ENABLE_TLS_CERT);
        syslogLogLevel = addCustomProperty(properties, ConfigProperty.SYSLOG_LOG_LEVEL);
    }

    public Property getSyslogRemoteServersPorts() {
        return syslogRemoteServersPorts;
    }

    public Property getSyslogServices() {
        return syslogServices;
    }

    public Property getSyslogEnable() {
        return syslogEnable;
    }

    public Property getSyslogEnableTls() {
        return syslogEnableTls;
    }

    public Property getSyslogTransportProtocol() {
        return syslogTransportProtocol;
    }

    public Property getSyslogEnableTlsCert() {
        return syslogEnableTlsCert;
    }

    public Property getSyslogLogLevel() {
        return syslogLogLevel;
    }
}