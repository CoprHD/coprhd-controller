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
package com.emc.sa.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletOutputStream;
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
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.uimodels.AnsiblePackage;
import com.emc.storageos.model.orchestration.InputParameterRestRep;
import com.emc.storageos.model.orchestration.OutputParameterRestRep;
import com.emc.storageos.model.orchestration.PrimitiveList;
import com.emc.storageos.model.orchestration.PrimitiveRestRep;
import com.emc.storageos.model.orchestration.PrimitiveUpdateParam;
import com.emc.storageos.primitives.Primitive;
import com.emc.storageos.primitives.PrimitiveHelper;
import com.emc.storageos.primitives.input.BasicInputParameter;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.BasicOutputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.emc.storageos.primitives.output.TableOutputParameter;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.NotFoundException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

@Path("/primitives")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = {
        ACL.OWN, ACL.ALL }, writeRoles = { Role.TENANT_ADMIN }, writeAcls = {
        ACL.OWN, ACL.ALL })
public class PrimitiveService {
    @Autowired
    private PrimitiveManager primitiveManager;

    private final PrimitiveList PRIMITIVE_LIST;
    private final ImmutableMap<URI, PrimitiveRestRep> PRIMITIVE_MAP;
    private static final Logger _log = LoggerFactory
            .getLogger(PrimitiveManager.class);

    private enum PrimitiveResourceType {
        ANSIBLE, SCRIPT;

        public static PrimitiveResourceType get(final String name) {
            try {
                return valueOf(name.toUpperCase());
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }
    }

    private enum PrimitiveTypeNames {
        VIPR("ViPRPrimitive"), ANSIBLE_PACKAGE("AnsiblePackage");

        private final String type;

        private PrimitiveTypeNames(final String type) {
            this.type = type;
        }

        public static PrimitiveTypeNames get(final String type) {
            for (PrimitiveTypeNames typeName : PrimitiveTypeNames.values()) {
                if (typeName.type().equals(type)) {
                    return typeName;
                }
            }
            return null;
        }

        public String toString() {
            return type;
        }

        public String type() {
            return type;
        }
    }

