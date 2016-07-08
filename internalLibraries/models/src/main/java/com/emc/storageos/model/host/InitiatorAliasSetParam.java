/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */package com.emc.storageos.model.host;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Request POST parameter for initiator alias set operation.
 */
@XmlRootElement(name = "initiator_alias_set")
public class InitiatorAliasSetParam {
    private URI systemURI;
    private String initiatorAlias;

    public InitiatorAliasSetParam() {
    }

    public InitiatorAliasSetParam(URI systemURI, String initiatorAlias) {
        this.systemURI = systemURI;
        this.initiatorAlias = initiatorAlias;
    }

    @XmlElement(required = true, name = "system_uri")
    public URI getSystemURI() {
        return systemURI;
    }

    public void setSystemURI(URI systemURI) {
        this.systemURI = systemURI;
    }

    @XmlElement(required = true, name = "initiator_alias")
    public String getInitiatorAlias() {
        return initiatorAlias;
    }

    public void setInitiatorAlias(String initiatorAlias) {
        this.initiatorAlias = initiatorAlias;
    }

}
