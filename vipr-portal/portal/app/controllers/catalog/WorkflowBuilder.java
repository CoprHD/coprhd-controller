/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package controllers.catalog;

import static util.BourneUtil.getCatalogClient;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.owasp.esapi.ESAPI;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam.InputCreateList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.model.customservices.CustomServicesValidationResponse;
import com.emc.storageos.model.customservices.CustomServicesWorkflowCreateParam;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowList;
import com.emc.storageos.model.customservices.CustomServicesWorkflowRestRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowUpdateParam;
import com.emc.storageos.model.customservices.InputParameterRestRep;
import com.emc.storageos.model.customservices.InputUpdateParam;
import com.emc.storageos.model.customservices.InputUpdateParam.InputUpdateList;
import com.emc.storageos.model.customservices.OutputParameterRestRep;
import com.emc.storageos.model.customservices.OutputUpdateParam;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.vipr.model.catalog.WFBulkRep;
import com.emc.vipr.model.catalog.WFDirectoryParam;
import com.emc.vipr.model.catalog.WFDirectoryRestRep;
import com.emc.vipr.model.catalog.WFDirectoryUpdateParam;
import com.emc.vipr.model.catalog.WFDirectoryWorkflowsUpdateParam;
import com.google.common.collect.ImmutableMap;
import com.google.gson.annotations.SerializedName;
import com.sun.jersey.api.client.ClientResponse;

import controllers.Common;
import models.customservices.ImportWorkflowForm;
import models.customservices.LocalAnsiblePrimitiveForm;
import models.customservices.RemoteAnsiblePrimitiveForm;
import models.customservices.RestAPIPrimitiveForm;
import models.customservices.ShellScriptPrimitiveForm;
import play.Logger;
import play.Play;
import play.data.validation.Valid;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.With;
import plugin.StorageOsPlugin;
import util.MessagesUtils;
import util.StringOption;

@With(Common.class)
public class WorkflowBuilder extends Controller {
    private static final String NO_PARENT = "#";
    private static final String MY_LIBRARY_ROOT = "myLib";
    private static final String VIPR_LIBRARY_ROOT = "viprLib";
    private static final String VIPR_PRIMITIVE_ROOT = "viprrest";
    private static final String NODE_TYPE_FILE = "file";
    private static final String MY_LIBRARY = "Custom Library";
    private static final String VIPR_LIBRARY = "ViPR Library";
    private static final String VIPR_PRIMITIVE_LIBRARY = "ViPR REST";
    private static final String JSTREE_A_ATTR_TITLE = "title";

    public static void view() {
        setAnsibleResources();
        setRestCallResources();
        Common.copyRenderArgsToAngular();
        render();
    }

    private static void setAnsibleResources() {
        final CustomServicesPrimitiveResourceList customServicesPrimitiveResourceList = getCatalogClient().customServicesPrimitives()
                .getPrimitiveResourcesByType(StepType.LOCAL_ANSIBLE.toString(), null);
        final List<StringOption> ansibleResourceNames = new ArrayList<StringOption>();
        if (null != customServicesPrimitiveResourceList.getResources()) {
            for (final NamedRelatedResourceRep resourceRep : customServicesPrimitiveResourceList.getResources()) {
                ansibleResourceNames.add(new StringOption(resourceRep.getId().toString(), resourceRep.getName()));
            }
        }
        renderArgs.put("ansibleResourceNames", ansibleResourceNames);

    }

    private static void setRestCallResources() {
        final List<StringOption> restCallAuthTypes = new ArrayList<StringOption>();
        restCallAuthTypes.add(new StringOption(AuthType.NONE.toString(), Messages.get("rest.authType.noAuth")));
        restCallAuthTypes.add(new StringOption(AuthType.BASIC.toString(), Messages.get("rest.authType.basicAuth")));
        renderArgs.put("restCallAuthTypes", restCallAuthTypes);
    }

    public static void getAssetOptions() {
        if (!Play.mode.isDev()) {
            PropertyInfo propInfo = StorageOsPlugin.getInstance().getCoordinatorClient().getPropertyInfo();
            if (propInfo != null) {
                final String assetOptions = propInfo.getProperty("custom_services_assetoptions");
                if (assetOptions != null) {
                    renderJSON(Arrays.asList(assetOptions.split("\\s*,\\s*")));
                }
            }
        } else {
            renderJSON(new ArrayList<String>(Arrays.asList(
                    "assetType.vipr.blockVirtualPool",
                    "assetType.vipr.virtualArray",
                    "assetType.vipr.project",
                    "assetType.vipr.host")));
        }
        renderJSON(new ArrayList<String>());
    }

    public static void getWFDirectories() {

        final List<Node> topLevelNodes = new ArrayList<Node>();
        prepareRootNodes(topLevelNodes);

        // get workflow directories and prepare nodes
        final WFBulkRep wfBulkRep = getCatalogClient().wfDirectories().getAll();
        String nodeParent;
        final Map<URI, WFDirectoryRestRep> fileParents = new HashMap<URI, WFDirectoryRestRep>();
        for (WFDirectoryRestRep wfDirectoryRestRep : wfBulkRep
                .getWfDirectories()) {
            if (null == wfDirectoryRestRep.getParent()) {
                nodeParent = MY_LIBRARY_ROOT;
            } else {
                nodeParent = wfDirectoryRestRep.getParent().getId().toString();
            }
            final Node node = new Node(wfDirectoryRestRep.getId().toString(),
                    wfDirectoryRestRep.getName(), nodeParent, WFBuilderNodeTypes.FOLDER.toString());

            // add workflows that are under this node
            if (null != wfDirectoryRestRep.getWorkflows()) {
                for (URI u : wfDirectoryRestRep.getWorkflows()) {
                    fileParents.put(u, wfDirectoryRestRep);
                }
            }
            topLevelNodes.add(node);
        }

        // Add primitives
        addPrimitivesByType(topLevelNodes, StepType.LOCAL_ANSIBLE.toString(), MY_LIBRARY_ROOT, fileParents);
        addPrimitivesByType(topLevelNodes, StepType.REMOTE_ANSIBLE.toString(), MY_LIBRARY_ROOT, fileParents);
        addPrimitivesByType(topLevelNodes, StepType.SHELL_SCRIPT.toString(), MY_LIBRARY_ROOT, fileParents);
        addPrimitivesByType(topLevelNodes, StepType.REST.toString(), MY_LIBRARY_ROOT, fileParents);
        addPrimitivesByType(topLevelNodes, StepType.VIPR_REST.toString(), VIPR_PRIMITIVE_ROOT, null);

        // Add workflows
        final CustomServicesWorkflowList customServicesWorkflowList = getCatalogClient()
                .customServicesPrimitives().getWorkflows();
        if (null != customServicesWorkflowList
                && null != customServicesWorkflowList.getWorkflows()) {
            for (NamedRelatedResourceRep o : customServicesWorkflowList
                    .getWorkflows()) {
                final String parent = fileParents.containsKey(o.getId()) ? fileParents.get(o.getId()).getId().toString() : MY_LIBRARY_ROOT;
                topLevelNodes.add(new Node(o.getId().toString(), o.getName(), parent, StepType.WORKFLOW.toString()));
            }
        }

        renderJSON(topLevelNodes);
    }

    // Preparing top level nodes in workflow directory
    private static void prepareRootNodes(final List<Node> topLevelNodes) {
        final Node myLib = new Node(MY_LIBRARY_ROOT, MY_LIBRARY, NO_PARENT, WFBuilderNodeTypes.FOLDER.toString());
        myLib.addBoldAnchorAttr();
        topLevelNodes.add(myLib);
        final Node viprLib = new Node(VIPR_LIBRARY_ROOT, VIPR_LIBRARY, NO_PARENT, WFBuilderNodeTypes.FOLDER.toString());
        viprLib.addBoldAnchorAttr();
        topLevelNodes.add(viprLib);
        topLevelNodes.add(new Node(VIPR_PRIMITIVE_ROOT, VIPR_PRIMITIVE_LIBRARY, VIPR_LIBRARY_ROOT, WFBuilderNodeTypes.FOLDER.toString()));
    }

