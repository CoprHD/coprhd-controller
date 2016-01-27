/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static controllers.Common.flashException;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.storageos.model.block.NamedVolumeGroupsList;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.filters.VplexVolumeFilter;
import com.emc.vipr.client.exceptions.ViPRException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

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
            VolumeGroup.MigrationGroupBy.CLUSTERS.name(), VolumeGroup.MigrationGroupBy.APPLICATIONS.name() };
    protected static final String[] MIGRATION_TYPE = { VolumeGroup.MigrationType.VPLEX.name() };

    public static void list() {
        MobilityGroupSupportDataTable dataTable = new MobilityGroupSupportDataTable();
        render(dataTable);
    }

    public static void listVirtualPoolAttributesJson(MobilityGroupForm mobilityGroup) {
        // List<StoragePoolInfo> items = Lists.newArrayList();
        List<String> items = Lists.newArrayList("ABC", "DEF");
        // if (mobilityGroup != null && mobilityGroup.sourceStorageSystem != null) {
        // // mobilityGroup.deserialize();
        // Map<URI, String> storageSystemNames = StorageSystemUtils.getStorageSystemNames();
        // List<StoragePoolRestRep> pools = getMatchingStoragePools(vpool);
        // for (StoragePoolRestRep pool : pools) {
        // String storageSystemName = storageSystemNames.get(id(pool.getStorageSystem()));
        // items.add(new StoragePoolInfo(pool, storageSystemName));
        // }
        // }
        renderJSON(DataTablesSupport.createJSON(items, params));
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

            List<StorageSystemRestRep> sourceStorageSystems = getViprClient().storageSystems().getAll();
            renderArgs.put("sourceStorageSystems", sourceStorageSystems);

            renderArgs.put("migrationGroupBys", StringOption.options(GROUP_BY, false));

            List<BlockVirtualPoolRestRep> sourceVirtualPools = getViprClient().blockVpools().getAll();
            renderArgs.put("sourceVirtualPools", sourceVirtualPools);

            if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.VOLUMES.name())) {
                // TODO based on migration type, only show VPLEX volumes
                List<URI> volumeIds = getViprClient().blockVolumes().listBulkIds();
                renderArgs.put("volumes", getViprClient().blockVolumes().getByIds(volumeIds, new VplexVolumeFilter()));
            } else if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.HOSTS.name())) {
                // TODO only show hosts connected to virtual pool
                List<URI> hostIds = getViprClient().hosts().listBulkIds();
                renderArgs.put("hosts", getViprClient().hosts().getByIds(hostIds));
            } else if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.CLUSTERS.name())) {
                // TODO only show clusters connected to virtual pool
                List<URI> clusterIds = getViprClient().clusters().listBulkIds();
                renderArgs.put("clusters", getViprClient().clusters().getByIds(clusterIds));
            } else if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.APPLICATIONS.name())) {
                renderArgs.put("applications", getViprClient().application().getApplications(new ResourceFilter<VolumeGroupRestRep>() {

                    @Override
                    public boolean acceptId(URI id) {
                        return true;
                    }

                    @Override
                    public boolean accept(VolumeGroupRestRep item) {
                        return item.getRoles() != null && item.getRoles().contains(VolumeGroup.VolumeGroupRole.COPY.name());
                    }

                }));
            }

            // Promise<List<VirtualArrayRestRep>> virtualArrays = new VirtualArraysCall().asPromise();

            // play.libs.F.Promise<List<VolumeRestRep>> sourceVolumes = new VolumesCall(mobilityGroup.getId()).asPromise();
            // List<NamedRelatedResourceRep> sourceVolumes =
            // getViprClient().application().getVolumes(mobilityGroup.getId());
            // renderArgs.put("sourceVolumes", sourceVolumes);
            // addDataObjectOptions("sourceVolumes", sourceVolumes);

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

        public URI sourceStorageSystem;

        public URI sourceVirtualPool;

        public String migrationType;

        public String migrationGroupBy;

        public Set<String> roles;

        public List<URI> volumes;

        public List<URI> hosts;

        public List<URI> clusters;

        public List<URI> applications;

        public MobilityGroupForm(VolumeGroupRestRep applicationForm) {
            this.id = applicationForm.getId().toString();
            this.name = applicationForm.getName();
            this.description = applicationForm.getDescription();
            this.roles = applicationForm.getRoles();
            this.sourceStorageSystem = applicationForm.getSourceStorageSystem();
            this.sourceVirtualPool = applicationForm.getSourceVirtualPool();
            this.migrationType = applicationForm.getMigrationType();
            this.migrationGroupBy = applicationForm.getMigrationGroupBy();

            this.volumes = Lists.newArrayList();
            if (migrationGroupBy.equals(VolumeGroup.MigrationGroupBy.VOLUMES.name())) {
                for (NamedRelatedResourceRep vol : getViprClient().application().getVolumes(applicationForm.getId())) {
                    volumes.add(vol.getId());
                }
            }

            this.hosts = Lists.newArrayList();
            if (migrationGroupBy.equals(VolumeGroup.MigrationGroupBy.HOSTS.name())) {
                for (NamedRelatedResourceRep host : getViprClient().application().getHosts(applicationForm.getId())) {
                    hosts.add(host.getId());
                }
            }

            this.clusters = Lists.newArrayList();
            if (migrationGroupBy.equals(VolumeGroup.MigrationGroupBy.CLUSTERS.name())) {
                for (NamedRelatedResourceRep cluster : getViprClient().application().getClusters(applicationForm.getId())) {
                    clusters.add(cluster.getId());
                }
            }
            this.applications = Lists.newArrayList();
            if (migrationGroupBy.equals(VolumeGroup.MigrationGroupBy.APPLICATIONS.name())) {
                NamedVolumeGroupsList applicationRefs =
                        getViprClient().application().getChildrenVolumeGroups(applicationForm.getId());
                if (applicationRefs != null) {
                    for (RelatedResourceRep application : applicationRefs.getVolumeGroups()) {
                        applications.add(application.getId());
                    }
                }
            }
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String fieldname) {
            Validation.valid(fieldname, this);
        }

        public void save() throws ViPRException {
            if (isNew()) {
                MobilityGroupSupportUtil.createMobilityGroup(name, description, ROLE, sourceStorageSystem, sourceVirtualPool,
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
                List<URI> addVolumes = null;
                List<URI> removeVolumes = null;
                if (this.migrationGroupBy.equals(VolumeGroup.MigrationGroupBy.VOLUMES.name())) {
                    List<NamedRelatedResourceRep> allVolumes = getViprClient().application().getVolumes(URI.create(id));
                    addVolumes = computeDiff(volumes, allVolumes, true);
                    removeVolumes = computeDiff(volumes, allVolumes, false);
                }

                List<URI> addHosts = null;
                List<URI> removeHosts = null;
                if (this.migrationGroupBy.equals(VolumeGroup.MigrationGroupBy.HOSTS.name())) {
                    List<NamedRelatedResourceRep> allHosts = getViprClient().application().getHosts(URI.create(id));
                    addHosts = computeDiff(hosts, allHosts, true);
                    removeHosts = computeDiff(hosts, allHosts, false);
                }

                List<URI> addClusters = null;
                List<URI> removeClusters = null;
                if (this.migrationGroupBy.equals(VolumeGroup.MigrationGroupBy.CLUSTERS.name())) {
                    List<NamedRelatedResourceRep> allClustes = getViprClient().application().getClusters(URI.create(id));
                    addClusters = computeDiff(clusters, allClustes, true);
                    removeClusters = computeDiff(clusters, allClustes, false);
                }

                List<URI> addApplications = null;
                List<URI> removeApplications = null;
                if (this.migrationGroupBy.equals(VolumeGroup.MigrationGroupBy.APPLICATIONS.name())) {
                    NamedVolumeGroupsList apps = getViprClient().application().getChildrenVolumeGroups(URI.create(id));
                    addApplications = computeDiff(applications, apps != null ? apps.getVolumeGroups()
                            : new ArrayList<RelatedResourceRep>(), true);
                    removeApplications = computeDiff(applications, apps != null ? apps.getVolumeGroups()
                            : new ArrayList<RelatedResourceRep>(),
                            false);
                }

                MobilityGroupSupportUtil.updateMobilityGroup(name, description, id, addVolumes, removeVolumes, addHosts, removeHosts,
                        addClusters, removeClusters);

                //

                if (addApplications != null) {
                    for (URI add : addApplications) {
                        Set<RelatedResourceRep> parents = getViprClient().application().getApplication(add).getParents();
                        VolumeGroupUpdateParam param = new VolumeGroupUpdateParam();
                        Set<String> parentResults = getParents(parents);
                        parentResults.add(id);
                        param.setParents(parentResults);
                        getViprClient().application().updateApplication(add, param);
                    }
                }

                if (removeApplications != null) {
                    for (URI add : removeApplications) {
                        Set<RelatedResourceRep> parents = getViprClient().application().getApplication(add).getParents();
                        VolumeGroupUpdateParam param = new VolumeGroupUpdateParam();
                        Set<String> parentResults = getParents(parents);
                        parentResults.remove(id);
                        if (parentResults.isEmpty()) {
                            parentResults.add("null");
                        }
                        param.setParents(parentResults);
                        getViprClient().application().updateApplication(add, param);
                    }
                }
            }

        }

        private Set<String> getParents(Set<RelatedResourceRep> apps) {
            Set<String> result = Sets.newHashSet();
            if (apps == null) {
                return result;
            }
            for (RelatedResourceRep app : apps) {
                result.add(app.getId().toString());
            }
            return result;
        }

        private List<URI> computeDiff(List<URI> selectedResources, Collection<? extends RelatedResourceRep> oldResources, boolean add) {
            List<URI> result = Lists.newArrayList();
            if (add && selectedResources != null) {
                // TODO fix this looping...
                List<URI> oldResourceIds = Lists.newArrayList();
                for (RelatedResourceRep oldResource : oldResources) {
                    oldResourceIds.add(oldResource.getId());
                }
                for (URI selectedResource : selectedResources) {
                    if (!oldResourceIds.contains(selectedResource)) {
                        result.add(selectedResource);
                    }
                }
            } else {
                for (RelatedResourceRep oldResource : oldResources) {
                    if (selectedResources == null || !selectedResources.contains(oldResource.getId())) {
                        result.add(oldResource.getId());
                    }
                }
            }
            return result;
        }
    }
}
