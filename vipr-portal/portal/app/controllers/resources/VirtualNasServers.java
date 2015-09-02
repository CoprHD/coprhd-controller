package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;
import java.util.Set;

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
    
    public static void virtualNasServersJson(String projectId){
        
        ProjectRestRep projRestRep = getViprClient().projects().get(uri(projectId));
        Set<String> vNasIds = projRestRep.getAssignedVNasServers();
        List<URI> vNasUris = Lists.newArrayList();
        for(String id:vNasIds){
             vNasUris.add(uri(id));
        }
        List<VirtualNASRestRep> vNasServers = getViprClient().virtualNasServers().getByIds(vNasUris);
        renderJSON(DataTablesSupport.createJSON(vNasServers, params));
    }
    
    public static class VirtualNasForProjectDataTable extends VirtualNasServerDataTable{
    
        public VirtualNasForProjectDataTable(){
            alterColumn("project").hidden();
              
        }
    
    }

}
