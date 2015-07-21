/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import org.codehaus.jackson.annotate.JsonValue;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * URI with user provided label
 */
public class NamedURI {
    private static final String UTF8_ENCODING = "UTF-8";

    private URI _reference;
    private String _name;

    public NamedURI() {
    }

    public NamedURI(URI reference, String name) {
        _reference = reference;
        _name = name;
    }

    @JsonValue
    @XmlValue
    public URI getURI() {
        return _reference;
    }

    public void setURI(URI reference) {
        _reference = reference;
    }

    @XmlTransient
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public static NamedURI fromString(String val) {
        if (val == null) {
            throw new IllegalArgumentException();
        }
        int lastIndex = val.lastIndexOf(':');
        if (lastIndex == -1) {
            throw new IllegalArgumentException(val);
        }
        try {
            return new NamedURI(URI.create(val.substring(0, lastIndex)),
                    URLDecoder.decode(val.substring(lastIndex + 1), UTF8_ENCODING));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public String toString() {
        try {
            if (_name == null) {
                return _reference.toString();
            } else {
                return String.format("%1$s:%2$s", _reference.toString(), URLEncoder.encode(_name, UTF8_ENCODING));
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean equals(Object target) {
        return toString().equals(target.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