    public static void editWFDirName(String id, String newName) throws URISyntaxException {
        WFDirectoryUpdateParam param = new WFDirectoryUpdateParam();
        param.setName(newName);
        getCatalogClient().wfDirectories().edit(new URI(id), param);
    }

    public static void deleteWFDir(String id) throws Exception {
        getCatalogClient().wfDirectories().delete(new URI(id));
    }

    public static void createWFDir(String name, String parent) throws URISyntaxException {
        WFDirectoryParam param = new WFDirectoryParam();
        param.setName(name);
        // Ignoring root parent
        URI parentURI = null;
        if (null != parent) {
            parentURI = MY_LIBRARY_ROOT.equals(parent) ? null : new URI(
                    parent);
        }
        param.setParent(parentURI);
        WFDirectoryRestRep wfDirectoryRestRep = getCatalogClient()
                .wfDirectories().create(param);
        renderJSON(wfDirectoryRestRep);
    }

    private static List<CustomServicesWorkflowDocument.Step> getStartEndSteps() {
        CustomServicesWorkflowDocument.Step start = new CustomServicesWorkflowDocument.Step();
        start.setFriendlyName("Start");
        start.setId("Start");
        start.setPositionX(1450);
        start.setPositionY(1800);
        CustomServicesWorkflowDocument.Step end = new CustomServicesWorkflowDocument.Step();
        end.setFriendlyName("End");
        end.setId("End");
        end.setPositionX(1450);
        end.setPositionY(2150);
        List<CustomServicesWorkflowDocument.Step> steps = new ArrayList<CustomServicesWorkflowDocument.Step>();
        steps.add(start);
        steps.add(end);
        return steps;
    }

    public static void createWorkflow(final String workflowName,
            final String dirID) throws URISyntaxException {
        // Create workflow with just name
        final CustomServicesWorkflowCreateParam param = new CustomServicesWorkflowCreateParam();
        final CustomServicesWorkflowDocument document = new CustomServicesWorkflowDocument();
        document.setName(workflowName);
        List<CustomServicesWorkflowDocument.Step> steps = getStartEndSteps();
        document.setSteps(steps);
        param.setDocument(document);

        final CustomServicesWorkflowRestRep customServicesWorkflowRestRep = getCatalogClient()
                .customServicesPrimitives().createWorkflow(param);

        // Add this workflowid to directory
        if (null != customServicesWorkflowRestRep) {
            addResourceToWFDirectory(customServicesWorkflowRestRep.getId(), dirID);
        } else {
            flash.error("Error creating workflow");
        }

        renderJSON(customServicesWorkflowRestRep);
    }

    private static void addResourceToWFDirectory(URI resourceID, String dirID) throws URISyntaxException {
        if (MY_LIBRARY_ROOT.equals(dirID)) {
            return;
        }
        final WFDirectoryUpdateParam wfDirectoryParam = new WFDirectoryUpdateParam();
        final Set<URI> addWorkflows = new HashSet<URI>();
        addWorkflows.add(resourceID);
        wfDirectoryParam
                .setWorkflows(new WFDirectoryWorkflowsUpdateParam(
                        addWorkflows, null));
        getCatalogClient().wfDirectories().edit(new URI(dirID),
                wfDirectoryParam);

    }

    public static void saveWorkflow(final URI workflowId,
            final CustomServicesWorkflowDocument workflowDoc) {
        final CustomServicesWorkflowUpdateParam param = new CustomServicesWorkflowUpdateParam();
        for (final CustomServicesWorkflowDocument.Step step : workflowDoc.getSteps()) {
            final String success_criteria = ESAPI.encoder().decodeForHTML(step.getSuccessCriteria());
            step.setSuccessCriteria(success_criteria);

            // If this workflow has any ansible steps add host_file input
            addInventoryFileInputs(step);
        }

        param.setDocument(workflowDoc);
        final CustomServicesWorkflowRestRep customServicesWorkflowRestRep = getCatalogClient()
                .customServicesPrimitives().editWorkflow(workflowId, param);
    }

    public static void getWorkflow(final URI workflowId) {
        CustomServicesWorkflowRestRep customServicesWorkflowRestRep = getCatalogClient()
                .customServicesPrimitives().getWorkflow(workflowId);
        renderJSON(customServicesWorkflowRestRep);
    }

    public static void validateWorkflow(final URI workflowId) {
        CustomServicesValidationResponse customServicesWorkflowValidationResponse = getCatalogClient()
                .customServicesPrimitives().validateWorkflow(workflowId);
        renderJSON(customServicesWorkflowValidationResponse);
    }

    public static void publishWorkflow(final URI workflowId) {
        CustomServicesWorkflowRestRep customServicesWorkflowRestRep = getCatalogClient()
                .customServicesPrimitives().publishWorkflow(workflowId);
        renderJSON(customServicesWorkflowRestRep);
    }

    public static void unpublishWorkflow(final URI workflowId) {
        CustomServicesWorkflowRestRep customServicesWorkflowRestRep = getCatalogClient()
                .customServicesPrimitives().unpublishWorkflow(workflowId);
        renderJSON(customServicesWorkflowRestRep);
    }

    public static void editWorkflowName(final String id, final String newName) throws URISyntaxException {
        URI workflowURI = new URI(id);
        CustomServicesWorkflowRestRep customServicesWorkflowRestRep = getCatalogClient()
                .customServicesPrimitives().getWorkflow(workflowURI);
        if (null != customServicesWorkflowRestRep) {
            final CustomServicesWorkflowUpdateParam param = new CustomServicesWorkflowUpdateParam();
            param.setDocument(customServicesWorkflowRestRep.getDocument());
            param.getDocument().setName(newName);
            getCatalogClient().customServicesPrimitives().editWorkflow(
                    workflowURI, param);
        } else {
            flash.error("Workflow " + id + "not found");
        }
    }

    public static void deleteWorkflow(final String workflowID,
            final String dirID) throws URISyntaxException {
        final URI workflowURI = new URI(workflowID);
        // Delete workflow
        getCatalogClient().customServicesPrimitives().deleteWorkflow(
                workflowURI);

        // Delete this reference in WFDirectory
        if (!StringUtils.equals(MY_LIBRARY_ROOT, dirID)) {
            final WFDirectoryUpdateParam param = new WFDirectoryUpdateParam();
            final Set<URI> removeWorkflows = new HashSet<URI>();
            removeWorkflows.add(workflowURI);
            param.setWorkflows(new WFDirectoryWorkflowsUpdateParam(null,
                    removeWorkflows));
            getCatalogClient().wfDirectories().edit(new URI(dirID), param);
        }
    }

    public static void deletePrimitive(final String primitiveId,
            final String dirID) throws URISyntaxException {
        try {
            final URI primitiveURI = new URI(primitiveId);
            // Delete primitive need to worry about error reporting
            ClientResponse response = getCatalogClient().customServicesPrimitives().deletePrimitive(primitiveURI);
            // Delete this reference in WFDirectory
            if (!StringUtils.equals(MY_LIBRARY_ROOT, dirID)) {
                final WFDirectoryUpdateParam param = new WFDirectoryUpdateParam();
                final Set<URI> removeWorkflows = new HashSet<URI>();
                removeWorkflows.add(primitiveURI);
                param.setWorkflows(new WFDirectoryWorkflowsUpdateParam(null, removeWorkflows));
                getCatalogClient().wfDirectories().edit(new URI(dirID), param);
            }
            flash.success(MessagesUtils.get("node.delete.success"));
        } catch (Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        } finally {
            view();
        }
    }

