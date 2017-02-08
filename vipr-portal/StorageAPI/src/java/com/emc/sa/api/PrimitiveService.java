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
package com.emc.sa.api;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.mapper.PrimitiveMapper;
import com.emc.sa.catalog.PrimitiveManager;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.uimodels.Ansible;
import com.emc.storageos.db.client.model.uimodels.AnsiblePackage;
import com.emc.storageos.db.client.model.uimodels.CustomServiceScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServiceScriptResource;
import com.emc.storageos.db.client.model.uimodels.PrimitiveResource;
import com.emc.storageos.db.client.model.uimodels.UserPrimitive;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.customservices.InputParameterRestRep;
import com.emc.storageos.model.customservices.OutputParameterRestRep;
import com.emc.storageos.model.customservices.PrimitiveBulkRestRep;
import com.emc.storageos.model.customservices.PrimitiveCreateParam;
import com.emc.storageos.model.customservices.PrimitiveList;
import com.emc.storageos.model.customservices.PrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.PrimitiveRestRep;
import com.emc.storageos.model.customservices.PrimitiveUpdateParam;
import com.emc.storageos.primitives.Primitive;
import com.emc.storageos.primitives.PrimitiveHelper;
import com.emc.storageos.primitives.ViPRPrimitive;
import com.emc.storageos.primitives.input.BasicInputParameter;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.BasicOutputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.emc.storageos.primitives.output.TableOutputParameter;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.svcs.errorhandling.resources.NotFoundException;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Multimaps;

@Path("/primitives")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = {
        ACL.OWN, ACL.ALL }, writeRoles = { Role.TENANT_ADMIN }, writeAcls = {
        ACL.OWN, ACL.ALL })
public class PrimitiveService extends CatalogTaggedResourceService {
    @Autowired
    private PrimitiveManager primitiveManager;
    
    private final ImmutableMap<URI, PrimitiveRestRep> PRIMITIVE_MAP;
    private static final Logger _log = LoggerFactory
            .getLogger(PrimitiveManager.class);

    private enum PrimitiveType {
        VIPR(ViPRPrimitive.class.getSimpleName(), 0x02), 
        ANSIBLE(Ansible.class.getSimpleName(), 0x04, AnsiblePackage.class.getSimpleName()), 
        SCRIPT(CustomServiceScriptPrimitive.class.getSimpleName(), 0x08, CustomServiceScriptResource.class.getSimpleName());

        public final static int ALL_MASK = 0xFF;
        
        private final static EnumSet<PrimitiveType> DYNAMIC_TYPES = EnumSet.of(ANSIBLE, SCRIPT);
        private final static String NO_RESOURCE = "NONE";
        
        private final String type;
        private final String resourceType;
        private final int mask;

        private PrimitiveType(final String type, final int mask) {
            this(type, mask, NO_RESOURCE);
        }
        
        private PrimitiveType(final String type, final int mask, final String resourceType) {
            this.type = type;
            this.mask = mask;
            this.resourceType = resourceType;
        }

        public static PrimitiveType get(final String primitiveType) {
            try {
                return PrimitiveType.valueOf(primitiveType.toUpperCase());
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }
        
        public static PrimitiveType fromTypeName(final String typeName) {
            for(PrimitiveType primitiveType : values()) {
                if(primitiveType.type().equals(typeName)) {
                    return primitiveType;
                }
            }
            return null;
        }
        
        public static PrimitiveType dynamicType(final String type) {
            final PrimitiveType primitiveType = get(type);
            if(null != primitiveType && DYNAMIC_TYPES.contains(primitiveType)) {
                return primitiveType;
            } else {
                return null;
            }
        }
        
        public static EnumSet<PrimitiveType> dynamicTypes() {
            return DYNAMIC_TYPES;
        }
        
        public String toString() {
            return type;
        }

        public String type() {
            return type;
        }
        
        public String resourceType() {
            return resourceType;
        }
        
        public int mask() {
            return mask;
        }
    }

