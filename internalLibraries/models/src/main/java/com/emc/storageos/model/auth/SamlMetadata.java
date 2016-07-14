/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.auth;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

/**
 * Class that contains all the saml service provider metadata
 * related information that is stored in zookeeper.
 *
 *It is stored in zookeeper under the path /config/saml/metadata.
 */
@XmlRootElement(name = "saml_metadata")
public class SamlMetadata implements Serializable{
    private static final long serialVersionUID = 538569426992678275L;

    private SamlBaseMetadata baseMetadata;
    private SamlExtendedMetadata extendedMetadata;

    /**
     * @return the base saml metadata information to be stored in zookeeper.
     */
    @XmlElement(name = "base_metadata")
    @JsonProperty("base_metadata")
    public SamlBaseMetadata getBaseMetadata() {
        if (baseMetadata == null) {
            baseMetadata = new SamlBaseMetadata();
        }
        return baseMetadata;
    }

    /**
     * Sets the base saml service provider metadata.
     *
     * @param baseMetadata base saml service provider metadata information.
     */
    public void setBaseMetadata(SamlBaseMetadata baseMetadata) {
        this.baseMetadata = baseMetadata;
    }

    /**
     * @return the extended saml metadata information to be stored in zookeeper.
     */
    @XmlElement(name = "extended_metadata")
    @JsonProperty("extended_metadata")
    public SamlExtendedMetadata getExtendedMetadata() {
        if (extendedMetadata == null) {
            extendedMetadata = new SamlExtendedMetadata();
        }
        return extendedMetadata;
    }

    /**
     * Sets the extended saml service provider metadata.
     *
     * @param extendedMetadata extended saml service provider metadata information.
     */
    public void setExtendedMetadata(SamlExtendedMetadata extendedMetadata) {
        this.extendedMetadata = extendedMetadata;
    }
}
