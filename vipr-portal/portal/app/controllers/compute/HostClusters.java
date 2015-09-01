/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.compute;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;
import java.util.Set;

import models.datatable.HostClusterDataTable;
import models.datatable.SimpleHostDataTable;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;
import util.ClusterUtils;
import util.HostUtils;
import util.MessagesUtils;
import util.ProjectUtils;
import util.VCenterUtils;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.HostUpdateParam;
import com.emc.storageos.model.host.cluster.ClusterCreateParam;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.cluster.ClusterUpdateParam;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.vipr.client.core.filters.DefaultResourceFilter;
import com.emc.vipr.client.core.filters.FilterChain;
import com.emc.vipr.client.core.filters.HostTypeFilter;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;
import controllers.util.FlashException;
import controllers.util.Models;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN") })
public class HostClusters extends Controller {
    protected static final String SAVED = "HostClusters.saved";
    protected static final String DELETED = "HostClusters.deleted";
    protected static final String REMOVED_HOSTS = "HostClusters.removedHosts";
    protected static final String ADDED_HOSTS = "HostClusters.addedHosts";
    protected static final String UNKNOWN = "HostClusters.unknown";
    protected static final String MODEL_NAME = "Cluster";
    protected static final String INTROSPECTION_STARTED = "Introspection.Started";
    protected static final String DETACH_STORAGE = "HostClusters.detachStorage";

    public static void list() {
        renderArgs.put("dataTable", new HostClusterDataTable());
        TenantSelector.addRenderArgs();
        render();
    }

    public static void listJson() {
        List<ClusterRestRep> clusters = ClusterUtils.getClusters(Models.currentAdminTenant());
        List<HostClusterDataTable.HostClusterInfo> hostClusterInfos = Lists.newArrayList();
        for (ClusterRestRep cluster : clusters) {
            hostClusterInfos.add(new HostClusterDataTable.HostClusterInfo(cluster));
        }

        renderJSON(DataTablesSupport.createJSON(hostClusterInfos, params));
    }

