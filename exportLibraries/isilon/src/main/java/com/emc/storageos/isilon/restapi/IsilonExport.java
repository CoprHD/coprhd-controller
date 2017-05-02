/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

import java.util.ArrayList;
import java.util.List;

/*
 * Class representing the isilon export object
 * member names should match the key names in json object
 */

@SuppressWarnings({ "squid:S00100" })
/*
 * Isilon API return with json fields has underline.
 */
public class IsilonExport {

    public static class IsilonIdentity {
        private String user;
        private ArrayList<String> groups;

        public IsilonIdentity() {
        }

        public IsilonIdentity(String u, ArrayList<String> grps) {
            user = u;
            groups = grps;
        }

        public String toString() {
            return "Identity (user: " + user + ", groups: " + groups + ")";
        }

        public String getUser() {
            return user;
        }

        public ArrayList<String> getGroups() {
            return groups;
        }
    }

    private Integer id;
    private String description;
    private ArrayList<String> paths;
    private ArrayList<String> conflicting_paths;

    private ArrayList<String> clients;
    private ArrayList<String> root_clients;
    private ArrayList<String> read_only_clients;
    private ArrayList<String> read_write_clients;
    private ArrayList<String> unresolved_clients;

    private boolean all_dirs;
    private boolean read_only;
    private boolean map_lookup_uid;
    private boolean return_32bit_file_ids;
    private IsilonIdentity map_all;
    private IsilonIdentity map_root;

    private ArrayList<String> security_flavors;   // security type

    public IsilonExport() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public ArrayList<String> getPaths() {
        return paths;
    }

    public ArrayList<String> getClients() {
        return clients;
    }

    public ArrayList<String> getRootClients() {
        return root_clients;
    }

    public ArrayList<String> getReadOnlyClients() {
        return read_only_clients;
    }

    public void setClients(ArrayList<String> clients) {
        this.clients = clients;
    }

    public void setRootClients(ArrayList<String> rootClients) {
        this.root_clients = rootClients;
    }

    public void setReadOnlyClients(ArrayList<String> readOnlyClients) {
        this.read_only_clients = readOnlyClients;
    }

    public void setReadWriteClients(ArrayList<String> readWriteClients) {
        this.read_write_clients = readWriteClients;
    }

    public ArrayList<String> getReadWriteClients() {
        return read_write_clients;
    }

    public void setPaths(ArrayList<String> paths) {
        this.paths = paths;
    }

    public void addPath(String path) {
        if (paths == null) {
            paths = new ArrayList<String>();
        }
        paths.add(path);
    }

    public void addClients(List<String> endpoints) {
        if (clients == null) {
            clients = new ArrayList<String>();
        }
        clients.addAll(endpoints);
    }

    public void addClient(String endpoint) {
        if (clients == null) {
            clients = new ArrayList<String>();
        }
        clients.add(endpoint);
    }

    public void addRootClient(String client) {
        if (root_clients == null) {
            root_clients = new ArrayList<String>();
        }
        root_clients.add(client);
    }

    public void addRootClients(List<String> endpoints) {
        if (root_clients == null) {
            root_clients = new ArrayList<String>();
        }
        root_clients.addAll(endpoints);
    }

    public void addReadWriteClients(List<String> endpoints) {
        if (read_write_clients == null) {
            read_write_clients = new ArrayList<String>();
        }
        read_write_clients.addAll(endpoints);
    }

    public void addReadOnlyClients(List<String> endpoints) {
        if (read_only_clients == null) {
            read_only_clients = new ArrayList<String>();
        }
        read_only_clients.addAll(endpoints);
    }

    public void setAllDirs() {
        all_dirs = true;
    }

    public boolean getAllDirs() {
        return all_dirs;
    }

    public void resetAllDirs() {
        all_dirs = false;
    }

    public void setReadOnly() {
        read_only = true;
    }

    public boolean getReadOnly() {
        return read_only;
    }

    public void resetReadOnly() {
        read_only = false;
    }

    // for now, fix this, till we figure out how to deal with this
    public void setMapRoot(String user) {
        ArrayList<String> groups = new ArrayList<String>();
        map_root = new IsilonIdentity(user, groups);
    }

    public IsilonIdentity getMap_root() {
        return map_root;
    }

    public void setMapAll(String user) {
        ArrayList<String> groups = new ArrayList<String>();
        map_all = new IsilonIdentity(user, groups);
    }

    public IsilonIdentity getMap_all() {
        return map_all;
    }

    public void setComment(String comm) {
        description = new String(comm);
    }

    public String getComment() {
        return description;
    }

    public ArrayList<String> getSecurityFlavors() {
        return security_flavors;
    }

    public void setSecurityFlavors(ArrayList<String> securityFlavors) {
        this.security_flavors = securityFlavors;
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("Export (id: " + id);
        str.append(", description: " + description);
        str.append(", paths: " + paths);
        str.append(", clients: " + clients);
        str.append(", read only clients :" + read_only_clients);
        str.append(", rw clients :" + read_write_clients);
        str.append(", root clients :" + root_clients);
        str.append(", security: " + security_flavors);
        str.append(", read_only: " + read_only);
        str.append(", all_dirs: " + all_dirs);
        str.append(", map_root: " + ((map_root != null) ? map_root.toString() : ""));
        str.append(", map_all: " + ((map_all != null) ? map_all.toString() : ""));
        str.append(")");
        return str.toString();
    }

    public boolean isMap_lookup_uid() {
        return map_lookup_uid;
    }

    public void setMap_lookup_uid(boolean map_lookup_uid) {
        this.map_lookup_uid = map_lookup_uid;
    }

    public boolean isReturn_32bit_file_ids() {
        return return_32bit_file_ids;
    }

    public void setReturn_32bit_file_ids(boolean return_32bit_file_ids) {
        this.return_32bit_file_ids = return_32bit_file_ids;
    }
}