    public PrimitiveService() {
        Builder<URI, PrimitiveRestRep> builder = ImmutableMap
                .<URI, PrimitiveRestRep> builder();
        for (final Primitive primitive : PrimitiveHelper.list()) {
            PrimitiveRestRep primitiveRestRep = new PrimitiveRestRep();
            primitiveRestRep.setId(primitive.getId());
            primitiveRestRep.setName(primitive.getName());
            primitiveRestRep.setType(primitive.getType().toString());
            primitiveRestRep.setFriendlyName(primitive.getFriendlyName());
            primitiveRestRep.setDescription(primitive.getDescription());
            primitiveRestRep.setSuccessCriteria(primitive.getSuccessCriteria());
            primitiveRestRep.setInputGroups(mapInput(primitive.getInput()));
            primitiveRestRep.setOutput(mapOutput(primitive.getOutput()));
            builder.put(primitiveRestRep.getId(), primitiveRestRep);
        }
        PRIMITIVE_MAP = builder.build();
    }

    /**
     * Get the list of primitives that can be used for creating orchestration
     * workflows
     *
     * @prereq none
     *
     *
     * @brief Get list of primitives
     *
     * @return PrimitiveList
     *
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public PrimitiveList getPrimitives(@QueryParam("type") final String type) {
        final int mask;
        if( null == type || type.equalsIgnoreCase("all")) {
            mask = PrimitiveType.ALL_MASK;
        } else {
            ArgValidator.checkFieldValueFromEnum(type.toUpperCase(), "type", PrimitiveType.class);
            mask = PrimitiveType.get(type).mask();
        }
        final List<URI> list = new ArrayList<URI>();
        
        if( (PrimitiveType.VIPR.mask() & mask) != 0) {
            list.addAll(PRIMITIVE_MAP.keySet());
        } 
        
        if( (PrimitiveType.ANSIBLE.mask() & mask) != 0 ) {
            final List<URI> ansiblePrimitives = primitiveManager.findAllAnsibleIds();
            if(null != ansiblePrimitives ) {
                for( final URI id : ansiblePrimitives ) {
                    list.add(id);
                }
            }
        }
        
        if( (PrimitiveType.SCRIPT.mask() & mask ) != 0) {
            final List<URI> scriptPrimitives = primitiveManager.findAllScriptPrimitiveIds();
            if(null != scriptPrimitives ) {
                for( final URI id : scriptPrimitives ) {
                    list.add(id);
                }
            }
        }
        
        final PrimitiveList primitiveList = new PrimitiveList();
        primitiveList.setPrimitives(list);
        return primitiveList;
    }

    /**
     * Create a new primitive
     * 
     * @prereq none
     * 
     * @brief Create a new user defined primitive
     * 
     * @param param a primitive creation parameter
     * 
     * @return PrimitiveRestRep 
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public PrimitiveRestRep makePrimitive(PrimitiveCreateParam param) {
        
        if(null != param.getType()) {
            ArgValidator.checkFieldValueFromEnum(param.getType().toUpperCase(), "type", PrimitiveType.dynamicTypes());
        } else {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("type");
        }
        
        final UserPrimitive primitive;
        final PrimitiveType type = PrimitiveType
                .get(param.getType());
        
        switch(type) {
        case ANSIBLE:
            primitive = makeAnsiblePrimitive(param);
            break;
        case SCRIPT:
            primitive = makeScriptPrimitive(param);
            break;
        default:
            throw BadRequestException.methodNotAllowed.notSupportedWithReason("Primitive creation not supported for: "+type);
        }
        
        primitiveManager.save(primitive);
        return PrimitiveMapper.map(primitive);
    }

    /**
     * Get a primitive that can be used in orchestration workflows
     *
     * @prereq none
     *
     *
     * @brief Get a primitive by name
     *
     * @param id
     * @return PrimitiveRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public PrimitiveRestRep getPrimitive(@PathParam("id") final URI id) {
        final PrimitiveType type = PrimitiveType.fromTypeName(URIUtil
                .getTypeName(id));
        if (null == type) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        switch (type) {
        case VIPR:
            final PrimitiveRestRep primitive = PRIMITIVE_MAP.get(id);
            if (null == primitive) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
            return primitive;
        case ANSIBLE:
            final Ansible ansible = primitiveManager.findById(id).asAnsible();
            if (null == ansible) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
            return PrimitiveMapper.map(ansible);
        case SCRIPT:
            final CustomServiceScriptPrimitive script = primitiveManager.findById(id).asCustomeServiceScript();
            if (null == script) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
            return PrimitiveMapper.map(script);
        default:
            throw APIException.notFound.unableToFindEntityInURL(id);

        }
    }

    /**
     * Upload a resource
     * @param request HttpServletRequest containing the file octet stream
     * @param type The type of the primitive file resource
     * @param name The user defined name of the resource
     * @return A rest response containing details of the resource that was created
     */
    @POST
    @Consumes({ MediaType.APPLICATION_OCTET_STREAM })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/resource/{type}")
    public PrimitiveResourceRestRep upload(
            @Context HttpServletRequest request,
            @PathParam("type") String type, @QueryParam("name") String name) {

        ArgValidator.checkFieldNotNull(name, "name");
        
        final PrimitiveType primitiveType = PrimitiveType
                .dynamicType(type);
        if (null == primitiveType) {
            throw NotFoundException.notFound.unableToFindEntityInURL(URI
                    .create(type));
        }

        final byte[] stream = read(request);
        final PrimitiveResource resource;
        switch (primitiveType) {
        case ANSIBLE:
            resource = makeAnsiblePackage(name, stream);
            break;
        case SCRIPT:
            resource = makeScript(name, stream);
            break;
        default:
            throw NotFoundException.notFound.unableToFindEntityInURL(URI
                    .create(type));
        }
        primitiveManager.save(resource);
        return PrimitiveMapper.map(resource);
    }

