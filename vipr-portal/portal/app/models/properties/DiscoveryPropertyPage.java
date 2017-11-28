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
    private Property vblockComputeSystemDiscoveryInterval;
    private Property scanEnabled;
    private Property scanInterval;
    private Property arrayAffinityDiscoveryEnabled;
    private Property arrayAffinityDiscoveryInterval;
    private Property cimConnectionTTL;
    private Property discoveryThreads;
    private Property arrayDiscoveryRefreshInterval;
    private Property fabricDiscoveryRefreshInterval;
    private Property computeSystemDiscoveryRefreshInterval;
    private Property vblockComputeSystemDiscoveryRefreshInterval;
    private Property scanRefreshInterval;
    private Property arrayAffinityDiscoveryRefreshInterval;
    private Property timeTolerance;

    public DiscoveryPropertyPage(Map<String, Property> properties) {
        super("Discovery");
        setRenderTemplate("discoveryPage.html");
        discoveryEnabled = addCustomProperty(properties, "controller_enable_autodiscovery");
        arrayDiscoveryInterval = addCustomProperty(properties, "controller_discovery_interval");
        fabricDiscoveryInterval = addCustomProperty(properties, "controller_ns_discovery_interval");
        computeSystemDiscoveryInterval = addCustomProperty(properties, "controller_cs_discovery_interval");
        vblockComputeSystemDiscoveryInterval = addCustomProperty(properties, "controller_compute_discovery_interval");
        scanEnabled = addCustomProperty(properties, "controller_enable_autoscan");
        scanInterval = addCustomProperty(properties, "controller_scan_interval");
        arrayAffinityDiscoveryEnabled = addCustomProperty(properties, "controller_enable_arrayaffinity_discovery");
        arrayAffinityDiscoveryInterval = addCustomProperty(properties, "controller_arrayaffinity_discovery_interval");
        cimConnectionTTL = addCustomProperty(properties, "cim_connection_max_inactive_time");
        discoveryThreads = addCustomProperty(properties, "controller_discovery_core_pool_size");
        arrayDiscoveryRefreshInterval = addCustomProperty(properties, "controller_discovery_refresh_interval");
        fabricDiscoveryRefreshInterval = addCustomProperty(properties, "controller_ns_discovery_refresh_interval");
        computeSystemDiscoveryRefreshInterval = addCustomProperty(properties, "controller_cs_discovery_refresh_interval");
        vblockComputeSystemDiscoveryRefreshInterval = addCustomProperty(properties, "controller_compute_discovery_refresh_interval");
        scanRefreshInterval = addCustomProperty(properties, "controller_scan_refresh_interval");
        arrayAffinityDiscoveryRefreshInterval = addCustomProperty(properties, "controller_arrayaffinity_discovery_refresh_interval");
        timeTolerance = addCustomProperty(properties, "controller_time_tolerance");
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

    public Property getVblockComputeSystemDiscoveryInterval() {
        return vblockComputeSystemDiscoveryInterval;
    }

    public Property getScanEnabled() {
        return scanEnabled;
    }

    public Property getScanInterval() {
        return scanInterval;
    }

    public Property getArrayAffinityDiscoveryEnabled() {
        return arrayAffinityDiscoveryEnabled;
    }

    public Property getArrayAffinityDiscoveryInterval() {
        return arrayAffinityDiscoveryInterval;
    }

    public Property getCimConnectionTTL() {
        return cimConnectionTTL;
    }

    public Property getDiscoveryThreads() {
        return discoveryThreads;
    }

    public Property getArrayDiscoveryRefreshInterval() {
        return arrayDiscoveryRefreshInterval;
    }

    public Property getFabricDiscoveryRefreshInterval() {
        return fabricDiscoveryRefreshInterval;
    }

    public Property getComputeSystemDiscoveryRefreshInterval() {
        return computeSystemDiscoveryRefreshInterval;
    }

    public Property getVblockComputeSystemDiscoveryRefreshInterval() {
        return vblockComputeSystemDiscoveryRefreshInterval;
    }

    public Property getScanRefreshInterval() {
        return scanRefreshInterval;
    }

    public Property getArrayAffinityDiscoveryRefreshInterval() {
        return arrayAffinityDiscoveryRefreshInterval;
    }
   
    public Property timeTolerance() {
        return timeTolerance;
    }
}
