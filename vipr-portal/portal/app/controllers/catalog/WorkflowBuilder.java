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
import com.emc.storageos.model.orchestration.OrchestrationWorkflowList;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowCreateParam;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowRestRep;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowUpdateParam;
import com.emc.storageos.model.orchestration.PrimitiveList;
import com.emc.storageos.model.orchestration.PrimitiveRestRep;
import com.emc.storageos.model.orchestration.internal.Primitive;
import com.emc.storageos.model.orchestration.internal.PrimitiveHelper;
import com.emc.storageos.model.orchestration.internal.ViPRPrimitive;
import com.emc.vipr.model.catalog.WFBulkRep;
import com.emc.vipr.model.catalog.WFDirectoryParam;
import com.emc.vipr.model.catalog.WFDirectoryRestRep;
import com.emc.vipr.model.catalog.WFDirectoryUpdateParam;
import com.emc.vipr.model.catalog.WFDirectoryWorkflowsUpdateParam;

import com.google.gson.annotations.SerializedName;

import controllers.Common;

import play.Logger;
import play.mvc.Controller;
import play.mvc.With;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static util.BourneUtil.getCatalogClient;

/**
 * @author Nick Aquino
 */
@With(Common.class)
public class WorkflowBuilder extends Controller {
    private static final String MY_LIBRARY_ROOT = "myLib";
    private static final String VIPR_LIBRARY_ROOT = "viprLib";
    private static final String VIPR_PRIMITIVE_ROOT = "viprrest";
    private static final String NODE_TYPE_FILE = "file";

    public static void view() {
        render();
    }


    private static class Node {
        private String id;
        private String  text;
        @SerializedName("parent")
        private String parentID;
        private PrimitiveRestRep data;
        private String type;

        Node() {
        }

        Node(String id, String text, String parentID) {
            this.id = id;
            this.text = text;
            this.parentID = parentID;
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
        OrchestrationWorkflowList orchestrationWorkflowList = getCatalogClient().orchestrationPrimitives().getWorkflows();
        if (null != orchestrationWorkflowList && null != orchestrationWorkflowList.getWorkflows()) {
            for (NamedRelatedResourceRep o : orchestrationWorkflowList.getWorkflows()) {
                oeId2NameMap.put(o.getId(), o.getName());
            }
        }

        // get workflow directories and prepare nodes
        WFBulkRep wfBulkRep = getCatalogClient().wfDirectories().getAll();
        List<Node> topLevelNodes = new ArrayList<Node>();
        Node node;
        String nodeParent;
        for (WFDirectoryRestRep wfDirectoryRestRep: wfBulkRep.getWfDirectories()) {
            if ( null == wfDirectoryRestRep.getParent()) {
                nodeParent = MY_LIBRARY_ROOT;
            }
            else {
                nodeParent = wfDirectoryRestRep.getParent().getId().toString();
            }
            node = new Node(wfDirectoryRestRep.getId().toString(), wfDirectoryRestRep.getName(), nodeParent);

            // add workflows that are under this node
            if (null != wfDirectoryRestRep.getWorkflows()) {
                for (URI u : wfDirectoryRestRep.getWorkflows()) {
                    if (oeId2NameMap.containsKey(u)) {
                        topLevelNodes.add(new Node(u.toString(), oeId2NameMap.get(u), node.id, NODE_TYPE_FILE));
                    }
                }
            }
            topLevelNodes.add(node);
        }

        // Get primitives data and prepare nodes
        addPrimitives(topLevelNodes);

        renderJSON(topLevelNodes);
    }

    public static void editWFDirName(String id, String newName) {
        try {
            WFDirectoryUpdateParam param = new WFDirectoryUpdateParam();
            param.setName(newName);
            getCatalogClient().wfDirectories().edit(new URI(id), param);
        }
        catch (Exception e) {
            Logger.error(e.getMessage());
        }
    }