    private static void addPrimitivesByType(final List<Node> topLevelNodes, final String type, String parentDefault,
            final Map<URI, WFDirectoryRestRep> fileParents) {
        final CustomServicesPrimitiveList primitiveList = getCatalogClient()
                .customServicesPrimitives().getPrimitivesByType(type);
        if (null == primitiveList || null == primitiveList.getPrimitives()) {
            return;
        }
        final List<String> categories = new ArrayList<String>();
        final List<String> services = new ArrayList<String>();

        final List<CustomServicesPrimitiveRestRep> primitives = getCatalogClient().customServicesPrimitives()
                .getByIds(primitiveList.getPrimitives());
        for (final CustomServicesPrimitiveRestRep primitive : primitives) {
            final String parent = (fileParents != null && fileParents.containsKey(primitive.getId()))
                    ? fileParents.get(primitive.getId()).getId().toString() : parentDefault;
            final Node node;

            if (StepType.VIPR_REST.toString().equals(type)) {
                final String[] folders = primitive.getName().split("/");
                final String service;
                if (folders.length == 3) {
                    if (!categories.contains(folders[0])) {
                        topLevelNodes.add(new Node(folders[0],
                                folders[0], parent, WFBuilderNodeTypes.FOLDER.toString()));
                        categories.add(folders[0]);
                    }
                    if (!services.contains(folders[1])) {
                        topLevelNodes.add(new Node(folders[1],
                                folders[1], folders[0], WFBuilderNodeTypes.FOLDER.toString()));
                        services.add(folders[1]);
                    }
                    service = folders[1];
                } else {
                    service = parent;
                }
                node = new Node(primitive.getId().toString(),
                        primitive.getFriendlyName(), service, type);
            } else {
                node = new Node(primitive.getId().toString(),
                        primitive.getName(), parent, type);
            }

            node.data = primitive;
            topLevelNodes.add(node);
        }
    }

    public static void saveShellScriptPrimitive(@Valid final ShellScriptPrimitiveForm shellPrimitive) {
        if (StringUtils.isNotEmpty(shellPrimitive.getId())) {
            editShellScriptPrimitive(shellPrimitive);
        } else {
            createShellScriptPrimitive(shellPrimitive);
        }
        view();
    }

    public static void saveRestAPIPrimitive(@Valid final RestAPIPrimitiveForm restAPIPrimitive) {
        if (StringUtils.isNotEmpty(restAPIPrimitive.getId())) {
            editRestAPIPrimitive(restAPIPrimitive);
        } else {
            createRestAPIPrimitive(restAPIPrimitive);
        }
        view();
    }

    public static void saveLocalAnsiblePrimitive(@Valid final LocalAnsiblePrimitiveForm localAnsible) {
        if (StringUtils.isNotEmpty(localAnsible.getId())) {
            editLocalAnsiblePrimitive(localAnsible);
        } else {
            createLocalAnsiblePrimitive(localAnsible);
        }
        view();
    }

    public static void saveRemoteAnsiblePrimitive(@Valid final RemoteAnsiblePrimitiveForm remoteAnsible) {
        if (StringUtils.isNotEmpty(remoteAnsible.getId())) {
            editRemoteAnsiblePrimitive(remoteAnsible);
        } else {
            createRemoteAnsiblePrimitive(remoteAnsible);
        }
        view();
    }

    public static void getPrimitive(final URI primitiveId, final String primitiveType) {
        final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives().getPrimitive(primitiveId);
        if (null == primitiveRestRep) {
            flash.error("Invalid primitive ID");
        } else {
            switch (StepType.fromString(primitiveType)) {
                case SHELL_SCRIPT:
                    renderJSON(mapPrimitiveScriptRestToForm(primitiveRestRep));
                case LOCAL_ANSIBLE:
                    renderJSON(mapPrimitiveLARestToForm(primitiveRestRep));
                case REST:
                    renderJSON(mapPrimitiveRestAPIToForm(primitiveRestRep));
                case REMOTE_ANSIBLE:
                    renderJSON(mapPrimitiveRARestToForm(primitiveRestRep));
                default:
                    Logger.error("Invalid primitive type: %s", primitiveType);
            }

        }

    }

    public static void editPrimitiveName(final URI primitiveID, final String newName) {
        final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives().getPrimitive(primitiveID);
        if (null != primitiveRestRep) {
            // Update name
            if (StringUtils.isBlank(newName) || newName.equals(primitiveRestRep.getName())) {
                // empty or no change ignore.
                return;
            }
            final CustomServicesPrimitiveUpdateParam primitiveUpdateParam = new CustomServicesPrimitiveUpdateParam();
            primitiveUpdateParam.setName(newName);
            primitiveUpdateParam.setFriendlyName(newName);
            getCatalogClient().customServicesPrimitives().updatePrimitive(primitiveID, primitiveUpdateParam);
        }
    }

    public static void getInventoryFilesForPackage(final URI packageId) {
        final List<String> inventoryFileNames = new ArrayList<String>();
        if (null != packageId) {
            final CustomServicesPrimitiveResourceList customServicesPrimitiveResourceList = getCatalogClient().customServicesPrimitives()
                    .getPrimitiveResourcesByType(CustomServicesConstants.ANSIBLE_INVENTORY_TYPE, packageId);
            if (null != customServicesPrimitiveResourceList.getResources()) {
                for (NamedRelatedResourceRep inventoryResource : customServicesPrimitiveResourceList.getResources()) {
                    inventoryFileNames.add(inventoryResource.getName());
                }
            }
        }
        renderJSON(inventoryFileNames);
    }

    public static void exportWorkflow(final URI workflowId) {
        try {
            final ClientResponse response = getCatalogClient().customServicesPrimitives().exportWorkflow(workflowId);
            if (response != null) {
                String fileName = null;
                // Extract filename from headers
                final Pattern regex = Pattern.compile("(?<=filename=).*");
                final Matcher regexMatcher = regex.matcher(response.getHeaders().get("Content-Disposition").get(0));
                if (regexMatcher.find()) {
                    fileName = regexMatcher.group();
                }
                renderBinary(response.getEntityInputStream(), fileName);
            }

        } catch (Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
            view();
        }
    }

    public static void importWorkflow(final ImportWorkflowForm importWorkflow) {
        try {
            final URI workflowDirectoryID = StringUtils.equals(MY_LIBRARY_ROOT, importWorkflow.getWfDirID()) ? null
                    : new URI(importWorkflow.getWfDirID());
            getCatalogClient().customServicesPrimitives().importWorkflow(workflowDirectoryID, importWorkflow.getWorkflowFile());
            flash.success(MessagesUtils.get("wfBuilder.import.workflow.success"));
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        }
        view();
    }