    /**
     * Update a primitive
     * @param id the ID of the primitivie to be updated
     * @param param Primitive update request entity
     * @return
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public PrimitiveRestRep updatePrimitive(@PathParam("id") final URI id,
            final PrimitiveUpdateParam param) {
        final UserPrimitive primitive = primitiveManager.findById(id);
        ArgValidator.checkEntity(primitive, id, true);
        
        final PrimitiveType type = PrimitiveType.fromTypeName(primitive.getClass().getSimpleName());
        switch (type) {
        case ANSIBLE:
            return updateAnsible(primitive.asAnsible(), param);
        case SCRIPT:
            return updateScriptPrimitive(primitive.asCustomeServiceScript(), param);
        default:
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
    }

    /**
     * Download a primitive file resource
     * @param type The type of the primitive resource
     * @param id The ID of the resource to download
     * @param response HttpServletResponse the servlet response to update with the file octet stream
     * @return Response containing the octet stream of the primitive file resource
     */
    @GET
    @Path("resource/{type}/{id}")
    public Response download(@PathParam("type") final String type,
            @PathParam("id") final URI id,
            @Context final HttpServletResponse response) {

        final PrimitiveType primitiveType = PrimitiveType.get(type);
        if (null == primitiveType || !URIUtil.getTypeName(id).equals(primitiveType.resourceType())) {
            throw NotFoundException.notFound.unableToFindEntityInURL(URI
                    .create(type));
        }

        final PrimitiveResource resource = primitiveManager.findResource(id);
        ArgValidator.checkEntity(resource, id, true);

   
        
        final byte[] bytes = Base64.decodeBase64(resource
                .getResource());
        response.setContentLength(bytes.length);
        
        
        response.setHeader("Content-Disposition", "attachment; filename="
                + resource.getLabel() + resource.suffix());
        return Response.ok(bytes).build();
    }
    
    @POST
    @Path("/bulk")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public PrimitiveBulkRestRep bulkGetPrimitives(final BulkIdParam ids) {
        return (PrimitiveBulkRestRep) super.getBulkResources(ids);
    }