    public static void deleteWFDir(String id) {
        try {
            getCatalogClient().wfDirectories().delete(new URI(id));
        }
        catch (Exception e) {
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
                parentURI = MY_LIBRARY_ROOT.equals(parent) ? null : new URI(parent);
            }
            param.setParent(parentURI);
            WFDirectoryRestRep wfDirectoryRestRep = getCatalogClient().wfDirectories().create(param);
            renderJSON(wfDirectoryRestRep);
        }
        catch (Exception e) {
            Logger.error(e.getMessage());
        }
    }

    public static void createWorkflow(final String workflowName, final String dirID) {
        try {
            // Create workflow with just name
            final OrchestrationWorkflowCreateParam param = new OrchestrationWorkflowCreateParam();
            final OrchestrationWorkflowDocument document = new OrchestrationWorkflowDocument();
            document.setName(workflowName);
            param.setDocument(document);
            final OrchestrationWorkflowRestRep orchestrationWorkflowRestRep = getCatalogClient().orchestrationPrimitives().createWorkflow(param);

            // Add this workflowid to directory
            if (null != orchestrationWorkflowRestRep) {
                final WFDirectoryUpdateParam wfDirectoryParam = new WFDirectoryUpdateParam();
                final Set<URI> addWorkflows = new HashSet<URI>();
                addWorkflows.add(orchestrationWorkflowRestRep.getId());
                wfDirectoryParam.setWorkflows(new WFDirectoryWorkflowsUpdateParam(addWorkflows, null));
                getCatalogClient().wfDirectories().edit(new URI(dirID), wfDirectoryParam);
            }
            else {
                flash.error("Error creating workflow");
            }

            renderJSON(orchestrationWorkflowRestRep);
        }
        catch (Exception e) {
            Logger.error(e.getMessage());
            flash.error("Error creating workflow");
        }
    }

    public static void editWorkflowName(final String id, final String newName) {
        try {
            final OrchestrationWorkflowUpdateParam param = new OrchestrationWorkflowUpdateParam();
            param.setDocument(new OrchestrationWorkflowDocument());
            param.getDocument().setName(newName);
            getCatalogClient().orchestrationPrimitives().editWorkflow(new URI(id), param);
        }
        catch (Exception e) {
            Logger.error(e.getMessage());
        }
    }

    public static void deleteWorkflow(final String workflowID, final String dirID) {
        try {
            final URI workflowURI = new URI(workflowID);
            // Delete workflow
            getCatalogClient().orchestrationPrimitives().deleteWorkflow(workflowURI);

            // Delete this reference in WFDirectory
            final WFDirectoryUpdateParam param = new WFDirectoryUpdateParam();
            final Set<URI> removeWorkflows = new HashSet<URI>();
            removeWorkflows.add(workflowURI);
            param.setWorkflows(new WFDirectoryWorkflowsUpdateParam(null, removeWorkflows));
            getCatalogClient().wfDirectories().edit(new URI(dirID), param);
        }
        catch (Exception e) {
            Logger.error(e.getMessage());
        }
    }

    // Get Primitives and add them to directory list
    private static void addPrimitives(List<Node> topLevelNodes) {
        try {
            PrimitiveList primitiveList = getCatalogClient().orchestrationPrimitives().getPrimitives();
            if (null == primitiveList) {
                return;
            }
            for (PrimitiveRestRep primitiveRestRep : primitiveList.getPrimitives()) {
                String parent;
                String primitiveName = primitiveRestRep.getName();
                Primitive primitive = PrimitiveHelper.get(primitiveName);
                if (primitive instanceof ViPRPrimitive) {
                    parent = VIPR_PRIMITIVE_ROOT;
                }
                else {
                    // Default grouping: "ViPR Library"
                    parent = VIPR_LIBRARY_ROOT;
                }
                Node node = new Node(primitive.getClass().getSimpleName(), primitiveRestRep.getFriendlyName(), parent, NODE_TYPE_FILE);
                node.data = primitiveRestRep;
                topLevelNodes.add(node);
            }
        }
        catch (Exception e) {
            Logger.error(e.getMessage());
        }
    }
}
