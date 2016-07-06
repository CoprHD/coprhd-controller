package com.emc.storageos.model.host;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Request POST parameter for initiator alias get operation.
 */
@XmlRootElement(name = "initiator_alias_get")
public class InitiatorAliasGetParam {
    private URI systemURI;

    public InitiatorAliasGetParam() {
    }

    public InitiatorAliasGetParam(URI systemURI) {
        this.systemURI = systemURI;
    }

    @XmlElement(required = true, name = "system")
    public URI getSystemURI() {
        return systemURI;
    }

    public void setSystemURI(URI systemURI) {
        this.systemURI = systemURI;
    }

}
