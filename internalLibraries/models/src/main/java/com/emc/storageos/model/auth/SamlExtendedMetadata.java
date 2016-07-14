/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.auth;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Extended saml service provider metadata class.
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SamlExtendedMetadata implements Serializable{
    private static final long serialVersionUID = -4001738530264990070L;

    private String entityAlias;
    private Boolean logoutRequestSigned;
    private Boolean logoutResponseSigned;
    private Boolean artifactResolveSigned;
    private String hostNameVerification;
    private Boolean signMetadata;
    private String signingAlgorithm;

    /**
     * @return the alias of the saml service provider (ViPR) entity.
     */
    @XmlElement(name = "entity_alias")
    @JsonProperty("entity_alias")
    public String getEntityAlias() {
        return entityAlias;
    }

    /**
     * Sets the alias of the saml service provider (ViPR) entity.
     *
     * @param entityAlias alias of the saml service provider (ViPR) entity.
     */
    public void setEntityAlias(String entityAlias) {
        this.entityAlias = entityAlias;
    }

    /**
     * @return whether the logout request to be signed or not.
     */
    @XmlElement(name = "logout_request_signed")
    @JsonProperty("logout_request_signed")
    public Boolean isLogoutRequestSigned() {
        return logoutRequestSigned;
    }

    /**
     * Sets whether all the saml authentication request to IDP
     * needs to be signed or not.
     *
     * @param logoutRequestSigned flag to indicate whether all the saml
     *                            logout request from service provider
     *                            to IDP is signed or not.
     */
    public void setLogoutRequestSigned(Boolean logoutRequestSigned) {
        this.logoutRequestSigned = logoutRequestSigned;
    }

    /**
     * @return whether the logout response to be signed or not.
     */
    @XmlElement(name = "logout_response_signed")
    @JsonProperty("logout_response_signed")
    public Boolean isLogoutResponseSigned() {
        return logoutResponseSigned;
    }

    /**
     * Sets whether all the saml logout response from IDP to SP
     * needs to be signed or not.
     *
     * @param logoutResponseSigned flag to indicate whether the saml logout
     *                             response from IDP to SP is singed or not.
     */
    public void setLogoutResponseSigned(Boolean logoutResponseSigned) {
        this.logoutResponseSigned = logoutResponseSigned;
    }

    /**
     * @return whether the saml artifact resolve messages between SP and IDP
     *          to be signed or not.
     */
    @XmlElement(name = "artifact_resolve_signed")
    @JsonProperty("artifact_resolve_signed")
    public Boolean isArtifactResolveSigned() {
        return artifactResolveSigned;
    }

    /**
     * Sets whether all the messages involved in saml artifactory resolve
     * procedure are to be signed or not.
     *
     * @param artifactResolveSigned flag to indicate whether saml artifactory
     *                              resolve messages to be signed or not.
     */
    public void setArtifactResolveSigned(Boolean artifactResolveSigned) {
        this.artifactResolveSigned = artifactResolveSigned;
    }

    /**
     * @return the scheme to allow/deny the incoming messages by verifying
     *          its host name.
     */
    @XmlElement(name = "host_name_verification")
    @JsonProperty("host_name_verification")
    public String getHostNameVerification() {
        return hostNameVerification;
    }

    /**
     * Sets the scheme to validate the host name of the incoming messages
     * to saml service provider.
     *
     * @param hostNameVerification scheme that validates/allows the incoming
     *                             messages to saml service provider based on its
     *                             origin host names. Valid values are (default,
     *                             defaultAndLocalhost, strict and allowAll).
    */
    public void setHostNameVerification(String hostNameVerification) {
        this.hostNameVerification = hostNameVerification;
    }

    /**
     * @return whether the saml service provider (ViPR) metadata is signed or not.
     */
    @XmlElement(name = "metadata_signed")
    @JsonProperty("metadata_signed")
    public Boolean isMetadataSigned() {
        return signMetadata;
    }

    /**
     * Sets whether the saml service provider (ViPR) is to be signed or not.
     *
     * @param signMetadata flag to indicate whether the saml service provider
     *                     metadata is to be signed or not.
     */
    public void setMetadataSigned(Boolean signMetadata) {
        this.signMetadata = signMetadata;
    }

    /**
     * @return the signing algorithm.
     */
    @XmlElement(name = "signing_algorithm")
    @JsonProperty("signing_algorithm")
    public String getSigningAlgorithm() {
        return signingAlgorithm;
    }

    /**
     * Sets the signing algorithm to sign communication messages
     * between IDP and SP.
     *
     * @param signingAlgorithm Encryption algorithm that should be used
     *                         to sign the messages between IDP and SP.
     */
    public void setSigningAlgorithm(String signingAlgorithm) {
        this.signingAlgorithm = signingAlgorithm;
    }
}
