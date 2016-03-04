/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.net.URI;
import java.util.List;

import models.datatable.MobilityGroupSupportDataTable;
import play.mvc.With;
import util.BourneUtil;
import util.MobilityGroupSupportUtil;
import util.datatable.DataTable;
import util.datatable.DataTablesSupport;

import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.google.common.collect.Lists;

import controllers.Common;

@With(Common.class)
public class MobilityGroups extends ResourceController {

    public static void list() {
        MobilityGroupSupportDataTable dataTable = new MobilityGroupSupportDataTable();
        render(dataTable);
    }

    public static void listJson() {
        List<MobilityGroupSupportDataTable.MobilityGroupSupport> mobilityGroups = MobilityGroupSupportDataTable.fetch();
        renderJSON(DataTablesSupport.createJSON(mobilityGroups, params));
    }

    public static void mobilityGroupResourcesTable(String id) {
        List<controllers.resources.MobilityGroups.MobilityGroupResourcesTable.MobilityGroupResource> mobilityGroupResourcesTable = getTable(
                uri(id)).fetch();
        renderJSON(DataTablesSupport.createJSON(mobilityGroupResourcesTable, params));
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
                resources.add(new Volume(resource.getId(), resource.getName()));
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
