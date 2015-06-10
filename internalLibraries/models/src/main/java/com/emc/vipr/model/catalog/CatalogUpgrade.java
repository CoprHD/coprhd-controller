/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "catalog_upgrade")
public class CatalogUpgrade {
    
    private boolean upgradeAvailable = Boolean.FALSE;

    @XmlElement(name = "upgrade_available")
    public boolean getUpgradeAvailable() {
        return upgradeAvailable;
    }

    public void setUpgradeAvailable(boolean upgradeAvailable) {
        this.upgradeAvailable = upgradeAvailable;
    }

}
