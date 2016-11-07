/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.emc.storageos.model.storagedriver.StorageDriverRestRep;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;
import models.datatable.StorageDriverDataTable;
import models.datatable.StorageDriverDataTable.StorageDriverInfo;
import play.data.binding.As;
import play.mvc.With;
import util.MessagesUtils;
import util.StorageDriverUtils;
import util.StorageSystemTypeUtils;
import util.datatable.DataTablesSupport;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class StorageDrivers extends ViprResourceController {

    protected static final String UNKNOWN = "disasterRecovery.unknown";
    protected static final String DELETED_SUCCESS = "storageDrivers.delete.init.success";
    protected static final String SAVED = "SMISProviders.saved";

    protected static final String INTALL_INIT_SUCCESS = "storageDrivers.install.init.success";
    protected static final String UPGRADE_INIT_SUCCESS = "storageDrivers.upgrade.init.success";

    // show create page
    public static void create() {
        render("@upload");
    }

    // This method does nothing
    public static void delete() {
        
    }

    // placeholder method, show upgrade page
    public static void upgrade(String driverName) {
        render("@upload", driverName);
    }

    // placeholder method
    public static void remove(String driverName) {
        // TODO send request to back-end API
        flash.success(MessagesUtils.get(DELETED_SUCCESS));
        list();
    }

    public static void list() {
        StorageDriverDataTable dataTable = new StorageDriverDataTable();
        render(dataTable);
    }

    public static void listJson() {
        List<StorageDriverInfo> drivers = Lists.newArrayList();
//        StorageDriverDataTable.StorageDriverInfo driver = new StorageDriverDataTable.StorageDriverInfo();
//        driver.driverName = "foo_driver";
//        driver.driverVersion = "1.0.0.1";
//        driver.supportedStorageSystems.add("Fool System");
//        driver.supportedStorageSystems.add("Storage Provider for Fool System");
//        driver.type = "Block";
//        driver.defaultNonSslPort = "8080";
//        driver.defaultSslPort = "443";
//        driver.status = "Ready";
//        drivers.add(driver);
//        driver = new StorageDriverDataTable.StorageDriverInfo();
//        driver.driverName = "bar_driver";
//        driver.driverVersion = "2.0.0.0";
//        driver.supportedStorageSystems.add("Bar System");
//        driver.type = "File";
//        driver.defaultNonSslPort = "9000";
//        driver.defaultSslPort = "4443";
//        driver.status = "In Use";
//        drivers.add(driver);
        for (StorageDriverRestRep driver : StorageDriverUtils.getDrivers().getDrivers()) {
            drivers.add(new StorageDriverInfo(driver));
        }
        renderJSON(DataTablesSupport.createJSON(drivers, params));
    }

    public static void uploadDriver(File deviceDriverFile, String driverName) throws IOException {
        if (deviceDriverFile == null) {
            flash.error("Error: please specify a driver jar file");
            if (driverName == null || driverName.isEmpty()) {
                create();
            } else {
                upgrade(driverName);
            }
        }

        // TODO upload driver

        if (driverName == null || driverName.isEmpty()) {
            flash.success(MessagesUtils.get(INTALL_INIT_SUCCESS));
        } else {
            flash.success(MessagesUtils.get(UPGRADE_INIT_SUCCESS));
        }
        list();
    }
}