    public static void itemDetails(String id) {
        ClusterRestRep cluster = ClusterUtils.getCluster(uri(id));
        if (cluster == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        ProjectRestRep project = ProjectUtils.getProject(ResourceUtils.id(cluster.getProject()));
        List<HostRestRep> hosts = ClusterUtils.getHosts(uri(id));
        VcenterDataCenterRestRep dataCenter = VCenterUtils.getDataCenter(ResourceUtils.id(cluster
                .getVcenterDataCenter()));
        VcenterRestRep vcenter = VCenterUtils.getVCenter(dataCenter);
        render(cluster, project, hosts, dataCenter, vcenter);
    }

    public static void create() {
        HostClusterForm hostCluster = new HostClusterForm();
        hostCluster.tenantId = Models.currentAdminTenant();

        render("@edit", hostCluster);
    }

    @FlashException("list")
    public static void edit(String id) {
        ClusterRestRep cluster = ClusterUtils.getCluster(uri(id));
        if (cluster != null) {
            HostClusterForm hostCluster = new HostClusterForm(cluster);
            render(hostCluster);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(HostClusterForm hostCluster) {
        hostCluster.validate("hostCluster");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        String id = hostCluster.save(hostCluster);
        flash.success(MessagesUtils.get(SAVED, hostCluster.name));
        list();
    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids, boolean detachStorage) {
        for (URI id : ResourceUtils.uris(ids)) {
            ClusterUtils.deactivate(id, detachStorage);
        }
        flash.success(MessagesUtils.get(DELETED));
        list();
    }

    @FlashException("list")
    public static void detachStorage(@As(",") String[] ids) {
        for (URI id : ResourceUtils.uris(ids)) {
            ClusterUtils.detachStorage(id);
        }
        flash.success(MessagesUtils.get(DETACH_STORAGE));
        list();
    }

    @FlashException("list")
    public static void editHosts(String id) {
        ClusterRestRep cluster = ClusterUtils.getCluster(uri(id));
        if (cluster != null) {
            SimpleHostDataTable dataTable = new SimpleHostDataTable();
            render(dataTable, cluster);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    public static void hostJson(String id) {
        List<HostRestRep> hosts = ClusterUtils.getHosts(uri(id));
        renderJSON(DataTablesSupport.createJSON(hosts, params));
    }

    public static void availableHostJson(String id) {
        final URI clusterId = uri(id);
        ClusterRestRep cluster = ClusterUtils.getCluster(clusterId);
        List<HostRestRep> hosts = null;

        DefaultResourceFilter<HostRestRep> defaultHostResourceFilter = new DefaultResourceFilter<HostRestRep>() {
            @Override
            public boolean accept(HostRestRep hostRestRep) {
                return hostRestRep.getCluster() == null || !hostRestRep.getCluster().getId().equals(clusterId);
            }
        };

        // If we have existing hosts in the cluster, limit to that host type
        List<HostRestRep> existingHosts = ClusterUtils.getHosts(uri(id));
        if (!existingHosts.isEmpty()) {
            FilterChain<HostRestRep> hostTypeFilter = new FilterChain<HostRestRep>(new HostTypeFilter(existingHosts.get(0).getType()));

            hosts = getViprClient().hosts().getByTenant(cluster.getTenant().getId(), hostTypeFilter.and(defaultHostResourceFilter));
        } else {
            hosts = getViprClient().hosts().getByTenant(cluster.getTenant().getId(), defaultHostResourceFilter);
        }

        renderJSON(DataTablesSupport.createJSON(hosts, params));
    }

    @FlashException(referrer = { "editHosts" })
    public static void removeHosts(String clusterId, @As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            for (String hostId : ids) {
                HostUpdateParam hostUpdateParam = new HostUpdateParam();
                hostUpdateParam.setCluster(uri("null"));
                HostUtils.updateHost(uri(hostId), hostUpdateParam, false);
            }
            flash.success(MessagesUtils.get(REMOVED_HOSTS));
        }
        editHosts(clusterId);
    }

    @FlashException(referrer = { "editHosts" })
    public static void addHosts(String clusterId, @As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            for (String hostId : ids) {
                HostUpdateParam hostUpdateParam = new HostUpdateParam();
                hostUpdateParam.setCluster(uri(clusterId));
                HostUtils.updateHost(uri(hostId), hostUpdateParam, false);
            }
            flash.success(MessagesUtils.get(ADDED_HOSTS));
        }
        editHosts(clusterId);
    }

    public static class HostClusterForm {
        public String id;
        public String tenantId;
        public Boolean autoExportEnabled;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        public HostClusterForm() {
        }

        public HostClusterForm(ClusterRestRep clusterResponse) {
            this();
            doReadFrom(clusterResponse);
        }

        protected void doReadFrom(ClusterRestRep clusterResponse) {
            this.id = clusterResponse.getId().toString();
            this.tenantId = clusterResponse.getTenant().getId().toString();
            this.name = clusterResponse.getName();
            this.autoExportEnabled = clusterResponse.getAutoExportEnabled();
        }

        protected void doWriteTo(ClusterUpdateParam clusterUpdateParam) {
            clusterUpdateParam.setName(this.name);
        }

        public void validate(String formName) {
            Validation.valid(formName, this);
        }

        /**
         * Clears all other validation error, except for the specified fields.
         * 
         * @param formName
         *            the form name.
         * @param fieldsToKeep
         *            the fields to keep.
         */
        protected void clearOtherErrors(String formName, String... fieldsToKeep) {
            Set<play.data.validation.Error> errors = Sets.newHashSet();
            for (String name : fieldsToKeep) {
                play.data.validation.Error error = Validation.error(String.format("%s.%s", formName, name));
                if (error != null) {
                    errors.add(error);
                }
            }
            Validation.clear();
            for (play.data.validation.Error error : errors) {
                Validation.addError(error.getKey(), error.message());
            }
        }

        public String save() {
            String hostId = null;
            if (isNew()) {
                hostId = createCluster();
            }
            else {
                hostId = updateCluster();
            }

            return hostId;
        }

        public String save(HostClusterForm hostCluster) {
            String hostId = null;
            if (isNew()) {
                hostId = createCluster();
            }
            else {
                hostId = updateCluster(hostCluster);
            }

            return hostId;
        }

        protected String createCluster() {
            ClusterCreateParam clusterCreateParam = new ClusterCreateParam(name);
            clusterCreateParam.setAutoExportEnabled(autoExportEnabled);

            return ClusterUtils.createCluster(tenantId, clusterCreateParam).toString();
        }

        protected String updateCluster() {
            ClusterUpdateParam hostUpdateParam = new ClusterUpdateParam();
            doWriteTo(hostUpdateParam);
            return ClusterUtils.updateHost(uri(this.id), hostUpdateParam).toString();
        }

        protected String updateCluster(HostClusterForm hostCluster) {
            ClusterUpdateParam hostUpdateParam = new ClusterUpdateParam();
            hostUpdateParam.setAutoExportEnabled(hostCluster.autoExportEnabled);

            doWriteTo(hostUpdateParam);
            return ClusterUtils.updateHost(uri(this.id), hostUpdateParam).toString();
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }
    }
}
