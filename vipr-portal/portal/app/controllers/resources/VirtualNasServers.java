package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import models.datatable.VirtualNasServerDataTable;
import models.datatable.VirtualNasServerDataTable.VirtualNasServerInfo;

import org.apache.commons.lang.StringUtils;

import play.mvc.With;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.vnas.VirtualNASRestRep;
import com.google.common.collect.Lists;

import controllers.Common;

@With(Common.class)
public class VirtualNasServers extends ResourceController{
    private static VirtualNasForProjectDataTable vNasDataTable = new VirtualNasForProjectDataTable();
    
    public static void vNasServers(String projectId) {
        setActiveProjectId(projectId);
        renderArgs.put("dataTable", vNasDataTable);
        addReferenceData();
        render();
    }
    
    public static void virtualNasServersJson(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            setActiveProjectId(projectId);
        } else {
            projectId = getActiveProjectId();
        }
        List<VirtualNasServerInfo> vNasServers = getVirtualNasServers(projectId);
        renderJSON(DataTablesSupport.createJSON(vNasServers, params));
    }

    private static List<VirtualNasServerInfo> getVirtualNasServers(String projectId) {
        if (projectId == null) {
            return Collections.EMPTY_LIST;
        }
        List<VirtualNasServerInfo> vNasServers = Lists.newArrayList();
        ProjectRestRep projRestRep = getViprClient().projects().get(uri(projectId));
        Set<String> vNasIds = projRestRep.getAssignedVNasServers();
        List<URI> vNasUris = Lists.newArrayList();
        if (vNasIds != null) {
            for (String id : vNasIds) {
                vNasUris.add(uri(id));
            }
            if (!vNasUris.isEmpty()) {
                List<VirtualNASRestRep> vNas = getViprClient().virtualNasServers().getByIds(vNasUris);
                for (VirtualNASRestRep vNasServer : vNas) {
                    vNasServers.add(new VirtualNasServerInfo(vNasServer,true));
                }
            }
        }
        return vNasServers;
    }

    public static class VirtualNasForProjectDataTable extends VirtualNasServerDataTable{
    
        public VirtualNasForProjectDataTable(){
            alterColumn("project").hidden();
              
        }
    
    }

}
