/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
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
package com.emc.sa.customservices;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;

import javax.annotation.PostConstruct;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.catalog.WorkflowDirectoryManager;
import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.catalog.primitives.CustomServicesResourceDAOs;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.util.Messages;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.uimodels.WFDirectory;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Component
public class CustomServicesBuiltIn {

    @Autowired
    private ModelClient client;
    
    @Autowired
    private WorkflowDirectoryManager wfDirectoryManager;
    
    @Autowired
    private CustomServicesPrimitiveDAOs daos;
    
    @Autowired
    private CustomServicesResourceDAOs resourceDAOs;
    
    private static final Logger log = Logger.getLogger(CustomServicesBuiltIn.class);
    private static final Messages MESSAGES = new Messages(CustomServicesBuiltIn.class, "custom-services-builtin");
    private static final Gson GSON = new GsonBuilder().create();
    
    @PostConstruct
    public void start() {
        try {
            importWorkflows(readWFDirectoryDef());
        } catch (IOException e) {
            throw new RuntimeException("Failed to import default workflows", e);
        }
    }

    private void importWorkflows(final WFDirectoryDef root) {
        
        final Map<String, WFDirectory> directories = makeWorkflowDirectory(URIUtil.uri(StringUtils.EMPTY), root);
        final WFDirectory rootWfDir = wfDirectoryManager.getWFDirectoryById(URIUtil.createInternalID(WFDirectory.class, root.id()));
        final Path path = Paths.get(CustomServicesConstants.WORKFLOW_DIRECTORY);
        
        try( final DirectoryStream<Path> wfs = Files.newDirectoryStream(path, "*"+CustomServicesConstants.WORKFLOW_PACKAGE_EXT) ) {
            log.info("Importing workflows from: " + path);
            for( final Path wf : wfs ) {
                log.info("Importing: " + wf);
                try(final FileInputStream stream = new FileInputStream(wf.toString())) {
                    final WFDirectory directory;
                    if( !directories.containsKey(wf.getFileName().toString())) {
                        directory = rootWfDir;
                    } else {
                        directory = directories.get(wf.getFileName().toString());
                    }
                    WorkflowHelper.importWorkflow(stream, directory, client, daos, resourceDAOs, true);
                } catch (final IOException e) {
                    log.error("Error reading workflow package: "+ wf + " exception: ", e);
                }
            }
        } catch (final IOException e) {
            log.error("Error reading directory: "+ path + " exception: ", e);
        }
    }
    
    private Map<String, WFDirectory> makeWorkflowDirectory(final URI parent, final WFDirectoryDef directory) {
        final WFDirectory wfDirectory = findDirectory(parent, directory);
        final Map<String, WFDirectory> workflows = new HashMap<String, WFDirectory>();
        if( CollectionUtils.isNotEmpty(directory.workflows())) {
            for( final String workflow : directory.workflows() ) {
                workflows.put(workflow, wfDirectory);
            }
        } 
        
        if( CollectionUtils.isNotEmpty(directory.directories())) {
            for( final WFDirectoryDef subDirectory : directory.directories() ) {
                workflows.putAll(makeWorkflowDirectory(wfDirectory.getId(), subDirectory));
            }
        }
        
        return workflows;
    }

    private WFDirectory findDirectory(final URI parent, final WFDirectoryDef directory) {
        final WFDirectory existing = client.wfDirectory().findById(URIUtil.createInternalID(WFDirectory.class, directory.id()));
        return existing != null ? existing : createDirectory(parent, directory);
    }

    private WFDirectory createDirectory(final URI parent, final WFDirectoryDef directory) {
        final WFDirectory wfDirectory = new WFDirectory();
        wfDirectory.setId(URIUtil.createInternalID(WFDirectory.class, directory.id()));
        wfDirectory.setLabel(getMessage(directory.label()));
        wfDirectory.setParent(parent);
        wfDirectoryManager.createWFDirectory(wfDirectory);
        return wfDirectory;
    }

    public static WFDirectoryDef readWFDirectoryDef() throws IOException {
        try( final InputStream in = CustomServicesBuiltIn.class.getResourceAsStream("custom-services-builtin.json")) {
            String directories = IOUtils.toString(in);  
            WFDirectoryDef root = GSON.fromJson(directories, WFDirectoryDef.class);
            return root;
        }
    }
    
    private String getMessage(final String key) {
        try {
            return (key != null) ? MESSAGES.get(key) : null;
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
