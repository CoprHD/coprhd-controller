/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.simulators.db.model;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.isilon.restapi.IsilonExport;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class of export data object
 */
@XmlRootElement(name = "Export")
public class Export extends AbstractSerializableNestedObject {
    private static final String ID = "id";
    private static final String COMMENT = "comment";
    private static final String PATHS = "paths";
    private static final String CLIENTS = "clients";
    private static final String READ_ONLY = "read_only";
    private static final String MAP_ALL_USER = "map_all_user";
    private static final String MAP_ALL_GROUPS = "map_all_group";
    private static final String MAP_ROOT_USER = "map_root_user";
    private static final String MAP_ROOT_GROUPS = "map_root_group";
    private static final String SECURITY_FLAVORS = "security_flavors";

    @XmlElement
    public String getId() {
        return getStringField(ID);
    }

    public void setId(String id) {
        if (id != null)
            setField(ID, id);
    }

    @XmlElement
    public String getComment() {
        return getStringField(COMMENT);
    }

    public void setComment(String comment) {
        if (comment != null)
            setField(COMMENT, comment);
    }

    @XmlElement
    public List<String> getPaths() {
        return getListOfStringsField(PATHS);
    }

    public void setPaths(List<String> paths) {
        if (paths != null)
            setListOfStringsField(PATHS, paths);
    }

    @XmlElement
    public List<String> getClients() {
        return getListOfStringsField(CLIENTS);
    }

    public void setClients(List<String> clients) {
        if (clients != null)
            setListOfStringsField(CLIENTS, clients);
    }

    @XmlElement
    public boolean getRead_only() {
        return getBooleanField(READ_ONLY);
    }

    public void setRead_only(boolean read_only) {
        setField(READ_ONLY, read_only);
    }

    @XmlElement
    public IsilonExport.IsilonIdentity getMap_all() {
        String user = getStringField(MAP_ALL_USER);
        List<String> grouplist = getListOfStringsField(MAP_ALL_GROUPS);

        if (user == null && grouplist.size() == 0)
            return null;

        return new IsilonExport.IsilonIdentity(user, new ArrayList<String>(grouplist));
    }

    public void setMap_all(IsilonExport.IsilonIdentity map_all) {
        if (map_all != null) {
            setField(MAP_ALL_USER, map_all.getUser());

            List<String> arr = null; //Arrays.asList(map_all.getGroups());
            setListOfStringsField(MAP_ALL_GROUPS, arr);
        }
    }

    @XmlElement
    public IsilonExport.IsilonIdentity getMap_root() {
        String user = getStringField(MAP_ROOT_USER);
        List<String> grouplist = getListOfStringsField(MAP_ROOT_GROUPS);
        String[] groups = grouplist.toArray(new String[grouplist.size()]);

        if (user == null && groups.length == 0)
            return null;

       // return new IsilonExport.IsilonIdentity(user, groups);
        return null;
    }

    public void setMap_root(IsilonExport.IsilonIdentity map_root) {
        if (map_root != null) {
            setField(MAP_ROOT_USER, map_root.getUser());

            //List<String> arr = Arrays.asList(map_root.getGroups());
            List<String> arr = null;
            setListOfStringsField(MAP_ROOT_GROUPS, arr);
        }
    }

    @XmlElement
    public List<String> getSecurityFlavors() {
        return getListOfStringsField(SECURITY_FLAVORS);
    }

    public void setSecurityFlavors(List<String> securityFlavors) {
        if (securityFlavors != null)
            setListOfStringsField(SECURITY_FLAVORS, securityFlavors);
    }

    /**
     * Builder for IsilonExport
     * @return
     */
    public IsilonExport build() {
        IsilonExport export = new IsilonExport();

        if (this.getId() != null)
            export.setId(Integer.parseInt(this.getId()));
        if (this.getClients() != null)
            export.setClients((ArrayList<String>)this.getClients());
        if (this.getComment() != null)
            export.setComment(this.getComment());
        if (this.getMap_all() != null)
            export.setMapAll(this.getMap_all().getUser());
        if (this.getMap_root() != null)
            export.setMapRoot(this.getMap_root().getUser());
        if (this.getPaths() != null)
            export.setPaths((ArrayList<String>)this.getPaths());
        export.setReadOnly();
        if (this.getSecurityFlavors() != null)
            export.setSecurityFlavors((ArrayList<String>)this.getSecurityFlavors());

        return export;
    }
}
