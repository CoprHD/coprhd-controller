/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Object service namespace information
 */
@Cf("ObjectBaseUrl")
@XmlRootElement(name = "object_base_url")
public class ObjectBaseUrl extends DataObject {

    private String _baseUrl;

    private Boolean _namespaceInHost;

    @XmlElement(name = "is_namespace_in_host")
    @Name("namespaceInHost")
    public Boolean getNamespaceInHost() {
        return _namespaceInHost;
    }

    public void setNamespaceInHost(Boolean _namespaceInHost) {
        this._namespaceInHost = _namespaceInHost;
        setChanged("namespaceInHost");
    }

    /**
     * Base URL for the specific api type
     * @return
     */
    @XmlElement
    @Name("url")
    public String getBaseUrl(){
        return _baseUrl;
    }

    public void setBaseUrl(String url) {
        _baseUrl = url;
        setChanged("url");
    }
}
