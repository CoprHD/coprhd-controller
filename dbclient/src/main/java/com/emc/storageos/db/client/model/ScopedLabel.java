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

import org.codehaus.jackson.annotate.JsonValue;

import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlValue;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * String label with namespace/scope
 */
public class ScopedLabel {
    private static final String UTF8_ENCODING = "UTF-8";

    private String _scope;
    private String _label;

    public ScopedLabel() {
    }

    public ScopedLabel(String scope, String label) {
        _scope = scope;
        _label = label;
    }

    @JsonValue
    @XmlValue
    public String getLabel() {
        return _label;
    }

    public void setLabel(String label) {
        _label = label;
    }

    @XmlTransient
    public String getScope() {
        return _scope;
    }

    public void setScope(String scope) {
        _scope = scope;
    }

    public static ScopedLabel fromString(String val) {
        if (val == null) {
            throw new IllegalArgumentException();
        }
        int lastIndex = val.lastIndexOf(':');

        try {
            if (lastIndex == -1) {
                return new ScopedLabel(null, URLDecoder.decode(val, UTF8_ENCODING));
            } else {
                return new ScopedLabel(URLDecoder.decode(val.substring(0, lastIndex)),
                        URLDecoder.decode(val.substring(lastIndex + 1), UTF8_ENCODING));
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        try {
            if (_scope == null) {
                return URLEncoder.encode(_label, UTF8_ENCODING);
            } else {
                return String.format("%1$s:%2$s", URLEncoder.encode(_scope.toString(), UTF8_ENCODING),
                        URLEncoder.encode(_label, UTF8_ENCODING));
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj.toString().equals(toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
