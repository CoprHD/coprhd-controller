/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("ComputeElementHBA")
public class ComputeElementHBA extends DiscoveredDataObject {

    private URI _computeElement;
    private URI _host;
    private URI _serviceProfileTemplate;

    private String _dn;
    private String _port;
    private String _node;
    private String _protocol;

    private String _vsanId;
    private String _templateName;

    @RelationIndex(cf = "ComputeRelationIndex", type = ComputeElement.class)
    @Name("computeElement")
    public URI getComputeElement() {
        return _computeElement;
    }

    public void setComputeElement(URI _computeElement) {
        this._computeElement = _computeElement;
        setChanged("computeElement");
    }

    @RelationIndex(cf = "ComputeRelationIndex", type = Host.class)
    @Name("host")
    public URI getHost() {
        return _host;
    }

    public void setHost(URI host) {
        this._host = host;
        setChanged("host");
    }

    @RelationIndex(cf = "ComputeRelationIndex", type = UCSServiceProfileTemplate.class)
    @Name("serviceProfileTemplate")
    public URI getServiceProfileTemplate() {
        return this._serviceProfileTemplate;
    }

    public void setServiceProfileTemplate(URI serviceProfileTemplate) {
        this._serviceProfileTemplate = serviceProfileTemplate;
        setChanged("serviceProfileTemplate");
    }

    @Name("dn")
    public String getDn() {
        return _dn;
    }

    public void setDn(String dn) {
        this._dn = dn;
        setChanged("dn");
    }

    @Name("port")
    public String getPort() {
        return _port;
    }

    public void setPort(String _port) {
        this._port = _port;
        setChanged("port");
    }

    @Name("node")
    public String getNode() {
        return _node;
    }

    public void setNode(String _node) {
        this._node = _node;
        setChanged("node");
    }

    @Name("protocol")
    public String getProtocol() {
        return _protocol;
    }

    public void setProtocol(String _protocol) {
        this._protocol = _protocol;
        setChanged("protocol");
    }

    @Name("vsanId")
    public String getVsanId() {
        return _vsanId;
    }

    public void setVsanId(String vsanId) {
        this._vsanId = vsanId;
        setChanged("vsanId");
    }

    @Name("templateName")
    public String getTemplateName() {
        return _templateName;
    }

    public void setTemplateName(String _templateName) {
        this._templateName = _templateName;
        setChanged("templateName");
    }

}