    private Ansible makeAnsiblePrimitive(PrimitiveCreateParam param) {
        final Ansible primitive = new Ansible();
        primitive.setId(URIUtil.createId(Ansible.class));
        primitive.setLabel(param.getName());
        primitive.setFriendlyName(param.getFriendlyName());
        primitive.setDescription(param.getDescription());
        primitive.setArchive(param.getResource());
        primitive.setPlaybook(param.getAttributes().get("playbook"));
        final StringSet extraVars = new StringSet();
        if( null != param.getInput() ) {
            for(String input : param.getInput()) {
                extraVars.add(input);
            }
        }
        primitive.setExtraVars(extraVars);
        
        final StringSet output = new StringSet();
        if(param.getOutput() != null ) {
            output.addAll(param.getOutput());
        }
        primitive.setOutput(output);
        
        return primitive;
    }
    
    private PrimitiveRestRep updateAnsible(final Ansible update,
            final PrimitiveUpdateParam param) {

        if (null != param.getInput()) {
            final StringSet extraVars = update.getExtraVars() == null ? new StringSet()
                    : update.getExtraVars();
            if (null != param.getInput().getAdd()) {
                extraVars.addAll(param.getInput().getAdd());
            }
            if (null != param.getInput().getRemove()) {
                extraVars.removeAll(param.getInput().getRemove());
            }
            update.setExtraVars(extraVars);
        }

        if( null != param.getAttributes()) {
            for(Entry<String, String> attribute : param.getAttributes().entrySet()) {
                switch(attribute.getKey()) {
                case "playbook":
                    final AnsiblePackage archive = primitiveManager.findResource(update.getArchive()).asAnsiblePackage();
                    if(!archive.getPlaybooks().contains(attribute.getValue())) {
                        throw BadRequestException.badRequests.parameterIsNotValid(attribute.getKey());
                    } else {
                        update.setPlaybook(attribute.getValue());
                    }
                    break;
                    default:
                        throw BadRequestException.badRequests.parameterIsNotValid(attribute.getKey());
                }
            }
        }
        return updatePrimitive(update, param);
    }


    private static Map<String, PrimitiveRestRep.InputGroup> mapInput(Map<Primitive.InputType, List<InputParameter>> inputParameters) {
        Map<String, PrimitiveRestRep.InputGroup> inputRestRep = new HashMap<String, PrimitiveRestRep.InputGroup>();
        for(final Entry<Primitive.InputType, List<InputParameter>> parameterType : inputParameters.entrySet()) {
            List<InputParameterRestRep> inputTypeRestRep = new ArrayList<InputParameterRestRep>();
            for(final InputParameter parameter : parameterType.getValue()) {
                if (parameter.isBasicInputParameter()) {
                    InputParameterRestRep inputParamRestRep = new InputParameterRestRep();
                    inputParamRestRep.setName(parameter.getName());
                    inputParamRestRep.setType(parameter.getType().name());
                    BasicInputParameter<?> inputParam = parameter
                            .asBasicInputParameter();
                    inputParamRestRep.setRequired(inputParam.getRequired());
                    if (null != inputParam.getDefaultValue()) {
                        inputParamRestRep.setDefaultValue(Collections
                                .singletonList(inputParam.getDefaultValue()
                                        .toString()));
                    }
                    inputTypeRestRep.add(inputParamRestRep);
                }
            }
            PrimitiveRestRep.InputGroup inputGroup = new PrimitiveRestRep.InputGroup(){{
                setInputGroup(inputTypeRestRep);
            }};
            inputRestRep.put(parameterType.getKey().toString(),inputGroup);
        }
        return inputRestRep;
    }

