/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static controllers.Common.flashException;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.MigrationRestRep;
import com.emc.storageos.model.event.EventRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import models.datatable.EventsDataTable;
import models.datatable.StorageGroupsDataTable;
import play.data.binding.As;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.EventUtils;
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
    private static final String CUTOVER_MULTIPLE = "resources.storageGroups.cutover.multiple";
    private static final String SYNC_START_MULTIPLE = "resources.storageGroups.syncstart.multiple";
    private static final String SYNC_STOP_MULTIPLE = "resources.storageGroups.syncstop.multiple";

    public static void listAll() {
        // StorageSystemSelector.addRenderArgs();

        renderArgs.put("dataTable", new StorageGroupsDataTable());

        // Common.angularRenderArgs().put("tenantId", Models.currentAdminTenant());

        render();
    }

    public static void listAllJson(URI storageSystem) {
        ViPRCoreClient client = getViprClient();

        storageSystem = URI.create("urn:storageos:StorageSystem:68d391ad-1342-41ba-adb4-c779e9a2be45:vdc1");

        List<NamedRelatedResourceRep> migrationRefs = client.storageSystems().listMigrations(storageSystem).getMigrations();

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
        List<EventsDataTable.Event> results = Lists.newArrayList();
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                if (StringUtils.isNotBlank(id)) {
                    EventRestRep event = EventUtils.getEvent(uri(id));
                    if (event != null) {
                        results.add(new EventsDataTable.Event(event));
                    }
                }
            }
        }
        renderJSON(results);
    }

    @Util
    public static MigrationSummary getEventSummary(MigrationRestRep migration) {
        return new MigrationSummary(migration);
    }

    public static void cancel(List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().cancelMigration(id);
            }
            flash.success(MessagesUtils.get(CANCEL_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void syncstart(List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().syncStartMigration(id);
            }
            flash.success(MessagesUtils.get(SYNC_START_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void syncstop(List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().syncStopMigration(id);
            }
            flash.success(MessagesUtils.get(SYNC_STOP_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void recover(String eventId) {
        try {
            if (StringUtils.isNotBlank(eventId)) {
                getViprClient().events().approve(uri(eventId));
                flash.success(MessagesUtils.get(REFRESHED_MULTIPLE, eventId));
            }
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void refresh(List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().refreshMigration(id);
            }
            flash.success(MessagesUtils.get(REFRESHED_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void cutover(List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().cutoverMigration(id);
            }
            flash.success(MessagesUtils.get(CUTOVER_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void commit(List<URI> ids) {
        try {
            for (URI id : ids) {
                getViprClient().blockConsistencyGroups().commitMigration(id);
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

            render(datastoresAffected, initiators, zonesCreated, zonesReused);
        } else {
            // error
        }
    }

    // "Suppressing Sonar violation of Field names should comply with naming convention"
    @SuppressWarnings("squid:S00116")
    private static class MigrationSummary {
        private Set<String> affectedDatastores;

        public MigrationSummary(MigrationRestRep migration) {
            this.affectedDatastores = migration.getDataStoresAffected();
        }
    }
}
