/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static controllers.Common.flashException;

import java.net.URI;
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
import util.BourneUtil;
import util.MessagesUtils;
import util.MobilityGroupSupportUtil;
import util.StringOption;
import util.datatable.DataTable;
import util.datatable.DataTablesSupport;

import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.exceptions.ViPRException;
import com.google.common.collect.Lists;

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
        renderArgs.put("migrationGroupBys", StringOption.options(GROUP_BY, false));
        render();
    }

    public static void cancel() {
        list();
    }

    public static void mobilityGroupResourcesTable(String id) {
        List<controllers.arrays.MobilityGroups.MobilityGroupResourcesTable.MobilityGroupResource> mobilityGroupResourcesTable = getTable(
                uri(id)).fetch();
        renderJSON(DataTablesSupport.createJSON(mobilityGroupResourcesTable, params));
    }

    public static void edit(String id) {
        VolumeGroupRestRep mobilityGroup = MobilityGroupSupportUtil.getMobilityGroup(id);
        if (mobilityGroup != null) {
            MobilityGroupForm mobilityGroupForm = new MobilityGroupForm(mobilityGroup);
            renderArgs.put("migrationTypes", StringOption.options(MIGRATION_TYPE));
            renderArgs.put("migrationGroupBys", StringOption.options(GROUP_BY, false));
            renderArgs.put("mobilityGroupResourcesTable", getTable(mobilityGroup.getId()));
            edit(mobilityGroupForm);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    private static MobilityGroupResourcesTable getTable(URI uri) {
        VolumeGroupRestRep mobilityGroup = MobilityGroupSupportUtil.getMobilityGroup(uri.toString());
        if (mobilityGroup.getMigrationGroupBy().equalsIgnoreCase(VolumeGroup.MigrationGroupBy.VOLUMES.name())) {
            return new MobilityGroupVolumesResourcesTable(mobilityGroup.getId());
        } else if (mobilityGroup.getMigrationGroupBy().equalsIgnoreCase(VolumeGroup.MigrationGroupBy.HOSTS.name())) {
            return new MobilityGroupHostsResourcesTable(mobilityGroup.getId());
        } else if (mobilityGroup.getMigrationGroupBy().equalsIgnoreCase(VolumeGroup.MigrationGroupBy.CLUSTERS.name())) {
            return new MobilityGroupClustersResourcesTable(mobilityGroup.getId());
        } else {
            return null;
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

    public static class MobilityGroupVolumesResourcesTable extends MobilityGroupResourcesTable {
        public MobilityGroupVolumesResourcesTable(URI id) {
            super(id);
            addColumn("name").setRenderFunction("renderVolumes");
            addColumn("virtualArray");
            addColumn("virtualPool");
        }

        @Override
        public List<MobilityGroupResource> fetch() {

            List<MobilityGroupResource> resources = Lists.newArrayList();
            List<NamedRelatedResourceRep> relatedReps = Lists.newArrayList();

            relatedReps = BourneUtil.getViprClient().application().getVolumes(this.id);

            for (NamedRelatedResourceRep resource : relatedReps) {
                resources.add(new MobilityGroupResource(resource.getId(), resource.getName()));
            }
            return resources;
        }

        public static class Volume extends MobilityGroupResource {
            public String type;
            public String virtualArray;
            public String virtualPool;

            public Volume(URI uri, String name) {
                super(uri, name);
                VolumeRestRep volume = BourneUtil.getViprClient().blockVolumes().get(uri);
                URI varray = volume.getVirtualArray().getId();
                URI vpool = volume.getVirtualPool().getId();
                virtualArray = BourneUtil.getViprClient().varrays().get(varray).getName();
                virtualPool = BourneUtil.getViprClient().blockVpools().get(vpool).getName();
            }
        }
    }

    public static class MobilityGroupHostsResourcesTable extends MobilityGroupResourcesTable {
        public MobilityGroupHostsResourcesTable(URI id) {
            super(id);
            addColumn("name").setRenderFunction("renderVolumes");
            addColumn("hostname");
            addColumn("type");
        }

        @Override
        public List<MobilityGroupResource> fetch() {

            List<NamedRelatedResourceRep> relatedReps = BourneUtil.getViprClient().application().getHosts(this.id);

            for (NamedRelatedResourceRep resource : relatedReps) {
                resources.add(new Host(resource.getId(), resource.getName()));
            }
            return resources;
        }

        public static class Host extends MobilityGroupResource {
            public String type;
            public String hostname;

            public Host(URI uri, String name) {
                super(uri, name);
                HostRestRep host = BourneUtil.getViprClient().hosts().get(uri);
                type = host.getType();
                hostname = host.getHostName();
            }
        }

    }

    public static class MobilityGroupClustersResourcesTable extends MobilityGroupResourcesTable {
        public MobilityGroupClustersResourcesTable(URI id) {
            super(id);
            addColumn("name").setRenderFunction("renderVolumes");
            addColumn("tenant");
        }

        @Override
        public List<MobilityGroupResource> fetch() {

            List<NamedRelatedResourceRep> relatedReps = BourneUtil.getViprClient().application().getClusters(this.id);

            for (NamedRelatedResourceRep resource : relatedReps) {
                resources.add(new Cluster(resource.getId(), resource.getName()));
            }
            return resources;
        }

        public static class Cluster extends MobilityGroupResource {
            public String tenant;

            public Cluster(URI uri, String name) {
                super(uri, name);
                URI tenantId = BourneUtil.getViprClient().clusters().get(uri).getTenant().getId();
                tenant = BourneUtil.getViprClient().tenants().get(tenantId).getName();
            }
        }
    }

    public abstract static class MobilityGroupResourcesTable extends DataTable {
        protected final URI id;
        protected List<MobilityGroupResource> resources = Lists.newArrayList();

        public MobilityGroupResourcesTable(URI id) {
            this.id = id;
            sortAll();
            setDefaultSort("name", "asc");
        }

        public abstract List<MobilityGroupResource> fetch();

        public static class MobilityGroupResource {
            public URI id;
            public String name;

            public MobilityGroupResource(URI uri, String name) {
                this.id = uri;
                this.name = name;
            }
        }
    }

}
