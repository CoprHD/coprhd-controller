/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.flashException;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.block.MigrationList;
import com.emc.storageos.model.block.MigrationRestRep;
import com.emc.storageos.model.block.NamedRelatedMigrationRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.Models;
import models.datatable.StorageGroupsDataTable;
import play.data.binding.As;
import play.mvc.Controller;
import play.mvc.With;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

/**
 * The UI controller for storage group migrations
 *
 */
@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class StorageGroups extends Controller {
    private static final String REFRESHED_MULTIPLE = "resources.storageGroups.refresh.multiple";
    private static final String CANCEL_MULTIPLE = "resources.storageGroups.cancel.multiple";
    private static final String COMMIT_MULTIPLE = "resources.storageGroups.commit.multiple";
    private static final String RECOVER_MULTIPLE = "resources.storageGroups.recover.multiple";
    private static final String CUTOVER_MULTIPLE = "resources.storageGroups.cutover.multiple";
    private static final String SYNC_START_MULTIPLE = "resources.storageGroups.syncstart.multiple";
    private static final String SYNC_STOP_MULTIPLE = "resources.storageGroups.syncstop.multiple";
    private static final String RESCAN_HOST_MULTIPLE = "resources.storageGroups.rescanhosts.multiple";
    private static final String INVENTORY_DELETE_MULTIPLE = "resources.storageGroups.inventoryDelete.multiple";

    public static void listAll() {
        StorageSystemSelector.addRenderArgs();
        renderArgs.put("dataTable", new StorageGroupsDataTable());
        renderArgs.put("currentStorageSystem", Models.currentStorageSystem());
        render();
    }

    public static void listAllJson() {
        ViPRCoreClient client = getViprClient();

        URI storageSystem = uri(Models.currentStorageSystem());

        List<NamedRelatedMigrationRep> migrationRefs = client.storageSystems().listMigrations(storageSystem).getMigrations();

        List<MigrationRestRep> migrationResourceRefs = client.blockMigrations().getByRefs(migrationRefs);

        List<StorageGroupsDataTable.StorageGroup> migrations = Lists.newArrayList();
        if (migrationResourceRefs != null) {
            for (MigrationRestRep migrationRestRep : migrationResourceRefs) {
                StorageGroupsDataTable.StorageGroup migration = new StorageGroupsDataTable.StorageGroup(migrationRestRep, client);
                migrations.add(migration);
            }
        }
        renderJSON(DataTablesSupport.createJSON(migrations, params));
    }

    public static void itemsJson(@As(",") String[] ids) {
        List<StorageGroupsDataTable.StorageGroup> results = Lists.newArrayList();
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                if (StringUtils.isNotBlank(id)) {
                    MigrationList migrations = getViprClient().blockConsistencyGroups().getConsistencyGroupMigrations(uri(id));
                    for (NamedRelatedMigrationRep migration : migrations.getMigrations()) {
                        MigrationRestRep migrationRestRep = getViprClient().blockMigrations().get(migration.getId());
                        if (migrationRestRep != null) {
                            results.add(new StorageGroupsDataTable.StorageGroup(migrationRestRep, getViprClient()));
                        }
                    }
                }
            }
        }
        renderJSON(results);
    }

    public static void cancel(@As(",") List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().migrationCancel(id, false);
            }
            flash.success(MessagesUtils.get(CANCEL_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void syncstart(@As(",") List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().migrationSyncStart(id);
            }
            flash.success(MessagesUtils.get(SYNC_START_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void syncstop(@As(",") List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().migrationSyncStop(id);
            }
            flash.success(MessagesUtils.get(SYNC_STOP_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void recover(@As(",") List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().migrationRecover(id, false);
            }
            flash.success(MessagesUtils.get(RECOVER_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void refresh(@As(",") List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().migrationRefresh(id);
            }
            flash.success(MessagesUtils.get(REFRESHED_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void cutover(@As(",") List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().migrationCutover(id);
            }
            flash.success(MessagesUtils.get(CUTOVER_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void inventorydelete(@As(",") List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().deactivate(id, VolumeDeleteTypeEnum.VIPR_ONLY);
            }
            flash.success(MessagesUtils.get(INVENTORY_DELETE_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void rescanHosts(@As(",") List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().rescanHostsForMigration(id);
            }
            flash.success(MessagesUtils.get(RESCAN_HOST_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void commit(@As(",") List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().migrationCommit(id);
            }
            flash.success(MessagesUtils.get(COMMIT_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void itemDetails(String migrationId) {
        MigrationRestRep migration = getViprClient().blockMigrations().get(uri(migrationId));

        if (migration != null) {

            Set<String> datastoresAffected = migration.getDataStoresAffected();
            Set<String> initiators = migration.getInitiators();
            Set<String> zonesCreated = migration.getZonesCreated();
            Set<String> zonesReused = migration.getZonesReused();
            Set<String> storagePorts = Sets.newHashSet();

            if (migration.getTargetStoragePorts() != null) {
                for (StoragePortRestRep port : getViprClient().storagePorts().getByIds(uris(migration.getTargetStoragePorts()))) {
                    storagePorts.add(port.getName());
                }
            }

            render(datastoresAffected, initiators, zonesCreated, zonesReused, storagePorts);
        } else {
            // error
        }
    }
}
