/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cimadapter.connections.cim;

// Java imports
import java.util.Stack;

// CIM imports
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;

/**
 * Simple string representation of a CIM object path that is intended to serve
 * as a unique id for the referenced CIM instance within its CIMOM (also
 * referenced in the path).
 * 
 * A CIM object path is a unique identifier for a CIM instance, but it's hard to
 * read when displayed and cumbersome to work with when it comes to basic event
 * processing. It is essentially a URL with a set of name -value pairs attached.
 * 
 * Example CIM Object Path:
 * //172.23.120.60/root/emc:Clar_DiskDrive.CreationClassName="Clar_D
 * iskDrive",DeviceID="CLARiiON+2_0_2",SystemCreationClassName="Clar
 * _StorageSystem",SystemName="CLARiiON+FNM00083700232"
 * 
 * This class concatenates the identifiers -- any key that does not look like a
 * class name -- to form a character-delimited string that serves as a unique id
 * for the CIM instance within its CIMOM.
 * 
 * Example Composite id (derived from the object path above):
 * CLARiiON+FNM00083700232/CLARiiON+2_0_2
 */
public class CimCompositeID {

    // The unique Id formed from the CIM object path.
    private String _value;

    // The delimiter for components that compose the Id.
    static private final char DELIMITER = '/';

    /**
     * Constructs a CIMCompositeID for the given CIM object path.
     * 
     * @param path The CIM object path.
     */
    public CimCompositeID(CIMObjectPath path) {
        _value = parsePath(path);
    }

    @Override
    /**
     * @{inheritDoc}
     */
    public String toString() {
        return _value;
    }

    @Override
    /**
     * @{inheritDoc}
     */
    public boolean equals(Object obj) {
        return _value.equals(obj);
    }

    @Override
    /**
     * @{inheritDoc}
     */
    public int hashCode() {
        return _value.hashCode();
    }

    /**
     * Constructs a composite Id string from the given CIM object path.
     * 
     * @param path The CIM object path.
     * 
     * @return The composite Id string.
     */
    public static String parsePath(CIMObjectPath path) {
        StringBuilder id = new StringBuilder();
        Stack<String> tokens = new Stack<String>();
        for (CIMProperty<?> p : path.getKeys()) {
            // Skip keys that are class names. Assume that
            // the remaining keys are identifiers and push
            // them onto the stack in order. This assumes
            // that identifiers are sorted from bottom to
            // top level.
            if (p.getName().endsWith(CimConstants.CLASS_NAME_KEY)) {
                continue;
            }
            tokens.push(p.getValue().toString());
        }

        while (!tokens.empty()) {
            id.append(DELIMITER);
            id.append(tokens.pop());
        }

        // Trim leading delimiter
        if (id.length() > 1) {
            return id.toString().substring(1);
        }

        return id.toString();
    }
}