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
package com.emc.sa.workflow;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAO;
import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.catalog.primitives.CustomServicesResourceDAO;
import com.emc.sa.catalog.primitives.CustomServicesResourceDAOs;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.Builder;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.ResourcePackage;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.ResourcePackage.ResourceBuilder;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.WorkflowMetadata;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.model.customservices.CustomServicesWorkflowRestRep;
import com.emc.storageos.primitives.CustomServicesPrimitive.StepType;
import com.emc.storageos.primitives.CustomServicesPrimitiveResourceType;
import com.emc.storageos.primitives.CustomServicesPrimitiveType;
import com.emc.storageos.primitives.java.vipr.CustomServicesViPRPrimitive;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

/**
 * Helper class to perform CRUD operations on a workflow
 */
public final class WorkflowHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WORKFLOWS_FOLDER = "workflows";
    private static final String OPERATIONS_FOLDER = "operations";
    private static final String RESOURCES_FOLDER = "resources";
    private static final String ROOT = "";
    private static final String METADATA_FILE = "worflow.md";
    private static final String VERSION = "1";
    
    private WorkflowHelper() {}
    
    /**
     * Create a workflow definition
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonGenerationException 
     */
    public static CustomServicesWorkflow create(final CustomServicesWorkflowDocument document) throws JsonGenerationException, JsonMappingException, IOException {
        final CustomServicesWorkflow workflow = new CustomServicesWorkflow();
        
        workflow.setId(URIUtil.createId(CustomServicesWorkflow.class));
        workflow.setLabel(document.getName());
        workflow.setName(document.getName());
        workflow.setDescription(document.getDescription());
        workflow.setSteps(toStepsJson(document.getSteps()));
        workflow.setPrimitives(getPrimitives(document));
        return workflow;
    }
    
    /**
     * Update a workflow definition
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonGenerationException 
     */
    public static CustomServicesWorkflow update(final CustomServicesWorkflow oeWorkflow, final CustomServicesWorkflowDocument document) throws JsonGenerationException, JsonMappingException, IOException {

        if(document.getDescription() != null) {
            oeWorkflow.setDescription(document.getDescription());
        }

        if(document.getName() != null) {
            oeWorkflow.setName(document.getName());
            oeWorkflow.setLabel(document.getName());
        }
        
        if( null != document.getSteps()  ) {
            oeWorkflow.setSteps(toStepsJson(document.getSteps()));
            oeWorkflow.setPrimitives(getPrimitives(document));
        }
        
        return oeWorkflow;
    }

    public static CustomServicesWorkflow updateState(final CustomServicesWorkflow oeWorkflow, final String state) {
        oeWorkflow.setState(state);
        return oeWorkflow;
    }
    
    public static CustomServicesWorkflowDocument toWorkflowDocument(final CustomServicesWorkflow workflow) throws JsonParseException, JsonMappingException, IOException {
        final CustomServicesWorkflowDocument document = new CustomServicesWorkflowDocument();
        document.setName(workflow.getName());
        document.setDescription(workflow.getDescription());
        document.setSteps(toDocumentSteps(workflow.getSteps()));
        
        return document;
    }
    
    public static CustomServicesWorkflowDocument toWorkflowDocument(final String workflow) throws JsonParseException, JsonMappingException, IOException {
        return MAPPER.readValue(workflow, CustomServicesWorkflowDocument.class);
    }
    
    public static String toWorkflowDocumentJson( CustomServicesWorkflow workflow) throws JsonGenerationException, JsonMappingException, JsonParseException, IOException {
        return MAPPER.writeValueAsString(toWorkflowDocument(workflow));
    }
    
    private static List<CustomServicesWorkflowDocument.Step> toDocumentSteps(final String steps) throws JsonParseException, JsonMappingException, IOException {
        return steps == null ? null :  MAPPER.readValue(steps, MAPPER.getTypeFactory().constructCollectionType(List.class, CustomServicesWorkflowDocument.Step.class));
    }
    
    private static String toStepsJson(final List<CustomServicesWorkflowDocument.Step> steps) throws JsonGenerationException, JsonMappingException, IOException {
        return MAPPER.writeValueAsString(steps);
    }
    
    private static StringSet getPrimitives(
            final CustomServicesWorkflowDocument document) {
        final StringSet primitives = new StringSet();
        for(final Step step : document.getSteps()) {
            final StepType stepType = (null == step.getType()) ? null : StepType.fromString(step.getType());
            if(null != stepType ) {
                switch(stepType) {
                case VIPR_REST:
                    break;
                default:
                    primitives.add(step.getOperation().toString());
                }
            }
        }
        return primitives;
    }

    /**
     * @param archive
     * @return
     */
    public static CustomServicesWorkflow importWorkflow(final byte[] archive, 
            final ModelClient client) {

        try (final TarArchiveInputStream tarIn = new TarArchiveInputStream(
                new ByteArrayInputStream(
                        archive))) {
            CustomServicesWorkflowPackage.Builder builder = new CustomServicesWorkflowPackage.Builder();
            TarArchiveEntry entry = tarIn.getNextTarEntry();
            final Map<URI, ResourceBuilder> resourceMap = new HashMap<URI, ResourceBuilder>();
            while (entry != null) {
                if( !entry.isDirectory()) {
                    final Path path = FileSystems.getDefault().getPath(entry.getName()).normalize();
                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    IOUtils.copy(tarIn, out);
                    byte[] bytes = out.toByteArray();
                    final String parent = path.getParent() == null ? ROOT : path.getParent().getFileName().toString(); 
                    switch(parent) {
                        case ROOT:
                            if( path.getFileName().equals(METADATA_FILE)) {
                                builder.metadata(MAPPER.readValue(bytes, WorkflowMetadata.class));
                            }
                            break;
                        case WORKFLOWS_FOLDER:
                            builder.addWorkflow(MAPPER.readValue(bytes, CustomServicesWorkflow.class));
                            break;
                        case OPERATIONS_FOLDER:
                            final String operationId = path.getFileName().toString();
                            builder.addOperation(MAPPER.readValue(bytes, ModelClient.getModelClass(URI.create(operationId))));
                            break;
                        case RESOURCES_FOLDER:
                            final boolean isMetadata;
                            final URI id;
                            final String filename = path.getFileName().toString();
                            if(filename.endsWith(".md")) {
                                id = URI.create(filename.substring(0, filename.indexOf('.')));
                                isMetadata = true;
                            } else {
                                id = URI.create(filename);
                                isMetadata = false;
                            }
                            final ResourceBuilder resourceBuilder;
                            if(!resourceMap.containsKey(id)) {
                                resourceBuilder = new ResourceBuilder();
                                resourceMap.put(id, resourceBuilder);
                            } else {
                                resourceBuilder = resourceMap.get(filename);
                            }
                            
                            if( isMetadata ) {
                                resourceBuilder.metadata(MAPPER.readValue(bytes, ModelClient.getModelClass(id)));
                            } else {
                                resourceBuilder.bytes(bytes);
                            }
                        default:
                            throw APIException.badRequests.invalidParameter("archive", "");
                    } 
                    
                }
                entry = tarIn.getNextTarEntry();
            }
            
            for( final ResourceBuilder resourceBuilder : resourceMap.values()) {
                builder.addResource(resourceBuilder.build());
            }
            

            return importWorkfow(builder.build(), client);
            
        } catch (final IOException e) {
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("Invalid workflow import package", e);
        }
    }


    private static CustomServicesWorkflow importWorkfow(final CustomServicesWorkflowPackage workflowPackage,
            final ModelClient client) {
        for( final Entry<URI, ResourcePackage> resources : workflowPackage.resources().entrySet()) {
            if( null == client.findById(resources.getKey() )) {
                final CustomServicesDBResource resource = resources.getValue().metadata();
                resource.setResource(resources.getValue().bytes());
                client.save(resource);
            }
        }
        
        for( final Entry<URI, CustomServicesDBPrimitive> operation : workflowPackage.operations().entrySet()) {
            if( null == client.findById(operation.getKey())) {
                client.save(operation.getValue());
            }
        }
        
        for(final  Entry<URI, CustomServicesWorkflow> workflow : workflowPackage.workflows().entrySet()) {
            if( null == client.findById(workflow.getKey())) {
                client.save(workflow.getValue());
            }
        }
        return client.customServicesWorkflows().findById(workflowPackage.metadata().getId());
    }

    public static byte[] exportWorkflow(final URI id, 
            final ModelClient client, 
            final CustomServicesPrimitiveDAOs daos, 
            final CustomServicesResourceDAOs resourceDAOs) {
        final CustomServicesWorkflowPackage workflowPackage = makeCustomServicesWorkflowPackage(id, client, daos, resourceDAOs);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final BufferedOutputStream bout = new BufferedOutputStream(out);

        try(final TarArchiveOutputStream tarOut = new TarArchiveOutputStream(bout)) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            addArchiveEntry(tarOut, METADATA_FILE, new Date(System.currentTimeMillis()), MAPPER.writeValueAsBytes(new WorkflowMetadata(id, VERSION)));
            for(  final Entry<URI, CustomServicesWorkflow> workflow : workflowPackage.workflows().entrySet()) {
                final String name = WORKFLOWS_FOLDER+"/"+workflow.getKey().toString();
                final byte[] content = MAPPER.writeValueAsBytes(workflow.getValue());
                final Date modTime = workflow.getValue().getCreationTime().getTime();
                addArchiveEntry(tarOut, name, modTime, content);
            }
            
            for( final Entry<URI, CustomServicesDBPrimitive> operation : workflowPackage.operations().entrySet()) {
                final String name = OPERATIONS_FOLDER+"/"+operation.getKey().toString();
                final byte[] content = MAPPER.writeValueAsBytes(operation.getValue());
                final Date modTime = operation.getValue().getCreationTime().getTime();
                addArchiveEntry(tarOut, name, modTime, content);
            }

            for( final Entry<URI, ResourcePackage> resource : workflowPackage.resources().entrySet()) {
                final String name = RESOURCES_FOLDER+"/"+resource.getKey().toString()+".md";
                final String resourceFile = RESOURCES_FOLDER+"/"+resource.getKey().toString();
                final byte[] metadata = MAPPER.writeValueAsBytes(resource.getValue().metadata());
                final Date modTime = resource.getValue().metadata().getCreationTime().getTime();
                addArchiveEntry(tarOut, name, modTime, metadata);
                addArchiveEntry(tarOut, resourceFile, modTime, resource.getValue().bytes());
            }
            tarOut.finish();

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * @param byteArray
     * @return
     * @throws IOException 
     */
    private static byte[] gzip(byte[] byteArray) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final BufferedOutputStream bout = new BufferedOutputStream(out);
        final GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(bout);
        
        gzip.write(byteArray);
        gzip.flush();
        return out.toByteArray();
    }

    /**
     * @param id
     * @param client
     * @param resourceDAOs 
     * @return
     */
    private static CustomServicesWorkflowPackage makeCustomServicesWorkflowPackage(final URI id, final ModelClient client, final CustomServicesPrimitiveDAOs daos, final CustomServicesResourceDAOs resourceDAOs) {
        final CustomServicesWorkflowPackage.Builder builder = new CustomServicesWorkflowPackage.Builder();
        builder.metadata(new WorkflowMetadata(id, VERSION));
        addWorkflow(builder, id, client, daos, resourceDAOs);
        return builder.build();
        
    }

    private static void addWorkflow(final Builder builder, final URI id, final ModelClient client, final CustomServicesPrimitiveDAOs daos, final CustomServicesResourceDAOs resourceDAOs) {
        final CustomServicesWorkflow dbWorkflow = client.customServicesWorkflows().findById(id);
        if( null == dbWorkflow ) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        final CustomServicesWorkflowRestRep workflow = CustomServicesWorkflowMapper.map(dbWorkflow);
        builder.addWorkflow(dbWorkflow);
        for( final Step step : workflow.getDocument().getSteps()) {
            final String stepId = step.getId();
            if( !StepType.END.toString().equalsIgnoreCase(stepId) &&
                    !StepType.START.toString().equalsIgnoreCase(stepId) ) {
                final URI operation = step.getOperation();
                final String type = URIUtil.getTypeName(operation);
                if(type.equals(CustomServicesWorkflow.class.getSimpleName())) {
                    addWorkflow(builder, operation, client, daos, resourceDAOs);
                } else if( !type.equals(CustomServicesViPRPrimitive.class.getSimpleName())) {
                    addOperation(builder, operation, daos, resourceDAOs);
                }
            }
        }
    }

    private static void addOperation(final Builder builder, final URI id, final CustomServicesPrimitiveDAOs daos, final CustomServicesResourceDAOs resourceDAOs) {
        final CustomServicesPrimitiveDAO<?> dao = daos.getByModel(URIUtil.getTypeName(id));
        if( null == dao ) {
            throw new RuntimeException("Operation type for " + id + " not found");
        }
        
        final CustomServicesPrimitiveType primitive = dao.get(id);
        
        if( null == primitive ) {
            throw new RuntimeException("Operation with ID " + id + " not found");
        }
        
        if( null != primitive.resource() ) {
            addResource(builder, primitive.resource(), resourceDAOs);
        }
        
        builder.addOperation((CustomServicesDBPrimitive) primitive.asModelObject());
    }

    private static void addResource(Builder builder, NamedURI id, CustomServicesResourceDAOs resourceDAOs) {
        
        final CustomServicesResourceDAO<?> dao = resourceDAOs.getByModel(URIUtil.getTypeName(id.getURI()));
        
        if( null == dao ) {
            throw new RuntimeException("Resource type for " + id + " not found");
        }
        
        final CustomServicesPrimitiveResourceType resource = dao.getResource(id.getURI());
         
        if( null == resource ) {
            throw new RuntimeException("Resource " + id + " not found");
        }
        CustomServicesDBResource dbResource = (CustomServicesDBResource) resource.asModelObject();
        builder.addResource(new ResourcePackage(dbResource, resource.resource()));
        
        for( final NamedElement related : dao.listRelatedResources(id.getURI())) {
            addResource(builder, new NamedURI(related.getId(), related.getName()), resourceDAOs);
        }
    }

    private static void addArchiveEntry(final TarArchiveOutputStream tarOut, final String name, final Date modTime, final byte[] bytes) throws IOException {
        tarOut.putArchiveEntry(makeArchiveEntry(name, modTime, bytes));
        IOUtils.copy(new ByteArrayInputStream(bytes), tarOut);
        tarOut.closeArchiveEntry();
    }

    private static TarArchiveEntry makeArchiveEntry(final String name, final Date modTime, final byte[] bytes) throws IOException {
        final TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        entry.setMode(0444);
        entry.setModTime(modTime);
        entry.setUserName("storageos");
        entry.setGroupName("storageos");
        return entry;
    }
    
    
}
