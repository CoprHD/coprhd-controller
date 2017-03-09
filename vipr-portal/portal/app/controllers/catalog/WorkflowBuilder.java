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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.owasp.esapi.ESAPI;

import play.Logger;
import play.data.validation.Required;
import play.data.validation.Valid;
import play.mvc.Controller;
import play.mvc.With;
import util.StringOption;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.model.customservices.CustomServicesWorkflowCreateParam;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowList;
import com.emc.storageos.model.customservices.CustomServicesWorkflowRestRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowUpdateParam;
import com.emc.storageos.model.customservices.InputParameterRestRep;
import com.emc.storageos.model.customservices.InputUpdateParam;
import com.emc.storageos.model.customservices.OutputParameterRestRep;
import com.emc.storageos.model.customservices.OutputUpdateParam;
import com.emc.storageos.primitives.CustomServicesPrimitive;
import com.emc.vipr.model.catalog.WFBulkRep;
import com.emc.vipr.model.catalog.WFDirectoryParam;
import com.emc.vipr.model.catalog.WFDirectoryRestRep;
import com.emc.vipr.model.catalog.WFDirectoryUpdateParam;
import com.emc.vipr.model.catalog.WFDirectoryWorkflowsUpdateParam;
import com.google.gson.annotations.SerializedName;

import controllers.Common;

/**
 * @author Nick Aquino
 */
@With(Common.class)
public class WorkflowBuilder extends Controller {
    private static final String NO_PARENT = "#";
    private static final String MY_LIBRARY_ROOT = "myLib";
    private static final String VIPR_LIBRARY_ROOT = "viprLib";
    private static final String VIPR_PRIMITIVE_ROOT = "viprrest";
    private static final String NODE_TYPE_FILE = "file";
    private static final String MY_LIBRARY = "My Library";
    private static final String VIPR_LIBRARY = "ViPR Library";
    private static final String VIPR_PRIMITIVE_LIBRARY = "ViPR REST Primitives";

    public static void view() {
        setAnsibleResources();
        render();
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

        Node() {
        }

        Node(String id, String text, String parentID, String type) {
            this.id = id;
            this.text = text;
            this.parentID = parentID;
            this.type = type;
        }
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
        addPrimitivesByType(topLevelNodes, WFBuilderNodeTypes.ANSIBLE.toString(), MY_LIBRARY_ROOT, fileParents);
        addPrimitivesByType(topLevelNodes, WFBuilderNodeTypes.SCRIPT.toString(), MY_LIBRARY_ROOT, fileParents);
        addPrimitivesByType(topLevelNodes, WFBuilderNodeTypes.VIPR.toString(), VIPR_PRIMITIVE_ROOT, null);

        // Add workflows
        final CustomServicesWorkflowList customServicesWorkflowList = getCatalogClient()
                .customServicesPrimitives().getWorkflows();
        if (null != customServicesWorkflowList
                && null != customServicesWorkflowList.getWorkflows()) {
            for (NamedRelatedResourceRep o : customServicesWorkflowList
                    .getWorkflows()) {
                final String parent = fileParents.containsKey(o.getId()) ? fileParents.get(o.getId()).getId().toString() : MY_LIBRARY_ROOT;
                topLevelNodes.add(new Node(o.getId().toString(), o.getName(), parent, WFBuilderNodeTypes.WORKFLOW.toString()));
            }
        }

        renderJSON(topLevelNodes);
    }

    // Preparing top level nodes in workflow directory
    private static void prepareRootNodes(final List<Node> topLevelNodes) {
        topLevelNodes.add(new Node(MY_LIBRARY_ROOT, MY_LIBRARY, NO_PARENT, WFBuilderNodeTypes.FOLDER.toString()));
        topLevelNodes.add(new Node(VIPR_LIBRARY_ROOT, VIPR_LIBRARY, NO_PARENT, WFBuilderNodeTypes.FOLDER.toString()));
        topLevelNodes.add(new Node(VIPR_PRIMITIVE_ROOT, VIPR_PRIMITIVE_LIBRARY, VIPR_LIBRARY_ROOT, WFBuilderNodeTypes.FOLDER.toString()));
    }