    private static void createShellScriptPrimitive(final ShellScriptPrimitiveForm shellPrimitive) {
        try {
            final String filename = FilenameUtils.getBaseName(shellPrimitive.getScript().getName());
            final CustomServicesPrimitiveResourceRestRep primitiveResourceRestRep = getCatalogClient().customServicesPrimitives()
                    .createPrimitiveResource("SCRIPT", shellPrimitive.getScript(), filename);
            if (null != primitiveResourceRestRep) {
                final CustomServicesPrimitiveCreateParam primitiveCreateParam = new CustomServicesPrimitiveCreateParam();
                primitiveCreateParam.setType(StepType.SHELL_SCRIPT.toString());
                primitiveCreateParam.setName(shellPrimitive.getName());
                primitiveCreateParam.setFriendlyName(shellPrimitive.getName());
                primitiveCreateParam.setDescription(shellPrimitive.getDescription());
                primitiveCreateParam.setResource(primitiveResourceRestRep.getId());
                if (StringUtils.isNotEmpty(shellPrimitive.getInputs())) {
                    final List<String> list = getListFromInputOutputString(shellPrimitive.getInputs());
                    final InputCreateList input = new InputCreateList();
                    input.setInput(list);
                    final ImmutableMap.Builder<String, InputCreateList> builder = ImmutableMap.<String, InputCreateList> builder()
                            .put(CustomServicesConstants.INPUT_PARAMS, input);
                    primitiveCreateParam.setInput(builder.build());
                }
                if (StringUtils.isNotEmpty(shellPrimitive.getOutputs())) {
                    primitiveCreateParam.setOutput(getListFromInputOutputString(shellPrimitive.getOutputs()));
                }
                final CustomServicesPrimitiveRestRep primitiveRestRep;

                try {

                    primitiveRestRep = getCatalogClient().customServicesPrimitives()
                            .createPrimitive(primitiveCreateParam);
                } catch (final Exception e1) {
                    if (primitiveResourceRestRep != null) {
                        // Resource was created but primitive creation failed
                        getCatalogClient().customServicesPrimitives().deletePrimitiveResource(primitiveResourceRestRep.getId());
                    }
                    throw e1;
                }
                if (primitiveRestRep != null) {
                    // add this to wf directory
                    addResourceToWFDirectory(primitiveRestRep.getId(), shellPrimitive.getWfDirID());
                } else {
                    flash.error("Error while creating primitive");
                }

                flash.success(MessagesUtils.get("wfBuilder.operation.save.success"));
            } else {
                flash.error("Error while uploading primitive resource");
            }
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        }
    }

    private static void editShellScriptPrimitive(final ShellScriptPrimitiveForm shellPrimitive) {
        try {
            final URI shellPrimitiveID = new URI(shellPrimitive.getId());
            // Check primitive is already used in workflow/s

            final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives().getPrimitive(
                    shellPrimitiveID);
            if (null != primitiveRestRep) {
                final CustomServicesWorkflowList customServicesWorkflowList = getCatalogClient().customServicesPrimitives().getWorkflows(
                        shellPrimitive.getId());
                if (customServicesWorkflowList != null && customServicesWorkflowList.getWorkflows() != null) {
                    if (!customServicesWorkflowList.getWorkflows().isEmpty()) {
                        flash.error("Operation %s is being used in Workflow", primitiveRestRep.getName());
                        return;
                    }
                }
                // Update name, description
                final CustomServicesPrimitiveUpdateParam primitiveUpdateParam = new CustomServicesPrimitiveUpdateParam();
                primitiveUpdateParam.setName(shellPrimitive.getName());
                primitiveUpdateParam.setFriendlyName(shellPrimitive.getName());
                primitiveUpdateParam.setDescription(shellPrimitive.getDescription());

                // Get and update differences between existing and new inputs
                final List<String> newInputs = getListFromInputOutputString(shellPrimitive.getInputs());
                final List<String> existingInputs = convertInputParamsGroupsToList(primitiveRestRep.getInputGroups());
                final InputUpdateParam inputUpdateParam = new InputUpdateParam();

                inputUpdateParam.setRemove(getInputParamsDiff(existingInputs, newInputs));
                inputUpdateParam.setAdd(getInputParamsDiff(newInputs, existingInputs));
                primitiveUpdateParam.setInput(inputUpdateParam);

                // Get and update differences between existing and new outputs
                final List<String> newOutputs = getListFromInputOutputString(shellPrimitive.getOutputs());
                final List<String> existingOutputs = convertOutputGroupsToList(primitiveRestRep.getOutput());
                OutputUpdateParam outputUpdateParam = new OutputUpdateParam();
                outputUpdateParam.setRemove((List<String>) CollectionUtils.subtract(existingOutputs, newOutputs));
                outputUpdateParam.setAdd((List<String>) CollectionUtils.subtract(newOutputs, existingOutputs));
                primitiveUpdateParam.setOutput(outputUpdateParam);

                final CustomServicesPrimitiveResourceRestRep primitiveResourceRestRep;

                if (shellPrimitive.isNewScript()) {
                    // create new resource
                    String filename = FilenameUtils.getBaseName(shellPrimitive.getScript().getName());
                    primitiveResourceRestRep = getCatalogClient().customServicesPrimitives()
                            .createPrimitiveResource("SCRIPT", shellPrimitive.getScript(), filename);
                    if (null != primitiveResourceRestRep) {
                        // Update resource link
                        primitiveUpdateParam.setResource(primitiveResourceRestRep.getId());
                        try {
                            getCatalogClient().customServicesPrimitives().updatePrimitive(shellPrimitiveID, primitiveUpdateParam);
                        } catch (final Exception e1) {
                            if (primitiveResourceRestRep != null) {
                                // Resource was created but primitive creation failed
                                getCatalogClient().customServicesPrimitives().deletePrimitiveResource(primitiveResourceRestRep.getId());
                            }
                            throw e1;
                        }
                    }
                } else {
                    getCatalogClient().customServicesPrimitives().updatePrimitive(shellPrimitiveID, primitiveUpdateParam);
                }

                flash.success(MessagesUtils.get("wfBuilder.operation.edit.success"));
            }
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        }
    }

    private static void createRestAPIPrimitive(final RestAPIPrimitiveForm restAPIPrimitive) {
        try {
            final CustomServicesPrimitiveCreateParam primitiveCreateParam = new CustomServicesPrimitiveCreateParam();
            primitiveCreateParam.setType(StepType.REST.toString());
            primitiveCreateParam.setName(restAPIPrimitive.getName());
            primitiveCreateParam.setDescription(restAPIPrimitive.getDescription());
            primitiveCreateParam.setFriendlyName(restAPIPrimitive.getName());
            primitiveCreateParam.setAttributes(new HashMap<String, String>());
            primitiveCreateParam.getAttributes().put(CustomServicesConstants.PATH.toString(), restAPIPrimitive.getRequestURL());
            if (restAPIPrimitive.getRawBody() == null) {
                primitiveCreateParam.getAttributes().put(CustomServicesConstants.BODY.toString(), "");
            } else {
                primitiveCreateParam.getAttributes().put(CustomServicesConstants.BODY.toString(), restAPIPrimitive.getRawBody());
            }
            // Only supported protocol is "https". Once other protocols are supported this value should come from UI form
            primitiveCreateParam.getAttributes().put(CustomServicesConstants.PROTOCOL.toString(), "https");
            primitiveCreateParam.getAttributes().put(CustomServicesConstants.METHOD.toString(), restAPIPrimitive.getMethod());
            primitiveCreateParam.getAttributes().put(CustomServicesConstants.AUTH_TYPE.toString(), restAPIPrimitive.getAuthType());

            final ImmutableMap.Builder<String, InputCreateList> builder = ImmutableMap.<String, InputCreateList> builder();
            // Add Input Groups
            addInputs(restAPIPrimitive.getHeaders(), builder, CustomServicesConstants.HEADERS);
            addInputs(restAPIPrimitive.getQueryParams(), builder, CustomServicesConstants.QUERY_PARAMS);
            primitiveCreateParam.setInput(builder.build());

            // Add Outputs
            if (StringUtils.isNotEmpty(restAPIPrimitive.getOutputs())) {
                primitiveCreateParam.setOutput(getListFromInputOutputString(restAPIPrimitive.getOutputs()));
            }

            final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives()
                    .createPrimitive(primitiveCreateParam);
            if (primitiveRestRep != null) {
                // add this to wf directory
                addResourceToWFDirectory(primitiveRestRep.getId(), restAPIPrimitive.getWfDirID());
                flash.success(MessagesUtils.get("wfBuilder.operation.save.success"));
            } else {
                flash.error("Error while creating primitive");
            }
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        }
    }

