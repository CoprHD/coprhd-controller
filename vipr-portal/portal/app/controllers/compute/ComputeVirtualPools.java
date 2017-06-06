/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.compute;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.flashException;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.compute.ComputeElementListRestRep;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import com.emc.storageos.model.pools.VirtualArrayAssignments;
import com.emc.storageos.model.vpool.ComputeVirtualPoolCreateParam;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.ServiceProfileTemplateAssignmentChanges;
import com.emc.storageos.model.vpool.ServiceProfileTemplateAssignments;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.emc.vipr.client.exceptions.ViPRException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;
import jobs.vipr.TenantsCall;
import models.ComputeSystemTypes;
import models.DriveTypes;
import models.PoolAssignmentTypes;
import models.SearchScopes;
import models.StorageSystemTypes;
import models.datatable.ComputeVirtualPoolElementDataTable;
import models.datatable.ComputeVirtualPoolElementDataTable.ComputeVirtualElementInfo;
import models.datatable.ComputeVirtualPoolsDataTable;
import models.datatable.ComputeVirtualPoolsDataTable.VirtualPoolInfo;
import play.Logger;
import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.BourneUtil;
import util.ComputeSystemUtils;
import util.ComputeVirtualPoolUtils;
import util.MessagesUtils;
import util.StringOption;
import util.TenantUtils;
import util.VirtualArrayUtils;
import util.VirtualPoolUtils;
import util.builders.ACLUpdateBuilder;
import util.datatable.DataTablesSupport;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class ComputeVirtualPools extends ViprResourceController {

    protected static final String SAVED = "ComputeVirtualPools.saved";
    protected static final String DELETED = "ComputeVirtualPools.deleted";
    protected static final String DELETED_ERROR = "ComputeVirtualPools.deleted.error";
    protected static final String UNKNOWN = "ComputeVirtualPools.unknown";
    protected static final String MODEL_NAME = "ComputeVirtualPools";

    //
    // Add reference data so that they can be reference in html template
    //

    private static void addReferenceData() {
        renderArgs.put("computeSystemTypeList",
                ComputeSystemTypes.options(ComputeSystemTypes.VALUES));
        renderArgs.put("searchScopeTypeList", SearchScopes.options(
                SearchScopes.ONELEVEL, SearchScopes.SUBTREE));
    }

    /**
     * if it was not redirect from another page, clean flash
     * 
     * @param redirect
     */
    public static void list() {
        renderArgs.put("dataTable", new ComputeVirtualPoolsDataTable());
        render();
    }

    public static void listJson() {
        performListJson(ComputeVirtualPoolUtils.getComputeVirtualPools(),
                new JsonItemOperation());
    }

    public static void create() {
        ComputeVirtualPoolsForm computeVirtualPool = new ComputeVirtualPoolsForm();

        // initializers
        addReferenceData();
        addStaticOptions();
        addDynamicOptions(computeVirtualPool);
        computeVirtualPool.useMatchedElements = Boolean.TRUE;
        renderArgs.put("computeVirtualPoolElementDataTable", createComputeVirtualPoolElementDataTable());
        render("@edit", computeVirtualPool);
    }

    public static void createx(String selectedTemplates) {
        ComputeVirtualPoolsForm computeVirtualPool = new ComputeVirtualPoolsForm();
        // initializers
        addReferenceData();
        addStaticOptions();
        addDynamicOptions(computeVirtualPool);
        computeVirtualPool.selectedTemplates = selectedTemplates;
        renderArgs.put("computeVirtualPoolElementDataTable", createComputeVirtualPoolElementDataTable());
        render("@edit", computeVirtualPool);
    }

    private static ComputeVirtualPoolElementDataTable createComputeVirtualPoolElementDataTable() {
        ComputeVirtualPoolElementDataTable dataTable = new ComputeVirtualPoolElementDataTable();
        dataTable.alterColumn("computeVirtualPool").hidden();
        dataTable.alterColumn("computeSystem").setVisible(true);
        dataTable.setDefaultSort("computeSystem", "asc");
        dataTable.alterColumn("name").hidden();

        return dataTable;
    }

    public static void edit(String id) {
        try {
            addReferenceData();
            ComputeVirtualPoolRestRep computeVirtualPool = ComputeVirtualPoolUtils
                    .getComputeVirtualPool(id);
            if (computeVirtualPool == null) {
                flash.error(MessagesUtils.get(UNKNOWN, id));
                list();
            }
            ComputeVirtualPoolsForm form = new ComputeVirtualPoolsForm(computeVirtualPool);
            form.loadTenant(computeVirtualPool);
            edit(form);

        } catch (Exception e) {
            flashException(e);
            list();
        }
    }

    private static void edit(ComputeVirtualPoolsForm computeVirtualPool) {
        addStaticOptions();
        addDynamicOptions(computeVirtualPool);
        renderArgs.put("computeVirtualPoolElementDataTable", createComputeVirtualPoolElementDataTable());
        render("@edit", computeVirtualPool);
    }

    public static void elementDetails(String id) {
        ComputeElementRestRep computeElement = ComputeSystemUtils
                .getComputeElement(id);
        if (computeElement == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        render(computeElement);
    }

    public static void computeVirtualPoolDetails(String id) {
        ComputeVirtualPoolRestRep computeVirtualPool = ComputeVirtualPoolUtils
                .getComputeVirtualPool(id);
        if (computeVirtualPool == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        List<NamedRelatedResourceRep> temps = computeVirtualPool.getServiceProfileTemplates();

        List<RelatedResourceRep> varrays = computeVirtualPool.getVirtualArrays();
        StringBuilder selectedTemplatesBuilder = new StringBuilder();
        for (RelatedResourceRep varray : varrays) {

            List<ComputeSystemRestRep> arrayComputes = VirtualArrayUtils.getComputeSystems(varray.getId());
            for (ComputeSystemRestRep acomp : arrayComputes) {
                for (NamedRelatedResourceRep spt : acomp.getServiceProfileTemplates()) {
                    if (CollectionUtils.isNotEmpty(temps)) {
                        for (NamedRelatedResourceRep template : temps) {
                            if (spt.getId().equals(template.getId())) {
                                selectedTemplatesBuilder.append(acomp.getName()).append(" - ").append(template.getName())
                                        .append(", ");
                            }
                        }
                    }
                }
            }
        }
        String selectedTemplatesString = StringUtils.stripEnd(selectedTemplatesBuilder.toString(), ", ");
        render(computeVirtualPool, selectedTemplatesString);
    }

    private static void handleError(ComputeVirtualPoolsForm computeVirtualPool) {
        Validation.keep();
        if (computeVirtualPool.isNew()) {
            createx(computeVirtualPool.selectedTemplates);
        } else {
            edit(computeVirtualPool.id);
        }
    }

    public static void save(ComputeVirtualPoolsForm computeVirtualPool) {
        try {
            computeVirtualPool.validate("computeVirtualPool");
            if (Validation.hasErrors()) {
                Logger.info("has errors error: %s", Validation.errors().toString());
                handleError(computeVirtualPool);
            }

            ComputeVirtualPoolRestRep vpool = computeVirtualPool.save();
            if (TenantUtils.canReadAllTenants() && VirtualPoolUtils.canUpdateACLs()) {
                saveTenantACLs(vpool.getId().toString(), computeVirtualPool.tenants, computeVirtualPool.enableTenants);
            }
            flash.success(MessagesUtils.get(SAVED, computeVirtualPool.name));
            list();
        } catch (Exception e) {
            flashException(e);
            handleError(computeVirtualPool);
        }
    }

    /**
     * Saves tenant ACLs on the virtual compute pool.
     * 
     * @param vpoolId
     *            the virtual compute pool ID.
     * @param tenants
     *            the tenant ACLs.
     * @param enableTenants
     *            the checked status for "Grant Access to Tenants".
     */
    private static void saveTenantACLs(String vpoolId, List<String> tenants, Boolean enableTenants) {
        Set<String> tenantIds = Sets.newHashSet();
        if (isTrue(enableTenants) && tenants != null) {
            tenantIds.addAll(tenants);
        }
        ACLUpdateBuilder builder = new ACLUpdateBuilder(ComputeVirtualPoolUtils.getComputeACLs(vpoolId));
        builder.setTenants(tenantIds);

        try {
            ComputeVirtualPoolUtils.updateComputeACLs(vpoolId, builder.getACLUpdate());
        } catch (ViPRException e) {
            Logger.error(e, "Failed to update Compute Virtual Pool ACLs");
            String errorDesc = e.getMessage();
            if (e instanceof ServiceErrorException) {
                errorDesc = ((ServiceErrorException) e).getDetailedMessage();
            }
            flash.error(MessagesUtils.get("computeVirtualPool.updateComputeVirtualPoolACLs.failed", errorDesc));
        }
    }

    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        performSuccessFail(ids, new DeleteOperation(), DELETED, DELETED_ERROR);
        list();
    }

    public static void listElements(String computePoolId) {
        ComputeVirtualPoolRestRep computeVirtualPool = ComputeVirtualPoolUtils
                .getComputeVirtualPool(computePoolId);
        ComputeVirtualPoolElementDataTable dataTable = new ComputeVirtualPoolElementDataTable();
        render("@listElements", computeVirtualPool, dataTable);
    }

    public static void elements(String id) {
        addReferenceData();

        ComputeVirtualPoolRestRep computeVirtualPool = ComputeVirtualPoolUtils
                .getComputeVirtualPool(id);
        ComputeVirtualPoolElementDataTable dataTable = new ComputeVirtualPoolElementDataTable();
        render("@listElements", computeVirtualPool, dataTable);
    }

    public static void listComputePoolElements(String id) {
        List<ComputeVirtualElementInfo> results = Lists.newArrayList();
        List<ComputeElementRestRep> allComputeElements = (List<ComputeElementRestRep>) ComputeSystemUtils
                .getAllComputeElements();
        ComputeVirtualPoolRestRep computePool = ComputeVirtualPoolUtils
                .getComputeVirtualPool(id);
        List<RelatedResourceRep> matchedElements = computePool
                .getMatchedComputeElements();

        for (RelatedResourceRep poolElement : matchedElements) {
            for (ComputeElementRestRep element : allComputeElements) {
                if (element.getId().equals(poolElement.getId())) {
                    results.add(new ComputeVirtualElementInfo(element,
                            computePool.getName(), (String) "No Name yet"));
                    break;
                }
            }
        }
        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    public static void getServiceProfileTemplates(ComputeVirtualPoolsForm computeVirtualPool) {
        List<ComputeSystemRestRep> allComputes = Lists.newArrayList();
        List<StringOption> templateList = Lists.newArrayList();
        List<String> temps = Lists.newArrayList();
        if (computeVirtualPool.id != null) {
            ComputeVirtualPoolRestRep computePool = ComputeVirtualPoolUtils
                    .getComputeVirtualPool(computeVirtualPool.id);
            for (NamedRelatedResourceRep tmp : computePool.getServiceProfileTemplates()) {
                temps.add(tmp.getId().toString());
            }
        }

        Map<String, Set<String>> csTemplatesMap = new HashMap<String, Set<String>>();
        Map<String, String> computeSystemsMap = new HashMap<String, String>();
        Map<String, String> templatesMap = new HashMap<String, String>();

        if (computeVirtualPool.virtualArrays != null) {
            for (String arrayId : computeVirtualPool.virtualArrays) {
                List<ComputeSystemRestRep> arrayComputes = VirtualArrayUtils.getComputeSystems(uri(arrayId));

                for (ComputeSystemRestRep acomp : arrayComputes) {
                    String compId = acomp.getId().toString();
                    if (!computeSystemsMap.containsKey(compId)) {
                        computeSystemsMap.put(compId, acomp.getName());
                    }
                    Set<String> spts = csTemplatesMap.get(compId);
                    if (spts == null) {
                        spts = new HashSet<String>();
                    }
                    for (NamedRelatedResourceRep spt : acomp.getServiceProfileTemplates()) {
                        spts.add(spt.getId().toString());
                        if (!templatesMap.containsKey(spt.getId().toString())) {
                            templatesMap.put(spt.getId().toString(), spt.getName());
                        }
                    }
                    csTemplatesMap.put(compId, spts);
                }

            }

            for (Entry<String, Set<String>> comp : csTemplatesMap.entrySet()) {
                Set<String> compTemplates = comp.getValue();
                if (compTemplates != null && !compTemplates.isEmpty()) {
                    String systemName = ComputeSystemTypes.getDisplayValue(ComputeSystemTypes.UCS) + " " + computeSystemsMap.get(comp.getKey());
                    computeVirtualPool.systems.add(new StringOption(comp.getKey(), systemName));
                    List<StringOption> templateOptions = Lists.newArrayList();
                    templateOptions.add(new StringOption("NONE", ""));
                    for (String template : compTemplates) {
                        templateOptions.add(new StringOption(template, templatesMap.get(template)));
                        if (!temps.isEmpty()) {
                            for (String templateId : temps) {
                                if (templateId.contains(template)) {
                                    templateList.add(new StringOption(comp.getKey(), template));
                                }
                            }
                        }
                    }
                    computeVirtualPool.systemOptions.put(comp.getKey(), templateOptions);
                }

            }

            computeVirtualPool.selectedTemplates = "{}";
            if (!templateList.isEmpty()) {
                String jsonString = "{\"";

                for (int index = 0; index < templateList.size(); index++) {
                    if (jsonString.indexOf("urn") > 0) {
                        jsonString = jsonString + ",\"";
                    }
                    jsonString = jsonString + templateList.get(index).id + "\":\"" + templateList.get(index).name + "\"";
                }
                jsonString = jsonString + "}";
                computeVirtualPool.selectedTemplates = jsonString;
            }
        }
        else {
            computeVirtualPool.selectedTemplates = "{}";
        }
        render("@templates", computeVirtualPool);

    }

    public static void listComputeElementsJson(String cvpid, ComputeVirtualPoolsForm computeVirtualPool) {

        List<ComputeVirtualElementInfo> results = Lists.newArrayList();
        computeVirtualPool.validateQualifiers("computeVirtualPool");
        if (Validation.hasErrors()) {
            // cannot call the errorhandler from inside the json call
            Logger.info("has errors error: %s", Validation.errors().toString());
        }
        else {
            List<ComputeSystemRestRep> allComputes = ComputeSystemUtils.getComputeSystems();
            List<ComputeElementRestRep> allComputeElements = (List<ComputeElementRestRep>) ComputeSystemUtils
                    .getAllComputeElements();

            if (NullColumnValueGetter.isNotNullValue(cvpid)) {
                computeVirtualPool.id =  NullColumnValueGetter.getStringValue(cvpid);
                // We always show the full list of matching elements - in case of manual - selected ones will be checked
                List<ComputeElementRestRep> matchedElements = ComputeVirtualPoolUtils.listMatchingComputeElements(computeVirtualPool
                        .createMatch());

                for (ComputeElementRestRep element : allComputeElements) {
                    String computeSystemName = ": " + element.getName();
                    for (ComputeSystemRestRep compSys : allComputes) {
                        if (compSys.getId().equals(element.getComputeSystem().getId())) {
                            computeSystemName = compSys.getName() + computeSystemName;
                            break;
                        }
                    }

                    for (ComputeElementRestRep filteredElement : matchedElements) {
                        if (filteredElement.getId().equals(element.getId())) {
                            results.add(new ComputeVirtualElementInfo(element,
                                    element.getName(), computeSystemName));
                            break;
                        }
                    }
                }
            }
            else {
                List<ComputeElementRestRep> matchedElements = ComputeVirtualPoolUtils.listMatchingComputeElements(computeVirtualPool
                        .createMatch());
                for (ComputeElementRestRep elementA : allComputeElements) {
                    String computeSystemName = ": " + elementA.getName();
                    for (ComputeSystemRestRep compSys : allComputes) {
                        if (compSys.getId().equals(elementA.getComputeSystem().getId())) {
                            computeSystemName = compSys.getName() + computeSystemName;
                            break;
                        }
                    }
                    for (ComputeElementRestRep elementM : matchedElements) {
                        if (elementM.getId().toString().equals(elementA.getId().toString())) {
                            results.add(new ComputeVirtualElementInfo(elementA,
                                    elementA.getName(), computeSystemName));
                        }
                    }
                }
            }
        }
        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    public static class ComputeVirtualPoolsForm {

        public String id;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        public String description;

        public Integer minProcessors;
        public Integer maxProcessors;
        public Integer minTotalCores;
        public Integer maxTotalCores;
        public Integer minTotalThreads;
        public Integer maxTotalThreads;
        public Integer minCpuSpeed;
        public Integer maxCpuSpeed;
        public Integer minMemory;
        public Integer maxMemory;
        public Integer minNics;
        public Integer maxNics;
        public Integer minHbas;
        public Integer maxHbas;

        public String systemType;
        public String elementSelection;

        public List<String> virtualArrays;

        public Boolean enableTenants = Boolean.FALSE;
        public List<String> tenants = new ArrayList<String>();

        public List<String> computeElements = Lists.newArrayList();;

        public List<StringOption> systems = Lists.newArrayList();
        public Map<String, List<StringOption>> systemOptions = Maps.newHashMap();

        public String selectedTemplates;
        public Boolean useMatchedElements;

        public Boolean inUse;

        public ComputeVirtualPoolsForm() {
        }

        public ComputeVirtualPoolsForm(
                ComputeVirtualPoolRestRep computeVirtualPool) {
            this.id = computeVirtualPool.getId().toString();
            this.inUse = computeVirtualPool.getInUse();
            this.name = computeVirtualPool.getName();
            this.description = computeVirtualPool.getDescription();
            this.minProcessors = computeVirtualPool.getMinProcessors();
            this.maxProcessors = computeVirtualPool.getMaxProcessors();
            this.minTotalCores = computeVirtualPool.getMinTotalCores();
            this.maxTotalCores = computeVirtualPool.getMaxTotalCores();
            this.minTotalThreads = computeVirtualPool.getMinTotalThreads();
            this.maxTotalThreads = computeVirtualPool.getMaxTotalThreads();
            this.minCpuSpeed = computeVirtualPool.getMinCpuSpeed();
            this.maxCpuSpeed = computeVirtualPool.getMaxCpuSpeed();
            this.minMemory = computeVirtualPool.getMinMemory();
            this.maxMemory = computeVirtualPool.getMaxMemory();
            this.minNics = computeVirtualPool.getMinNics();
            this.maxNics = computeVirtualPool.getMaxNics();
            this.minHbas = computeVirtualPool.getMinHbas();
            this.maxHbas = computeVirtualPool.getMaxHbas();
            this.systemType = computeVirtualPool.getSystemType();

            this.virtualArrays = ResourceUtils.stringRefIds(computeVirtualPool.getVirtualArrays());
            this.useMatchedElements = computeVirtualPool.getUseMatchedElements();
            if (computeVirtualPool.getUseMatchedElements()) {
                this.elementSelection = PoolAssignmentTypes.AUTOMATIC;
            } else {
                this.elementSelection = PoolAssignmentTypes.MANUAL;
                ComputeElementListRestRep elementList = ComputeVirtualPoolUtils.getAssignedComputeElements(computeVirtualPool.getId()
                        .toString());
                for (ComputeElementRestRep ce : elementList.getList()) {
                    this.computeElements.add(ce.getId().toString());
                }
            }
        }

        public boolean isNew() {
            return StringUtils.isBlank(this.id);
        }

        private void loadTenant(ComputeVirtualPoolRestRep computeVirtualPool) {
            List<ACLEntry> acls = BourneUtil.getViprClient().computeVpools().getACLs(computeVirtualPool.getId());
            for (ACLEntry acl : acls) {
                if (acl.getTenant() != null) {
                    this.tenants.add(acl.getTenant());
                }
            }

            if (!tenants.isEmpty()) {
                this.enableTenants = true;
            }
        }

        public ComputeVirtualPoolRestRep save() {
            ComputeVirtualPoolRestRep computeVirtualPool;
            if (isNew()) {
                computeVirtualPool = create();
                this.id = ResourceUtils.stringId(computeVirtualPool);
                // return create();
            } else {
                computeVirtualPool = update();
                computeVirtualPool = ComputeVirtualPoolUtils.getComputeVirtualPool(id);
                // return update();
            }
            computeVirtualPool = saveComputeElements(computeVirtualPool);
            return computeVirtualPool;
        }

        @SuppressWarnings("unchecked")
        private ComputeVirtualPoolRestRep update() {
            ComputeVirtualPoolUpdateParam param = new ComputeVirtualPoolUpdateParam();
            ComputeVirtualPoolRestRep oldComputePool = ComputeVirtualPoolUtils.getComputeVirtualPool(id);
            param.setName(this.name);
            param.setDescription(StringUtils.trimToNull(this.description));
            // when the virtualComputePool is inuse - keep the setting the same since we don't allow them to change it - protecting against
            // npe
            if (oldComputePool.getInUse() || StringUtils.isEmpty(this.elementSelection)) {
                param.setUseMatchedElements(oldComputePool.getUseMatchedElements());
            }
            else {
                if (this.elementSelection.equalsIgnoreCase(PoolAssignmentTypes.MANUAL)) {
                    param.setUseMatchedElements(false);
                } else {
                    param.setUseMatchedElements(true);
                }
            }

            setUpdateQualifiers(oldComputePool, param);
            List<String> newArrays = Lists.newArrayList();
            if (this.virtualArrays != null) {
                for (String vaobj : this.virtualArrays) {
                    newArrays.add(vaobj);
                }
            }
            List<String> oldArrays = ResourceUtils.stringRefIds(oldComputePool.getVirtualArrays());
            Set<String> add = Sets.newHashSet(CollectionUtils.subtract(newArrays, oldArrays));
            Set<String> remove = Sets.newHashSet(CollectionUtils.subtract(oldArrays, newArrays));
            VirtualArrayAssignmentChanges changes = new VirtualArrayAssignmentChanges();
            if (!add.isEmpty()) {
                changes.setAdd(new VirtualArrayAssignments(add));
            }
            if (!remove.isEmpty()) {
                changes.setRemove(new VirtualArrayAssignments(remove));
            }
            param.setVarrayChanges(changes);
            List<String> templates = Lists.newArrayList();
            if (this.selectedTemplates.indexOf("urn") > 0) {
                // when qualifiers change but selectedTemplates do not - fix the selectedTemplate string
                if (this.selectedTemplates.indexOf(",") == 0) {
                    this.selectedTemplates = this.selectedTemplates.substring(1);
                }
                // HashMap<String, String> map = (HashMap<String, String>) JSON.parse(this.selectedTemplates);
                try {
                    String jsonString = this.selectedTemplates.substring(0, this.selectedTemplates.indexOf("}") + 1);
                    Gson gson = new Gson();
                    Type type = new TypeToken<Map<String, String>>() {
                    }.getType();
                    Map<String, String> map = gson.fromJson(jsonString, type);

                    for (Iterator<String> it = map.values().iterator(); it.hasNext();) {
                        Object value = it.next();
                        if (!(value.equals("NONE"))) {
                            templates.add(value.toString());
                        }
                    }
                } catch (Exception e) {
                    flashException(e);
                }
            }
            List<String> oldTemplates = ResourceUtils.stringRefIds(oldComputePool.getServiceProfileTemplates());
            Set<String> addSPT = Sets.newHashSet(CollectionUtils.subtract(templates, oldTemplates));
            Set<String> removeSPT = Sets.newHashSet(CollectionUtils.subtract(oldTemplates, templates));
            ServiceProfileTemplateAssignmentChanges sptChanges = new ServiceProfileTemplateAssignmentChanges();
            if (!addSPT.isEmpty()) {
                sptChanges.setAdd(new ServiceProfileTemplateAssignments(addSPT));
            }
            if (!removeSPT.isEmpty()) {
                sptChanges.setRemove(new ServiceProfileTemplateAssignments(removeSPT));
            }
            param.setSptChanges(sptChanges);

            return ComputeVirtualPoolUtils.update(id, param);
        }

        private ComputeVirtualPoolRestRep create() {
            ComputeVirtualPoolCreateParam param = new ComputeVirtualPoolCreateParam();
            param.setName(StringUtils.trimToNull(this.name));
            param.setDescription(StringUtils.trimToNull(this.description));
            param.setSystemType("Cisco_UCSM"); // StringUtils.defaultIfEmpty(systemType, ComputeSystemTypes.UCS));
            if (virtualArrays != null) {
                param.setVarrays(Sets.newHashSet(virtualArrays));
            }
            else {
                param.setVarrays(Sets.<String> newHashSet());
            }

            param.setMinProcessors(this.minProcessors);
            param.setMaxProcessors(this.maxProcessors);
            param.setMinTotalCores(this.minTotalCores);
            param.setMaxTotalCores(this.maxTotalCores);
            param.setMinTotalThreads(this.minTotalThreads);
            param.setMaxTotalThreads(this.maxTotalThreads);
            param.setMinCpuSpeed(this.minCpuSpeed);
            param.setMaxCpuSpeed(this.maxCpuSpeed);
            param.setMinMemory(this.minMemory);
            param.setMaxMemory(this.maxMemory);
            param.setMinNics(this.minNics);
            param.setMaxNics(this.maxNics);
            param.setMinHbas(this.minHbas);
            param.setMaxHbas(this.maxHbas);
            if (this.elementSelection.equalsIgnoreCase(PoolAssignmentTypes.MANUAL)) {
                param.setUseMatchedElements(false);
            } else {
                param.setUseMatchedElements(true);
            }
            List<String> templates = Lists.newArrayList();
            if (this.selectedTemplates.indexOf("urn") > 0) {
                // HashMap<String, String> map = (HashMap<String, String>) JSON.parse(this.selectedTemplates);
                try {
                    String jsonString = this.selectedTemplates.substring(0, this.selectedTemplates.indexOf("}") + 1);
                    Gson gson = new Gson();
                    Type type = new TypeToken<Map<String, String>>() {
                    }.getType();
                    Map<String, String> map = gson.fromJson(jsonString, type);

                    for (Iterator<String> it = map.values().iterator(); it.hasNext();) {
                        Object value = it.next();
                        if (!(value.equals("NONE"))) {
                            templates.add(value.toString());
                        }
                    }
                } catch (Exception e) {
                    flashException(e);
                }
            }

            param.setServiceProfileTemplates(Sets.newHashSet(templates));

            return ComputeVirtualPoolUtils.create(param);
        }

        private ComputeVirtualPoolCreateParam createMatch() {
            ComputeVirtualPoolCreateParam param = new ComputeVirtualPoolCreateParam();
            param.setName(StringUtils.trimToNull(this.name));
            param.setDescription(StringUtils.trimToNull(this.description));
            param.setSystemType("Cisco_UCSM"); // StringUtils.defaultIfEmpty(systemType, ComputeSystemTypes.UCS));
            if (virtualArrays != null) {
                param.setVarrays(Sets.newHashSet(virtualArrays));
            }
            else {
                param.setVarrays(Sets.<String> newHashSet());
            }
            if(NullColumnValueGetter.isNotNullValue(this.id)) {
                param.setId(this.id);
            }

            param.setMinProcessors(this.minProcessors);
            param.setMaxProcessors(this.maxProcessors);
            param.setMinTotalCores(this.minTotalCores);
            param.setMaxTotalCores(this.maxTotalCores);
            param.setMinTotalThreads(this.minTotalThreads);
            param.setMaxTotalThreads(this.maxTotalThreads);
            param.setMinCpuSpeed(this.minCpuSpeed);
            param.setMaxCpuSpeed(this.maxCpuSpeed);
            param.setMinMemory(this.minMemory);
            param.setMaxMemory(this.maxMemory);
            param.setMinNics(this.minNics);
            param.setMaxNics(this.maxNics);
            param.setMinHbas(this.minHbas);
            param.setMaxHbas(this.maxHbas);
            param.setUseMatchedElements(true);

            return param;
        }

        private void setUpdateQualifiers(ComputeVirtualPoolRestRep oldComputePool, ComputeVirtualPoolUpdateParam param) {
            // send all qualifiers (changed or not)
            param.setMinProcessors(this.minProcessors);
            param.setMaxProcessors(this.maxProcessors);
            param.setMinTotalCores(this.minTotalCores);
            param.setMaxTotalCores(this.maxTotalCores);
            param.setMinTotalThreads(this.minTotalThreads);
            param.setMaxTotalThreads(this.maxTotalThreads);
            param.setMinCpuSpeed(this.minCpuSpeed);
            param.setMaxCpuSpeed(this.maxCpuSpeed);
            param.setMinMemory(this.minMemory);
            param.setMaxMemory(this.maxMemory);
            param.setMinNics(this.minNics);
            param.setMaxNics(this.maxNics);
            param.setMinHbas(this.minHbas);
            param.setMaxHbas(this.maxHbas);
        }

        /**
         * Saves the compute elements associated with the given virtual compute pool.
         * 
         * @param pool
         *            the virtual pool.
         */
        public ComputeVirtualPoolRestRep saveComputeElements(ComputeVirtualPoolRestRep pool) {
            Set<String> oldValues = Sets.newHashSet(ResourceUtils.stringRefIds(pool.getMatchedComputeElements()));
            Set<String> newValues = Sets.newHashSet();
            if (isFalse(pool.getUseMatchedElements())) {
                if (computeElements != null) {
                    newValues.addAll(computeElements);
                }
            }

            Set<String> add = Sets.difference(newValues, oldValues);
            Set<String> remove = Sets.difference(oldValues, newValues);
            // Don't bother updating if there is nothing to add/remove
            if (isFalse(pool.getUseMatchedElements()) && (!add.isEmpty() || !remove.isEmpty())) {
                pool = updateComputeElements(add, remove);
            }
            return pool;
        }

        public ComputeVirtualPoolRestRep updateComputeElements(Set<String> add, Set<String> remove) {
            return ComputeVirtualPoolUtils.updateAssignedComputeElements(id, add, remove);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);
            validateQualifiers(fieldName);
        }

        public void validateQualifiers(String fieldName) {
            if (this.minProcessors != null && (this.minProcessors <= 0 || this.minProcessors > 65535)) {
                Validation.addError(fieldName + ".minProcessors",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.maxProcessors != null && (this.maxProcessors <= 0 || this.maxProcessors > 65535)) {
                Validation.addError(fieldName + ".maxProcessors",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.minTotalCores != null && (this.minTotalCores <= 0 || this.minTotalCores > 65535)) {
                Validation.addError(fieldName + ".minTotalCores",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.maxTotalCores != null && (this.maxTotalCores <= 0 || this.maxTotalCores > 65535)) {
                Validation.addError(fieldName + ".maxTotalCores",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.minTotalThreads != null && (this.minTotalThreads <= 0 || this.minTotalThreads > 65535)) {
                Validation.addError(fieldName + ".minTotalThreads",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.maxTotalThreads != null && (this.maxTotalThreads <= 0 || this.maxTotalThreads > 65535)) {
                Validation.addError(fieldName + ".maxTotalThreads",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.minCpuSpeed != null && (this.minCpuSpeed <= 0 || this.minCpuSpeed > 65535)) {
                Validation.addError(fieldName + ".minCpuSpeed",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.maxCpuSpeed != null && (this.maxCpuSpeed <= 0 || this.maxCpuSpeed > 65535)) {
                Validation.addError(fieldName + ".maxCpuSpeed",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.minMemory != null && (this.minMemory <= 0 || this.minMemory > 65535)) {
                Validation.addError(fieldName + ".minMemory",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.maxMemory != null && (this.maxMemory <= 0 || this.maxMemory > 65535)) {
                Validation.addError(fieldName + ".maxMemory",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.minNics != null && (this.minNics <= 0 || this.minNics > 65535)) {
                Validation.addError(fieldName + ".minNics",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.maxNics != null && (this.maxNics <= 0 || this.maxNics > 65535)) {
                Validation.addError(fieldName + ".maxNics",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.minHbas != null && (this.minHbas <= 0 || this.minHbas > 65535)) {
                Validation.addError(fieldName + ".minHbas",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.maxHbas != null && (this.maxHbas <= 0 || this.maxHbas > 65535)) {
                Validation.addError(fieldName + ".maxHbas",
                        MessagesUtils.get("computeVirtualPool.range"));
            }
            if (this.minProcessors != null && this.maxProcessors != null && this.minProcessors > this.maxProcessors) {
                Validation.addError(fieldName + ".minProcessors",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
                Validation.addError(fieldName + ".maxProcessors",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
            }
            if (this.minTotalCores != null && this.maxTotalCores != null && this.minTotalCores > this.maxTotalCores) {
                Validation.addError(fieldName + ".minTotalCores",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
                Validation.addError(fieldName + ".maxTotalCores",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
            }
            if (this.minTotalThreads != null && this.maxTotalThreads != null && this.minTotalThreads > this.maxTotalThreads) {
                Validation.addError(fieldName + ".minTotalThreads",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
                Validation.addError(fieldName + ".maxTotalThreads",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
            }
            if (this.minCpuSpeed != null && this.maxCpuSpeed != null && this.minCpuSpeed > this.maxCpuSpeed) {
                Validation.addError(fieldName + ".minCpuSpeed",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
                Validation.addError(fieldName + ".maxCpuSpeed",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
            }
            if (this.minMemory != null && this.maxMemory != null && this.minMemory > this.maxMemory) {
                Validation.addError(fieldName + ".minMemory",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
                Validation.addError(fieldName + ".maxMemory",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
            }
            if (this.minNics != null && this.maxNics != null && this.minNics > this.maxNics) {
                Validation.addError(fieldName + ".minNics",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
                Validation.addError(fieldName + ".maxNics",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
            }
            if (this.minHbas != null && this.maxHbas != null && this.minHbas > this.maxHbas) {
                Validation.addError(fieldName + ".minHbas",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
                Validation.addError(fieldName + ".maxHbas",
                        MessagesUtils.get("computeVirtualPool.minAndmax"));
            }
        }

        public Map<String, Set<String>> getVirtualPoolAttributes() {
            Map<String, Set<String>> allAttributes = VirtualArrayUtils.getAvailableAttributes(uris(virtualArrays));
            Map<String, Set<String>> attributes = Maps.newHashMap();
            attributes.put("driveType", Sets.newHashSet(allAttributes.get(VirtualArrayUtils.ATTRIBUTE_DRIVE_TYPES)));
            attributes.get("driveType").add(DriveTypes.NONE);
            attributes.put("protocols", Sets.newHashSet(allAttributes.get(VirtualArrayUtils.ATTRIBUTE_PROTOCOLS)));
            attributes.put("raidLevels", Sets.newHashSet(allAttributes.get(VirtualArrayUtils.ATTRIBUTE_RAID_LEVELS)));
            attributes.put("systemType", Sets.newHashSet(allAttributes.get(VirtualArrayUtils.ATTRIBUTE_SYSTEM_TYPES)));
            attributes.get("systemType").add(StorageSystemTypes.NONE);

            return attributes;
        }

        public Map<String, Set<String>> getTemplateAttributes(List<String> templateInfo) {

            Map<String, Set<String>> attrs = Maps.newHashMap();
            attrs.put("templateOptions", Sets.newHashSet(templateInfo));
            attrs.get("templateOptions").add(DriveTypes.NONE);

            return attrs;
        }

    }

    protected static class JsonItemOperation implements
            ResourceValueOperation<VirtualPoolInfo, ComputeVirtualPoolRestRep> {
        @Override
        public VirtualPoolInfo performOperation(
                ComputeVirtualPoolRestRep computeVirtualPool) throws Exception {
            return new VirtualPoolInfo(computeVirtualPool);
        }
    }

    public static void listVirtualArrayAttributesJson(ComputeVirtualPoolsForm computeVirtualPool) {
        if (computeVirtualPool == null) {
            renderJSON(Collections.emptyList());
        }
        renderJSON(computeVirtualPool.getVirtualPoolAttributes());// NOSONAR
                                                                  // ("Suppressing Sonar violation of Possible null pointer deference of computeVirtualPool")
    }

    private static void addDynamicOptions(ComputeVirtualPoolsForm vpool) {
        renderArgs.put("virtualArrayOptions", dataObjectOptions(VirtualArrayUtils.getVirtualArrays()));
        if (TenantUtils.canReadAllTenants() && VirtualPoolUtils.canUpdateACLs()) {
            addDataObjectOptions("tenantOptions", new TenantsCall().asPromise());
        }
    }

    private static void addStaticOptions() {
        renderArgs.put("poolAssignmentOptions", PoolAssignmentTypes.options(
                PoolAssignmentTypes.AUTOMATIC,
                PoolAssignmentTypes.MANUAL
                ));
    }

    protected static class DeleteOperation implements ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            ComputeVirtualPoolUtils.deactivateCompute(id);
            return null;
        }
    }

}
