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

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowCreateParam;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowList;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowRestRep;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowUpdateParam;
import com.emc.storageos.model.orchestration.PrimitiveCreateParam;
import com.emc.storageos.model.orchestration.PrimitiveList;
import com.emc.storageos.model.orchestration.PrimitiveRestRep;
import com.emc.storageos.model.orchestration.PrimitiveResourceRestRep;
import com.emc.storageos.primitives.Primitive;
import com.emc.storageos.primitives.PrimitiveHelper;
import com.emc.storageos.primitives.ViPRPrimitive;
import com.emc.vipr.model.catalog.WFBulkRep;
import com.emc.vipr.model.catalog.WFDirectoryParam;
import com.emc.vipr.model.catalog.WFDirectoryRestRep;
import com.emc.vipr.model.catalog.WFDirectoryUpdateParam;
import com.emc.vipr.model.catalog.WFDirectoryWorkflowsUpdateParam;
import com.google.gson.annotations.SerializedName;
import controllers.Common;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import util.StringOption;

import org.apache.commons.lang.StringUtils;
import org.owasp.esapi.ESAPI;
import play.Logger;
import play.data.validation.Required;
import play.data.validation.Valid;

import play.mvc.Controller;
import play.mvc.With;
import static util.BourneUtil.getCatalogClient;

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
        FOLDER, WORKFLOW, SCRIPT, ANSIBLE, VIPR_REST;

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
        private PrimitiveRestRep data;
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
        // GET workflow ids and names
        Map<URI, String> oeId2NameMap = new HashMap<URI, String>();
        OrchestrationWorkflowList orchestrationWorkflowList = getCatalogClient()
                .orchestrationPrimitives().getWorkflows();
        if (null != orchestrationWorkflowList
                && null != orchestrationWorkflowList.getWorkflows()) {
            for (NamedRelatedResourceRep o : orchestrationWorkflowList
                    .getWorkflows()) {
                oeId2NameMap.put(o.getId(), o.getName());
            }
        }

        // get workflow directories and prepare nodes
        WFBulkRep wfBulkRep = getCatalogClient().wfDirectories().getAll();
        List<Node> topLevelNodes = new ArrayList<Node>();
		prepareRootNodes(topLevelNodes);
        Node node;
        String nodeParent;
        for (WFDirectoryRestRep wfDirectoryRestRep : wfBulkRep
                .getWfDirectories()) {
            if (null == wfDirectoryRestRep.getParent()) {
                nodeParent = MY_LIBRARY_ROOT;
            } else {
                nodeParent = wfDirectoryRestRep.getParent().getId().toString();
            }
            node = new Node(wfDirectoryRestRep.getId().toString(),
                    wfDirectoryRestRep.getName(), nodeParent, WFBuilderNodeTypes.FOLDER.toString());

            // add workflows that are under this node
            if (null != wfDirectoryRestRep.getWorkflows()) {
                for (URI u : wfDirectoryRestRep.getWorkflows()) {
                    if (oeId2NameMap.containsKey(u)) {
                        topLevelNodes.add(new Node(u.toString(), oeId2NameMap
                                .remove(u), node.id, WFBuilderNodeTypes.WORKFLOW.toString()));
                    }
                }
            }
            topLevelNodes.add(node);
        }

		//add remaining workflows with no parent to default root - My_Library_root
        for(Map.Entry<URI,String> entry : oeId2NameMap.entrySet()) {
            topLevelNodes.add(new Node(entry.getKey().toString(), entry.getValue(), MY_LIBRARY_ROOT, WFBuilderNodeTypes.WORKFLOW.toString()));
        }
        // Get primitives data and prepare nodes
        addPrimitives(topLevelNodes);

        renderJSON(topLevelNodes);
    }

	// Preparing top level nodes in workflow directory
    private static void prepareRootNodes(List<Node> topLevelNodes) {
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

    //TODO: remove this method and use another means of hardcoding
    private static List<OrchestrationWorkflowDocument.Step> getStartEndSteps(){
        OrchestrationWorkflowDocument.Step start = new OrchestrationWorkflowDocument.Step();
        start.setFriendlyName("Start");
        start.setId("Start");
        start.setPositionX(1793);
        start.setPositionY(1783);
        OrchestrationWorkflowDocument.Step end = new OrchestrationWorkflowDocument.Step();
        end.setFriendlyName("End");
        end.setId("End");
        end.setPositionX(2041);
        end.setPositionY(1783);
        List<OrchestrationWorkflowDocument.Step> steps = new ArrayList<OrchestrationWorkflowDocument.Step>();
        steps.add(start);
        steps.add(end);
        return steps;
    }

    public static void createWorkflow(final String workflowName,
            final String dirID) {
        try {
            // Create workflow with just name
            final OrchestrationWorkflowCreateParam param = new OrchestrationWorkflowCreateParam();
            final OrchestrationWorkflowDocument document = new OrchestrationWorkflowDocument();
            document.setName(workflowName);
            List<OrchestrationWorkflowDocument.Step> steps = getStartEndSteps();
            document.setSteps(steps);
            param.setDocument(document);
            final OrchestrationWorkflowRestRep orchestrationWorkflowRestRep = getCatalogClient()
                    .orchestrationPrimitives().createWorkflow(param);

            // Add this workflowid to directory
            if (!MY_LIBRARY_ROOT.equals(dirID) && null != orchestrationWorkflowRestRep) {
                final WFDirectoryUpdateParam wfDirectoryParam = new WFDirectoryUpdateParam();
                final Set<URI> addWorkflows = new HashSet<URI>();
                addWorkflows.add(orchestrationWorkflowRestRep.getId());
                wfDirectoryParam
                        .setWorkflows(new WFDirectoryWorkflowsUpdateParam(
                                addWorkflows, null));
                getCatalogClient().wfDirectories().edit(new URI(dirID),
                        wfDirectoryParam);
            } else {
                flash.error("Error creating workflow");
            }

            renderJSON(orchestrationWorkflowRestRep);
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error("Error creating workflow");
        }
    }

    public static void saveWorkflow(final URI workflowId,
                                      final OrchestrationWorkflowDocument workflowDoc) {
        try {
            final OrchestrationWorkflowUpdateParam param = new OrchestrationWorkflowUpdateParam();
            for (final OrchestrationWorkflowDocument.Step step : workflowDoc.getSteps()){
                final String success_criteria = ESAPI.encoder().decodeForHTML(step.getSuccessCriteria());
                step.setSuccessCriteria(success_criteria);
            }
            param.setDocument(workflowDoc);
            final OrchestrationWorkflowRestRep orchestrationWorkflowRestRep = getCatalogClient()
                    .orchestrationPrimitives().editWorkflow(workflowId,param);
        } catch (final Exception e) {
            Logger.error(e.getMessage());
            flash.error("Error saving workflow");
        }
    }

    public static void getWorkflow(final URI workflowId) {
        OrchestrationWorkflowRestRep orchestrationWorkflowRestRep = getCatalogClient()
                .orchestrationPrimitives().getWorkflow(workflowId);
        renderJSON(orchestrationWorkflowRestRep);
    }

    public static void validateWorkflow(final URI workflowId) {
        OrchestrationWorkflowRestRep orchestrationWorkflowRestRep = getCatalogClient()
                .orchestrationPrimitives().validateWorkflow(workflowId);
        renderJSON(orchestrationWorkflowRestRep);
    }

    public static void publishWorkflow(final URI workflowId) {
        OrchestrationWorkflowRestRep orchestrationWorkflowRestRep = getCatalogClient()
                .orchestrationPrimitives().publishWorkflow(workflowId);
        renderJSON(orchestrationWorkflowRestRep);
    }

    public static void unpublishWorkflow(final URI workflowId) {
        OrchestrationWorkflowRestRep orchestrationWorkflowRestRep = getCatalogClient()
                .orchestrationPrimitives().unpublishWorkflow(workflowId);
        renderJSON(orchestrationWorkflowRestRep);
    }

    public static void editWorkflowName(final String id, final String newName) {
        try {
            URI workflowURI = new URI(id);
            OrchestrationWorkflowRestRep orchestrationWorkflowRestRep = getCatalogClient()
                    .orchestrationPrimitives().getWorkflow(workflowURI);
            if (null != orchestrationWorkflowRestRep) {
                final OrchestrationWorkflowUpdateParam param = new OrchestrationWorkflowUpdateParam();
                param.setDocument(orchestrationWorkflowRestRep.getDocument());
                param.getDocument().setName(newName);
                getCatalogClient().orchestrationPrimitives().editWorkflow(
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
            getCatalogClient().orchestrationPrimitives().deleteWorkflow(
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

    // Get Primitives and add them to directory list
    private static void addPrimitives(List<Node> topLevelNodes) {
        try {
            PrimitiveList primitiveList = getCatalogClient()
                    .orchestrationPrimitives().getPrimitives();
            if (null == primitiveList) {
                return;
            }
            for (PrimitiveRestRep primitiveRestRep : primitiveList
                    .getPrimitives()) {
                String parent;
                String primitiveName = primitiveRestRep.getName();
                Primitive primitive = PrimitiveHelper.get(primitiveName);
                if (primitive instanceof ViPRPrimitive) {
                    parent = VIPR_PRIMITIVE_ROOT;
                } else {
                    // Default grouping: "ViPR Library"
                    parent = VIPR_LIBRARY_ROOT;
                }
                //TODO: Handle other primitive types (ansible, script)
                Node node = new Node(primitive.getClass().getSimpleName(),
                        primitiveRestRep.getFriendlyName(), parent,
                        WFBuilderNodeTypes.VIPR_REST.toString());
                node.data = primitiveRestRep;
                topLevelNodes.add(node);
            }
        } catch (final Exception e) {
            Logger.error(e.getMessage());
        }
    }

    public static class ShellScriptPrimitiveForm{
        // Name and Description step
        @Required
        private String name;
        @Required
        private String description;
        @Required
        private File script;
        private String scriptName;
        private String inputs; //comma separated list of inputs
        private String outputs; // comma separated list of ouputs

        //TODO
        public void validate(){

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
    }


    public static void create(@Valid ShellScriptPrimitiveForm shellPrimitive){
        shellPrimitive.validate();
        //TODO : call APIs to load script and create this primitive
        view();
    }
	
	public static class LocalAnsiblePrimitiveForm {
        @Required
        private String name;
        public String description;

        private boolean existing;
        private String existingResource;
        private File ansiblePackage;
        private String ansiblePackageName;
        private String ansiblePlaybook;
        @Required
        private String hostFilePath;
        private String inputs; //comma separated list of inputs
        private String outputs; // comma separated list of ouputs

        //TODO
        public void validate(){

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
    }

    public static void createLocalAnsiblePrimitive(@Valid LocalAnsiblePrimitiveForm localAnsible){
        localAnsible.validate();

        try {
            PrimitiveResourceRestRep primitiveResourceRestRep = null;
            if(localAnsible.isExisting()) {
                //TODO: waiting for resources GET
            }
            else if(null != localAnsible.ansiblePackage) {
                //upload ansible package
                primitiveResourceRestRep = getCatalogClient().orchestrationPrimitives().createPrimitiveResource("ANSIBLE", localAnsible.ansiblePackage, localAnsible.ansiblePackageName);
            }

            // Create Primitive
            if (null != primitiveResourceRestRep) {
                PrimitiveCreateParam primitiveCreateParam = new PrimitiveCreateParam();
                //TODO - remove this hardcoded string once the enum is available
                primitiveCreateParam.setType("ANSIBLE");
                primitiveCreateParam.setName(localAnsible.getName());
                primitiveCreateParam.setDescription(localAnsible.getDescription());
                primitiveCreateParam.setResource(primitiveResourceRestRep.getId());
                primitiveCreateParam.setAttributes(new HashMap<String, String>());
                primitiveCreateParam.getAttributes().put("playbook", localAnsible.getAnsiblePlaybook());
                if (StringUtils.isNotEmpty(localAnsible.getInputs())) {
                    primitiveCreateParam.setInput(Arrays.asList(localAnsible.getInputs().split(",")));
                }
                if (StringUtils.isNotEmpty(localAnsible.getOutputs())) {
                    primitiveCreateParam.setOutput(Arrays.asList(localAnsible.getOutputs().split(",")));
                }

                getCatalogClient().orchestrationPrimitives().createPrimitive(primitiveCreateParam);
            }
            else {
                //TODO: throw error
            }
        }
        catch (final Exception e) {
            Logger.error(e.getMessage());
        }

        view();

    }

    private static void setAnsibleResources() {
        //TODO - Get these resources using API and remove this temporary data
        List<StringOption> ansibleResourceNames = new ArrayList<StringOption>();
        ansibleResourceNames.add(new StringOption("1x","CreateHostPackage"));
        ansibleResourceNames.add(new StringOption("2x","Create Project Package"));
        renderArgs.put("ansibleResourceNames", ansibleResourceNames);

    }
}
