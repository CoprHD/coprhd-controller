package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.model.block.export.ExportPathPolicy;
import com.emc.storageos.model.block.export.ExportPathPolicyRestRep;
import com.emc.storageos.model.block.export.ExportPathPolicyUpdate;
import com.emc.storageos.model.block.export.StoragePorts;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.core.util.CachedResources;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;
import models.datatable.ExportPathPoliciesDataTable;
import models.datatable.ExportPathPoliciesDataTable.StoragePortDisplayDataTable;
import models.datatable.StoragePortDataTable.StoragePortInfo;
import play.Logger;
import play.data.binding.As;
import play.data.validation.Validation;
import play.i18n.Messages;
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

    /**
     * Render the list of ExportPathPolicies.
     */
    public static void list() {
        ExportPathPoliciesDataTable dataTable = createExportPathPoliciesDataTable();
        render(dataTable);
    }

    /**
     * Render the ExportPathPolicy data table.
     * 
     * @return the ExportPathPolicy data table
     */
    private static ExportPathPoliciesDataTable createExportPathPoliciesDataTable() {
        ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
        return dataTable;
    }

    /**
     * ExportPathPolicy form class.
     */
    public static class ExportPathPolicyForm {
        public String id;
        public String name;
        public String description;
        public Integer maxPaths;
        public Integer pathsPerInitiator;
        public Integer minPaths;
        public List<URI> storagePorts;
        public Integer maxInitiatorsPerPort;

        /**
         * Load an ExportPathPolicyForm with the given ExportPathPolicyRestRep as the model.
         * 
         * @param restRep the ExportPathPolicyRestRep to load the form with
         * @return an ExportPathPolicyForm
         */
        public ExportPathPolicyForm load(ExportPathPolicyRestRep restRep) {
            this.id = restRep.getId() != null ? restRep.getId().toString() : null;
            this.name = restRep.getName();
            this.description = restRep.getDescription();
            this.maxPaths = restRep.getMaxPaths();
            this.minPaths = restRep.getMinPaths();
            this.pathsPerInitiator = restRep.getPathsPerInitiator();
            this.maxInitiatorsPerPort = restRep.getMaxInitiatorsPerPort();
            this.storagePorts = restRep.getStoragePorts();
            return this;
        }

        /**
         * Validate the ExportPathPolicyForm.
         * 
         * @param formName the name of the form
         */
        public void validate(String formName) {
            Validation.required(formName + ".name", name);
            Validation.required(formName + ".description", description);
        }

        /**
         * Returns true if the form has not yet had its data saved to the database.
         * 
         * @return true if the form has not yet had its data saved to the database
         */
        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        /**
         * Takes a comma-separated list of URI Strings and converts them
         * to URIs, then calling setStoragePorts at the end.
         * 
         * @param storagePortUriStrings a comma separated list of URI Strings
         */
        public void setStoragePortsFromStringOfUris(String storagePortUriStrings) {
            if (storagePortUriStrings != null && !storagePortUriStrings.isEmpty()) {
                List<URI> storagePortUris = new ArrayList<URI>();
                storagePortUriStrings = storagePortUriStrings.replaceAll("\\[|\\]", "");
                String[] storagePorts = storagePortUriStrings.split(",");
                for (String port : storagePorts) {
                    port = port.trim();
                    if (URIUtil.isValid(port)) {
                        storagePortUris.add(URI.create(port));
                    }
                }
                if (!storagePortUris.isEmpty()) {
                    this.storagePorts = storagePortUris;
                }
            }
        }
    }

    /**
     * Renders the ExportPathPolicy list.
     */
    public static void exportPathPolices() {
        ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
        renderArgs.put("dataTable", dataTable);
        ExportPathPolicyForm exportPathPolicyForm = new ExportPathPolicyForm();
        render("@list", dataTable, exportPathPolicyForm);
    }

    /**
     * Renders the ExportPathPolicy JSON data for the data table.
     */
    public static void exportPathPoliciesJson() {
        List<ExportPathPoliciesDataTable.ExportPathPoliciesModel> results = Lists.newArrayList();
        List<ExportPathPolicyRestRep> exportPathPolicies = getViprClient().exportPathPolicies().getExportPathPoliciesList();

        for (ExportPathPolicyRestRep exportPathPolicy : exportPathPolicies) {
            results.add(new ExportPathPoliciesDataTable.ExportPathPoliciesModel(
                    exportPathPolicy.getId(), exportPathPolicy.getName(),
                    exportPathPolicy.getDescription(),
                    exportPathPolicy.getMinPaths(), exportPathPolicy.getMaxPaths(),
                    exportPathPolicy.getPathsPerInitiator(), exportPathPolicy.getMaxInitiatorsPerPort()));
        }
        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    /**
     * Renders the item details snippet that goes in the ExportPathPolicy list table, when you click for more details.
     * 
     * @param id the ExportPathPolicy to load details for
     */
    public static void itemDetails(String id) {
        ExportPathPolicyRestRep policy = getViprClient().exportPathPolicies().get(uri(id));
        if (policy == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        List<StoragePortRestRep> storagePortReps = StoragePortUtils.getStoragePorts(policy.getStoragePorts());
        List<String> storagePorts = new ArrayList<String>();
        CachedResources<StorageSystemRestRep> storageSystems = StorageSystemUtils.createCache();

        for (StoragePortRestRep port : storagePortReps) {
            StoragePortInfo info = new StoragePortInfo(port, storageSystems.get(port.getStorageDevice()));
            storagePorts.add(info.storageSystem + ": " + info.portGroup + " / " + info.name + " | " + port.getPortNetworkId());
        }
        render(storagePorts);
    }

    /**
     * Render data for the various num paths dropdowns (re-used by all).
     */
    private static void renderNumPathsArgs() {
        renderArgs.put(
                "numPathsOptions",
                StringOption
                        .options(new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
                                "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32" }, false));
    }

    /**
     * Renders the form page for adding a new ExportPathPolicy.
     */
    @FlashException(value = "list", keep = true)
    public static void addExportPathPolicy() {
        ExportPathPolicyForm exportPathPolicyForm = new ExportPathPolicyForm();
        ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
        StoragePortDisplayDataTable portDataTable = dataTable.new StoragePortDisplayDataTable();
        StoragePortDisplayDataTable portSelectionDataTable = dataTable.new StoragePortDisplayDataTable();

        renderNumPathsArgs();
        render("@edit", exportPathPolicyForm, portDataTable, portSelectionDataTable);
    }

    /**
     * Renders the form page for editing an existing ExportPathPolicy.
     * 
     * @param id the ExportPathPolicy to load for editing
     */
    @FlashException(value = "list", keep = true)
    public static void edit(String id) {
        ExportPathPolicyRestRep exportPathPolicyRestRep = getViprClient().exportPathPolicies().get(uri(id));
        renderArgs.put("exportPathPolicyId", id);
        renderNumPathsArgs();
        ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
        StoragePortDisplayDataTable portDataTable = dataTable.new StoragePortDisplayDataTable();
        StoragePortDisplayDataTable portSelectionDataTable = dataTable.new StoragePortDisplayDataTable();
        String storagePortsLoadId = id;

        if (exportPathPolicyRestRep != null) {
            renderArgs.put("exportPathPolicy", exportPathPolicyRestRep);
            ExportPathPolicyForm exportPathPolicyForm = new ExportPathPolicyForm().load(exportPathPolicyRestRep);
            render(exportPathPolicyForm, dataTable, portDataTable, portSelectionDataTable, storagePortsLoadId);
        } else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            exportPathPolices();
        }
    }

    /**
     * Renders a form page for duplicating the ExportPathPolicy by id.
     * 
     * @param ids a single id for the ExportPathPolicy to duplicate (don't change this var name)
     */
    public static void duplicate(String ids) {
        ExportPathPolicyRestRep exportPathPolicyRestRep = getViprClient().exportPathPolicies().get(uri(ids));
        if (exportPathPolicyRestRep == null) {
            flash.error(MessagesUtils.get(UNKNOWN, ids));
            exportPathPolices();
        }
        ExportPathPolicyForm exportPathPolicy = new ExportPathPolicyForm().load(exportPathPolicyRestRep);

        renderNumPathsArgs();
        ExportPathPoliciesDataTable dataTable = new ExportPathPoliciesDataTable();
        StoragePortDisplayDataTable portDataTable = dataTable.new StoragePortDisplayDataTable();
        StoragePortDisplayDataTable portSelectionDataTable = dataTable.new StoragePortDisplayDataTable();

        String storagePortsLoadId = ids;
        renderArgs.put("exportPathPolicyId", null);
        exportPathPolicy.id = null;
        exportPathPolicy.name = Messages.get("exportPathPolicy.duplicate.name", exportPathPolicy.name);
        render("@edit", exportPathPolicy, dataTable, portDataTable, portSelectionDataTable, storagePortsLoadId);
    }

    /**
     * Delete ExportPathPolicy objects by ids.
     * 
     * @param ids the ExportPathPolicy IDs to delete
     */
    @FlashException("list")
    public static void deleteExportPathPolicy(@As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                getViprClient().exportPathPolicies().delete(uri(id));
            }
            flash.success(MessagesUtils.get("exportPathParameters.deleted"));
        }
        exportPathPolices();
    }

    /**
     * Saves or updates the given ExportPathPolicy form.
     * 
     * @param exportPathPolicy the given ExportPathPolicy form
     */
    @FlashException(keep = true, referrer = { "edit" })
    public static void saveExportPathPolicy(ExportPathPolicyForm exportPathPolicy) {
        Logger.info("ExportPathPolicyForm: " + exportPathPolicy);
        if (exportPathPolicy == null) {
            Logger.error("No export path policy provided");
            badRequest("No export path policy provided");
            return;
        }
        Logger.info("params: " + params);

        exportPathPolicy.id = params.get("exportPathPolicy.id");
        if (exportPathPolicy.isNew()) {
            exportPathPolicy.setStoragePortsFromStringOfUris(params.get("exportPathPolicy.storagePorts"));
            ExportPathPolicy input = createExportPathPolicy(exportPathPolicy);
            getViprClient().exportPathPolicies().create(input);
        } else {
            ExportPathPolicyRestRep exportPathPolicyRestRep = getViprClient().exportPathPolicies().get(uri(exportPathPolicy.id));
            ExportPathPolicyUpdate input = updateExportPathPolicy(exportPathPolicy);
            getViprClient().exportPathPolicies().update(exportPathPolicyRestRep.getId(), input);
        }
        flash.success(MessagesUtils.get("exportPathPolicy.saved", exportPathPolicy.name));
        exportPathPolices();
    }

    /**
     * Creates an ExportPathPolicy create object.
     * 
     * @param exportPathPolicyForm the ExportPathPolicy form to use as model
     * @return the new ExportPathPolicyCreate object to send to the REST API
     */
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

    /**
     * Creates an ExportPathPolicy update object (after user updates in UI).
     * 
     * @param exportPathPolicyForm the ExportPathPolicy form to use as model
     * @return the ExportPathPolicyUpdate object to send to the REST API
     */
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

    /**
     * Handles adding storage ports to an ExportPathPolicy.
     * 
     * @param exportPathPolicyId the ExportPathPolicy id
     * @param storagePortIds a comma-separated list of new storage port URIs
     * @param eppExistingStoragePortIds a comma-separated list of already-selected storage port URIs
     * @param eppName the ExportPathPolicy name
     * @param eppDesc the ExportPathPolicy description
     * @param eppMinPaths the ExportPathPolicy min paths
     * @param eppMaxPaths the ExportPathPolicy max paths
     * @param eppPathsPerInitiator the ExportPathPolicy paths per inits
     * @param eppMaxInitiatorsPerPort the ExportPathPolicy max inits per port
     */
    @FlashException(referrer = { "edit" })
    public static void addStoragePortsToPolicy(String exportPathPolicyId, String storagePortIds, String eppExistingStoragePortIds,
            String eppName, String eppDesc,
            String eppMinPaths, String eppMaxPaths, String eppPathsPerInitiator, String eppMaxInitiatorsPerPort) {
        if (exportPathPolicyId == null || "".equals(exportPathPolicyId)) {
            ExportPathPolicy input = new ExportPathPolicy();
            input.setName(eppName);
            input.setDescription(eppDesc);
            input.setMinPaths(Integer.valueOf(eppMinPaths));
            input.setMaxPaths(Integer.valueOf(eppMaxPaths));
            input.setPathsPerInitiator(Integer.valueOf(eppPathsPerInitiator));
            input.setMaxInitiatorsPerPort(Integer.valueOf(eppMaxInitiatorsPerPort));
            if (eppExistingStoragePortIds != null && !eppExistingStoragePortIds.isEmpty()) {
                List<URI> storagePortUris = new ArrayList<URI>();
                eppExistingStoragePortIds = eppExistingStoragePortIds.replaceAll("\\[|\\]", "");
                String[] storagePorts = eppExistingStoragePortIds.split(",");
                for (String port : storagePorts) {
                    port = port.trim();
                    if (URIUtil.isValid(port)) {
                        storagePortUris.add(URI.create(port));
                    }
                }
                if (!storagePortUris.isEmpty()) {
                    input.setStoragePorts(storagePortUris);
                }
            }
            ExportPathPolicyRestRep rep = getViprClient().exportPathPolicies().create(input);
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
        ExportPathPolicyUpdate exportPathPolicyUpdate = new ExportPathPolicyUpdate();
        StoragePorts storagePorts = new StoragePorts();
        if (!portsToAdd.isEmpty()) {
            storagePorts.setStoragePorts(portsToAdd);
            exportPathPolicyUpdate.setPortsToAdd(storagePorts.getStoragePorts());
        }
        getViprClient().exportPathPolicies().update(exportPathParametersRestRep.getId(), exportPathPolicyUpdate);

        edit(exportPathPolicyId);

    }

    /**
     * Removes existing storage ports from an ExportPathPolicy.
     * 
     * @param exportPathPolicyId the id of the ExportPathPolicy
     * @param ids the storage ports to remove
     */
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

    /**
     * Renders the JSON for the ExportPathPolicy storage ports (for loading in a data table).
     * 
     * @param exportPathPolicyId the ExportPathPolicy to load storage ports from
     */
    public static void storagePortsJson(String exportPathPolicyId) {
        List<StoragePortInfo> results = Lists.newArrayList();

        if (exportPathPolicyId != null && !"null".equals(exportPathPolicyId)) {
            ExportPathPolicyRestRep exportPathParametersRestRep = getViprClient().exportPathPolicies().get(uri(exportPathPolicyId));
            List<URI> storagePortUris = exportPathParametersRestRep.getStoragePorts();
            CachedResources<StorageSystemRestRep> storageSystems = StorageSystemUtils.createCache();

            List<StoragePortRestRep> storagePorts = StoragePortUtils
                    .getStoragePorts(storagePortUris);
            for (StoragePortRestRep storagePort : storagePorts) {
                results.add(new StoragePortInfo(storagePort, storageSystems.get(storagePort.getStorageDevice())));
            }
        }

        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    /**
     * Renders JSON for all the storage ports available in the system.
     * 
     * @param exporthPathPolicyId the ExportPathPolicy to load storage ports from.
     */
    public static void availablePortsJson(String exporthPathPolicyId) {
        List<StoragePortInfo> results = Lists.newArrayList();
        List<StoragePortRestRep> storagePorts = StoragePortUtils.getStoragePorts();

        if (exporthPathPolicyId != null && !"null".equals(exporthPathPolicyId)) {
            ExportPathPolicyRestRep exportPathParametersRestRep = getViprClient().exportPathPolicies().get(uri(exporthPathPolicyId));
            List<URI> storagePortUris = exportPathParametersRestRep.getStoragePorts();
            List<StoragePortRestRep> portsNotForSelection = StoragePortUtils.getStoragePorts(storagePortUris);
            storagePorts.removeAll(portsNotForSelection);
        }

        CachedResources<StorageSystemRestRep> storageSystems = StorageSystemUtils.createCache();

        for (StoragePortRestRep storagePort : storagePorts) {
            results.add(new StoragePortInfo(storagePort, storageSystems.get(storagePort.getStorageDevice())));
        }

        renderJSON(DataTablesSupport.createJSON(results, params));
    }
}
