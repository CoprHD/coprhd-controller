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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import play.Logger;
import play.mvc.Controller;
import play.mvc.With;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowList;
import com.emc.storageos.model.orchestration.PrimitiveList;
import com.emc.storageos.model.orchestration.PrimitiveRestRep;
import com.emc.storageos.primitives.Primitive;
import com.emc.storageos.primitives.PrimitiveHelper;
import com.emc.storageos.primitives.ViPRPrimitive;
import com.emc.vipr.model.catalog.WFBulkRep;
import com.emc.vipr.model.catalog.WFDirectoryParam;
import com.emc.vipr.model.catalog.WFDirectoryRestRep;
import com.google.gson.annotations.SerializedName;

import controllers.Common;

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
        OrchestrationWorkflowList orchestrationWorkflowList = getCatalogClient().oePrimitives().getWorkflows();
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
                    topLevelNodes.add(new Node(u.toString(), oeId2NameMap.get(u), node.id, NODE_TYPE_FILE));
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
            WFDirectoryParam param = new WFDirectoryParam();
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

    // Get Primitives and add them to directory list
    private static void addPrimitives(List<Node> topLevelNodes) {
        try {
            PrimitiveList primitiveList = getCatalogClient().oePrimitives().getPrimitives();
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