    public static void editWFDirName(String id, String newName) {
        try {
            WFDirectoryUpdateParam param = new WFDirectoryUpdateParam();
            param.setName(newName);
            getCatalogClient().wfDirectories().edit(new URI(id), param);
        } catch (final Exception e) {
            Logger.error(e.getMessage());
        }
    }

    public static void deleteWFDir(String id) {
        try {
            getCatalogClient().wfDirectories().delete(new URI(id));
        } catch (final Exception e) {
            Logger.error(e.getMessage());
        }
    }

    public static void createWFDir(String name, String parent) {
        try {
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
        } catch (final Exception e) {
            Logger.error(e.getMessage());
        }
    }

    // TODO: remove this method and use another means of hardcoding
    private static List<CustomServicesWorkflowDocument.Step> getStartEndSteps() {
        CustomServicesWorkflowDocument.Step start = new CustomServicesWorkflowDocument.Step();
        start.setFriendlyName("Start");
        start.setId("Start");
        start.setPositionX(1793);
        start.setPositionY(1783);
        CustomServicesWorkflowDocument.Step end = new CustomServicesWorkflowDocument.Step();
        end.setFriendlyName("End");
        end.setId("End");
        end.setPositionX(2041);
        end.setPositionY(1783);
        List<CustomServicesWorkflowDocument.Step> steps = new ArrayList<CustomServicesWorkflowDocument.Step>();
        steps.add(start);
        steps.add(end);
        return steps;
    }

    public static void createWorkflow(final String workflowName,
            final String dirID) {
        try {
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
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error("Error creating workflow");
        }
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
        try {
            final CustomServicesWorkflowUpdateParam param = new CustomServicesWorkflowUpdateParam();
            for (final CustomServicesWorkflowDocument.Step step : workflowDoc.getSteps()) {
                final String success_criteria = ESAPI.encoder().decodeForHTML(step.getSuccessCriteria());
                step.setSuccessCriteria(success_criteria);
            }
            param.setDocument(workflowDoc);
            final CustomServicesWorkflowRestRep customServicesWorkflowRestRep = getCatalogClient()
                    .customServicesPrimitives().editWorkflow(workflowId, param);
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error("Error saving workflow");
        }
    }

    public static void getWorkflow(final URI workflowId) {
        CustomServicesWorkflowRestRep customServicesWorkflowRestRep = getCatalogClient()
                .customServicesPrimitives().getWorkflow(workflowId);
        renderJSON(customServicesWorkflowRestRep);
    }