    private static List<OutputParameterRestRep> mapOutput(List<OutputParameter> output) {
        List<OutputParameterRestRep> outputRestRep = new ArrayList<OutputParameterRestRep>();
        int index = 0;
        for(final OutputParameter parameter : output) {
            if(parameter.isBasicOutputParameter()) {
                BasicOutputParameter outputParam = parameter.asBasicOutputParameter();
                OutputParameterRestRep parameterRestRep = new OutputParameterRestRep();
                parameterRestRep.setName(outputParam.getName());
                parameterRestRep.setType(outputParam.getType().name());
                outputRestRep.add(index++, parameterRestRep);
            } else {
                TableOutputParameter outputParam = parameter
                        .asTableOutputParameter();
                for (final BasicOutputParameter column : outputParam
                        .getColumns()) {
                    OutputParameterRestRep parameterRestRep = new OutputParameterRestRep();
                    parameterRestRep.setName(column.getName());
                    parameterRestRep.setType(column.getType().name());
                    parameterRestRep.setTable(outputParam.getName());
                    outputRestRep.add(index++, parameterRestRep);
                }
            }
        }
        return outputRestRep;
    }

    private byte[] read(final HttpServletRequest request) {
        try {
            final ByteArrayOutputStream file = new ByteArrayOutputStream();
            final byte buffer[] = new byte[2048];
            final InputStream in = request.getInputStream();
            int nRead = 0;
            while ((nRead = in.read(buffer)) > 0) {
                file.write(buffer, 0, nRead);
            }
            return file.toByteArray();
        } catch (IOException e) {
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("failed to read octet stream", e);
        }
    }
    
    private AnsiblePackage makeAnsiblePackage(final String name, final byte[] archive) {
        final StringSet playbooks = getPlaybooks(archive);

        final AnsiblePackage ansiblePackage = new AnsiblePackage();
        ansiblePackage.setLabel(name);
        ansiblePackage.setId(URIUtil.createId(AnsiblePackage.class));

        ansiblePackage.setPlaybooks(playbooks);
        ansiblePackage.setResource(Base64.encodeBase64(archive));
        return ansiblePackage;
    }

    private StringSet getPlaybooks(final byte[] archive) {
        try(final TarArchiveInputStream tarIn = new TarArchiveInputStream(
                new GzipCompressorInputStream(new ByteArrayInputStream(
                        archive)))) {
            TarArchiveEntry entry = tarIn.getNextTarEntry();
            final StringSet playbooks = new StringSet();
    
            while (entry != null) {
                if (entry.isFile()
                        && entry.getName().toLowerCase().endsWith(".yml")) {
                    
                    final java.nio.file.Path path = FileSystems
                            .getDefault().getPath(entry.getName());
                    final java.nio.file.Path root = path.getRoot() != null ? path
                            .getRoot() : FileSystems.getDefault().getPath(
                            ".");
                    if (path.getParent() == null
                            || 0 == path.getParent().compareTo(root)) {
                        final String playbook = path.getFileName().toString();
                        _log.info("Top level playbook: " + playbook);
                        playbooks.add(playbook);
                    } 
                }
                entry = tarIn.getNextTarEntry();
            }
            return playbooks;
        } catch (IOException e) {
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("Invalid ansible archive", e);
        }
       
    }
    
    private CustomServiceScriptResource makeScript(final String name, final byte[] file) {
        CustomServiceScriptResource script = new CustomServiceScriptResource();
        script.setId(URIUtil.createId(CustomServiceScriptResource.class));
        script.setLabel(name);
        script.setResource(Base64.encodeBase64(file));
        return script;
    }
    
    private CustomServiceScriptPrimitive makeScriptPrimitive(PrimitiveCreateParam param) {
        final CustomServiceScriptPrimitive primitive = new CustomServiceScriptPrimitive();
        primitive.setId(URIUtil.createId(CustomServiceScriptPrimitive.class));
        primitive.setLabel(param.getName());
        primitive.setFriendlyName(param.getFriendlyName());
        primitive.setDescription(param.getDescription());
        primitive.setScript(param.getResource());
       
        final StringSet inputs = new StringSet();
        if( null != param.getInput() ) {
            inputs.addAll(param.getInput());
        }
        primitive.setInput(inputs);
        final StringSet output = new StringSet();
        if(param.getOutput() != null ) {
            output.addAll(param.getOutput());
        }
        primitive.setOutput(output);
        return primitive;
    }
    