    private static void editRestAPIPrimitive(final RestAPIPrimitiveForm restAPIPrimitive) {
        try {
            final URI restPrimitiveID = new URI(restAPIPrimitive.getId());
            final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives()
                    .getPrimitive(restPrimitiveID);
            if (null != primitiveRestRep) {
                // Update name, description
                final CustomServicesPrimitiveUpdateParam primitiveUpdateParam = new CustomServicesPrimitiveUpdateParam();
                primitiveUpdateParam.setName(restAPIPrimitive.getName());
                primitiveUpdateParam.setFriendlyName(restAPIPrimitive.getName());
                primitiveUpdateParam.setDescription(restAPIPrimitive.getDescription());

                // Get and update differences between existing and new inputs
                final InputUpdateParam inputUpdateParam = new InputUpdateParam();
                inputUpdateParam.setRemove(new HashMap<String, InputUpdateList>());
                inputUpdateParam.setAdd(new HashMap<String, InputUpdateList>());
                prepareInputUpdates(CustomServicesConstants.INPUT_PARAMS, restAPIPrimitive.getInputs(), primitiveRestRep, inputUpdateParam);
                prepareInputUpdates(CustomServicesConstants.HEADERS, restAPIPrimitive.getHeaders(), primitiveRestRep, inputUpdateParam);
                prepareInputUpdates(CustomServicesConstants.QUERY_PARAMS, restAPIPrimitive.getQueryParams(), primitiveRestRep,
                        inputUpdateParam);
                primitiveUpdateParam.setInput(inputUpdateParam);

                // Get and update differences between existing and new outputs
                final List<String> newOutputs = getListFromInputOutputString(restAPIPrimitive.getOutputs());
                final List<String> existingOutputs = convertOutputGroupsToList(primitiveRestRep.getOutput());
                final OutputUpdateParam outputUpdateParam = new OutputUpdateParam();
                outputUpdateParam.setRemove((List<String>) CollectionUtils.subtract(existingOutputs, newOutputs));
                outputUpdateParam.setAdd((List<String>) CollectionUtils.subtract(newOutputs, existingOutputs));
                primitiveUpdateParam.setOutput(outputUpdateParam);

                // Set attributes
                primitiveUpdateParam.setAttributes(new HashMap<String, String>());
                primitiveUpdateParam.getAttributes().put(CustomServicesConstants.PATH.toString(), restAPIPrimitive.getRequestURL());
                if (restAPIPrimitive.getRawBody() == null) {
                    primitiveUpdateParam.getAttributes().put(CustomServicesConstants.BODY.toString(), "");
                } else {
                    primitiveUpdateParam.getAttributes().put(CustomServicesConstants.BODY.toString(), restAPIPrimitive.getRawBody());
                }

                // Only supported protocol is "https". Once other protocols are supported this value should come from UI form
                primitiveUpdateParam.getAttributes().put(CustomServicesConstants.METHOD.toString(), restAPIPrimitive.getMethod());
                primitiveUpdateParam.getAttributes().put(CustomServicesConstants.AUTH_TYPE.toString(), restAPIPrimitive.getAuthType());

                getCatalogClient().customServicesPrimitives().updatePrimitive(restPrimitiveID, primitiveUpdateParam);
                flash.success(MessagesUtils.get("wfBuilder.operation.edit.success"));
            }
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        }
    }

    private static void editLocalAnsiblePrimitive(final LocalAnsiblePrimitiveForm localAnsible) {
        try {
            final URI localAnsiblePrimitiveID = new URI(localAnsible.getId());
            final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives()
                    .getPrimitive(localAnsiblePrimitiveID);
            if (null != primitiveRestRep) {
                // Update name, description
                final CustomServicesPrimitiveUpdateParam primitiveUpdateParam = new CustomServicesPrimitiveUpdateParam();
                primitiveUpdateParam.setName(localAnsible.getName());
                primitiveUpdateParam.setFriendlyName(localAnsible.getName());
                primitiveUpdateParam.setDescription(localAnsible.getDescription());

                // Get and update differences between existing and new inputs
                final List<String> newInputs = getListFromInputOutputString(localAnsible.getInputs());
                final List<String> existingInputs = convertInputParamsGroupsToList(primitiveRestRep.getInputGroups());
                final InputUpdateParam inputUpdateParam = new InputUpdateParam();
                inputUpdateParam.setRemove(getInputParamsDiff(existingInputs, newInputs));
                inputUpdateParam.setAdd(getInputParamsDiff(newInputs, existingInputs));
                primitiveUpdateParam.setInput(inputUpdateParam);

                // Get and update differences between existing and new outputs
                final List<String> newOutputs = getListFromInputOutputString(localAnsible.getOutputs());
                final List<String> existingOutputs = convertOutputGroupsToList(primitiveRestRep.getOutput());
                final OutputUpdateParam outputUpdateParam = new OutputUpdateParam();
                outputUpdateParam.setRemove((List<String>) CollectionUtils.subtract(existingOutputs, newOutputs));
                outputUpdateParam.setAdd((List<String>) CollectionUtils.subtract(newOutputs, existingOutputs));
                primitiveUpdateParam.setOutput(outputUpdateParam);

                // Set playbook
                primitiveUpdateParam.setAttributes(new HashMap<String, String>());
                primitiveUpdateParam.getAttributes().put(CustomServicesConstants.ANSIBLE_PLAYBOOK, localAnsible.getAnsiblePlaybook());

                URI packageId = null;
                boolean updateDone = false;
                if (!localAnsible.isExisting()) {
                    // NEW RESOURCE
                    // Before creating resource check if this primitive is not used
                    final CustomServicesWorkflowList customServicesWorkflowList = getCatalogClient().customServicesPrimitives()
                            .getWorkflows(localAnsible.getId());
                    if (customServicesWorkflowList != null && customServicesWorkflowList.getWorkflows() != null) {
                        if (!customServicesWorkflowList.getWorkflows().isEmpty()) {
                            flash.error("Primitive %s is being used in Workflow", primitiveRestRep.getName());
                            return;
                        }
                    }

                    // create new resource
                    final CustomServicesPrimitiveResourceRestRep primitiveResourceRestRep = getCatalogClient().customServicesPrimitives()
                            .createPrimitiveResource("ANSIBLE", localAnsible.getAnsiblePackage(), localAnsible.getAnsiblePackageName());
                    if (null != primitiveResourceRestRep) {
                        // Update resource link
                        packageId = primitiveResourceRestRep.getId();
                        primitiveUpdateParam.setResource(packageId);
                    }
                } else {
                    // EXISTING RESOURCE
                    packageId = new URI(localAnsible.getExistingResource());
                    primitiveUpdateParam.setResource(packageId);

                    // Changes to existing inventory files
                    updateDone = updateInventoryFiles(packageId, localAnsible.getUpdatedInventoryFiles());
                }

                // Upload new inventory files
                final boolean uploadDone = uploadInventoryFiles(packageId, localAnsible.getInventoryFiles());

                // Update workflows with new inventory files
                boolean updatedWorkflows = false;
                if (uploadDone || updateDone) {
                    updatedWorkflows = updateWorkflowInventoryFiles(localAnsible.getId());
                }

                // If this primitive is part of any workflow, ignore update
                if (!updatedWorkflows) {
                    getCatalogClient().customServicesPrimitives().updatePrimitive(localAnsiblePrimitiveID, primitiveUpdateParam);
                } else {
                    Logger.info("Ignoring local ansible primitive {} update as it is part of workflow", localAnsible.getName());
                }

                flash.success(MessagesUtils.get("wfBuilder.operation.edit.success"));
            }
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        }
    }

