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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.util.encoders.Base64;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.LoggerFactory;

import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAO;
import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.catalog.primitives.CustomServicesPrimitiveMapper;
import com.emc.sa.catalog.primitives.CustomServicesResourceDAO;
import com.emc.sa.catalog.primitives.CustomServicesResourceDAOs;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.Builder;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.ResourcePackage;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.ResourcePackage.ResourceBuilder;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.WorkflowMetadata;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.WorkflowVersion;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.db.client.model.uimodels.WFDirectory;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.model.customservices.CustomServicesWorkflowRestRep;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.CustomServicesPrimitive.StepType;
import com.emc.storageos.primitives.CustomServicesPrimitiveResourceType;
import com.emc.storageos.primitives.CustomServicesPrimitiveType;
import com.emc.storageos.primitives.java.vipr.CustomServicesViPRPrimitive;
import com.emc.storageos.security.keystore.impl.KeyCertificateAlgorithmValuesHolder;
import com.emc.storageos.security.keystore.impl.KeystoreEngine;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Helper class to perform CRUD operations on a workflow
 */
public final class WorkflowHelper {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(WorkflowHelper.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WORKFLOWS_FOLDER = "workflows";
    private static final String OPERATIONS_FOLDER = "operations";
    private static final String RESOURCES_FOLDER = "resources";
    private static final String ROOT = "";
    private static final String METADATA_FILE = "workflow.md";
    private static final WorkflowVersion CURRENT_VERSION = new WorkflowVersion(2, 0, 0, 0);
    private static final ImmutableList<String> SUPPORTED_VERSIONS = ImmutableList.<String> builder()
            .add(CURRENT_VERSION.toString())
            .add(new WorkflowVersion(1, 0, 0, 0).toString())
            .build();
    private static final int MAX_IMPORT_NAME_INDEX = 100;
    private static final Set<String> ATTRIBUTES = ImmutableSet.<String> builder()
            .add(CustomServicesConstants.WORKFLOW_TIMEOUT_CONFIG)
            .add(CustomServicesConstants.WORKFLOW_LOOP)
            .build();

    private WorkflowHelper() {
    }

    /**
     * Create a workflow definition
     * 
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonGenerationException
     */
    public static CustomServicesWorkflow create(final CustomServicesWorkflowDocument document)
            throws JsonGenerationException, JsonMappingException, IOException {
        final CustomServicesWorkflow workflow = new CustomServicesWorkflow();

        workflow.setId(URIUtil.createId(CustomServicesWorkflow.class));
        if (StringUtils.isNotBlank(document.getName())) {
            workflow.setLabel(document.getName().trim());
        } else {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("name");
        }
        workflow.setDescription(document.getDescription());
        workflow.setSteps(toStepsJson(document.getSteps()));
        workflow.setPrimitives(getPrimitives(document));
        workflow.setAttributes(getAttributes(document));
        return workflow;
    }

    /**
     * Update a workflow definition
     * 
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonGenerationException
     */
    public static void update(final CustomServicesWorkflow oeWorkflow, final CustomServicesWorkflowDocument document)
            throws JsonGenerationException, JsonMappingException, IOException {

        if (document.getDescription() != null) {
            oeWorkflow.setDescription(document.getDescription().trim());
        }

        if (document.getName() != null) {
            oeWorkflow.setLabel(document.getName().trim());
        }

        if (null != document.getSteps()) {
            oeWorkflow.setSteps(toStepsJson(document.getSteps()));
            oeWorkflow.removePrimitives(StringSetUtil.stringSetToUriList(oeWorkflow.getPrimitives()));
            oeWorkflow.addPrimitives(StringSetUtil.stringSetToUriList(getPrimitives(document)));
            oeWorkflow.setAttributes(getAttributes(document));
        }
    }

    public static CustomServicesWorkflow updateState(final CustomServicesWorkflow oeWorkflow, final String state) {
        oeWorkflow.setState(state);
        return oeWorkflow;
    }

    public static CustomServicesWorkflowDocument toWorkflowDocument(final CustomServicesWorkflow workflow)
            throws JsonParseException, JsonMappingException, IOException {
        final CustomServicesWorkflowDocument document = new CustomServicesWorkflowDocument();
        document.setName(workflow.getLabel());
        document.setDescription(workflow.getDescription());
        document.setSteps(toDocumentSteps(workflow.getSteps()));
        if (!MapUtils.isEmpty(workflow.getAttributes())) {
            ImmutableMap.Builder<String, String> attributeMap = ImmutableMap.<String, String> builder();
            for (final Entry<String, String> attribute : workflow.getAttributes().entrySet()) {
                attributeMap.put(attribute.getKey(), attribute.getValue());
            }
            document.setAttributes(attributeMap.build());
        }

        return document;
    }

    public static CustomServicesWorkflowDocument toWorkflowDocument(final String workflow)
            throws JsonParseException, JsonMappingException, IOException {
        return MAPPER.readValue(workflow, CustomServicesWorkflowDocument.class);
    }

    public static String toWorkflowDocumentJson(CustomServicesWorkflow workflow)
            throws JsonGenerationException, JsonMappingException, JsonParseException, IOException {
        return MAPPER.writeValueAsString(toWorkflowDocument(workflow));
    }

    private static List<CustomServicesWorkflowDocument.Step> toDocumentSteps(final String steps)
            throws JsonParseException, JsonMappingException, IOException {
        return steps == null ? null
                : MAPPER.readValue(steps,
                        MAPPER.getTypeFactory().constructCollectionType(List.class, CustomServicesWorkflowDocument.Step.class));
    }

    private static String toStepsJson(final List<CustomServicesWorkflowDocument.Step> steps)
            throws JsonGenerationException, JsonMappingException, IOException {
        return MAPPER.writeValueAsString(steps);
    }

    private static StringSet getPrimitives(
            final CustomServicesWorkflowDocument document) {
        final StringSet primitives = new StringSet();
        if (CollectionUtils.isEmpty(document.getSteps())) {
            return primitives;
        }
        for (final Step step : document.getSteps()) {
            final StepType stepType = (null == step.getType()) ? null : StepType.fromString(step.getType());
            if (null != stepType) {
                switch (stepType) {
                    case VIPR_REST:
                        break;
                    default:
                        if (step.getOperation() != null) {
                            primitives.add(step.getOperation().toString());
                        }

                }
            }
        }
        return primitives;
    }

    public static StringMap getAttributes(final CustomServicesWorkflowDocument document) {
        final StringMap map = new StringMap();
        final Map<String, String> attributes = document.getAttributes();
        if (attributes != null) {
            for (final Entry<String, String> attribute : attributes.entrySet()) {
                if (!ATTRIBUTES.contains(attribute.getKey())) {
                    throw new IllegalStateException("Unknown attribute key: " + attribute.getKey());
                }
                map.put(attribute.getKey(), attribute.getValue());
            }
        }
        return map;
    }

    public static CustomServicesWorkflow importWorkflow(final InputStream stream,
            final WFDirectory wfDirectory, final ModelClient client,
            final CustomServicesPrimitiveDAOs daos,
            final CustomServicesResourceDAOs resourceDAOs,
            final boolean isPublish ) {

        try (final DataInputStream dis = new DataInputStream(stream)) {
            final WorkflowMetadata metadata = readMetadata(dis);
            if (!SUPPORTED_VERSIONS.contains(metadata.getVersion().toString())) {
                throw APIException.badRequests.workflowVersionNotSupported(metadata.getVersion().toString(), SUPPORTED_VERSIONS);
            }

            final byte[] encodedKey = readBytes(dis);
            final String algo = dis.readUTF();
            final byte[] sig = readBytes(dis);
            final byte[] data = readBytes(dis);

            // We should have read all of the bytes if there are any left in the stream throw an error
            if (dis.read(new byte[1]) > 0) {
                throw APIException.badRequests.workflowArchiveCannotBeImported("Corrupt data too many bytes");
            }

            final Signature signatureFactory = Signature.getInstance(algo);
            final KeyFactory keyFactory = KeyFactory.getInstance(KeyCertificateAlgorithmValuesHolder.DEFAULT_KEY_ALGORITHM);
            final PublicKey key = keyFactory.generatePublic(new X509EncodedKeySpec(Base64.decode(encodedKey)));
            signatureFactory.initVerify(key);
            signatureFactory.update(metadata.toBytes());
            signatureFactory.update(data);
            if (!signatureFactory.verify(sig)) {
                throw APIException.badRequests.workflowArchiveCannotBeImported("Corrupted data unable to verify signature");
            }

            return importWorkflow(metadata, data, wfDirectory, client, daos, resourceDAOs, isPublish);

        } catch (final IOException | GeneralSecurityException e) {
            log.error("Failed to import the archive: ", e);
            throw APIException.badRequests.workflowArchiveCannotBeImported(e.getMessage());
        }
    }

    private static byte[] readBytes(final DataInputStream dis) throws IOException {
        final int length = dis.readInt();
        if (length < 0) {
            throw new IOException("Invalid workflow package");
        }

        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int remaining = length;
            while (remaining > 0) {
                final byte[] bytes = new byte[remaining > 2048 ? 2048 : remaining];
                final int nRead = dis.read(bytes);
                if (nRead < 0) {
                    throw new IOException("Unexpected end of stream");
                }
                baos.write(bytes, 0, nRead);
                remaining -= nRead;
            }
            return baos.toByteArray();
        }

    }

