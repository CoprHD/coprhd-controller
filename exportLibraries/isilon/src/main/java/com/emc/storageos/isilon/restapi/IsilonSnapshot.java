/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.isilon.restapi;

/*
* Class representing the isilon snapshot object
* member names should match the key names in json object
*/
public class IsilonSnapshot {
    private String  id;
    private String  name;
    private String  schedule;
    private String  timestamp;  /* timestamp */
    private String  expires;    /* timestamp */
    private String  path;
    private String  size;       /* bytes as int */
    private String  pct_filesystem; /* <float 0.0 - 100.0> */
    private String  pct_reserve;    /* <float 0.0 - 100.0> */
    private String  alias;
    private String  alias_target;   /* int */
    private Boolean has_locks;

    public IsilonSnapshot() {    }

    /**
     * Constructor 
     * @param n    Name
     * @param p    Path to snapshot
     * @param a    Alias
     * @param exp  Expiration
     */
    public IsilonSnapshot(String n, String p, String a, String exp) {
        name = n;
        path = p;
        if (a != null && !a.isEmpty()) {
            alias = a;
        }
        if (exp != null && !exp.isEmpty()) {
            expires = exp;
        }
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String newName) {
        name = newName;
    }

    /* To do  - get/set timestamp for expires */
    public void setTimestamp() {

    }
    
    public String getId() {
        return id;
    }
    
    public String getPath() {
        return path;
    }
    
    public String getTimestamp() {
        return timestamp;
    }

    public String getSize() {
        return size;
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(" Snapshot( Name: " + name);
        str.append(" , id: " + id);
        str.append(" , path: " + path);
        str.append((timestamp != null)?" , timestamp: " + timestamp: "");
        str.append(")");
        return str.toString();
    }
}
