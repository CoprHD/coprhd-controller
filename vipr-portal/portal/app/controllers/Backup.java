/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import static controllers.Common.flashException;

import java.util.List;

import models.datatable.BackupDataTable;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.Required;
import play.data.validation.Valid;
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

    protected static final String SAVED_SUCCESS = "backup.save.success";
    protected static final String DELETED_SUCCESS = "backup.delete.success";
    protected static final String DELETED_ERROR = "backup.delete.error";

    public static void list() {
        BackupDataTable dataTable = new BackupDataTable();
        render(dataTable);
    }

    public static void listJson() {
        List<BackupDataTable.Backup> backups = BackupDataTable.fetch();
        renderJSON(DataTablesSupport.createJSON(backups, params));
    }

    public static void itemsJson(@As(",") String[] ids) {
    	  List<BackupDataTable.Backup> results = Lists.newArrayList();
          if (ids != null && ids.length > 0) {
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
    
    public static void create() {
        render();
    }

    public static void cancel() {
        list();
    }

    @FlashException(keep = true, referrer = { "create" })
    public static void save(@Valid BackupForm backupForm) {
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
        list();
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
        list();
    }

    @FlashException(value = "list")
    public static void upload(String id) {
            BackupUtils.uploadBackup(id);
            list();
    }
    
    public static void getUploadStatus(String id) {
            BackupUploadStatus status = BackupUtils.getUploadStatus(id);
            renderJSON(status);
        
    }
    
    private static void backToReferrer() {
        String referrer = Common.getReferrer();
        if (StringUtils.isNotBlank(referrer)) {
            redirect(referrer);
        } else {
            list();
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

}