    private static WorkflowMetadata readMetadata(final DataInputStream dis) throws IOException {
        // First 16 bytes (four integers are the version
        final WorkflowVersion version = new WorkflowVersion(dis.readInt(),
                dis.readInt(),
                dis.readInt(),
                dis.readInt());

        // Next bytes are a UTF string containing the workflow ID
        final URI id = URI.create(dis.readUTF());

        return new WorkflowMetadata(id, version);
    }

    /**
     * Import an archive given the tar.gz contents
     * 
     * @param archive the tar.gz contents of the workflow package
     * @param wfDirectory The directory to import the workflow to
     * @param client database client
     * @param daos DAO beans to access operations
     * @param resourceDAOs DAO beans to access resources
     * @return
     */
    public static CustomServicesWorkflow importWorkflow(final WorkflowMetadata metadata,
            final byte[] archive,
            final WFDirectory wfDirectory, final ModelClient client,
            final CustomServicesPrimitiveDAOs daos,
            final CustomServicesResourceDAOs resourceDAOs,
            final boolean isPublish ) {

        try (final TarArchiveInputStream tarIn = new TarArchiveInputStream(
                new GZIPInputStream(new ByteArrayInputStream(
                        archive)))) {
            final CustomServicesWorkflowPackage.Builder builder = new CustomServicesWorkflowPackage.Builder();
            builder.metadata(metadata);
            TarArchiveEntry entry = tarIn.getNextTarEntry();
            final Map<URI, ResourceBuilder> resourceMap = new HashMap<URI, ResourceBuilder>();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    final Path path = getPath(entry);
                    final byte[] bytes = read(tarIn);
                    addEntry(builder, resourceMap, path, bytes);

                }
                entry = tarIn.getNextTarEntry();
            }

