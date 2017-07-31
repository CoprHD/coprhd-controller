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

        public ExportPathPolicyForm form(ExportPathPolicyRestRep restRep) {
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
            // Validation.required(formName + ".storagePorts", storagePorts);
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }
    }

    public static void exportPathPolices() {
        // addReferenceData();

        ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
        renderArgs.put("dataTable", dataTable);
        ExportPathPolicyForm exportPathPolicyForm = new ExportPathPolicyForm();
        render("@list", dataTable, exportPathPolicyForm);
    }

    public static void exportPathPoliciesJson() {
        List<ExportPathPoliciesDataTable.ExportPathPoliciesModel> results = Lists.newArrayList();
        List<ExportPathPolicyRestRep> exportPathPolicies = getViprClient().exportPathPolicies().getExportPathPoliciesList();

        for (ExportPathPolicyRestRep exportPathPolicy : exportPathPolicies) {
            results.add(new ExportPathPoliciesDataTable.ExportPathPoliciesModel(
                    exportPathPolicy.getId(), exportPathPolicy.getName(),
                    exportPathPolicy.getDescription(), 
                    exportPathPolicy.getMinPaths(), exportPathPolicy.getMaxPaths(), 
                    exportPathPolicy.getPathsPerInitiator(), exportPathPolicy.getMaxInitiatorsPerPort(),
                    exportPathPolicy.getStoragePorts().size()));
        }
        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    private static void renderNumPathsArgs() {
        renderArgs.put(
                "numPathsOptions",
                StringOption.options(new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
                        "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32" }, false));
    }

    // @FlashException(value = "exportPathPolicies", keep = true)
    public static void addExportPathPolicy(String storageSystemId) {
        ExportPathPolicyForm exportPathPolicyForm = new ExportPathPolicyForm();// createExportPathParams();//new ExportPathParameters();//
        ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
        StoragePortsDataTable portDataTable = dataTable.new StoragePortsDataTable();
        PortSelectionDataTable portSelectionDataTable = dataTable.new PortSelectionDataTable();

        renderNumPathsArgs();
        render("@edit", exportPathPolicyForm, portDataTable, portSelectionDataTable);
    }

    // @FlashException(value = "exportPathPolicies", keep = true)
    public static void edit(String id) {
        ExportPathPolicyRestRep exportPathPolicyRestRep = getViprClient().exportPathPolicies().get(uri(id));
        renderArgs.put("exportPathPolicyId", id);
        renderNumPathsArgs();
        ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
        StoragePortsDataTable portDataTable = dataTable.new StoragePortsDataTable();
        PortSelectionDataTable portSelectionDataTable = dataTable.new PortSelectionDataTable();

        if (exportPathPolicyRestRep != null) {
            ExportPathPolicyForm exportPathPolicyForm = new ExportPathPolicyForm().form(exportPathPolicyRestRep);
            render(exportPathPolicyForm, dataTable, portDataTable, portSelectionDataTable);
        } else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            exportPathPolices();
        }

    }

    // @FlashException("exportPathPolicies")
    public static void deleteExportPathPolicy(@As(",") String[] ids, String storageSystemId) {
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                getViprClient().exportPathPolicies().delete(uri(id));
            }
            flash.success(MessagesUtils.get("exportPathParameters.deleted"));
        }
        exportPathPolices();
    }

    @FlashException(keep = true, referrer = { "edit" })
    public static void saveExportPathPolicy(ExportPathPolicyForm exportPathPolicy, String storageSystemId) {
        if (exportPathPolicy == null) {
            Logger.error("No export path policy provided");
            badRequest("No export path policy provided");
            return;
        }
        /*
         * portGroup.validate("portGroup");
         * if (Validation.hasErrors()) {
         * Common.handleError();
         * }
         */
        exportPathPolicy.id = params.get("id");
        if (exportPathPolicy.isNew()) {

            ExportPathPolicy input = createExportPathPolicy(exportPathPolicy);
            getViprClient().exportPathPolicies().create(input); // FIXME: had "true" second arg
        } else {
            ExportPathPolicyRestRep exportPathPolicyRestRep = getViprClient().exportPathPolicies().get(uri(exportPathPolicy.id));
            ExportPathPolicyUpdate input = updateExportPathPolicy(exportPathPolicy);
            getViprClient().exportPathPolicies().update(exportPathPolicyRestRep.getId(), input);
        }
        flash.success(MessagesUtils.get("exportPathPolicy.saved", exportPathPolicy.name));
        exportPathPolices();
    }

    public static ExportPathPolicy createExportPathPolicy(ExportPathPolicyForm exportPathPolicyForm) {
        ExportPathPolicy exportPathPolicy = new ExportPathPolicy();
        exportPathPolicy.setName(exportPathPolicyForm.name.trim());
        exportPathPolicy.setDescription(exportPathPolicyForm.description.trim());
        exportPathPolicy.setMaxPaths(exportPathPolicyForm.maxPaths);
        exportPathPolicy.setMinPaths(exportPathPolicyForm.minPaths);
        exportPathPolicy.setPathsPerInitiator(exportPathPolicyForm.pathsPerInitiator);
        exportPathPolicy.setMaxInitiatorsPerPort(exportPathPolicyForm.maxInitiatorsPerPort);

        exportPathPolicy.setStoragePorts(exportPathPolicyForm.storagePorts);
        return exportPathPolicy;
    }

    public static ExportPathPolicyUpdate updateExportPathPolicy(ExportPathPolicyForm exportPathPolicyForm) {
        ExportPathPolicyUpdate exportPathPolicyUpdate = new ExportPathPolicyUpdate();
        exportPathPolicyUpdate.setName(exportPathPolicyForm.name.trim());
        exportPathPolicyUpdate.setDescription(exportPathPolicyForm.description.trim());
        exportPathPolicyUpdate.setMaxPaths(exportPathPolicyForm.maxPaths);
        exportPathPolicyUpdate.setMinPaths(exportPathPolicyForm.minPaths);
        exportPathPolicyUpdate.setPathsPerInitiator(exportPathPolicyForm.pathsPerInitiator);
        exportPathPolicyUpdate.setMaxInitiatorsPerPort(exportPathPolicyForm.maxInitiatorsPerPort);
        return exportPathPolicyUpdate;
    }

    @FlashException(referrer = { "edit" })
    public static void addStoragePortsToPolicy(String exportPathPolicyId, String storageSystemId, String storagePortIds, String pgName, String pgDesc) {

        if (exportPathPolicyId == null || "".equals(exportPathPolicyId)) {
            ExportPathPolicy input = new ExportPathPolicy();
            input.setName(pgName);
            input.setDescription(pgDesc);
            ExportPathPolicyRestRep rep = getViprClient().exportPathPolicies().create(input);  // FIXME: had "true" second arg
            exportPathPolicyId = rep.getId().toString();
        }
        String[] ids = storagePortIds.split(",");
        ExportPathPolicyRestRep exportPathParametersRestRep = getViprClient().exportPathPolicies().get(uri(exportPathPolicyId));
        List<URI> storagePortsInDb = exportPathParametersRestRep.getStoragePorts();
        List<URI> portsToAdd = Lists.newArrayList();
        for (String value : ids) {
            if (StringUtils.isNotBlank(value)) {
                if (!storagePortsInDb.contains(uri(value))) {
                    portsToAdd.add(uri(value));
                }
            }
        }
        ExportPathPolicyUpdate pathParam = new ExportPathPolicyUpdate();
        StoragePorts storagePorts = new StoragePorts();
        if (!portsToAdd.isEmpty()) {
            storagePorts.setStoragePorts(portsToAdd);
            pathParam.setPortsToAdd(storagePorts.getStoragePorts());
        }
        getViprClient().exportPathPolicies().update(exportPathParametersRestRep.getId(), pathParam);

        edit(exportPathPolicyId);

    }

    @FlashException(referrer = { "edit" })
    public static void removeStoragePortsFromPolicy(String exportPathPolicyId, @As(",") String[] ids) {

        ExportPathPolicyRestRep exportPathPolicyRestRep = getViprClient().exportPathPolicies().get(uri(exportPathPolicyId));
        List<URI> exportPathPoliciesStoragePortsInDb = exportPathPolicyRestRep.getStoragePorts();
        List<URI> storagePortsToRemove = Lists.newArrayList();
        for (String value : ids) {
            if (StringUtils.isNotBlank(value)) {
                if (exportPathPoliciesStoragePortsInDb.contains(uri(value))) {
                    storagePortsToRemove.add(uri(value));
                }
            }
        }
        ExportPathPolicyUpdate exportPathPolicyUpdate = new ExportPathPolicyUpdate();
        StoragePorts storagePorts = new StoragePorts();
        if (!storagePortsToRemove.isEmpty()) {
            storagePorts.setStoragePorts(storagePortsToRemove);
            exportPathPolicyUpdate.setPortsToRemove(storagePorts.getStoragePorts());
        }
        getViprClient().exportPathPolicies().update(exportPathPolicyRestRep.getId(), exportPathPolicyUpdate);
        edit(exportPathPolicyId);
    }

    public static void storagePortsJson(String exportPathPolicyId) {
        List<StoragePortInfo> results = Lists.newArrayList();
        
        if (exportPathPolicyId != null && !"null".equals(exportPathPolicyId)) {
            ExportPathPolicyRestRep exportPathParametersRestRep = getViprClient().exportPathPolicies().get(uri(exportPathPolicyId));
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
