package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import models.datatable.VirtualNasServerDataTable;
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
        List<VirtualNASRestRep> vNasServers = getVirtualNasServers(projectId);
        renderJSON(DataTablesSupport.createJSON(vNasServers, params));
    }

    private static List<VirtualNASRestRep> getVirtualNasServers(String projectId) {
        if (projectId == null) {
            return Collections.EMPTY_LIST;
        }
        List<VirtualNASRestRep> vNasServers = Lists.newArrayList();
        ProjectRestRep projRestRep = getViprClient().projects().get(uri(projectId));
        Set<String> vNasIds = projRestRep.getAssignedVNasServers();
        List<URI> vNasUris = Lists.newArrayList();
        if (vNasIds != null) {
            for (String id : vNasIds) {
                vNasUris.add(uri(id));
            }
            if (!vNasUris.isEmpty()) {
                vNasServers = getViprClient().virtualNasServers().getByIds(vNasUris);
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
