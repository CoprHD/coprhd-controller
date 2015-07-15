/*
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

package com.emc.storageos.simulators.impl;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.isilon.restapi.IsilonExport;
import com.emc.storageos.isilon.restapi.IsilonSmartQuota;
import com.emc.storageos.simulators.ObjectStore;
import com.emc.storageos.simulators.db.constraint.SimulatorAlternateIdConstraint;
import com.emc.storageos.simulators.db.constraint.SimulatorContainmentConstraint;
import com.emc.storageos.simulators.db.impl.SimulatorDbClient;
import com.emc.storageos.simulators.db.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation for the db based object store
 */

public class ObjectStoreImplDb implements ObjectStore {
    private static Logger _log = LoggerFactory.getLogger(ObjectStoreImplDb.class);
    // empty uri for initial quota in directory
    public static URI _emptyURI;
    private static final int usage = 4096;
    private SimulatorDbClient _dbClient;

    /**
     * Default constructor
     */
    public ObjectStoreImplDb() {
        _emptyURI = URI.create("null");
    }

    public void setDbClient(SimulatorDbClient dbClient) {
        _dbClient = dbClient;    
    }

    public SimulatorDbClient getDbClient() {
        return _dbClient;
    }

    // used to generate unique id for each object created
    // FIX ME - this needs to be populated from db - for now seed it off timestamp
    private AtomicLong nextId = new AtomicLong(System.currentTimeMillis());
    private AtomicInteger nextExportId = new AtomicInteger();

    /**
     * Get next unique id
     *
     * @return
     */
    private String getUniqueId() {
        return Long.toString(nextId.getAndIncrement());
    }

    /**
     * Create uri
     *
     * @param prefix    prefix string
     * @param id        unique id
     * @return          uri
     */
    private URI createURI(String prefix, String id) {
        return URI.create(String.format("urn:" + prefix + ":%1$s", id));
    }

    /**
     * Get Id from URI by removing the prefix
     *
     * @param prefix    prefix string
     * @param uri       uri
     * @return          unique id
     */
    private String getIdInURI(String prefix, URI uri) {
        if (uri == null || !uri.toString().startsWith("urn:" + prefix + ":"))
            return "";
            
        int startIndex = ("urn:" + prefix + ":").length();

        return uri.toString().substring(startIndex);
    }

    @Override
    public boolean checkPath(String s) {
        boolean bRet = false;
        try {
            bRet = getDbClient().queryObject(Directory.class, createURI("dir", s)) != null;
        } catch (Exception e) {
            _log.error("checkPath: query exception ", e);
        }

        return bRet;
    }

    @Override
    public boolean checkQuotaExist(String s, boolean recursive) {
        SimulatorDbClient dbClient = getDbClient();
        Directory curObj = new Directory();

        try {
            URI uri = createURI("dir", s);
            curObj = dbClient.queryObject(Directory.class, uri);
            if (curObj == null)
                return false;

            if (!_emptyURI.equals(curObj.getQuota())) {
                return true;
            }

            if (recursive) {
                List<URI> fs = dbClient.queryByConstraint(SimulatorContainmentConstraint.Factory.getDirectoryByParentConstraint(uri));
                for (URI u : fs) {
                    boolean bRet = checkQuotaExist(getIdInURI("dir", u), recursive);
                    if (bRet) {
                        return true;
                    }
                }
            } else {
                // Not reasonable to do a non-recursive deletion
            }
        } catch (Exception e) {
            _log.error("check quota exist exception", e);
        }

        return false;
    }

    @Override
    public ArrayList<String> listDir(String dir) {
        ArrayList<String> ret = new ArrayList<String>();
        String trimDir = trimPath(dir);

        URI uri = createURI("dir", dir);
        List<URI> fs = null;
        try {
            fs = getDbClient().queryByConstraint(SimulatorContainmentConstraint.Factory.getDirectoryByParentConstraint(uri));
            for (URI u : fs) {
                if (checkPath(getIdInURI("dir", u)))
                    ret.add(getDirNameByPath(getIdInURI("dir", u)));
            }
        } catch (Exception e) {
            _log.error("listDir: query exception ", e);
        }

        return ret;
    }

    @Override
    public void createDir(String dir, boolean recursive) throws Exception {
        DbClient dbClient = getDbClient();

        // check existence
        if (dbClient.queryObject(Directory.class, createURI("dir", dir)) != null) {
            return;
        }

        if (!(trimPath(dir).startsWith("ifs"))) {
            throw new Exception("invalid directory name");
        }

        Directory curDirObj = null;
        String current = dir;
        String parent = getParent(dir);
        if (recursive) {
            // create all paths recursively
            while (parent.compareTo(dir) != 0 && !checkPath(parent)) {
                // Insert child itself first
                curDirObj = new Directory();
                curDirObj.setId(createURI("dir", current));
                curDirObj.setParent(createURI("dir", parent));
                curDirObj.setQuota(_emptyURI);
                dbClient.persistObject(curDirObj);

                // roll to its parent
                current = parent;
                parent = getParent(current);
            }

            // update parent URI info to child
            curDirObj = new Directory();
            curDirObj.setId(createURI("dir", current));
            curDirObj.setParent(createURI("dir", parent));
            curDirObj.setQuota(_emptyURI);
            dbClient.persistObject(curDirObj);
        } else {
            // parent must exist, or throw
            if (parent.compareTo(trimPath(dir)) != 0 && !checkPath(parent)) {
                throw new Exception("parent doesn't exist");
            }

            // update parent URI info to child
            curDirObj = new Directory();
            curDirObj.setId(createURI("dir", current));
            curDirObj.setParent(createURI("dir", parent));
            curDirObj.setQuota(_emptyURI);
            dbClient.persistObject(curDirObj);
        }
    }

