/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Map;
import util.ConfigProperty;

public class SyslogPropertiesPage extends CustomPropertyPage {
    private Property syslogRemoteServersPorts;
    private Property syslogEnable;
    private Property syslogTransportProtocol;
    private Property syslogDriverCa;

    public SyslogPropertiesPage(Map<String, Property> properties) {
        super("Syslog Forwarder");
        setRenderTemplate("syslogPage.html");
        syslogRemoteServersPorts = addCustomProperty(properties, ConfigProperty.SYSLOG_REMOTE_SERVERS_PORTS);
        syslogEnable = addCustomProperty(properties, ConfigProperty.SYSLOG_ENABLE);
        syslogDriverCa = addCustomProperty(properties, ConfigProperty.SYSLOG_DRIVER_CA);
        syslogTransportProtocol = addCustomProperty(properties, ConfigProperty.SYSLOG_TRANSPORT_PROTOCOL);
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
