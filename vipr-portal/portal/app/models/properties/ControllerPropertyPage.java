/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.properties;

import java.util.Map;

public class ControllerPropertyPage extends CustomPropertyPage {
    private Property meteringEnabled;
    private Property meteringInterval;
    private Property monitoringEnabled;

    public ControllerPropertyPage(Map<String, Property> properties) {
        super("Controller");
        setRenderTemplate("controllerPage.html");
        meteringEnabled = addCustomProperty(properties, "controller_enable_metering");
        meteringInterval = addCustomProperty(properties, "controller_metering_interval");
        monitoringEnabled = addCustomProperty(properties, "controller_enable_monitoring");
    }

    public Property getMeteringEnabled() {
        return meteringEnabled;
    }

    public Property getMeteringInterval() {
        return meteringInterval;
    }

    public Property getMonitoringEnabled() {
        return monitoringEnabled;
    }
}