    private static void createLocalAnsiblePrimitive(@Valid final LocalAnsiblePrimitiveForm localAnsible) {
        try {
            final CustomServicesPrimitiveResourceRestRep primitiveResourceRestRep;
            if (localAnsible.isExisting()) {
                primitiveResourceRestRep = getCatalogClient().customServicesPrimitives()
                        .getPrimitiveResource(new URI(localAnsible.getExistingResource()));
            } else if (null != localAnsible.getAnsiblePackage()) {
                // upload ansible package
                primitiveResourceRestRep = getCatalogClient().customServicesPrimitives().createPrimitiveResource("ANSIBLE",
                        localAnsible.getAnsiblePackage(), localAnsible.getAnsiblePackageName());
            } else {
                throw new Exception("Error while uploading/retrieving primitive resource");
            }

            if (null != primitiveResourceRestRep) {
                try {
                    // Upload ansible inventory files
                    uploadInventoryFiles(primitiveResourceRestRep.getId(), localAnsible.getInventoryFiles());
                } catch (final Exception e1) {
                    if (primitiveResourceRestRep != null) {
                        // Resource was created but primitive creation failed
                        // Resource was created but inventory creation failed. Remove inventory that was created (if any)
                        updateInventoryFiles(primitiveResourceRestRep.getId(), "");
                        getCatalogClient().customServicesPrimitives().deletePrimitiveResource(primitiveResourceRestRep.getId());
                    }
                    throw e1;
                }

                // Create Primitive
                final CustomServicesPrimitiveCreateParam primitiveCreateParam = new CustomServicesPrimitiveCreateParam();
                primitiveCreateParam.setType(StepType.LOCAL_ANSIBLE.toString());
                primitiveCreateParam.setName(localAnsible.getName());
                primitiveCreateParam.setDescription(localAnsible.getDescription());
                primitiveCreateParam.setFriendlyName(localAnsible.getName());
                primitiveCreateParam.setResource(primitiveResourceRestRep.getId());
                primitiveCreateParam.setAttributes(new HashMap<String, String>());
                primitiveCreateParam.getAttributes().put(CustomServicesConstants.ANSIBLE_PLAYBOOK, localAnsible.getAnsiblePlaybook());
                final ImmutableMap.Builder<String, InputCreateList> builder = ImmutableMap.<String, InputCreateList> builder();
                // Add Input Groups
                addInputs(localAnsible.getInputs(), builder, CustomServicesConstants.INPUT_PARAMS);
                primitiveCreateParam.setInput(builder.build());

                if (StringUtils.isNotEmpty(localAnsible.getOutputs())) {
                    primitiveCreateParam.setOutput(getListFromInputOutputString(localAnsible.getOutputs()));
                }
                final CustomServicesPrimitiveRestRep primitiveRestRep;

                try {
                    primitiveRestRep = getCatalogClient().customServicesPrimitives().createPrimitive(primitiveCreateParam);
                } catch (final Exception e1) {
                    if (primitiveResourceRestRep != null) {
                        // Resource was created but primitive creation failed
                        // Resource was created but primitive creation failed. Remove inventory that was created (if any)
                        updateInventoryFiles(primitiveResourceRestRep.getId(), "");
                        getCatalogClient().customServicesPrimitives().deletePrimitiveResource(primitiveResourceRestRep.getId());
                    }
                    throw e1;
                }
                if (primitiveRestRep != null) {
                    // add this to wf directory
                    addResourceToWFDirectory(primitiveRestRep.getId(), localAnsible.getWfDirID());
                } else {
                    flash.error("Error while creating primitive");
                }

                flash.success(MessagesUtils.get("wfBuilder.operation.save.success"));
            } else {
                flash.error("Error while uploading primitive resource");
            }
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        }
    }

    private static void createRemoteAnsiblePrimitive(@Valid final RemoteAnsiblePrimitiveForm remoteAnsible) {
        try {
            // Create Primitive
            final CustomServicesPrimitiveCreateParam primitiveCreateParam = new CustomServicesPrimitiveCreateParam();
            primitiveCreateParam.setType(StepType.REMOTE_ANSIBLE.toString());
            primitiveCreateParam.setName(remoteAnsible.getName());
            primitiveCreateParam.setDescription(remoteAnsible.getDescription());
            primitiveCreateParam.setFriendlyName(remoteAnsible.getName());
            primitiveCreateParam.setAttributes(new HashMap<String, String>());
            primitiveCreateParam.getAttributes().put(CustomServicesConstants.ANSIBLE_PLAYBOOK, remoteAnsible.getPlaybookPath());
            primitiveCreateParam.getAttributes().put(CustomServicesConstants.ANSIBLE_BIN, remoteAnsible.getAnsibleBinPath());
            final ImmutableMap.Builder<String, InputCreateList> builder = ImmutableMap.<String, InputCreateList> builder();
            // Add Input Groups
            addInputs(remoteAnsible.getInputs(), builder, CustomServicesConstants.INPUT_PARAMS);
            primitiveCreateParam.setInput(builder.build());

            if (StringUtils.isNotEmpty(remoteAnsible.getOutputs())) {
                primitiveCreateParam.setOutput(getListFromInputOutputString(remoteAnsible.getOutputs()));
            }

            final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives()
                    .createPrimitive(primitiveCreateParam);
            if (primitiveRestRep != null) {
                // add this to wf directory
                addResourceToWFDirectory(primitiveRestRep.getId(), remoteAnsible.getWfDirID());
            } else {
                flash.error("Error while creating primitive");
            }

            flash.success(MessagesUtils.get("wfBuilder.operation.save.success"));
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        }
    }

    private static void editRemoteAnsiblePrimitive(final RemoteAnsiblePrimitiveForm remoteAnsible) {
        try {
            final URI shellPrimitiveID = new URI(remoteAnsible.getId());

            final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives().getPrimitive(
                    shellPrimitiveID);
            if (null != primitiveRestRep) {
                // Update name, description
                final CustomServicesPrimitiveUpdateParam primitiveUpdateParam = new CustomServicesPrimitiveUpdateParam();
                primitiveUpdateParam.setName(remoteAnsible.getName());
                primitiveUpdateParam.setFriendlyName(remoteAnsible.getName());
                primitiveUpdateParam.setDescription(remoteAnsible.getDescription());

                // Get and update differences between existing and new inputs
                final List<String> newInputs = getListFromInputOutputString(remoteAnsible.getInputs());
                final List<String> existingInputs = convertInputParamsGroupsToList(primitiveRestRep.getInputGroups());
                final InputUpdateParam inputUpdateParam = new InputUpdateParam();
                inputUpdateParam.setRemove(getInputParamsDiff(existingInputs, newInputs));
                inputUpdateParam.setAdd(getInputParamsDiff(newInputs, existingInputs));
                primitiveUpdateParam.setInput(inputUpdateParam);

                // Get and update differences between existing and new outputs
                final List<String> newOutputs = getListFromInputOutputString(remoteAnsible.getOutputs());
                final List<String> existingOutputs = convertOutputGroupsToList(primitiveRestRep.getOutput());
                OutputUpdateParam outputUpdateParam = new OutputUpdateParam();
                outputUpdateParam.setRemove((List<String>) CollectionUtils.subtract(existingOutputs, newOutputs));
                outputUpdateParam.setAdd((List<String>) CollectionUtils.subtract(newOutputs, existingOutputs));
                primitiveUpdateParam.setOutput(outputUpdateParam);

                // Attributes
                primitiveUpdateParam.setAttributes(new HashMap<String, String>());
                primitiveUpdateParam.getAttributes().put(CustomServicesConstants.ANSIBLE_PLAYBOOK, remoteAnsible.getPlaybookPath());
                primitiveUpdateParam.getAttributes().put(CustomServicesConstants.ANSIBLE_BIN, remoteAnsible.getAnsibleBinPath());

                getCatalogClient().customServicesPrimitives().updatePrimitive(shellPrimitiveID, primitiveUpdateParam);
                flash.success(MessagesUtils.get("wfBuilder.operation.edit.success"));
            }
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        }
    }