    public static void validateWorkflow(final URI workflowId) {
        CustomServicesWorkflowRestRep customServicesWorkflowRestRep = getCatalogClient()
                .customServicesPrimitives().validateWorkflow(workflowId);
        renderJSON(customServicesWorkflowRestRep);
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

    public static void editWorkflowName(final String id, final String newName) {
        try {
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
        } catch (final Exception e) {
            Logger.error(e.getMessage());
        }
    }

    public static void deleteWorkflow(final String workflowID,
            final String dirID) {
        try {
            final URI workflowURI = new URI(workflowID);
            // Delete workflow
            getCatalogClient().customServicesPrimitives().deleteWorkflow(
                    workflowURI);

            // Delete this reference in WFDirectory
            final WFDirectoryUpdateParam param = new WFDirectoryUpdateParam();
            final Set<URI> removeWorkflows = new HashSet<URI>();
            removeWorkflows.add(workflowURI);
            param.setWorkflows(new WFDirectoryWorkflowsUpdateParam(null,
                    removeWorkflows));
            getCatalogClient().wfDirectories().edit(new URI(dirID), param);
        } catch (final Exception e) {
            Logger.error(e.getMessage());
        }
    }


    private static void addPrimitivesByType(final List<Node> topLevelNodes, final String type, String parentDefault,
            final Map<URI, WFDirectoryRestRep> fileParents) {
        final CustomServicesPrimitiveList primitiveList = getCatalogClient()
                .customServicesPrimitives().getPrimitivesByType(type);
        if (null == primitiveList || null == primitiveList.getPrimitives()) {
            return;
        }

        List<CustomServicesPrimitiveRestRep> primitives = getCatalogClient().customServicesPrimitives().getByIds(primitiveList.getPrimitives());
        for (final CustomServicesPrimitiveRestRep primitive : primitives) {
            final String parent = (fileParents != null && fileParents.containsKey(primitive.getId()))
                    ? fileParents.get(primitive.getId()).getId().toString() : parentDefault;
            final Node node;

            if (WFBuilderNodeTypes.VIPR.toString().equals(type)) {
                node = new Node(primitive.getId().toString(),
                        primitive.getFriendlyName(), parent, type);
            } else {
                node = new Node(primitive.getId().toString(),
                        primitive.getName(), parent, type);
            }

            // TODO: remove this later
            node.data = primitive;
            topLevelNodes.add(node);
        }
    }

    public static class ShellScriptPrimitiveForm {
        private String id; // this is empty for CREATE
        // Name and Description step
        @Required
        private String name;
        @Required
        private String description;
        @Required
        private File script;
        @Required
        private String scriptName;
        private String inputs; // comma separated list of inputs
        private String outputs; // comma separated list of ouputs
        private boolean newScript; // if true create new resource(delete any existing)
        private String wfDirID; // this is empty for EDIT

        // TODO
        public void validate() {
            // check if script is not null
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public boolean isNewScript() {
            return newScript;
        }

        public void setNewScript(boolean newScript) {
            this.newScript = newScript;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public File getScript() {
            return script;
        }

        public void setScript(File script) {
            this.script = script;
        }

        public String getScriptName() {
            return scriptName;
        }

        public void setScriptName(String scriptName) {
            this.scriptName = scriptName;
        }

        public String getInputs() {
            return inputs;
        }

        public void setInputs(String inputs) {
            this.inputs = inputs;
        }

        public String getOutputs() {
            return outputs;
        }

        public void setOutputs(String outputs) {
            this.outputs = outputs;
        }

        public String getWfDirID() {
            return wfDirID;
        }

        public void setWfDirID(String wfDirID) {
            this.wfDirID = wfDirID;
        }
    }

    private static void editShellScriptPrimitive(final ShellScriptPrimitiveForm shellPrimitive) {
        try {
            final URI shellPrimitiveID = new URI(shellPrimitive.getId());
            final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives().getPrimitive(shellPrimitiveID);
            if (null != primitiveRestRep) {
                // Update name, description
                final CustomServicesPrimitiveUpdateParam primitiveUpdateParam = new CustomServicesPrimitiveUpdateParam();
                primitiveUpdateParam.setName(shellPrimitive.getName());
                primitiveUpdateParam.setDescription(shellPrimitive.getDescription());

                // Get and update differences between existing and new inputs
                final List<String> newInputs = getListFromInputOutputString(shellPrimitive.getInputs());
                final List<String> existingInputs = convertInputGroupsToList(primitiveRestRep.getInputGroups());
                final InputUpdateParam inputUpdateParam = new InputUpdateParam();
                inputUpdateParam.setRemove((List<String>) CollectionUtils.subtract(existingInputs, newInputs));
                inputUpdateParam.setAdd((List<String>) CollectionUtils.subtract(newInputs, existingInputs));
                primitiveUpdateParam.setInput(inputUpdateParam);

                // Get and update differences between existing and new outputs
                final List<String> newOutputs = getListFromInputOutputString(shellPrimitive.getOutputs());
                final List<String> existingOutputs = convertOutputGroupsToList(primitiveRestRep.getOutput());
                OutputUpdateParam outputUpdateParam = new OutputUpdateParam();
                outputUpdateParam.setRemove((List<String>) CollectionUtils.subtract(existingOutputs, newOutputs));
                outputUpdateParam.setAdd((List<String>) CollectionUtils.subtract(newOutputs, existingOutputs));
                primitiveUpdateParam.setOutput(outputUpdateParam);

                if (shellPrimitive.newScript) {
                    // TODO: delete old resource

                    // create new resource
                    final CustomServicesPrimitiveResourceRestRep primitiveResourceRestRep = getCatalogClient().customServicesPrimitives()
                            .createPrimitiveResource("SCRIPT", shellPrimitive.script, shellPrimitive.scriptName);
                    if (null != primitiveResourceRestRep) {
                        // TODO: update resource link
                    }
                }
                // TODO: update script name ( if it is different from existing name)

                getCatalogClient().customServicesPrimitives().updatePrimitive(shellPrimitiveID, primitiveUpdateParam);
            }
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        }
    }

    private static List<String> getListFromInputOutputString(final String param) {
        return StringUtils.isNotBlank(param) ? Arrays.asList(param.split(",")) : new ArrayList<String>();
    }

    private static void createShellScriptPrimitive(final ShellScriptPrimitiveForm shellPrimitive) {
        try {

            final CustomServicesPrimitiveResourceRestRep primitiveResourceRestRep = getCatalogClient().customServicesPrimitives()
                    .createPrimitiveResource("SCRIPT", shellPrimitive.script, shellPrimitive.scriptName);
            if (null != primitiveResourceRestRep) {
                final CustomServicesPrimitiveCreateParam primitiveCreateParam = new CustomServicesPrimitiveCreateParam();
                // TODO - remove this hardcoded string once the enum is available
                primitiveCreateParam.setType("SCRIPT");
                primitiveCreateParam.setName(shellPrimitive.getName());
                primitiveCreateParam.setFriendlyName(shellPrimitive.getName());
                primitiveCreateParam.setDescription(shellPrimitive.getDescription());
                primitiveCreateParam.setResource(primitiveResourceRestRep.getId());
                if (StringUtils.isNotEmpty(shellPrimitive.getInputs())) {
                    primitiveCreateParam.setInput(getListFromInputOutputString(shellPrimitive.getInputs()));
                }
                if (StringUtils.isNotEmpty(shellPrimitive.getOutputs())) {
                    primitiveCreateParam.setOutput(getListFromInputOutputString(shellPrimitive.getOutputs()));
                }
                
                final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives()
                        .createPrimitive(primitiveCreateParam);
                if (primitiveRestRep != null) {
                    // add this to wf directory
                    addResourceToWFDirectory(primitiveRestRep.getId(), shellPrimitive.getWfDirID());
                } else {
                    flash.error("Error while creating primitive");
                }
            } else {
                flash.error("Error while uploading primitive resource");
            }
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            // flash.error(e.getMessage());
        }
    }

    public static void saveShellScriptPrimitive(@Valid final ShellScriptPrimitiveForm shellPrimitive) {
        shellPrimitive.validate();
        if (StringUtils.isNotEmpty(shellPrimitive.getId())) {
            editShellScriptPrimitive(shellPrimitive);
        } else {
            createShellScriptPrimitive(shellPrimitive);
        }
        view();

    }

    public static class LocalAnsiblePrimitiveForm {
        private String id;
        @Required
        private String name;
        private String description;
        private boolean existing;
        private String existingResource;
        private File ansiblePackage;
        @Required
        private String ansiblePackageName;
        @Required
        private String ansiblePlaybook;
        @Required
        private String hostFilePath;
        private String inputs; // comma separated list of inputs
        private String outputs; // comma separated list of ouputs
        private String wfDirID;

        // TODO
        public void validate() {

        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getExistingResource() {
            return existingResource;
        }

        public void setExistingResource(String existingResource) {
            this.existingResource = existingResource;
        }

        public File getAnsiblePackage() {
            return ansiblePackage;
        }

        public void setAnsiblePackage(File ansiblePackage) {
            this.ansiblePackage = ansiblePackage;
        }

        public String getAnsiblePackageName() {
            return ansiblePackageName;
        }

        public void setAnsiblePackageName(String ansiblePackageName) {
            this.ansiblePackageName = ansiblePackageName;
        }

        public String getHostFilePath() {
            return hostFilePath;
        }

        public void setHostFilePath(String hostFilePath) {
            this.hostFilePath = hostFilePath;
        }

        public String getInputs() {
            return inputs;
        }

        public void setInputs(String inputs) {
            this.inputs = inputs;
        }

        public String getOutputs() {
            return outputs;
        }

        public void setOutputs(String outputs) {
            this.outputs = outputs;
        }

        public String getAnsiblePlaybook() {
            return ansiblePlaybook;
        }

        public void setAnsiblePlaybook(String ansiblePlaybook) {
            this.ansiblePlaybook = ansiblePlaybook;
        }

        public boolean isExisting() {
            return existing;
        }

        public void setExisting(boolean existing) {
            this.existing = existing;
        }

        public String getWfDirID() {
            return wfDirID;
        }

        public void setWfDirID(String wfDirID) {
            this.wfDirID = wfDirID;
        }
    }

    public static void saveLocalAnsiblePrimitive(@Valid final LocalAnsiblePrimitiveForm localAnsible) {
        localAnsible.validate();
        if (StringUtils.isNotEmpty(localAnsible.getId())) {
            editLocalAnsiblePrimitive(localAnsible);
        } else {
            createLocalAnsiblePrimitive(localAnsible);
        }
        view();

    }

    private static void editLocalAnsiblePrimitive(final LocalAnsiblePrimitiveForm localAnsible) {
        try {
            final URI localAnsiblePrimitiveID = new URI(localAnsible.getId());
            final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives().getPrimitive(localAnsiblePrimitiveID);
            if (null != primitiveRestRep) {
                // Update name, description
                final CustomServicesPrimitiveUpdateParam primitiveUpdateParam = new CustomServicesPrimitiveUpdateParam();
                primitiveUpdateParam.setName(localAnsible.getName());
                primitiveUpdateParam.setDescription(localAnsible.getDescription());

                // Get and update differences between existing and new inputs
                final List<String> newInputs = getListFromInputOutputString(localAnsible.getInputs());
                final List<String> existingInputs = convertInputGroupsToList(primitiveRestRep.getInputGroups());
                final InputUpdateParam inputUpdateParam = new InputUpdateParam();
                inputUpdateParam.setRemove((List<String>) CollectionUtils.subtract(existingInputs, newInputs));
                inputUpdateParam.setAdd((List<String>) CollectionUtils.subtract(newInputs, existingInputs));
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
                primitiveUpdateParam.getAttributes().put("playbook", localAnsible.getAnsiblePlaybook());

                if (!localAnsible.isExisting()) {
                    // TODO: delete old resource

                    // create new resource
                    final CustomServicesPrimitiveResourceRestRep primitiveResourceRestRep = getCatalogClient().customServicesPrimitives()
                            .createPrimitiveResource("ANSIBLE", localAnsible.ansiblePackage, localAnsible.ansiblePackageName);
                    if (null != primitiveResourceRestRep) {
                        // TODO: update resource link
                    }
                }
                // TODO: update script name ( if it is different from existing name)

                getCatalogClient().customServicesPrimitives().updatePrimitive(localAnsiblePrimitiveID, primitiveUpdateParam);
            }
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        }

    }

    private static void createLocalAnsiblePrimitive(@Valid final LocalAnsiblePrimitiveForm localAnsible) {
        try {
            CustomServicesPrimitiveResourceRestRep primitiveResourceRestRep = null;
            if (localAnsible.isExisting()) {
                // TODO: waiting for resources GET
            } else if (null != localAnsible.ansiblePackage) {
                // upload ansible package
                primitiveResourceRestRep = getCatalogClient().customServicesPrimitives().createPrimitiveResource("ANSIBLE",
                        localAnsible.ansiblePackage, localAnsible.ansiblePackageName);
            }

            // Create Primitive
            if (null != primitiveResourceRestRep) {

                final CustomServicesPrimitiveCreateParam primitiveCreateParam = new CustomServicesPrimitiveCreateParam();
                // TODO - remove this hardcoded string once the enum is available
                primitiveCreateParam.setType("ANSIBLE");
                primitiveCreateParam.setName(localAnsible.getName());
                primitiveCreateParam.setDescription(localAnsible.getDescription());
                primitiveCreateParam.setFriendlyName(localAnsible.getName());
                primitiveCreateParam.setResource(primitiveResourceRestRep.getId());
                primitiveCreateParam.setAttributes(new HashMap<String, String>());
                primitiveCreateParam.getAttributes().put("playbook", localAnsible.getAnsiblePlaybook());
                if (StringUtils.isNotEmpty(localAnsible.getInputs())) {
                    primitiveCreateParam.setInput(getListFromInputOutputString(localAnsible.getInputs()));
                }
                if (StringUtils.isNotEmpty(localAnsible.getOutputs())) {
                    primitiveCreateParam.setOutput(getListFromInputOutputString(localAnsible.getOutputs()));
                }

                final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives()
                        .createPrimitive(primitiveCreateParam);
                if (primitiveRestRep != null) {
                    // add this to wf directory
                    addResourceToWFDirectory(primitiveRestRep.getId(), localAnsible.getWfDirID());
                } else {
                    flash.error("Error while creating primitive");
                }
            } else {
                flash.error("Error while uploading primitive resource");
            }
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error(e.getMessage());
        }

        view();

    }

    private static void setAnsibleResources() {
        // TODO - Get these resources using API and remove this temporary data
        final List<StringOption> ansibleResourceNames = new ArrayList<StringOption>();
        ansibleResourceNames.add(new StringOption("1x", "CreateHostPackage"));
        ansibleResourceNames
                .add(new StringOption("urn:storageos:AnsiblePackage:32efea07-d2b7-4581-b577-60c7d95c7f53:vdc1", "Create Project Package"));
        renderArgs.put("ansibleResourceNames", ansibleResourceNames);

    }

    private static List<String> convertInputGroupsToList(final Map<String, CustomServicesPrimitiveRestRep.InputGroup> inputGroups) {
        final List<String> inputNameList = new ArrayList<String>();
        if (null != inputGroups && !inputGroups.isEmpty() && inputGroups.containsKey(CustomServicesPrimitive.InputType.INPUT_PARAMS)) {
            final List<InputParameterRestRep> inputParameterRestRepList = inputGroups.get(CustomServicesPrimitive.InputType.INPUT_PARAMS).getInputGroup();
            for (InputParameterRestRep inputParameterRestRep : inputParameterRestRepList) {
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

    private static ShellScriptPrimitiveForm mapPrimitiveScriptRestToForm(final CustomServicesPrimitiveRestRep primitiveRestRep) {
        final ShellScriptPrimitiveForm shellPrimitive = new ShellScriptPrimitiveForm();
        if (null != primitiveRestRep) {
            shellPrimitive.setId(primitiveRestRep.getId().toString());
            shellPrimitive.setName(primitiveRestRep.getName());
            shellPrimitive.setDescription(primitiveRestRep.getDescription());
            shellPrimitive.setInputs(convertListToString(convertInputGroupsToList(primitiveRestRep.getInputGroups())));
            shellPrimitive.setOutputs(convertListToString(convertOutputGroupsToList(primitiveRestRep.getOutput())));
            // TODO: get script name from API
            shellPrimitive.setScriptName("SAMPLE NAME");
        }
        return shellPrimitive;
    }

    private static LocalAnsiblePrimitiveForm mapPrimitiveLARestToForm(final CustomServicesPrimitiveRestRep primitiveRestRep) {
        final LocalAnsiblePrimitiveForm localAnsiblePrimitiveForm = new LocalAnsiblePrimitiveForm();
        if (null != primitiveRestRep) {
            localAnsiblePrimitiveForm.setId(primitiveRestRep.getId().toString());
            localAnsiblePrimitiveForm.setName(primitiveRestRep.getName());
            localAnsiblePrimitiveForm.setDescription(primitiveRestRep.getDescription());
            localAnsiblePrimitiveForm.setInputs(convertListToString(convertInputGroupsToList(primitiveRestRep.getInputGroups())));
            localAnsiblePrimitiveForm.setOutputs(convertListToString(convertOutputGroupsToList(primitiveRestRep.getOutput())));
            // TODO: get script name from API
            localAnsiblePrimitiveForm.setAnsiblePackageName("SAMPLE NAME");
            localAnsiblePrimitiveForm.setAnsiblePlaybook(primitiveRestRep.getAttributes().get("playbook"));
            localAnsiblePrimitiveForm.setExisting(true);
        }
        return localAnsiblePrimitiveForm;
    }

    public static void getPrimitive(final URI primitiveId, final String primitiveType) {
        final CustomServicesPrimitiveRestRep primitiveRestRep = getCatalogClient().customServicesPrimitives().getPrimitive(primitiveId);
        if (null == primitiveRestRep) {
            flash.error("Invalid primitive ID");
        } else {
            switch (WFBuilderNodeTypes.valueOf(primitiveType)) {
                case SCRIPT:
                    renderJSON(mapPrimitiveScriptRestToForm(primitiveRestRep));
                case ANSIBLE:
                    renderJSON(mapPrimitiveLARestToForm(primitiveRestRep));
                default:
                    Logger.error("Invalid primitive type: %s", primitiveType);
            }

        }

    }
}