    public PrimitiveService() {
        Builder<URI, PrimitiveRestRep> builder = ImmutableMap
                .<URI, PrimitiveRestRep> builder();
        for (final Primitive primitive : PrimitiveHelper.list()) {
            PrimitiveRestRep primitiveRestRep = new PrimitiveRestRep();
            primitiveRestRep.setId(primitive.getId());
            primitiveRestRep.setName(primitive.getName());
            primitiveRestRep.setFriendlyName(primitive.getFriendlyName());
            primitiveRestRep.setDescription(primitive.getDescription());
            primitiveRestRep.setSuccessCriteria(primitive.getSuccessCriteria());
            primitiveRestRep.setInput(mapInput(primitive.getInput()));
            primitiveRestRep.setOutput(mapOutput(primitive.getOutput()));
            builder.put(primitiveRestRep.getId(), primitiveRestRep);
        }
        PRIMITIVE_MAP = builder.build();
        PRIMITIVE_LIST = new PrimitiveList(ImmutableList
                .<PrimitiveRestRep> builder().addAll((PRIMITIVE_MAP.values()))
                .build());
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
    public PrimitiveList getPrimitives() {
        return PRIMITIVE_LIST;
    }

    /**
     * Get a primitive that can be used in orchestration workflows
     *
     * @prereq none
     *
     *
     * @brief Get a primitive by name
     *
     * @param name
     * @return PrimitiveRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public PrimitiveRestRep getPrimitive(@PathParam("id") final URI id) {
        final PrimitiveTypeNames type = PrimitiveTypeNames.get(URIUtil
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
        case ANSIBLE_PACKAGE:
            final AnsiblePackage ansiblePackage = primitiveManager.findById(id);
            if (null == ansiblePackage) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
            return PrimitiveMapper.map(ansiblePackage);
        default:
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
    }

    @POST
    @Consumes({ MediaType.APPLICATION_OCTET_STREAM })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/resource/{type}")
    public PrimitiveRestRep uploadAnsiblePackage(
            @Context HttpServletRequest request,
            @PathParam("type") String type, @QueryParam("name") String name) {

        final PrimitiveResourceType resourceType = PrimitiveResourceType
                .get(type);
        if (null == resourceType) {
            throw NotFoundException.notFound.unableToFindEntityInURL(URI
                    .create(type));
        }

        try {
            final InputStream in = request.getInputStream();
            final ByteArrayOutputStream file = new ByteArrayOutputStream();
            final byte buffer[] = new byte[2048];
            int nRead = 0;
            while ((nRead = in.read(buffer)) > 0) {
                file.write(buffer, 0, nRead);
            }

            switch (resourceType) {
            case ANSIBLE:
                final TarArchiveInputStream tarIn = new TarArchiveInputStream(
                        new GzipCompressorInputStream(new ByteArrayInputStream(
                                file.toByteArray())));
                TarArchiveEntry entry = tarIn.getNextTarEntry();
                final StringSet entryPoints = new StringSet();

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
                            entryPoints.add(playbook);
                        } 
                    }
                    entry = tarIn.getNextTarEntry();
                }

                final AnsiblePackage ansiblePackage = new AnsiblePackage();
                ansiblePackage.setLabel(name);
                ansiblePackage.setId(URIUtil.createId(AnsiblePackage.class));

                ansiblePackage.setEntryPoints(entryPoints);
                ansiblePackage.setArchive(Base64.encodeBase64(file
                        .toByteArray()));
                primitiveManager.save(ansiblePackage);
                return PrimitiveMapper.map(ansiblePackage);
            default:
                throw NotFoundException.notFound.unableToFindEntityInURL(URI
                        .create(type));

            }

        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

    }

    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public PrimitiveRestRep updatePrimitive(@PathParam("id") final URI id,
            final PrimitiveUpdateParam param) {
        final PrimitiveTypeNames type = PrimitiveTypeNames.get(URIUtil
                .getTypeName(id));

        switch (type) {
        case ANSIBLE_PACKAGE:
            final AnsiblePackage ansiblePackage = primitiveManager.findById(id);
            if (null == ansiblePackage) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
            return updateAnsiblePackage(ansiblePackage, param);
        }
        return null;
    }

    @GET
    @Path("resource/{type}/{id}")
    public Response download(@PathParam("type") final String type,
            @PathParam("id") final URI id,
            @Context final HttpServletResponse response) {

        final PrimitiveResourceType resourceType = PrimitiveResourceType
                .get(type);

        if (null == resourceType) {
            throw NotFoundException.notFound.unableToFindEntityInURL(URI
                    .create(type));
        }

        switch (resourceType) {
        case ANSIBLE:
            final AnsiblePackage ansiblePackage = primitiveManager.findById(id);
            final byte[] archive = Base64.decodeBase64(ansiblePackage
                    .getArchive());

            response.setContentLength(archive.length);
            response.setHeader("Content-Disposition", "attachment; filename="
                    + ansiblePackage.getLabel() + ".tar");

            try (final InputStream in = new ByteArrayInputStream(archive)) {
                final ServletOutputStream outStream = response
                        .getOutputStream();
                final byte[] bbuf = new byte[archive.length];

                int length = 0;
                while ((in != null) && ((length = in.read(bbuf)) != -1)) {
                    outStream.write(bbuf, 0, length);
                }
                outStream.flush();
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }

            return Response.ok().build();
        default:
            throw NotFoundException.notFound.unableToFindEntityInURL(URI
                    .create(type));
        }
    }

    private PrimitiveRestRep updateAnsiblePackage(AnsiblePackage update,
            final PrimitiveUpdateParam param) {

        if (null != param.getName()) {
            update.setLabel(param.getName());
        }
        if (null != param.getFriendlyName()) {
            update.setFriendlyName(param.getFriendlyName());
        }
        if (null != param.getDescription()) {
            update.setDescription(param.getDescription());
        }

        if (null != param.getInput()) {
            final StringSet extraVars = update.getExtraVars() == null ? new StringSet()
                    : update.getExtraVars();
            if (null != param.getInput().getAdd()) {
                for (final String input : param.getInput().getAdd()) {
                    if (input.startsWith("@extraVar.")) {
                        extraVars.add(input.substring("@extraVar.".length()));
                    }
                }
            }
            if (null != param.getInput().getRemove()) {
                for (final String input : param.getInput().getRemove()) {
                    if (input.startsWith("@extraVar.")) {
                        extraVars
                                .remove(input.substring("@extraVar.".length()));
                    }
                }
            }
            update.setExtraVars(extraVars);
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

    private static List<InputParameterRestRep> mapInput(
            List<InputParameter> inputParameters) {
        List<InputParameterRestRep> inputRestRep = new ArrayList<InputParameterRestRep>();
        int index = 0;
        for (final InputParameter parameter : inputParameters) {

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
                inputRestRep.add(index++, inputParamRestRep);
            }

        }
        return inputRestRep;
    }

    private static List<OutputParameterRestRep> mapOutput(
            List<OutputParameter> output) {
        List<OutputParameterRestRep> outputRestRep = new ArrayList<OutputParameterRestRep>();
        int index = 0;
        for (final OutputParameter parameter : output) {
            if (parameter.isBasicOutputParameter()) {
                BasicOutputParameter outputParam = parameter
                        .asBasicOutputParameter();
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

}