    private static ShellScriptPrimitiveForm mapPrimitiveScriptRestToForm(final CustomServicesPrimitiveRestRep primitiveRestRep) {
        final ShellScriptPrimitiveForm shellPrimitive = new ShellScriptPrimitiveForm();
        if (null != primitiveRestRep) {
            shellPrimitive.setId(primitiveRestRep.getId().toString());
            shellPrimitive.setName(primitiveRestRep.getName());
            shellPrimitive.setDescription(primitiveRestRep.getDescription());
            shellPrimitive.setInputs(convertListToString(convertInputParamsGroupsToList(primitiveRestRep.getInputGroups())));
            shellPrimitive.setOutputs(convertListToString(convertOutputGroupsToList(primitiveRestRep.getOutput())));
            shellPrimitive.setScriptName(primitiveRestRep.getResource().getName());
        }
        return shellPrimitive;
    }

    private static LocalAnsiblePrimitiveForm mapPrimitiveLARestToForm(final CustomServicesPrimitiveRestRep primitiveRestRep) {
        final LocalAnsiblePrimitiveForm localAnsiblePrimitiveForm = new LocalAnsiblePrimitiveForm();
        if (null != primitiveRestRep) {
            localAnsiblePrimitiveForm.setId(primitiveRestRep.getId().toString());
            localAnsiblePrimitiveForm.setName(primitiveRestRep.getName());
            localAnsiblePrimitiveForm.setDescription(primitiveRestRep.getDescription());
            localAnsiblePrimitiveForm.setInputs(convertListToString(convertInputParamsGroupsToList(primitiveRestRep.getInputGroups())));
            localAnsiblePrimitiveForm.setOutputs(convertListToString(convertOutputGroupsToList(primitiveRestRep.getOutput())));
            localAnsiblePrimitiveForm.setAnsiblePlaybook(primitiveRestRep.getAttributes().get(CustomServicesConstants.ANSIBLE_PLAYBOOK));
            localAnsiblePrimitiveForm.setExisting(true);
            localAnsiblePrimitiveForm.setExistingResource(primitiveRestRep.getResource().getId().toString());
        }
        return localAnsiblePrimitiveForm;
    }

    private static RemoteAnsiblePrimitiveForm mapPrimitiveRARestToForm(final CustomServicesPrimitiveRestRep primitiveRestRep) {
        final RemoteAnsiblePrimitiveForm remoteAnsiblePrimitiveForm = new RemoteAnsiblePrimitiveForm();
        if (null != primitiveRestRep) {
            remoteAnsiblePrimitiveForm.setId(primitiveRestRep.getId().toString());
            remoteAnsiblePrimitiveForm.setName(primitiveRestRep.getName());
            remoteAnsiblePrimitiveForm.setDescription(primitiveRestRep.getDescription());
            remoteAnsiblePrimitiveForm.setInputs(convertListToString(convertInputParamsGroupsToList(primitiveRestRep.getInputGroups())));
            remoteAnsiblePrimitiveForm.setOutputs(convertListToString(convertOutputGroupsToList(primitiveRestRep.getOutput())));
            remoteAnsiblePrimitiveForm.setAnsibleBinPath(primitiveRestRep.getAttributes().get(CustomServicesConstants.ANSIBLE_BIN));
            remoteAnsiblePrimitiveForm.setPlaybookPath(primitiveRestRep.getAttributes().get(CustomServicesConstants.ANSIBLE_PLAYBOOK));
        }
        return remoteAnsiblePrimitiveForm;
    }

    private static RestAPIPrimitiveForm mapPrimitiveRestAPIToForm(final CustomServicesPrimitiveRestRep primitiveRestRep) {
        final RestAPIPrimitiveForm restAPIPrimitiveForm = new RestAPIPrimitiveForm();
        if (null != primitiveRestRep) {
            restAPIPrimitiveForm.setId(primitiveRestRep.getId().toString());
            restAPIPrimitiveForm.setName(primitiveRestRep.getName());
            restAPIPrimitiveForm.setDescription(primitiveRestRep.getDescription());
            final Map<String, String> attributes = primitiveRestRep.getAttributes();
            restAPIPrimitiveForm.setAuthType(attributes.get(CustomServicesConstants.AUTH_TYPE.toString()));
            restAPIPrimitiveForm.setRequestURL(attributes.get(CustomServicesConstants.PATH.toString()));
            restAPIPrimitiveForm.setRawBody(attributes.get(CustomServicesConstants.BODY.toString()));
            restAPIPrimitiveForm.setMethod(attributes.get(CustomServicesConstants.METHOD.toString()));
            restAPIPrimitiveForm.setQueryParams(convertListToString(
                    convertInputGroupsToList(primitiveRestRep.getInputGroups(), CustomServicesConstants.QUERY_PARAMS.toString())));
            restAPIPrimitiveForm.setHeaders(convertListToString(
                    convertInputGroupsToList(primitiveRestRep.getInputGroups(), CustomServicesConstants.HEADERS.toString())));
            restAPIPrimitiveForm.setInputs(convertListToString(convertInputParamsGroupsToList(primitiveRestRep.getInputGroups())));
            restAPIPrimitiveForm.setOutputs(convertListToString(convertOutputGroupsToList(primitiveRestRep.getOutput())));
        }
        return restAPIPrimitiveForm;
    }

    private static void addInputs(final String inputs, final ImmutableMap.Builder<String, InputCreateList> builder,
            final String inputGroupType) {
        if (StringUtils.isNotEmpty(inputs)) {
            final List<String> list = getListFromInputOutputString(inputs);
            final InputCreateList input = new InputCreateList();
            input.setInput(list);
            builder.put(inputGroupType, input);
        }
    }

    private static void prepareInputUpdates(final String inputGroupType, final String inputs,
            final CustomServicesPrimitiveRestRep primitiveRestRep, final InputUpdateParam inputUpdateParam) {
        final List<String> newInputs = getListFromInputOutputString(inputs);
        final List<String> existingInputs = convertInputGroupsToList(primitiveRestRep.getInputGroups(), inputGroupType);

        inputUpdateParam.getRemove().putAll(getInputDiff(existingInputs, newInputs, inputGroupType));
        inputUpdateParam.getAdd().putAll(getInputDiff(newInputs, existingInputs, inputGroupType));

    }

    private static List<String> convertInputParamsGroupsToList(final Map<String, CustomServicesPrimitiveRestRep.InputGroup> inputGroups) {
        return convertInputGroupsToList(inputGroups, CustomServicesConstants.INPUT_PARAMS);
    }

    private static List<String> convertInputGroupsToList(final Map<String, CustomServicesPrimitiveRestRep.InputGroup> inputGroups,
            final String inputGroupType) {
        final List<String> inputNameList = new ArrayList<String>();
        if (MapUtils.isNotEmpty(inputGroups) && inputGroups.containsKey(inputGroupType)) {
            final List<InputParameterRestRep> inputParameterRestRepList = inputGroups.get(inputGroupType).getInputGroup();
            for (final InputParameterRestRep inputParameterRestRep : inputParameterRestRepList) {
                inputNameList.add(inputParameterRestRep.getName());
            }
        }
        return inputNameList;
    }

