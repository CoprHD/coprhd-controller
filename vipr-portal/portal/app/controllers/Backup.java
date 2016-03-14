/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import static controllers.Common.flashException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.vipr.model.sys.backup.BackupRestoreStatus;
import com.emc.vipr.model.sys.backup.BackupInfo;
import models.datatable.BackupDataTable;
import models.datatable.BackupDataTable.Type;

import org.apache.commons.lang.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.data.binding.As;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;
import util.BackupUtils;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

import com.emc.vipr.client.exceptions.ViPRException;
import com.emc.vipr.model.sys.backup.BackupSets.BackupSet;
import com.emc.vipr.model.sys.backup.BackupUploadStatus;
import com.google.common.collect.Lists;

import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;

/**
 * @author mridhr
 *
 */
@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class Backup extends Controller {
    private static final Logger log = LoggerFactory.getLogger(Backup.class);

    protected static final String SAVED_SUCCESS = "backup.save.success";
    protected static final String DELETED_SUCCESS = "backup.delete.success";
    protected static final String DELETED_ERROR = "backup.delete.error";

    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SYSTEM_MONITOR"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
    public static void list(Type type) {
        if (type == null) {
            type = Type.LOCAL;
        }

        BackupDataTable dataTable = new BackupDataTable(type);
        renderArgs.put("type", type);
        render(dataTable);
    }

    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SYSTEM_MONITOR"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
    public static void listJson(Type type) {
        List<BackupDataTable.Backup> backups = BackupDataTable.fetch(type == null ? Type.LOCAL : type);
        renderJSON(DataTablesSupport.createJSON(backups, params));
    }

    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SYSTEM_MONITOR"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
    public static void itemsJson(@As(",") String[] ids) {
        List<BackupDataTable.Backup> results = Lists.newArrayList();
        if (ids != null) {
            for (String id : ids) {
                if (StringUtils.isNotBlank(id)) {
                    BackupSet backup = BackupUtils.getBackup(id);
                    if (backup != null) {
                        results.add(new BackupDataTable.Backup(backup));
                    }
                }
            }
        }
        renderJSON(results);
    }

    public static void externalItemsJson(@As(",") String[] ids) {
        List<BackupDataTable.Backup> results = Lists.newArrayList();
        if (ids != null) {
            for (String id : ids) {
                if (StringUtils.isNotBlank(id)) {
                    BackupInfo backupInfo = BackupUtils.getExternalBackup(id);
                    BackupDataTable.Backup backup = new BackupDataTable.Backup(id);
                    if (backupInfo.getCreateTime() != 0) {
                        backup.creationtime = backupInfo.getCreateTime();
                    }
                    backup.status = backupInfo.getRestoreStatus().getStatus().name();
                    results.add(backup);
                }
            }
        }
        renderJSON(results);
    }
    
    public static void externalStatusJson(@As(",") String[] ids) {
        List<BackupDataTable.Backup> results = Lists.newArrayList();
        for (String id : ids) {
            if (StringUtils.isNotBlank(id)) {
                BackupDataTable.Backup backup = new BackupDataTable.Backup(id);
                backup.status = BackupUtils.getRestoreStatus(id, false).getStatus().name();
                results.add(backup);
            }

        }
        renderJSON(results);
    }

    public static void create() {
        render();
    }

    public static void cancel() {
        list(Type.LOCAL);
    }

    @FlashException(keep = true, referrer = { "create" })
    public static void save() {
        BackupForm backupForm = new BackupForm();
        backupForm.name = params.get("backupForm.name");
        backupForm.force = params.get("backupForm.force", boolean.class);
        backupForm.validate("name");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        try {
            backupForm.save();
            flash.success(MessagesUtils.get(SAVED_SUCCESS, backupForm.name));
            backToReferrer();
        } catch (ViPRException e) {
            flashException(e);
            error(backupForm);
        }
    }

    public static void edit(String id) {
        list(Type.LOCAL);
    }

    @FlashException(value = "list")
    public static void delete(@As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            boolean deleteExecuted = false;
            for (String backupName : ids) {
                BackupUtils.deleteBackup(backupName);
                deleteExecuted = true;
            }
            if (deleteExecuted == true) {
                flash.success(MessagesUtils.get("backups.deleted"));
            }
        }
        list(Type.LOCAL);
    }

    @FlashException(value = "list")
    public static void upload(String id) {
        BackupUtils.uploadBackup(id);
        list(Type.LOCAL);
    }

    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SYSTEM_MONITOR"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
    public static void getUploadStatus(String id) {
        BackupUploadStatus status = BackupUtils.getUploadStatus(id);
        renderJSON(status);

    }

    public static void restore(String id, Type type) {
        if (type == Type.REMOTE) { // pull first if remote backup set
            BackupUtils.pullBackup(id);
        }

        BackupRestoreStatus status = BackupUtils.getRestoreStatus(id, type == Type.LOCAL);
        renderArgs.put("status", status);
        renderArgs.put("percentages", new RestoreStatus(status).percentageMap);
        renderArgs.put("id", id);
        renderArgs.put("type", type);

        render();
    }

    public static void cancelPullBackup(Type type) {
        if (type == Type.REMOTE) {
            BackupUtils.cancelPullBackup();
        }
        list(type);
    }

    public static void doRestore() {
        Type type = params.get("restoreForm.type", Type.class);
        boolean isLocal = type == Type.LOCAL;
        String name = params.get("restoreForm.name");
        RestoreForm restoreForm = new RestoreForm(name, params.get("restoreForm.password"), isLocal, params.get("restoreForm.isGeoFromScratch", boolean.class));
        restoreForm.restore();

        BackupRestoreStatus status = BackupUtils.getRestoreStatus(name, isLocal);
        if (status.isNotSuccess()) {
            list(type);
        }
        Maintenance.maintenance(Common.reverseRoute(Backup.class, "list", "type", type));
    }

    public static void getRestoreStatus(String id, Type type) {
        BackupRestoreStatus status = BackupUtils.getRestoreStatus(id, type == Type.LOCAL);
        renderJSON(new RestoreStatus(status));
    }

    private static void backToReferrer() {
        String referrer = Common.getReferrer();
        if (StringUtils.isNotBlank(referrer)) {
            redirect(referrer);
        } else {
            list(Type.LOCAL);
        }
    }

    /**
     * Handles an error while saving a backup form.
     * 
     * @param backupForm
     *            the backup form.
     */
    private static void error(BackupForm backupForm) {
        params.flash();
        Validation.keep();
        create();
    }

    public static class BackupForm {

        @Required
        public String name;

        public boolean force;

        public void validate(String fieldname) {
            Validation.valid(fieldname, this);
        }

        public void save() throws ViPRException {
            BackupUtils.createBackup(name, force);
        }
    }

    public static class RestoreForm {

        @Required
        public String name;

        @Required
        public String password;

        public boolean isLocal;

        public boolean isGeoFromScratch = false;

        public RestoreForm(String name, String password, boolean isLocal, boolean isGeo) {
            this.name = name;
            this.password = password;
            this.isLocal = isLocal;
            this.isGeoFromScratch = isGeo;
        }

        public void restore() throws ViPRException {
            BackupUtils.restore(name, StringUtils.trimToNull(password), isLocal, isGeoFromScratch);
        }
    }

    public static class RestoreStatus {
        private String backupName;
        private BackupRestoreStatus.Status status;
        private boolean isGeo;
        private Map<String, Long> sizeToDownload;
        private Map<String, Long> downloadedSize;
        private Map<String, Integer> percentageMap;
        private String details;

        public RestoreStatus(BackupRestoreStatus origin) {
            this.backupName = origin.getBackupName();
            this.sizeToDownload = origin.getSizeToDownload();
            this.downloadedSize = origin.getDownloadedSize();
            this.status = origin.getStatus();
            this.isGeo = origin.isGeo();
            this.details = origin.getDetails();

            percentageMap = new HashMap<String, Integer>(sizeToDownload.size());
            for (String hostname : sizeToDownload.keySet()) {
                int percentage = sizeToDownload.get(hostname) == 0L ? 0
                        : (int) (downloadedSize.get(hostname) * 100 / sizeToDownload.get(hostname));
                percentageMap.put(hostname, percentage);
            }
        }
    }
}
