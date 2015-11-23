package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("VVol")
public class VVol extends DataObject{

    private URI protocolEndpoint;
    
    private String vVolSecondaryId;
    
    private StringMap extensions;

    @RelationIndex(cf = "RelationIndex", type = ProtocolEndpoint.class)
    @IndexByKey
    @Name("protocolEndpoint")
    public URI getProtocolEndpoint() {
        return protocolEndpoint;
    }

    public void setProtocolEndpoint(URI protocolEndpoint) {
        this.protocolEndpoint = protocolEndpoint;
        setChanged("protocolEndpoint");
    }

    @Name("vVolSecondaryId")
    public String getvVolSecondaryId() {
        return vVolSecondaryId;
    }

    public void setvVolSecondaryId(String vVolSecondaryId) {
        this.vVolSecondaryId = vVolSecondaryId;
        setChanged("vVolSecondaryId");
    }

    @Name("extensions")
    public StringMap getExtensions() {
        return extensions;
    }

    public void setExtensions(StringMap extensions) {
        this.extensions = extensions;
        setChanged("extensions");
    }
    
    
}
