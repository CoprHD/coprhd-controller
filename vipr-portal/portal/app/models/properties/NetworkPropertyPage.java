/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Map;

public class NetworkPropertyPage extends CustomPropertyPage {

    private Property dnsServers;
    private Property ntpServers;
    private Property virtualIp;

    public NetworkPropertyPage(Map<String, Property> properties) {
        super("Network");
        setRenderTemplate("networkPage.html");
        dnsServers = addCustomProperty(properties, "network_nameservers");
        ntpServers = addCustomProperty(properties, "network_ntpservers");
        virtualIp = addCustomProperty(properties, "network_vip");
    }

    public Property getDnsServers() {
        return dnsServers;
    }

    public Property getNtpServers() {
        return ntpServers;
    }

    public Property getVirtualIp() {
        return virtualIp;
    }
}
