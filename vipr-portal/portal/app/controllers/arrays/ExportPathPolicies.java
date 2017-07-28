package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.block.export.ExportPathPolicy;
import com.emc.storageos.model.block.export.ExportPathPolicyRestRep;
import com.emc.storageos.model.block.export.ExportPathPolicyUpdate;
import com.emc.storageos.model.block.export.StoragePorts;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;
import models.datatable.ExportPathPoliciesDataTable;
import models.datatable.ExportPathPoliciesDataTable.PortSelectionDataTable;
import models.datatable.ExportPathPoliciesDataTable.StoragePortsDataTable;
import models.datatable.StoragePortDataTable.StoragePortInfo;
import play.Logger;
import play.data.binding.As;
import play.data.validation.Validation;
import play.mvc.With;
import util.MessagesUtils;
import util.StoragePortUtils;
import util.StorageSystemUtils;
import util.StringOption;
import util.datatable.DataTablesSupport;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class ExportPathPolicies extends ViprResourceController {

    protected static final String UNKNOWN = "ExportPathPolicies.unknown";

    public static void list() {
        ExportPathPoliciesDataTable dataTable = createExportPathPoliciesDataTable();
        render(dataTable);
    }

    private static ExportPathPoliciesDataTable createExportPathPoliciesDataTable() {
        ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
        return dataTable;
    }

    public static class ExportPathPolicyForm {
        public String id;
        public String name;
        public String description;
        public Integer maxPaths;
        public Integer pathsPerInitiator;
        public Integer minPaths;
        public List<URI> storagePorts;
        public Integer maxInitiatorsPerPort;

        public ExportPathPolicyForm form(ExportPathPolicyRestRep restRep){
            this.id = restRep.getId().toString();
            this.name = restRep.getName();
            this.description = restRep.getDescription();
            this.maxPaths = restRep.getMaxPaths();
            this.minPaths = restRep.getMinPaths();
            this.pathsPerInitiator = restRep.getPathsPerInitiator();
            this.maxInitiatorsPerPort = restRep.getMaxInitiatorsPerPort();
            this.storagePorts = restRep.getStoragePorts();
            
            return this;
        }

        public void validate(String formName) {
            Validation.required(formName + ".name", name);
            Validation.required(formName + ".description", description);
            Validation.required(formName + ".storagePorts", storagePorts);
        }
        public boolean isNew() {
            return StringUtils.isBlank(id);
        }
    }

    public static void portGroups(String id) {
        // addReferenceData();

        StorageSystemRestRep storageSystem = StorageSystemUtils.getStorageSystem(id);
        ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
        renderArgs.put("dataTable", dataTable);
        renderArgs.put("storageSystemId", id);
        renderArgs.put("storageSystemName", storageSystem.getName());
        ExportPathPolicyForm portGroup = new ExportPathPolicyForm();
        render("@listPortGroups", storageSystem, dataTable,portGroup);
    }

    public static void portGroupsJson(String storageId) {
        List<ExportPathPoliciesDataTable.ExportPathParamsModel> results = Lists.newArrayList();
        List<ExportPathPolicyRestRep> portGroups = getViprClient().exportPathPolicies().getPortGroups();

        for (ExportPathPolicyRestRep portGroup : portGroups) {
            results.add(new ExportPathPoliciesDataTable.ExportPathParamsModel(portGroup.getId(), portGroup.getName(), portGroup
                    .getDescription()));
        }
        renderArgs.put("storageSystemId", storageId);
        renderJSON(DataTablesSupport.createJSON(results, params));
    }
    
    private static void renderNumPathsArgs(){
        renderArgs.put(
                "numPathsOptions",
                StringOption.options(new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
                        "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32" }, false));
    }
    
   // @FlashException(value = "portGroups", keep = true)
    public static void addPortGroup(String storageSystemId) {
        
        ExportPathPolicyForm portGroup = new ExportPathPolicyForm();//createExportPathParams();//new ExportPathParameters();//
        ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
        StoragePortsDataTable portDataTable = dataTable.new StoragePortsDataTable();
        PortSelectionDataTable portSelectionDataTable = dataTable.new PortSelectionDataTable();
        renderArgs.put("storageSystemId", storageSystemId);
        renderNumPathsArgs();
        //ExportPathParametersRestRep resp =getViprClient().exportPathParameters().create(input);
        ///editPortGroup(resp.getId().toString(),storageSystemId);
        render("@editPortGroup",portGroup, portDataTable, portSelectionDataTable);
       
    }
    
    //@FlashException(value = "portGroups", keep = true)
    public static void editPortGroup(String id, String storageSystemId) {
        ExportPathPolicyRestRep exportPathParametersRestRep = getViprClient().exportPathPolicies().get(uri(id));
        renderArgs.put("storageSystemId", storageSystemId);
        renderArgs.put("pathParamId", id);
        renderNumPathsArgs();
        ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
        StoragePortsDataTable portDataTable = dataTable.new StoragePortsDataTable();
        PortSelectionDataTable portSelectionDataTable = dataTable.new PortSelectionDataTable();
        //StorageArrayPortDataTable portDataTable = new StorageArrayPortDataTable(storageSystem);
        if (exportPathParametersRestRep != null) {
            ExportPathPolicyForm portGroup = new ExportPathPolicyForm().form(exportPathParametersRestRep);
            render(portGroup,portDataTable,portSelectionDataTable);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            portGroups(storageSystemId);
        }

    }
    @FlashException("portGroups")
    public static void deletePortGroup(@As(",") String[] ids,String storageSystemId) {
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                getViprClient().exportPathPolicies().delete(uri(id));
            }
            flash.success(MessagesUtils.get("exportPathParameters.deleted"));
        }
        portGroups(storageSystemId);
    }

    @FlashException(keep = true, referrer = { "editPortGroup" })
    public static void savePortGroup(ExportPathPolicyForm portGroup,String storageSystemId) {
        if (portGroup == null) {
            Logger.error("No port group parameters passed");
            badRequest("No port group parameters passed");
            return;
        }
       /* portGroup.validate("portGroup");
        if (Validation.hasErrors()) {
            Common.handleError();
        }*/
        portGroup.id = params.get("id");
        if (portGroup.isNew()) {
            
            ExportPathPolicy input = createExportPathParams(portGroup);
            getViprClient().exportPathPolicies().create(input); // FIXME: had "true" second arg
        } else {
            ExportPathPolicyRestRep exportPathParametersRestRep = getViprClient().exportPathPolicies().get(uri(portGroup.id));
            ExportPathPolicyUpdate input = updateExportPathParams(portGroup);
            getViprClient().exportPathPolicies().update(exportPathParametersRestRep.getId(), input);
        }
        flash.success(MessagesUtils.get("portGroup.saved", portGroup.name));
        portGroups(storageSystemId);
    }
    
    public static ExportPathPolicy createExportPathParams( ExportPathPolicyForm portGroup ) {
        ExportPathPolicy pathParam = new ExportPathPolicy();
        pathParam.setName(portGroup.name.trim());
        pathParam.setDescription(portGroup.description.trim());
        pathParam.setMaxPaths(portGroup.maxPaths);
        pathParam.setMinPaths(portGroup.minPaths);
        pathParam.setPathsPerInitiator(portGroup.pathsPerInitiator);
        pathParam.setMaxInitiatorsPerPort(portGroup.maxInitiatorsPerPort);

        pathParam.setStoragePorts(portGroup.storagePorts);
        return pathParam;
    }
    
    public static ExportPathPolicyUpdate updateExportPathParams( ExportPathPolicyForm portGroup ) {
        ExportPathPolicyUpdate pathParam = new ExportPathPolicyUpdate();
        pathParam.setName(portGroup.name.trim());
        pathParam.setDescription(portGroup.description.trim());
        pathParam.setMaxPaths(portGroup.maxPaths);
        pathParam.setMinPaths(portGroup.minPaths);
        pathParam.setPathsPerInitiator(portGroup.pathsPerInitiator);
        pathParam.setMaxInitiatorsPerPort(portGroup.maxInitiatorsPerPort);
        return pathParam;
    }
    
    @FlashException(referrer = { "editPortGroup" })
    public static void addPorts(String pathParamId,String storageSystemId, String portIds, String pgName, String pgDesc) {
         
        if(pathParamId == null || "".equals(pathParamId)){
            ExportPathPolicy input = new ExportPathPolicy();
            input.setName(pgName);
            input.setDescription(pgDesc);
            ExportPathPolicyRestRep rep = getViprClient().exportPathPolicies().create(input);  // FIXME: had "true" second arg
            pathParamId = rep.getId().toString();
        }
        String [] ids = portIds.split(",");
        ExportPathPolicyRestRep exportPathParametersRestRep = getViprClient().exportPathPolicies().get(uri(pathParamId));
        List<URI> portsInDb = exportPathParametersRestRep.getStoragePorts();
            List<URI> portsToAdd = Lists.newArrayList();
            for (String value : ids) {
                if (StringUtils.isNotBlank(value)) {
                    if (!portsInDb.contains(uri(value))) {
                        portsToAdd.add(uri(value));
                    }   
                }
            }
            ExportPathPolicyUpdate pathParam = new ExportPathPolicyUpdate();
            StoragePorts  storagePorts = new StoragePorts(); 
            if (!portsToAdd.isEmpty()) {
                storagePorts.setStoragePorts(portsToAdd);
                pathParam.setPortsToAdd(storagePorts.getStoragePorts());
            }
            getViprClient().exportPathPolicies().update(exportPathParametersRestRep.getId(), pathParam);
        
        editPortGroup(pathParamId,storageSystemId);
        
    }
    
    @FlashException(referrer = { "editPortGroup" })
    public static void deletePorts(String pathParamId,String storageSystemId, @As(",") String[] ids) {
        
        ExportPathPolicyRestRep exportPathParametersRestRep = getViprClient().exportPathPolicies().get(uri(pathParamId));
        List<URI> portsInDb = exportPathParametersRestRep.getStoragePorts();
            List<URI> portsToRemove = Lists.newArrayList();
            for (String value : ids) {
                if (StringUtils.isNotBlank(value)) {
                    if (portsInDb.contains(uri(value))) {
                        portsToRemove.add(uri(value));
                    }   
                }
            }
            ExportPathPolicyUpdate pathParam = new ExportPathPolicyUpdate();
            StoragePorts  storagePorts = new StoragePorts(); 
            if (!portsToRemove.isEmpty()) {
                storagePorts.setStoragePorts(portsToRemove);
                pathParam.setPortsToRemove(storagePorts.getStoragePorts());
            }
            getViprClient().exportPathPolicies().update(exportPathParametersRestRep.getId(), pathParam);
        editPortGroup(pathParamId,storageSystemId);
    }
    
    public static void storagePortsJson(String pathParamId) {
        List<StoragePortInfo> results = Lists.newArrayList();
        
        if (pathParamId != null && !"null".equals(pathParamId)) {
            ExportPathPolicyRestRep exportPathParametersRestRep = getViprClient().exportPathPolicies().get(uri(pathParamId));
            List<URI> storagePortUris = exportPathParametersRestRep.getStoragePorts();
            
            List<StoragePortRestRep> storagePorts = StoragePortUtils
                    .getStoragePorts(storagePortUris);
            for (StoragePortRestRep storagePort : storagePorts) {
                results.add(new StoragePortInfo(storagePort));
            }
            
        } 
        
        renderJSON(DataTablesSupport.createJSON(results, params));
    }
    
    public static void availablePortsJson(String val) {
        List<PortSelectionDataTable.PortSelectionModel> results = Lists.newArrayList();
        String[] data = val.split("~~~");
        String pathParamId = "";
        String systemId = "";
        if(data.length > 1){
            pathParamId = data[0];
            systemId = data[1];
        }
        List<StoragePortRestRep> storagePorts = StoragePortUtils.getStoragePorts(systemId);

        if (pathParamId != null && !"null".equals(pathParamId)) {
            ExportPathPolicyRestRep exportPathParametersRestRep = getViprClient().exportPathPolicies().get(uri(pathParamId));
            List<URI> storagePortUris = exportPathParametersRestRep.getStoragePorts();
            List<StoragePortRestRep> portsNotForSelection = StoragePortUtils.getStoragePorts(storagePortUris);
            storagePorts.removeAll(portsNotForSelection);

        }
        for (StoragePortRestRep storagePort : storagePorts) {
            ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
            PortSelectionDataTable portSelectionDataTable = dataTable.new PortSelectionDataTable();
            results.add(portSelectionDataTable.new PortSelectionModel(storagePort));
        }

        renderJSON(DataTablesSupport.createJSON(results, params));
    }
}
