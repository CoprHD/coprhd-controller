/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Map;

public class DiscoveryPropertyPage extends CustomPropertyPage {
    private Property discoveryEnabled;
    private Property arrayDiscoveryInterval;
    private Property fabricDiscoveryInterval;
    private Property computeSystemDiscoveryInterval;
    private Property scanEnabled;
    private Property scanInterval;
    private Property cimConnectionTTL;

    public DiscoveryPropertyPage(Map<String, Property> properties) {
        super("Discovery");
        setRenderTemplate("discoveryPage.html");
        discoveryEnabled = addCustomProperty(properties, "controller_enable_autodiscovery");
        arrayDiscoveryInterval = addCustomProperty(properties, "controller_discovery_interval");
        fabricDiscoveryInterval = addCustomProperty(properties, "controller_ns_discovery_interval");
        computeSystemDiscoveryInterval = addCustomProperty(properties, "controller_cs_discovery_interval");
        scanEnabled = addCustomProperty(properties, "controller_enable_autoscan");
        scanInterval = addCustomProperty(properties, "controller_scan_interval");
        cimConnectionTTL = addCustomProperty(properties, "cim_connection_max_inactive_time");
    }

    public Property getDiscoveryEnabled() {
        return discoveryEnabled;
    }

    public Property getArrayDiscoveryInterval() {
        return arrayDiscoveryInterval;
    }

    public Property getFabricDiscoveryInterval() {
        return fabricDiscoveryInterval;
    }

    public Property getComputeSystemDiscoveryInterval() {
        return computeSystemDiscoveryInterval;
    }

    public Property getScanEnabled() {
        return scanEnabled;
    }

    public Property getScanInterval() {
        return scanInterval;
    }

    public Property getCimConnectionTTL() {
        return cimConnectionTTL;
    }
}