    private static List<String> convertOutputGroupsToList(final List<OutputParameterRestRep> outputParameterRestRepList) {
        final List<String> outputNameList = new ArrayList<String>();
        if (null != outputParameterRestRepList) {
            for (OutputParameterRestRep outputParameterRestRep : outputParameterRestRepList) {
                outputNameList.add(outputParameterRestRep.getName());
            }
        }
        return outputNameList;
    }

    private static String convertListToString(final List<String> inList) {
        return inList == null ? "" : String.join(",", inList);
    }

    private static Map<String, InputUpdateList> getInputParamsDiff(
            final List<String> left, final List<String> right) {
        return getInputDiff(left, right, CustomServicesConstants.INPUT_PARAMS);
    }

    private static Map<String, InputUpdateList> getInputDiff(
            final List<String> left, final List<String> right, final String inputGroupType) {
        final List<String> updateList = (List<String>) CollectionUtils.subtract(left, right);
        if (CollectionUtils.isEmpty(updateList)) {
            return ImmutableMap.<String, InputUpdateList> builder().build();
        }
        final InputUpdateList update = new InputUpdateList();
        update.setInput(updateList);
        return ImmutableMap.<String, InputUpdateList> builder()
                .put(inputGroupType, update)
                .build();
    }

    private static List<String> getListFromInputOutputString(final String param) {
        return StringUtils.isNotBlank(param) ? Arrays.asList(param.split(",")) : new ArrayList<String>();
    }

    // For each ansible step in workflow this method will look for host_file input and set the options to <hostid, hostname>
    private static void addInventoryFileInputs(final CustomServicesWorkflowDocument.Step step) {
        if (StringUtils.isNotEmpty(step.getType()) && StepType.LOCAL_ANSIBLE.toString().equals(step.getType())) {
            CustomServicesWorkflowDocument.InputGroup inputGroup = step.getInputGroups().get(CustomServicesConstants.ANSIBLE_OPTIONS);
            for (final CustomServicesWorkflowDocument.Input input : inputGroup.getInputGroup()) {
                if (StringUtils.equals(CustomServicesConstants.ANSIBLE_HOST_FILE, input.getName())) {
                    // Setting type and options for host_file
                    input.setType(CustomServicesConstants.InputType.FROM_USER_MULTI.toString());
                    final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives()
                            .getPrimitive(step.getOperation());
                    final CustomServicesPrimitiveResourceList customServicesPrimitiveResourceList = getCatalogClient()
                            .customServicesPrimitives()
                            .getPrimitiveResourcesByType(CustomServicesConstants.ANSIBLE_INVENTORY_TYPE,
                                    primitiveRestRep.getResource().getId());
                    if (customServicesPrimitiveResourceList != null
                            && CollectionUtils.isNotEmpty(customServicesPrimitiveResourceList.getResources())) {
                        final Map<String, String> options = new HashMap<String, String>();
                        for (NamedRelatedResourceRep n : customServicesPrimitiveResourceList.getResources()) {
                            options.put(n.getId().toString(), n.getName());
                        }
                        Logger.debug("Adding inventory file options {}", options);
                        input.setOptions(options);
                    } else {
                        // remove inventory files that have been set previously but deleted.
                        Logger.debug("Removing all the inventory files");
                        input.setOptions(new HashMap<String, String>());
                    }

                    break;
                }
            }
        }
    }

    private static boolean uploadInventoryFiles(final URI packageId, final File[] inventoryFiles) throws java.io.IOException {
        boolean uploadDone = false;
        if (null != packageId && null != inventoryFiles && inventoryFiles.length > 0) {
            for (File inventoryFile : inventoryFiles) {
                CustomServicesPrimitiveResourceRestRep inventoryFilesResourceRestRep = getCatalogClient().customServicesPrimitives()
                        .createPrimitiveResource(CustomServicesConstants.ANSIBLE_INVENTORY_TYPE,
                                inventoryFile, inventoryFile.getName(), packageId);
                if (null == inventoryFilesResourceRestRep) {
                    Logger.error("Error while uploading primitive resource - inventory file %s for ansible package %s",
                            inventoryFile.getName(), packageId);
                    flash.error("Error while uploading primitive resource - inventory file %s for ansible package %s",
                            inventoryFile.getName(), packageId);

                }
                uploadDone = true;
            }
        }
        return uploadDone;
    }

    // Keep only newInventoryFileNames - get existing and remove the ones that are not there in the newly sent list
    private static boolean updateInventoryFiles(final URI packageId, final String newInventoryFileNames) {
        boolean updateDone = false;
        if (null == packageId) {
            return updateDone;
        }

        final Map<String, URI> inventoryFiles = new HashMap<String, URI>();
        final CustomServicesPrimitiveResourceList customServicesPrimitiveResourceList = getCatalogClient().customServicesPrimitives()
                .getPrimitiveResourcesByType(CustomServicesConstants.ANSIBLE_INVENTORY_TYPE, packageId);
        if (null != customServicesPrimitiveResourceList.getResources()) {
            for (NamedRelatedResourceRep inventoryResource : customServicesPrimitiveResourceList.getResources()) {
                inventoryFiles.put(inventoryResource.getName(), inventoryResource.getId());
            }
        }
        final String[] fileNames = null != newInventoryFileNames ? newInventoryFileNames.split(",") : new String[0];
        final List<String> newInventoryFileNamesList = Arrays.asList(fileNames);
        inventoryFiles.keySet().removeAll(newInventoryFileNamesList);
        for (final URI inventoryId : inventoryFiles.values()) {
            getCatalogClient().customServicesPrimitives().deletePrimitiveResource(inventoryId);
            updateDone = true;
        }

        return updateDone;

    }

    private static boolean updateWorkflowInventoryFiles(final String localAnsiblePrimitiveId) {
        boolean updatedWorkflows = false;
        final CustomServicesWorkflowList customServicesWorkflowList = getCatalogClient().customServicesPrimitives().getWorkflows(
                localAnsiblePrimitiveId);
        if (customServicesWorkflowList != null && CollectionUtils.isNotEmpty(customServicesWorkflowList.getWorkflows())) {
            for (final NamedRelatedResourceRep resourceRep : customServicesWorkflowList.getWorkflows()) {
                final URI workflowId = resourceRep.getId();
                final CustomServicesWorkflowRestRep customServicesWorkflowRestRep = getCatalogClient().customServicesPrimitives()
                        .getWorkflow(workflowId);
                if (null != customServicesWorkflowRestRep) {
                    Logger.info("Updating workflow {} with new host inventory files", workflowId);
                    final CustomServicesWorkflowUpdateParam param = new CustomServicesWorkflowUpdateParam();
                    for (final CustomServicesWorkflowDocument.Step step : customServicesWorkflowRestRep.getDocument().getSteps()) {
                        addInventoryFileInputs(step);
                    }
                    param.setDocument(customServicesWorkflowRestRep.getDocument());
                    getCatalogClient().customServicesPrimitives().editWorkflow(workflowId, param);

                    updatedWorkflows = true;
                }
            }
        }
        return updatedWorkflows;
    }

    private static enum WFBuilderNodeTypes {
        FOLDER, WORKFLOW, SCRIPT, ANSIBLE, VIPR;

        public static WFBuilderNodeTypes get(final String name) {
            try {
                return valueOf(name.toUpperCase());
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static class Node {
        private String id;
        private String text;
        @SerializedName("parent")
        private String parentID;
        private CustomServicesPrimitiveRestRep data;
        private String type;
        @SerializedName("a_attr")
        private Map<String, String> anchorAttr = new HashMap<String, String>();

        Node() {
        }

        Node(String id, String text, String parentID, String type) {
            this.id = id;
            this.text = text;
            this.parentID = parentID;
            this.type = type;
        }

        public void addBoldAnchorAttr() {
            this.anchorAttr.put("style", "font-weight:bold;");
        }
    }
}
