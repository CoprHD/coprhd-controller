/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.auth.service.impl.resource;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedDataManager;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.model.auth.SamlMetadata;
import com.emc.storageos.model.auth.SamlMetadataResponse;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.keystore.impl.KeystoreEngine;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.xml.io.MarshallingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.saml.key.JKSKeyManager;
import org.springframework.security.saml.metadata.ExtendedMetadata;
import org.springframework.security.saml.metadata.MetadataGenerator;
import org.springframework.security.saml.util.SAMLUtil;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DirectFieldBindingResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Authentication resource for all the SAML based SSO activities.
 */

@Path("/saml")
public class SamlAuthenticationResource {

    private final Logger _log = LoggerFactory.getLogger(this.getClass());

    private static final int MAX_SAML_SP_METADATA_COUNT = 1;

    private static final String DEFAULT_SECURITY_PROFILE="pkix";
    private static final String DEFAULT_SERVICE_PROVIDER_METADATA_FILE_NAME="ViPRMetadata.xml";

    @Autowired
    private CoordinatorClient coordinatorClient;

    @Autowired
    private DbClientImpl dbClient;

    @Autowired
    JKSKeyManager keyManager;

    protected DistributedDataManager _distDataManager;

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        _distDataManager.close();
    }

    public void setCoordinator(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;

        _distDataManager = coordinatorClient.createDistributedDataManager(
                String.format("%s/%s", ZkPath.CONFIG, Constants.SAML_METADATA),
                        MAX_SAML_SP_METADATA_COUNT);
        if (null == _distDataManager) {
            throw com.emc.storageos.security.exceptions.SecurityException.fatals.coordinatorNotInitialized();
        }
    }

    public void setDbClient(DbClientImpl dbClient) {
        this.dbClient = dbClient;
    }

    public void setKeyManager(JKSKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    /**
     * Provides the SAML Service Provider metadata XML.
     *
     * @return SAML Service Provider metadata XML file
     *          with the name ViPRMetadata.xml.
     *
     * @throws Exception
     */
    @GET
    @Path("/metadata")
    @Produces(MediaType.APPLICATION_XML)
    public Response getSAMLServiceProviderMetadata() throws Exception{
        String metadataPath = getSamlSPMetadataConfigPath();
        SamlMetadata metadata = (SamlMetadata)_distDataManager.getData(metadataPath, false);

        if (metadata == null) {
            throw APIException.badRequests.unableToFindSamlSPMetadata(metadataPath);
        }

        final String metadataString = MapToMetadataGenerator(metadata);

        StreamingOutput streamingOutput = new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                Writer writer = new BufferedWriter(new OutputStreamWriter(output));
                writer.write(metadataString);
                writer.flush();
            }
        };

        return Response.ok(streamingOutput).header("Content-Disposition", String.format("attachment, filename=\"%s\"",
                DEFAULT_SERVICE_PROVIDER_METADATA_FILE_NAME)).build();
    }

    /**
     * Provides the SAML Service Provider metadata object. This is used to show the
     * SP metadata details in the UI.
     *
     * @return the SAML Service Provider metadata object.
     *
     * @throws Exception
     */
    @GET
    @Path("/metadata/object")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SamlMetadata getSAMLServiceProviderMetadataObject() throws Exception{
        String metadataPath = getSamlSPMetadataConfigPath();
        SamlMetadata metadata = (SamlMetadata)_distDataManager.getData(metadataPath, false);
        if (metadata == null) {
            throw APIException.badRequests.unableToFindSamlSPMetadata(metadataPath);
        }
        return metadata;
    }

    /**
     * Create the SAML Service Provider metadata. This metadata is stored
     * in the Zookeeper under the path /config/saml/metadata/sp
     *
     * @param samlMetadata api payload to create the SAML Service Provider
     *                     metadata.
     *
     * @return the SAML Service Provider metadata object.
     *
     * @throws Exception
     */
    @POST
    @Path("/metadata")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public SamlMetadata generateSAMLServiceProviderMetadata(SamlMetadata samlMetadata) throws Exception{

        MetadataValidator metadataValidator = new MetadataValidator();
        BindingResult bindingResult = new DirectFieldBindingResult(samlMetadata, "SamlMetadata");
        metadataValidator.validate(samlMetadata, bindingResult);

        MapToMetadataGenerator(samlMetadata);

        String metadataPath = getSamlSPMetadataConfigPath();
        SamlMetadata existingMetadata = (SamlMetadata)_distDataManager.getData(metadataPath, false);
        if(existingMetadata != null) {
            _distDataManager.removeNode(metadataPath);
        }

        _distDataManager.putData(metadataPath, samlMetadata);

        return samlMetadata;
    }

    /**
     * Deletes the SAML Service Provider metadata from the Zookeeper.
     *
     * @return successful message or exception.
     *
     * @throws Exception
     */
    @POST
    @Path("/metadata/delete")
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public Response deleteSAMLServiceProviderMetadata() throws Exception{
        String metadataPath = getSamlSPMetadataConfigPath();
        SamlMetadata existingMetadata = (SamlMetadata)_distDataManager.getData(metadataPath, false);
        if(existingMetadata == null) {
            throw APIException.badRequests.unableToFindSamlSPMetadata(metadataPath);
        }

        _distDataManager.removeNode(metadataPath);

        return Response.ok("SAML Service Provider metadata deleted successfully").build();
    }

    /**
     * Formats the SAML Service Provider metadata config path
     * "/config/saml/metadata/sp" in the Zookeeper.
     *
     * @return the SAML Service Provider config path.
     */
    private String getSamlSPMetadataConfigPath() {
        return String.format("%s/%s/%s", ZkPath.CONFIG, Constants.SAML_METADATA, Constants.SAML_METADATA_SP);
    }

    /**
     * Maps the SAML Service Provider creation payload the actual SAML
     * metadata and extended metadata.
     *
     * @param samlMetadata SAML Service Provider metadata creation payload.
     *
     * @return SAML Service Provider metadata XML as a string.
     *
     * @throws MarshallingException
     */
    private String MapToMetadataGenerator(SamlMetadata samlMetadata) throws MarshallingException {
        ExtendedMetadata extendedMetadata = new ExtendedMetadata();
        MetadataGenerator generator = new MetadataGenerator();

        generator.setKeyManager(keyManager);
        generator.setExtendedMetadata(extendedMetadata);

        // Basic metadata properties
        generator.setEntityId(samlMetadata.getBaseMetadata().getEntityID());
        generator.setEntityBaseURL(samlMetadata.getBaseMetadata().getEntityBaseURL());
        generator.setRequestSigned(samlMetadata.getBaseMetadata().isRequestSigned());
        generator.setWantAssertionSigned(samlMetadata.getBaseMetadata().isAssertionSigned());

        Collection<String> bindingsSSO = new LinkedList<String>();
        Collection<String> bindingsHoKSSO = new LinkedList<String>();

        bindingsSSO.add(SAMLConstants.SAML2_ARTIFACT_BINDING_URI);
        bindingsHoKSSO.add(SAMLConstants.SAML2_ARTIFACT_BINDING_URI);

        // Set bindings
        generator.setBindingsSSO(bindingsSSO);
        generator.setBindingsHoKSSO(bindingsHoKSSO);
        generator.setAssertionConsumerIndex(0);

        // Keys
        extendedMetadata.setSigningKey(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS.toLowerCase());
        extendedMetadata.setEncryptionKey(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS.toLowerCase());
        extendedMetadata.setTlsKey(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS.toLowerCase());

        // Discovery
        extendedMetadata.setIdpDiscoveryEnabled(false);
        generator.setIncludeDiscoveryExtension(false);

        // Alias
        extendedMetadata.setAlias(samlMetadata.getExtendedMetadata().getEntityAlias());

        // Security settings
        extendedMetadata.setSecurityProfile(DEFAULT_SECURITY_PROFILE);
        extendedMetadata.setSslSecurityProfile(DEFAULT_SECURITY_PROFILE);
        extendedMetadata.setRequireLogoutRequestSigned(samlMetadata.getExtendedMetadata().isLogoutRequestSigned());
        extendedMetadata.setRequireLogoutResponseSigned(samlMetadata.getExtendedMetadata().isLogoutResponseSigned());
        extendedMetadata.setRequireArtifactResolveSigned(samlMetadata.getExtendedMetadata().isArtifactResolveSigned());
        extendedMetadata.setSslHostnameVerification(samlMetadata.getExtendedMetadata().getHostNameVerification());

        // Metadata signing
        extendedMetadata.setSignMetadata(samlMetadata.getExtendedMetadata().isMetadataSigned());
        extendedMetadata.setSigningAlgorithm(samlMetadata.getExtendedMetadata().getSigningAlgorithm());

        // Generate values
        EntityDescriptor generatedDescriptor = generator.generateMetadata();
        ExtendedMetadata generatedExtendedMetadata = generator.generateExtendedMetadata();

        String metadataString = SAMLUtil.getMetadataAsString(null, keyManager, generatedDescriptor, generatedExtendedMetadata);
        return metadataString;
    }
}
