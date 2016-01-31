/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static controllers.Common.flashException;
import static util.BourneUtil.getViprClient;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.datatable.MobilityGroupSupportDataTable;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.MessagesUtils;
import util.MobilityGroupSupportUtil;
import util.StringOption;
import util.datatable.DataTablesSupport;

import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.client.exceptions.ViPRException;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class MobilityGroups extends ViprResourceController {

    protected static final String SAVED_SUCCESS = "mobilityGroup.save.success";
    protected static final String UNKNOWN = "MobilityGroups.unknown";
    protected static final Set<String> ROLE = new HashSet(Arrays.asList(new String[] { "MOBILITY" }));
    protected static final String[] GROUP_BY = { VolumeGroup.MigrationGroupBy.VOLUMES.name(), VolumeGroup.MigrationGroupBy.HOSTS.name(),
            VolumeGroup.MigrationGroupBy.CLUSTERS.name() };
    protected static final String[] MIGRATION_TYPE = { VolumeGroup.MigrationType.VPLEX.name() };

    public static void list() {
        MobilityGroupSupportDataTable dataTable = new MobilityGroupSupportDataTable();
        render(dataTable);
    }

    public static void listJson() {
        List<MobilityGroupSupportDataTable.MobilityGroupSupport> mobilityGroups = MobilityGroupSupportDataTable.fetch();
        renderJSON(DataTablesSupport.createJSON(mobilityGroups, params));
    }

    public static void create() {
        renderArgs.put("migrationTypes", StringOption.options(MIGRATION_TYPE));

        List<StorageSystemRestRep> sourceStorageSystems = getViprClient().storageSystems().getAll();
        renderArgs.put("sourceStorageSystems", sourceStorageSystems);

        renderArgs.put("migrationGroupBys", StringOption.options(GROUP_BY, false));

        List<BlockVirtualPoolRestRep> sourceVirtualPools = getViprClient().blockVpools().getAll();
        renderArgs.put("sourceVirtualPools", sourceVirtualPools);

        render();
    }

    public static void cancel() {
        list();
    }

    public static void edit(String id) {
        VolumeGroupRestRep mobilityGroup = MobilityGroupSupportUtil.getMobilityGroup(id);
        if (mobilityGroup != null) {
            MobilityGroupForm mobilityGroupForm = new MobilityGroupForm(mobilityGroup);
            renderArgs.put("migrationTypes", StringOption.options(MIGRATION_TYPE));
            renderArgs.put("migrationGroupBys", StringOption.options(GROUP_BY, false));
            edit(mobilityGroupForm);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    private static void edit(MobilityGroupForm mobilityGroupForm) {
        render("@edit", mobilityGroupForm);
    }

    public static void delete(@As(",") String[] ids) {
        try {
            if (ids != null && ids.length > 0) {
                boolean deleteExecuted = false;
                for (String mobilityGroup : ids) {
                    MobilityGroupSupportUtil.deleteMobilityGroup(uri(mobilityGroup));
                    deleteExecuted = true;
                }
                if (deleteExecuted == true) {
                    flash.success(MessagesUtils.get("mobilityGroups.deleted"));
                }
            }
        } catch (ViPRException e) {
            flashException(e);
        }
        list();
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(MobilityGroupForm mobilityGroupForm) {
        mobilityGroupForm.validate("mobilityGroupForm");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        try {
            mobilityGroupForm.save();
            flash.success(MessagesUtils.get(SAVED_SUCCESS, mobilityGroupForm.name));
            backToReferrer();
        } catch (ViPRException e) {
            flashException(e);
            error(mobilityGroupForm);
        }
    }

    private static void backToReferrer() {
        String referrer = Common.getReferrer();
        if (StringUtils.isNotBlank(referrer)) {
            redirect(referrer);
        } else {
            list();
        }
    }

    private static void error(MobilityGroupForm mobilityGroupForm) {
        params.flash();
        Validation.keep();
        edit(mobilityGroupForm);
    }

    public static class MobilityGroupForm {

        public String id;
        @MaxSize(128)
        @MinSize(2)
        @Required
        public String name;

        public String description;

        public String migrationType;

        public String migrationGroupBy;

        public Set<String> roles;

        public MobilityGroupForm(VolumeGroupRestRep applicationForm) {
            this.id = applicationForm.getId().toString();
            this.name = applicationForm.getName();
            this.description = applicationForm.getDescription();
            this.roles = applicationForm.getRoles();
            this.migrationType = applicationForm.getMigrationType();
            this.migrationGroupBy = applicationForm.getMigrationGroupBy();
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String fieldname) {
            Validation.valid(fieldname, this);
        }

        public void save() throws ViPRException {
            if (isNew()) {
                MobilityGroupSupportUtil.createMobilityGroup(name, description, ROLE,
                        migrationGroupBy, migrationType);
            } else {
                VolumeGroupRestRep oldApplication = MobilityGroupSupportUtil.getMobilityGroup(id);
                this.migrationGroupBy = oldApplication.getMigrationGroupBy();

                if (oldApplication.getName().equals(name)) {
                    this.name = "";
                }
                if (oldApplication.getDescription().equals(description)) {
                    this.description = "";
                }

                MobilityGroupSupportUtil.updateMobilityGroup(name, description, id);
            }
        }
    }
}
