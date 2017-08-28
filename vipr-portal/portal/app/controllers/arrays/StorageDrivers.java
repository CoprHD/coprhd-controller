/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
import util.datatable.DataTablesSupport;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class StorageDrivers extends ViprResourceController {

    protected static final String UNKNOWN = "disasterRecovery.unknown";
    protected static final String DELETED_SUCCESS = "storageDrivers.delete.init.success";
    protected static final String SAVED = "SMISProviders.saved";

    protected static final String INTALL_INIT_SUCCESS = "storageDrivers.install.init.success";
    protected static final String UPGRADE_INIT_SUCCESS = "storageDrivers.upgrade.init.success";

    public static void create() {
        render("@install");
    }

    @FlashException
    public static void delete(String driverName) {
        StorageDriverUtils.uninstallDriver(driverName);
        flash.success(MessagesUtils.get(DELETED_SUCCESS));
        list();
    }

    // placeholder method, show upgrade page
    public static void upgrade(String driverName) {
        render("@upgrade", driverName);
    }

    public static void list() {
        StorageDriverDataTable dataTable = new StorageDriverDataTable();
        render(dataTable);
    }

    public static void listJson() {
        List<StorageDriverInfo> drivers = Lists.newArrayList();
        for (StorageDriverRestRep driver : StorageDriverUtils.getDrivers().getDrivers()) {
            drivers.add(new StorageDriverInfo(driver));
        }
        renderJSON(DataTablesSupport.createJSON(drivers, params));
    }

    @FlashException
    public static void saveDriver(File driverFile) throws IOException {
        if (driverFile == null) {
            flash.error("Error: please specify a driver jar file");
            create();
        }
        StorageDriverUtils.installDriver(driverFile);
        flash.success(MessagesUtils.get(INTALL_INIT_SUCCESS));
        list();
    }

    @FlashException
    public static void upgradeDriver(File driverFile, String driverName, boolean force) {
        if (driverFile == null) {
            flash.error("Error: please specify a driver jar file");
            upgrade(driverName);
        }
        StorageDriverUtils.upgradeDriver(driverFile, driverName, force);
        flash.success(MessagesUtils.get(UPGRADE_INIT_SUCCESS));
        list();
    }

    public static void itemsJson(@As(",") String[] ids) {
        List<String> uuids = Arrays.asList(ids);
        itemsJson(uuids);
    }

    private static void itemsJson(List<String> names) {
        List<StorageDriverRestRep> drivers = new ArrayList<StorageDriverRestRep>();
        for (String name : names) {
            StorageDriverRestRep driver = StorageDriverUtils.getDriver(name);
            if (driver != null) {
                drivers.add(driver);
            }
        }
        performItemsJson(drivers, new JsonItemOperation());
    }

    static class JsonItemOperation implements ResourceValueOperation<StorageDriverInfo, StorageDriverRestRep> {
        @Override
        public StorageDriverInfo performOperation(StorageDriverRestRep driver) throws Exception {
            return new StorageDriverInfo(driver);
        }
    }
}