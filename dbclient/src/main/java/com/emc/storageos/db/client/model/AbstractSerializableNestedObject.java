/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;


import org.omg.CosNaming.IstringHelper;

import javax.xml.bind.annotation.XmlTransient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.*;


/**
 * Abstract base for nested types.  Uses simple Java properties serialization format
 * to de/serialize nested objects from/to byte[].
 *
 * Note that we only support 'flat' nested types currently - this means nested types may contain
 * only primitive fields and no further nesting of other complex types is supported.
 */
public abstract class AbstractSerializableNestedObject{
    /**
     * 
     */
    private Properties _field;

    public AbstractSerializableNestedObject() {
        _field = new Properties();
    }

    protected void setField(String key, String val) {
        _field.setProperty(key, val);
    }

    protected void setField(String key, int val) {
        _field.setProperty(key, Integer.toString(val));
    }

    protected void setField(String key, long val) {
        _field.setProperty(key, Long.toString(val));
    }

    protected void setField(String key, boolean val) {
        _field.setProperty(key, Boolean.toString(val));
    }

    protected void setField(String key, Calendar val) {
        setField(key, val.getTimeInMillis());
    }

    protected void setListOfStringsField(String key, List<String> val) {
        StringBuffer buffer = new StringBuffer();

        if (val == null) {
            val = new ArrayList<String>();
        }
        Iterator<String> it = val.iterator();
        while (it.hasNext()) {
            buffer.append(it.next());
            if (it.hasNext()) {
                buffer.append(",");
            }
        }

        _field.setProperty(key, buffer.toString());
    }

    protected List<String> getListOfStringsField(String key) {
       List<String> result = new ArrayList<String>();
       String val = _field.getProperty(key);
       if (val == null) {
           return result;
       }
       StringTokenizer tokenizer = new StringTokenizer(val, ",");
       while (tokenizer.hasMoreElements()) {
           result.add(tokenizer.nextToken());
       }
       return result;
    }

    protected Integer getIntField(String key) {
        String val = _field.getProperty(key);
        if (val == null) {
            return null;
        }
        return Integer.parseInt(val);
    }

    protected Long getLongField(String key) {
        String val = _field.getProperty(key);
        if (val == null) {
            return null;
        }
        return Long.parseLong(val);
    }

    protected Boolean getBooleanField(String key) {
        String val = _field.getProperty(key);
        if (val == null) {
            return null;
        }
        return Boolean.parseBoolean(val);
    }

    protected String getStringField(String key) {
        return _field.getProperty(key);
    }

    protected void setField(String key, URI val) {
        _field.setProperty(key, val.toString());
    }

    protected URI getURIField(String key) {
        String val = _field.getProperty(key);
        if (val == null) {
            return null;
        }
        return URI.create(val);
    }

    protected Calendar getDateField(String key) {
        Long val = getLongField(key);
        if (val == null) {
            return null;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(val);
        return c;
    }

    /**
     * Serializes this nested object into byte[]
     *
     * @return
     */
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            _field.store(out, null);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Deserializes byte[]
     *
     * @param content
     */
    public void loadBytes(byte[] content) {
        try {
            _field = new Properties();
            _field.load(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Needed for use in generic Set
     *
     * @param obj
     * @return
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AbstractSerializableNestedObject)) {
            return false;
        }
        return _field.equals(((AbstractSerializableNestedObject)obj)._field);
    }

    @Override
    public int hashCode(){
    	return _field.hashCode();
    }
}
