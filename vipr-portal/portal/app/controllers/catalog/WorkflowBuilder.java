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

import com.emc.vipr.model.catalog.WFBulkRep;
import com.emc.vipr.model.catalog.WFDirectoryParam;
import com.emc.vipr.model.catalog.WFDirectoryRestRep;
import com.google.gson.annotations.SerializedName;
import controllers.Common;
import play.Logger;
import play.mvc.Controller;
import play.mvc.With;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static util.BourneUtil.getCatalogClient;

/**
 * @author Nick Aquino
 */
@With(Common.class)
public class WorkflowBuilder extends Controller {
    private static final String MY_LIBRARY_ROOT = "myLib";

    public static void view() {
        render();
    }

    private static class Node {
        private String id;
        private String  text;
        @SerializedName("parent")
        private String parentID;
    }

    public static void getWFDirectories() {
        WFBulkRep wfBulkRep = getCatalogClient().wfDirectories().getAll();
        List<Node> topLevelNodes = new ArrayList<Node>();
        Node node;
        for (WFDirectoryRestRep wfDirectoryRestRep: wfBulkRep.getWfDirectories()) {
            node = new Node();
            node.id= wfDirectoryRestRep.getId().toString();
            node.text = wfDirectoryRestRep.getName();

            if ( null == wfDirectoryRestRep.getParent()) {
                node.parentID = MY_LIBRARY_ROOT;
            }
            else {
                node.parentID=wfDirectoryRestRep.getParent().getId().toString();
            }
            topLevelNodes.add(node);
        }
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
}