            for (final ResourceBuilder resourceBuilder : resourceMap.values()) {
                builder.addResource(resourceBuilder.build());
            }

            return importWorkflow(builder.build(), wfDirectory, client, daos, resourceDAOs, isPublish);

        } catch (final IOException e) {
            log.error("Failed to import the archive: ", e);
            throw APIException.badRequests.workflowArchiveCannotBeImported(e.getMessage());
        }
    }

    private static CustomServicesWorkflow importWorkflow(final CustomServicesWorkflowPackage workflowPackage,
            final WFDirectory wfDirectory,
            final ModelClient client,
            final CustomServicesPrimitiveDAOs daos,
            final CustomServicesResourceDAOs resourceDAOs,
            final boolean isPublish ) throws JsonGenerationException, JsonMappingException, IOException {

        // TODO: This will only import new items. If hte user wants to update an existing item they'll need to delete the
        // item and import it again. We should support update of an item as will as import of new items.

        for (final Entry<URI, ResourcePackage> resource : workflowPackage.resources().entrySet()) {
            final CustomServicesResourceDAO<?> dao = resourceDAOs.getByModel(URIUtil.getTypeName(resource.getKey()));
            final CustomServicesPrimitiveResourceRestRep metadata = resource.getValue().metadata();
            if (null == dao) {
                throw new RuntimeException("Type not found for ID " + metadata.getId());
            }

            if (!dao.importResource(metadata, resource.getValue().bytes())) {
                log.info("Resource " + resource.getKey() + " previously imported");
            }
        }

        for (final Entry<URI, CustomServicesPrimitiveRestRep> operation : workflowPackage.operations().entrySet()) {
            final CustomServicesPrimitiveDAO<?> dao = daos.getByModel(URIUtil.getTypeName(operation.getKey()));
            if (null == dao) {
                throw new RuntimeException("Type not found for ID " + operation.getKey());
            }
            if (dao.importPrimitive(operation.getValue())) {
                wfDirectory.addWorkflows(Collections.singleton(operation.getKey()));
            } else {
                log.info("Primitive " + operation.getKey() + " previously imported");
            }

        }

        for (final Entry<URI, CustomServicesWorkflowRestRep> workflow : workflowPackage.workflows().entrySet()) {
            final CustomServicesWorkflow model = client.customServicesWorkflows().findById(workflow.getKey());
            if (null == model || model.getInactive()) {
                importWorkflow(workflow.getValue(), client, wfDirectory, isPublish);
            } else {
                if (isPublish) {
                    log.debug("change the state of already imported workflow");
                    model.setState(CustomServicesWorkflow.CustomServicesWorkflowStatus.PUBLISHED.toString());
                    client.save(model);
                }
                log.info("Workflow " + workflow.getKey() + " previously imported");
            }
        }
        return client.customServicesWorkflows().findById(workflowPackage.metadata().getId());
    }

    /**
     * @param client
     * @param wfDirectory
     * @param workflow
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonGenerationException
     */
    private static void importWorkflow(final CustomServicesWorkflowRestRep workflow, final ModelClient client,
            final WFDirectory wfDirectory, final boolean isPublish) throws JsonGenerationException, JsonMappingException, IOException {

        final CustomServicesWorkflow dbWorkflow = new CustomServicesWorkflow();
        dbWorkflow.setId(workflow.getId());
        dbWorkflow.setLabel(findImportName(workflow.getName(), client));
        dbWorkflow.setInactive(false);
        dbWorkflow.setDescription(workflow.getDocument().getDescription());
        dbWorkflow.setSteps(toStepsJson(workflow.getDocument().getSteps()));
        dbWorkflow.setPrimitives(getPrimitives(workflow.getDocument()));
        dbWorkflow.setAttributes(getAttributes(workflow.getDocument()));
        if (isPublish) {
		    log.debug("change the state of workflow to publish");
            dbWorkflow.setState(CustomServicesWorkflow.CustomServicesWorkflowStatus.PUBLISHED.toString());
        }
        client.save(dbWorkflow);
        if (null != wfDirectory.getId()) {
            wfDirectory.addWorkflows(Collections.singleton(workflow.getId()));
            client.save(wfDirectory);
        }
    }

    public static byte[] exportWorkflow(final URI id,
            final ModelClient client,
            final CustomServicesPrimitiveDAOs daos,
            final CustomServicesResourceDAOs resourceDAOs,
            final KeyStore keystore) {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (final DataOutputStream dos = new DataOutputStream(baos)) {
                final CustomServicesWorkflowPackage workflowPackage = makeCustomServicesWorkflowPackage(id, client, daos, resourceDAOs);
                // make the compressed archive
                final byte[] archiveBytes = makeArchive(workflowPackage, client, daos, resourceDAOs);

                // write the meta data to the output stream
                writeMetadata(workflowPackage, dos);
                // sign the metadata+archive bytes and write the signature and signature metadata to the stream
                writeSignature(workflowPackage.metadata().toBytes(), archiveBytes, keystore, dos);
                // write the archive bytes into the stream
                writeArchive(archiveBytes, dos);

                return baos.toByteArray();
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static void writeArchive(final byte[] archiveBytes, final DataOutputStream dos) throws IOException {
        dos.writeInt(archiveBytes.length);
        dos.write(archiveBytes);
    }

    private static void writeSignature(final byte[] metadataBytes,
            final byte[] archiveBytes,
            final KeyStore keystore,
            final DataOutputStream dos) throws IOException {

        try {
            final Key priv = keystore.getKey(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS, null);
            final Certificate certificate = keystore.getCertificate(
                    KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS);
            final PublicKey pub = certificate.getPublicKey();
            final String algo = ((X509Certificate) certificate).getSigAlgName();
            final Signature signatureFactory = Signature.getInstance(algo);
            signatureFactory.initSign((PrivateKey) priv);
            signatureFactory.update(metadataBytes);
            signatureFactory.update(archiveBytes);
            final byte[] signature = signatureFactory.sign();
            final byte[] encodedKey = Base64.encode(pub.getEncoded());
            dos.writeInt(encodedKey.length);
            dos.write(encodedKey);
            dos.writeUTF(algo);
            dos.writeInt(signature.length);
            dos.write(signature);
        } catch (final GeneralSecurityException e) {
            throw APIException.internalServerErrors.genericApisvcError("Failed to sign workflow package ", e);
        }
    }

    private static void writeMetadata(final CustomServicesWorkflowPackage workflowPackage, final DataOutputStream dos) throws IOException {
        final WorkflowVersion version = workflowPackage.metadata().getVersion();
        dos.writeInt(version.major());
        dos.writeInt(version.minor());
        dos.writeInt(version.servicePack());
        dos.writeInt(version.patch());

        // Write out the workflow ID as a UTF-8 string
        dos.writeUTF(workflowPackage.metadata().getId().toString());
    }

    public static byte[] makeArchive(final CustomServicesWorkflowPackage workflowPackage,
            final ModelClient client,
            final CustomServicesPrimitiveDAOs daos,
            final CustomServicesResourceDAOs resourceDAOs) {

        try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            makeArchive(out, workflowPackage);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param out
     * @param workflowPackage
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonGenerationException
     */
    private static void makeArchive(final ByteArrayOutputStream out, final CustomServicesWorkflowPackage workflowPackage)
            throws IOException {
        try (final TarArchiveOutputStream tarOut = new TarArchiveOutputStream(new GZIPOutputStream(new BufferedOutputStream(out)))) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            for (final Entry<URI, CustomServicesWorkflowRestRep> workflow : workflowPackage.workflows().entrySet()) {
                final String name = WORKFLOWS_FOLDER + "/" + workflow.getKey().toString();
                final byte[] content = MAPPER.writeValueAsBytes(workflow.getValue());
                final Date modTime = workflow.getValue().getCreationTime().getTime();
                addArchiveEntry(tarOut, name, modTime, content);
            }

            for (final Entry<URI, CustomServicesPrimitiveRestRep> operation : workflowPackage.operations().entrySet()) {
                final String name = OPERATIONS_FOLDER + "/" + operation.getKey().toString();
                final byte[] content = MAPPER.writeValueAsBytes(operation.getValue());
                final Date modTime = operation.getValue().getCreationTime().getTime();
                addArchiveEntry(tarOut, name, modTime, content);
            }

            for (final Entry<URI, ResourcePackage> resource : workflowPackage.resources().entrySet()) {
                final String name = RESOURCES_FOLDER + "/" + resource.getKey().toString() + ".md";
                final String resourceFile = RESOURCES_FOLDER + "/" + resource.getKey().toString();
                final byte[] metadata = MAPPER.writeValueAsBytes(resource.getValue().metadata());
                final Date modTime = resource.getValue().metadata().getCreationTime().getTime();
                addArchiveEntry(tarOut, name, modTime, metadata);
                addArchiveEntry(tarOut, resourceFile, modTime, resource.getValue().bytes());
            }
            tarOut.finish();
        }
    }

    /**
     * @param id
     * @param client
     * @param resourceDAOs
     * @return
     */
    private static CustomServicesWorkflowPackage makeCustomServicesWorkflowPackage(final URI id, final ModelClient client,
            final CustomServicesPrimitiveDAOs daos, final CustomServicesResourceDAOs resourceDAOs) {
        final CustomServicesWorkflowPackage.Builder builder = new CustomServicesWorkflowPackage.Builder();
        builder.metadata(new WorkflowMetadata(id, CURRENT_VERSION));
        addWorkflow(builder, id, client, daos, resourceDAOs);
        return builder.build();

    }

    private static void addWorkflow(final Builder builder, final URI id, final ModelClient client, final CustomServicesPrimitiveDAOs daos,
            final CustomServicesResourceDAOs resourceDAOs) {
        final CustomServicesWorkflow dbWorkflow = client.customServicesWorkflows().findById(id);
        if (null == dbWorkflow) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        final CustomServicesWorkflowRestRep workflow = CustomServicesWorkflowMapper.map(dbWorkflow);
        builder.addWorkflow(workflow);
        for (final Step step : workflow.getDocument().getSteps()) {
            final String stepId = step.getId();
            if (!StepType.END.toString().equalsIgnoreCase(stepId) &&
                    !StepType.START.toString().equalsIgnoreCase(stepId)) {
                final URI operation = step.getOperation();
                final String type = URIUtil.getTypeName(operation);
                if (type.equals(CustomServicesWorkflow.class.getSimpleName())) {
                    addWorkflow(builder, operation, client, daos, resourceDAOs);
                } else if (!type.equals(CustomServicesViPRPrimitive.class.getSimpleName())) {
                    addOperation(builder, operation, daos, resourceDAOs);
                }
            }
        }
    }

    private static void addOperation(final Builder builder, final URI id, final CustomServicesPrimitiveDAOs daos,
            final CustomServicesResourceDAOs resourceDAOs) {
        final CustomServicesPrimitiveDAO<?> dao = daos.getByModel(URIUtil.getTypeName(id));
        if (null == dao) {
            throw new RuntimeException("Operation type for " + id + " not found");
        }

        final CustomServicesPrimitiveType primitive = dao.export(id);

        if (null == primitive) {
            throw new RuntimeException("Operation with ID " + id + " not found");
        }

        if (null != primitive.resource()) {
            addResource(builder, primitive.resource(), resourceDAOs);
        }

        builder.addOperation(CustomServicesPrimitiveMapper.map(primitive));
    }

    private static void addResource(Builder builder, NamedURI id, CustomServicesResourceDAOs resourceDAOs) {

        final CustomServicesResourceDAO<?> dao = resourceDAOs.getByModel(URIUtil.getTypeName(id.getURI()));

        if (null == dao) {
            throw new RuntimeException("Resource type for " + id + " not found");
        }

        final CustomServicesPrimitiveResourceType resource = dao.getResource(id.getURI());

        if (null == resource) {
            throw new RuntimeException("Resource " + id + " not found");
        }
        builder.addResource(new ResourcePackage(CustomServicesPrimitiveMapper.map(resource), resource.resource()));

        for (final NamedElement related : dao.listRelatedResources(id.getURI())) {
            addResource(builder, new NamedURI(related.getId(), related.getName()), resourceDAOs);
        }
    }

    private static void addArchiveEntry(final TarArchiveOutputStream tarOut, final String name, final Date modTime, final byte[] bytes)
            throws IOException {
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

    private static void addEntry(final CustomServicesWorkflowPackage.Builder builder, final Map<URI, ResourceBuilder> resourceMap,
            final Path path, final byte[] bytes) throws IOException, JsonParseException, JsonMappingException {
        final String parent = path.getParent() == null ? ROOT : path.getParent().getFileName().toString();
        switch (parent) {
            case WORKFLOWS_FOLDER:
                addWorkflow(builder, bytes);
                return;
            case OPERATIONS_FOLDER:
                addOperation(builder, bytes);
                return;
            case RESOURCES_FOLDER:
                addResource(resourceMap, path, bytes);
                return;
            default:
                throw APIException.badRequests.workflowArchiveContentsInvalid(parent);
        }
    }

    private static void addResource(final Map<URI, ResourceBuilder> resourceMap, final Path path, final byte[] bytes) throws IOException,
            JsonParseException, JsonMappingException {
        final boolean isMetadata;
        final URI id;
        final String filename = path.getFileName().toString();
        if (filename.endsWith(".md")) {
            id = URI.create(filename.substring(0, filename.indexOf('.')));
            isMetadata = true;
        } else {
            id = URI.create(filename);
            isMetadata = false;
        }
        final ResourceBuilder resourceBuilder;
        if (!resourceMap.containsKey(id)) {
            resourceBuilder = new ResourceBuilder();
            resourceMap.put(id, resourceBuilder);
        } else {
            resourceBuilder = resourceMap.get(id);
        }

        if (isMetadata) {
            resourceBuilder.metadata(MAPPER.readValue(bytes, CustomServicesPrimitiveResourceRestRep.class));
        } else {
            resourceBuilder.bytes(bytes);
        }
    }

    private static void addOperation(final CustomServicesWorkflowPackage.Builder builder, final byte[] bytes) throws IOException,
            JsonParseException, JsonMappingException {
        builder.addOperation(MAPPER.readValue(bytes, CustomServicesPrimitiveRestRep.class));
    }

    private static void addWorkflow(final CustomServicesWorkflowPackage.Builder builder, final byte[] bytes) throws IOException,
            JsonParseException, JsonMappingException {
        builder.addWorkflow(MAPPER.readValue(bytes, CustomServicesWorkflowRestRep.class));
    }

    private static byte[] read(final TarArchiveInputStream tarIn) throws IOException {
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            IOUtils.copy(tarIn, out);
            return out.toByteArray();
        }
    }

    private static Path getPath(final TarArchiveEntry entry) {
        final Path path = FileSystems.getDefault().getPath(entry.getName());
        if (null == path) {
            throw APIException.badRequests.workflowArchiveCannotBeImported("Uknown file: " + entry.getName());
        }
        return path.normalize();
    }

    private static String findImportName(final String name, final ModelClient client) {
        return isNameAvailable(name, client) ? name : findImportName(name, client, 1);
    }

    private static String findImportName(final String baseName, final ModelClient client, final int index) {
        if (index < 1) {
            throw new RuntimeException("Import name index cannot be negative");
        } else if (index > MAX_IMPORT_NAME_INDEX) {
            // Throw an exception if we cannot find a suitable import name
            // After many tries
            throw APIException.badRequests
                    .workflowArchiveCannotBeImported("Too many duplicate names: " + baseName + " please rename an existing workflow.");
        }
        final String name = String.format("%s (%d)", baseName, index);
        return isNameAvailable(name, client) ? name : findImportName(baseName, client, index + 1);
    }

    private static boolean isNameAvailable(final String name, final ModelClient client) {
        final List<NamedElement> existing = client.findByLabel(CustomServicesWorkflow.class, name);
        return (null == existing || existing.isEmpty());
    }

}