    @Override
    public void deleteDir(String dir, boolean recursive) throws Exception {
        SimulatorDbClient dbClient = getDbClient();
        Directory curObj = new Directory();

        URI uri = createURI("dir", dir);
        curObj = dbClient.queryObject(Directory.class, uri);
        if (curObj == null)
            return;

        List<URI> fs = dbClient.queryByConstraint(SimulatorContainmentConstraint.Factory.getDirectoryByParentConstraint(uri));
        for (URI u : fs) {
            if (recursive) {
                deleteDir(getIdInURI("dir", u), recursive);
            } else {
                if (checkPath(getIdInURI("dir", u)))
                    throw new Exception("Sub directories exist, cannot delete dir.");
            }
        }

        dbClient.removeObject(curObj);
    }


    @Override
    public String createQuota(String path, long limit) throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        String dir = trimPath(path);
        String id = getUniqueId();
        URI uri;
        Directory directoryDataObject;

        directoryDataObject = dbClient.queryObject(Directory.class, createURI("dir", dir));
        if (directoryDataObject == null)
            throw new Exception("Directory does not exist");

        if (!_emptyURI.equals(directoryDataObject.getQuota()))
            throw new Exception("quota already set");

        // insert quota info to db
        Quota quota = new Quota(id, limit, 0, directoryDataObject.getId());
        dbClient.persistQuotaIndex(quota);

        // update corresponding directory quota info
        uri = createURI("quota", id);
        directoryDataObject.setQuota(uri);
        dbClient.persistObject(directoryDataObject);

