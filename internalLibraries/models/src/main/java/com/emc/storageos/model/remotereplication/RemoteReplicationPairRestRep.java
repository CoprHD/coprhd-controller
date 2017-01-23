package com.emc.storageos.model.remotereplication;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

import java.net.URI;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "remote_replication_pair")
public class RemoteReplicationPairRestRep extends DataObjectRestRep {

    // native id of this pair
    private String nativeId;

    // Element type (block or file element)
    private String elementType;

    // If replication pair is part of replication group should be set to replication group link, otherwise null.
    private RelatedResourceRep replicationGroup;

    // Either direct replication set parent or replication set of the replication group parent.
    private RelatedResourceRep replicationSet;

    // Replication mode of this pair.
    private String replicationMode;

    // Replication state of this pair.
    String replicationState;

    // Replication pair source element.
    private URI sourceElement;

    // Replication pair target element.
    private URI targetElement;

    @XmlElement(name = "native_id")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    @XmlElement(name = "element_type")
    public String getElementType() {
        return elementType;
    }

    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    @XmlElement(name = "replication_group")
    public RelatedResourceRep getReplicationGroup() {
        return replicationGroup;
    }

    public void setReplicationGroup(RelatedResourceRep replicationGroup) {
        this.replicationGroup = replicationGroup;
    }

    @XmlElement(name = "replication_set")
    public RelatedResourceRep getReplicationSet() {
        return replicationSet;
    }

    public void setReplicationSet(RelatedResourceRep replicationSet) {
        this.replicationSet = replicationSet;
    }

    @XmlElement(name = "replication_state")
    public String getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(String replicationState) {
        this.replicationState = replicationState;
    }

    @XmlElement(name = "source_element")
    public URI getSourceElement() {
        return sourceElement;
    }

    public void setSourceElement(URI sourceElement) {
        this.sourceElement = sourceElement;
    }

    @XmlElement(name = "target_element")
    public URI getTargetElement() {
        return targetElement;
    }

    public void setTargetElement(URI targetElement) {
        this.targetElement = targetElement;
    }

    @XmlElement(name = "replication_mode")
    public String getReplicationMode() {
        return replicationMode;
    }

    public void setReplicationMode(String replicationMode) {
        this.replicationMode = replicationMode;
    }
}
