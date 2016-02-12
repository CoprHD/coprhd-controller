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
 * Base saml Service Provider (SP) metadata information to be
 * stored in the zookeeper. This information will be used to generate
 * the saml SP metadata when the IDP requests it.
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SamlBaseMetadata implements Serializable{
    private static final long serialVersionUID = 538569426992678275L;

    private String entityID;
    private String entityBaseURL;
    private Boolean requestSigned;
    private Boolean assertionSigned;
    private List<SamlResponseAttribute> samlResponseAttributes;

    /**
     * @return the saml SSO service provider (ViPR) entityID.
     */
    @XmlElement(name = "entity_id")
    @JsonProperty("entity_id")
    public String getEntityID() {
        return entityID;
    }

    /**
     * Sets the saml SSO service provider (ViPR) entityID.
     *
     * @param entityID entityID of the saml service provider
     *                 that can be used by the IDP do identify the
     *                 service provider.
     */
    public void setEntityID(String entityID) {
        this.entityID = entityID;
    }

    /**
     * @return the saml service provider (ViPR) entity baseURL.
     */
    @XmlElement(name = "entity_base_url")
    @JsonProperty("entity_base_url")
    public String getEntityBaseURL() {
        return entityBaseURL;
    }

    /**
     * Sets the saml service provider (ViPR) entity baseURL.
     *
     * @param entityBaseURL saml service provider baseURL and
     *                      all the saml URLs will be appended on
     *                      top of this baseURL.
     */
    public void setEntityBaseURL(String entityBaseURL) {
        this.entityBaseURL = entityBaseURL;
    }

    /**
     * @return whether the request to be signed or not.
     */
    @XmlElement(name = "request_signed")
    @JsonProperty("request_signed")
    public Boolean isRequestSigned() {
        return requestSigned;
    }

    /**
     * Sets whether all the saml authentication request to IDP
     * needs to be signed or not.
     *
     * @param requestSigned flag to indicate whether all the saml
     *                      authentication request (except logout) from
     *                      service provider to IDP is signed or not.
     */
    public void setRequestSigned(Boolean requestSigned) {
        this.requestSigned = requestSigned;
    }

    /**
     * @return whether the response to be signed or not.
     */
    @XmlElement(name = "assertion_signed")
    @JsonProperty("assertion_signed")
    public Boolean isAssertionSigned() {
        return assertionSigned;
    }

    /**
     * Sets whether all the saml authentication response from IDP to
     * SP needs to be signed or not.
     *
     * @param assertionSigned flag to indicate whether all the saml
     *                        authentication response (except logout) from
     *                        IDP to service is signed or not.
     */
    public void setAssertionSigned(Boolean assertionSigned) {
        this.assertionSigned = assertionSigned;
    }

    /**
     * @return the list attributes to be included in the response
     *          sent from IDP to SP in the authentication response.
     */
    @XmlElementWrapper(name = "saml_response_attributes")
    public List<SamlResponseAttribute> getSamlResponseAttributes() {
        if (samlResponseAttributes == null) {
            samlResponseAttributes = new ArrayList<SamlResponseAttribute>();
        }
        return samlResponseAttributes;
    }

    /**
     * Sets the attributes to be returned in the saml authentication
     * response from IDP to SP.
     *
     * @param samlResponseAttributes list of saml attributes to be included in the
     *                               saml response.
     */
    public void setSamlResponseAttributes(List<SamlResponseAttribute> samlResponseAttributes) {
        this.samlResponseAttributes = samlResponseAttributes;
    }
}