    private PrimitiveRestRep updateScriptPrimitive(final CustomServiceScriptPrimitive update, final PrimitiveUpdateParam param) {
        return updatePrimitive(update, param);
    }
    
    private PrimitiveRestRep updatePrimitive(final UserPrimitive update, final PrimitiveUpdateParam param) {
        if (null != param.getName()) {
            update.setLabel(param.getName());
        }
        if (null != param.getFriendlyName()) {
            update.setFriendlyName(param.getFriendlyName());
        }
        if (null != param.getDescription()) {
            update.setDescription(param.getDescription());
        }
        
        if (null != param.getOutput()) {
            final StringSet output = update.getOutput() == null ? new StringSet()
                    : update.getOutput();
            if (null != param.getOutput().getAdd()) {
                for (final String addOutput : param.getOutput().getAdd()) {
                    output.add(addOutput);
                }
            }
            if (null != param.getOutput().getRemove()) {
                for (final String rmOutput : param.getOutput().getRemove()) {
                    output.remove(rmOutput);
                }
            }
            update.setOutput(output);
        }
        primitiveManager.save(update);
        return PrimitiveMapper.map(update);
    }

    @Override
    protected DataObject queryResource(URI id) {
        return primitiveManager.findById(id);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.CUSTOM_SERVICE_PRIMITIVE;
        
    }
    
    @Override
    public Class<UserPrimitive> getResourceClass() {
        return UserPrimitive.class;
    }
    
    @Override
    public PrimitiveBulkRestRep queryBulkResourceReps(final List<URI> ids) {
        return new PrimitiveBulkRestRep(getPrimitiveRestReps(ids));
    }
    
    @Override
    public PrimitiveBulkRestRep queryFilteredBulkResourceReps(final List<URI> ids) { 
        //TODO do we need any filtering?
        return new PrimitiveBulkRestRep(getPrimitiveRestReps(ids));
    }
    
    private List<PrimitiveRestRep> getPrimitiveRestReps(final List<URI> ids) {
        final ImmutableListMultimap<PrimitiveService.PrimitiveType, URI> idGroups = Multimaps.index(ids, new Function<URI, PrimitiveService.PrimitiveType>() {
            @Override
            public PrimitiveType apply(final URI id) {
                PrimitiveType type = PrimitiveType.fromTypeName(URIUtil.getTypeName(id));
                if( null == type) {
                    throw BadRequestException.badRequests.invalidParameter("id", id.toString());
                }
                return type;
            }
        });
        
        final ImmutableList.Builder<PrimitiveRestRep> builder = ImmutableList.<PrimitiveRestRep>builder();
        for( final Entry<PrimitiveType, Collection<URI>> entry : idGroups.asMap().entrySet()) {
            switch(entry.getKey()) {
            case VIPR:
                builder.addAll(Collections2.transform(Collections2.filter(entry.getValue(), Predicates.in(PRIMITIVE_MAP.keySet())), Functions.forMap(PRIMITIVE_MAP)));
                break;
            case ANSIBLE:
                
                builder.addAll(BulkList.wrapping(_dbClient.queryIterativeObjects(Ansible.class, entry.getValue()), new Function<Ansible, PrimitiveRestRep>() {

                    @Override
                    public PrimitiveRestRep apply(final Ansible from) {
                        return PrimitiveMapper.map(from);
                    }
                    
                }).iterator());
                break;
            case SCRIPT:
                builder.addAll(BulkList.wrapping(_dbClient.queryIterativeObjects(CustomServiceScriptPrimitive.class, entry.getValue()), new Function<CustomServiceScriptPrimitive, PrimitiveRestRep>() {

                    @Override
                    public PrimitiveRestRep apply(final CustomServiceScriptPrimitive from) {
                        return PrimitiveMapper.map(from);
                    }
                    
                }).iterator());
                break;
            default:
                throw InternalServerErrorException.internalServerErrors.genericApisvcError("Unknown Primitive type", new RuntimeException("Primitive type "+ entry.getKey() + " not supported by bulk API"));  
            }
        }
        return builder.build();
    }
}