        return id;
    }

    @Override
    public void deleteQuota(String id) throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        URI uri = createURI("quota", id);
        Quota quota = dbClient.queryQuotaIndex(uri);
        if (quota == null)
            throw new Exception("Not found");

        // delete quota
        dbClient.deleteQuotaIndex(uri);

        // update corresponding directory's quota info
        List<URI> fs = dbClient.queryByConstraint(
                SimulatorAlternateIdConstraint.Factory.getDirectoryIdConstraint(uri.toString()));
        for (URI u : fs) {
            Directory directoryDataObject =
                    dbClient.queryObject(Directory.class, u);
            if (directoryDataObject == null)
                throw new Exception("Directory does not exist.");
            directoryDataObject.setQuota(_emptyURI);
            dbClient.persistObject(directoryDataObject);
        }
    }

    @Override
    public String createExport(IsilonExport export) throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        Export exportObject = new Export();
        String id = Integer.toString(nextExportId.getAndIncrement());
        exportObject.setId(id);
        exportObject.setClients(export.getClients());
        exportObject.setComment(export.getComment());
        exportObject.setPaths(export.getPaths());
        exportObject.setMap_all(export.getMap_all());
        exportObject.setMap_root(export.getMap_root());
        exportObject.setRead_only(export.getReadOnly());
        exportObject.setSecurityFlavors(export.getSecurityFlavors());

        dbClient.persistExport(exportObject);

        return id;
    }

    @Override
    public IsilonExport getExport(String id) throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        Export export = dbClient.queryExport(createURI("export", id));
        return export.build();
    }

    @Override
    public void deleteExport(String id) throws Exception {
        SimulatorDbClient dbClient = getDbClient();
        URI uri = createURI("export", id);

        // check if exists
        Export export = dbClient.queryExport(uri);
        if (export == null)
            throw new Exception("export does not exist");

        // remove
        dbClient.deleteExport(uri);
    }

    @Override
    public void modifyExport(String id, IsilonExport export) throws Exception {
        SimulatorDbClient dbClient = getDbClient();
        Export exportObject = dbClient.queryExport(createURI("export", id));

        // modify fields if not null
        if (export.getClients() != null)
            exportObject.setClients(export.getClients());
        if (export.getComment() != null)
            exportObject.setComment(export.getComment());
        if (export.getPaths() != null)
            exportObject.setPaths(export.getPaths());
        if (export.getMap_all() != null)
            exportObject.setMap_all(export.getMap_all());
        if (export.getMap_root() != null)
            exportObject.setMap_root(export.getMap_root());
        if (export.getSecurityFlavors() != null)
            exportObject.setSecurityFlavors(export.getSecurityFlavors());
        exportObject.setRead_only(export.getReadOnly());

        dbClient.persistExport(exportObject);
    }

    @Override
    public String createSnapshot(String name, String path) throws Exception {
        DbClient dbClient = getDbClient();
        String dir = trimPath(path);
        String id;
        URI pathUri;

        // check to see if directory exist or not
        URI dirURI;
        Directory directory = dbClient.queryObject(Directory.class, dirURI = createURI("dir", dir));
        if (directory == null)
            throw new Exception("path not found");

        // put to db
        Snapshot snapshot = new Snapshot();
        snapshot.setDirectory(dirURI);
        id = getUniqueId();
        snapshot.setId(createURI("snap", id));
        snapshot.setPath(pathUri = getSnapPath(name, dir));
        dbClient.persistObject(snapshot);

        // create snapshot directory
        createDir(pathUri.toString(), true);

        return id;
    }

    @Override
    public void deleteSnapshot(String id) throws Exception {
        DbClient dbClient = getDbClient();

        // check if exists
        Snapshot snapshot = dbClient.queryObject(Snapshot.class, createURI("snap", id));
        if (snapshot == null)
            throw new Exception("snapshot does not exist");

        // check if directory exits
        Directory directory = dbClient.queryObject(Directory.class, snapshot.getDirectory());
        if (directory == null)
            throw new Exception("directory does not exist");

        // remove
        dbClient.removeObject(snapshot);
    }

    @Override
    public IsilonSmartQuota getQuota(String id) throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        // get quota object
        Quota quota = dbClient.queryQuotaIndex(createURI("quota", id));
        if (quota == null)
            throw new Exception("Quota not found: " + createURI("quota", id));
        Directory directory = dbClient.queryObject(Directory.class, quota.getDirectory());
        if (directory == null)
            throw new Exception("Path not found");

        IsilonSmartQuota isilonSmartQuota = new IsilonSmartQuota("/" +
                getIdInURI("dir", quota.getDirectory()), quota.getLimit());
        isilonSmartQuota.setUsage(usage, 0, 0);
        isilonSmartQuota.setId(id);

        return isilonSmartQuota;
    }

    @Override
    public ArrayList<IsilonSmartQuota> listQuotas(String start, int num) {
        SimulatorDbClient dbClient = getDbClient();
        ArrayList<IsilonSmartQuota> list = new ArrayList<IsilonSmartQuota>();

        try {
            if (start == null)
                start = "";

            ArrayList<Quota> quotaList = dbClient.queryQuotaIndexByPage(createURI("quota", start), num);
            if (quotaList == null)
                throw new Exception("Quota not found");

            for (int i = 0; i < quotaList.size(); i++) {
                Directory directory = dbClient.queryObject(Directory.class, quotaList.get(i).getDirectory());
                IsilonSmartQuota isilonSmartQuota = new IsilonSmartQuota("/" + getIdInURI("dir", directory.getId()), quotaList.get(i).getLimit());
                isilonSmartQuota.setUsage(usage, 0, 0);
                isilonSmartQuota.setId(quotaList.get(i).getId());
                list.add(isilonSmartQuota);
            }
        } catch (Exception e) {
            _log.error("listQuotas: query exception ", e);
        }

        return list;
    }

    @Override
    public int getDirCount(String dir, boolean recursive) {
        ArrayList<String> ret = new ArrayList<String>();
        String trimDir = trimPath(dir);
        int count = 0;

        URI uri = createURI("dir", dir);
        List<URI> fs = null;
        try {
            fs = getDbClient().queryByConstraint(SimulatorContainmentConstraint.Factory.getDirectoryByParentConstraint(uri));

            for (URI u : fs) {
                if (checkPath(getIdInURI("dir", u))) {
                    count++;
                    if (recursive)
                        count += getDirCount(getIdInURI("dir", u), recursive);
                }
            }
        } catch (Exception e) {
            _log.error("getDirCount: query exception ", e);
        }

        return count;
    }

    /**
     * trimPath - take out leading and trailing / and replace all // with /
     * just a way to create a comparable string representation of the path
     *
     * @param s path to trim
     * @return trimmed path
     */
    private String trimPath(String s) {
        // replace all // with /
        String s1 = s.replace("//", "/");
        int start = 0;
        int end = s1.length();
        if (s1.startsWith("/")) {
            start++;
        }
        if (s1.endsWith("/")) {
            end--;
        }

        String s2 = s1.substring(start, end);
        return s2;
    }

    /**
     * Get parent directory
     *
     * @param s path
     * @return parent directory path
     */
    private String getParent(String s) {
        String st = trimPath(s);
        return st.substring(0, st.lastIndexOf("/"));
    }

    private String getDirNameByPath(String s) {
        if (s == null || "".equals(s))
            return "";

        return s.substring(s.lastIndexOf("/") + 1);
    }

    /**
     * Get snapshot path's uri
     *
     * @param name      snapshot name
     * @param path      snapshot path
     * @return          snapshot uri
     */
    private URI getSnapPath(String name, String path) {
        String prefix = "ifs/";
        return URI.create(String.format("ifs/.snapshot/%1$s/%2$s", name, path.substring(prefix.length())));
    }
}
