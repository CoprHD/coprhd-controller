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

package com.emc.storageos.simulators.impl;

import com.emc.storageos.isilon.restapi.IsilonExport;
import com.emc.storageos.isilon.restapi.IsilonSmartQuota;
import com.emc.storageos.simulators.ObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation for the memory based object store
 */
public class ObjectStoreImpl implements ObjectStore {
    private static Logger _log = LoggerFactory.getLogger(ObjectStoreImpl.class);
    private static final int usage = 4096;

    /**
     * Class representing a directory entry
     */
    private class DirObject {
        /* quota identifier, if one exists */
        private String _quota;
        /* exports map - mapping host to export id*/
        private ConcurrentMap<String, String> _exports;

        public ConcurrentMap<String, String> getExports() {
            return _exports;
        }

        public void setExports(ConcurrentMap<String, String> exports) {
            _exports = exports;
        }

        public String getQuota() {
            return _quota;
        }

        public String toString() {
            return "Empty";
        }
    }

    /**
     * Class representing the quota object
     */
    private class QuotaObject {
        /* path to which this quota applies */
        private String _path;
        /* hard limit for the quota */
        private long _limit;

        public QuotaObject(String path, long limit) {
            _path = path;
            _limit = limit;
        }
    }

    /**
     * Class representing the export object
     */
    public static class ExportObject {
        private Integer             _id;
        private String              _comment;
        private ArrayList<String>   _paths;
        private ArrayList<String>   _clients;
        private boolean             _read_only;
        private IsilonExport.IsilonIdentity _map_all;
        private IsilonExport.IsilonIdentity _map_root;
        private ArrayList<String>   _security_flavors;

        private ExportObject(Integer id, String comment, ArrayList<String> paths,
                             ArrayList<String> clients, boolean read_only,
                             IsilonExport.IsilonIdentity map_all, IsilonExport.IsilonIdentity map_root,
                             ArrayList<String> security_flavors) {
            _id = id;
            _comment = comment;
            _paths = paths;
            _clients = clients;
            _read_only = read_only;
            _map_all = map_all;
            _map_root = map_root;
            _security_flavors = security_flavors;
        }
    }

    // used to generate unique id for each object created
    private AtomicLong nextId = new AtomicLong();

    // map quota id to path and limit
    private ConcurrentMap<String, QuotaObject> _quotas;
    // map export id to export object
    private ConcurrentMap<String, ExportObject> _exports;
    // map snapshot id to path
    private ConcurrentMap<String, String> _snapshots;
    // map path to DirObject
    private ConcurrentMap<String, DirObject> _dirMap;

    /**
     * Default contrsuctor
     */
    private ObjectStoreImpl() {
        _dirMap = new ConcurrentHashMap<String, DirObject>();
        _dirMap.put("ifs", new DirObject());

        _quotas = new ConcurrentHashMap<String, QuotaObject>();
        _exports = new ConcurrentHashMap<String, ExportObject>();
        _snapshots = new ConcurrentHashMap<String, String>();
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
     * Add path to directory map
     *
     * @param s directory path
     */
    private void addPath(String s) {
        _dirMap.put(trimPath(s), new DirObject());
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

    @Override
    public boolean checkPath(String s) {
        return _dirMap.containsKey(trimPath(s));
    }

    @Override
    public boolean checkQuotaExist(String s, boolean recursive) {
        String dirT = trimPath(s);
        if (_dirMap.containsKey(dirT)) {
            if (_dirMap.get(dirT)._quota != null)
                return true;

            ArrayList<String> removed = new ArrayList<String>();
            for (String key : _dirMap.keySet()) {
                if (key.startsWith(dirT + "/")) {
                    removed.add(key);
                }
            }
            if (recursive) {
                for (String key : removed) {
                    if (_dirMap.get(key)._quota != null)
                        return true;
                }
            }
        }

        return false;
    }

    @Override
    public ArrayList<String> listDir(String dir) {
        ArrayList<String> ret = new ArrayList<String>();
        String trimDir = trimPath(dir);
        for (String key : _dirMap.keySet()) {
            if (key.compareTo(trimDir) != 0
                    && key.startsWith(trimDir + "/")
                    && !key.substring(trimDir.length() + 1).contains("/")
                    && !ret.contains(key.substring(trimDir.length() + 1))) {
                ret.add(key.substring(trimDir.length() + 1));
            }
        }
        return ret;
    }

    @Override
    public void createDir(String dir, boolean recursive) throws Exception {

        // if already exists, return
        if (checkPath(dir)) {
            return;
        }

        if (!(trimPath(dir).startsWith("ifs"))) {
            throw new Exception("invalid directory name");
        }

        String parent = getParent(dir);
        if (recursive) {
            // create all paths recursively
            while (parent.compareTo(dir) != 0 && !checkPath(parent)) {
                addPath(parent);
                parent = getParent(parent);
            }
            addPath(dir);
        } else {
            // parent must exist, or throw
            if (parent.compareTo(trimPath(dir)) != 0 && !checkPath(parent)) {
                throw new Exception("parent doesn't exist");
            }
            addPath(dir);
        }
    }

    @Override
    public void deleteDir(String dir, boolean recursive) throws Exception {
        String dirT = trimPath(dir);
        if (_dirMap.containsKey(dirT)) {
            ArrayList<String> removed = new ArrayList<String>();
            for (String key : _dirMap.keySet()) {
                if (key.startsWith(dirT + "/")) {
                    removed.add(key);
                }
            }
            if (recursive) {
                for (String key : removed) {
                    _dirMap.remove(key);
                }
            } else {
                if (removed.size() > 0) {
                    // fail if any sub directories exist
                    throw new Exception("Sub directories exist, cannot delete dir.");
                }
            }
            _dirMap.remove(dirT);
        } else {
            throw new Exception("Not found");
        }
    }

    /**
     * Get next unique id
     *
     * @return
     */
    private String getUniqueId() {
        return Long.toString(nextId.getAndIncrement());
    }

    @Override
    public String createQuota(String path, long limit) throws Exception {
        String dir = trimPath(path);
        if (_dirMap.containsKey(dir)) {
            DirObject obj = _dirMap.get(dir);
            if (obj._quota != null) {
                throw new Exception("quota already set");
            }
            String id = getUniqueId();
            obj._quota = id;
            _quotas.put(id, new QuotaObject(dir, limit));
            return id;
        }
        throw new Exception("path not found");
    }

    @Override
    public void deleteQuota(String id) throws Exception {

        if (_quotas.containsKey(id)) {
            QuotaObject qObj = _quotas.get(id);
            _dirMap.get(qObj._path)._quota = null;
            _quotas.remove(id);
        } else {
            throw new Exception("Not found");
        }
    }

    @Override
    public String createExport(IsilonExport export) throws Exception {
        String id = getUniqueId();

        ExportObject exportObject =
                new ExportObject(export.getId(),
                        export.getComment(),
                        export.getPaths(),
                        export.getClients(),
                        export.getReadOnly(),
                        export.getMap_all(),
                        export.getMap_root(),
                        export.getSecurityFlavors());

        _exports.put(id, exportObject);

        return id;
    }

    @Override
    public IsilonExport getExport(String id) throws Exception {
        ExportObject exportObj = _exports.get(id);

        if (exportObj == null)
            throw new Exception("export not found");

        IsilonExport isilonExport = new IsilonExport();
        isilonExport.setId(Integer.parseInt(id));
        isilonExport.setComment(exportObj._comment);
        isilonExport.setClients(exportObj._clients);
        isilonExport.setPaths(exportObj._paths);
        isilonExport.setReadOnly();
        if (exportObj._map_all != null)
            isilonExport.setMapAll(exportObj._map_all.getUser());
        if (exportObj._map_root != null)
            isilonExport.setMapRoot(exportObj._map_root.getUser());
        isilonExport.setSecurityFlavors(exportObj._security_flavors);

        return isilonExport;
    }

    @Override
    public void deleteExport(String id) throws Exception {
        if (_exports.containsKey(id)) {
            ExportObject exObj = _exports.get(id);
            _exports.remove(id);
        } else {
            throw new Exception("Not found");
        }
    }

    @Override
    public void modifyExport(String id, IsilonExport export) throws Exception {
        ExportObject exportObj = _exports.get(id);

        // modify fields if not null
        if (export.getClients() != null)
            exportObj._clients = export.getClients();
        if (export.getComment() != null)
            exportObj._comment = export.getComment();
        if (export.getPaths() != null)
            exportObj._paths = export.getPaths();
        if (export.getMap_all() != null)
            exportObj._map_all = export.getMap_all();
        if (export.getMap_root() != null)
            exportObj._map_root = export.getMap_root();
        if (export.getSecurityFlavors() != null)
            exportObj._security_flavors = export.getSecurityFlavors();
        exportObj._read_only = export.getReadOnly();

    }

    /**
     * Generate snapshot path
     *
     * @param name snapshot name
     * @param path parent directory for this snapshot
     * @return snapshot path
     */
    private String getSnapPath(String name, String path) {
        String prefix = "ifs/";
        return String.format("ifs/.snapshot/%1$s/%2$s", name, path.substring(prefix.length()));
    }

    @Override
    public String createSnapshot(String name, String path) throws Exception {
        String dir = trimPath(path);
        if (_dirMap.containsKey(dir)) {
            String id = getUniqueId();
            String snapPath = getSnapPath(name, dir);
            _snapshots.put(id, snapPath);
            _dirMap.put(snapPath, new DirObject());
            return id;
        }
        throw new Exception("path not found");
    }

    @Override
    public void deleteSnapshot(String id) throws Exception {
        if (_snapshots.containsKey(id)) {
            String path = _snapshots.get(id);
            if (_dirMap.get(path).getExports() != null &&
                    _dirMap.get(path).getExports().size() > 0) {
                throw new Exception("Existing exports");
            }
            _dirMap.remove(path);
            _snapshots.remove(id);
        } else {
            throw new Exception("Not found");
        }
    }

    @Override
    public IsilonSmartQuota getQuota(String id) throws Exception {
        QuotaObject quotaObj = _quotas.get(id);

        if (quotaObj == null)
            throw new Exception("quota not found");

        IsilonSmartQuota isilonSmartQuota = new IsilonSmartQuota("/" + quotaObj._path, quotaObj._limit);
        isilonSmartQuota.setUsage(usage, 0, 0);
        isilonSmartQuota.setId(id);

        return isilonSmartQuota;
    }

    @Override
    public ArrayList<IsilonSmartQuota> listQuotas(String start, int num) {
        Set<String> keySet = _quotas.keySet();
        List<String> keyList = new ArrayList<String>(keySet);
        Collections.sort(keyList);
        int startIndex = 0;

        ArrayList<IsilonSmartQuota> list = new ArrayList<IsilonSmartQuota>();
        if (start != null)
            startIndex = keyList.indexOf(start);

        for (int i = 0; i < num && (startIndex+i) < keyList.size(); i++) {
            String id = keyList.get(startIndex + i);
            QuotaObject quota = _quotas.get(id);
            IsilonSmartQuota isilonSmartQuota = new IsilonSmartQuota("/" + quota._path, quota._limit);
            isilonSmartQuota.setUsage(usage, 0, 0);
            isilonSmartQuota.setId(id);
            list.add(isilonSmartQuota);
        }

        return list;
    }

    @Override
    public int getDirCount(String dir, boolean recursive) {
        int count = 0;
        String trimDir = trimPath(dir);
        for (String key : _dirMap.keySet()) {
            if (recursive) {
                if (key.compareTo(trimDir) != 0
                        && key.startsWith(trimDir + "/")) {
                    count++;
                }
            } else {
                if (key.compareTo(trimDir) != 0
                        && key.startsWith(trimDir + "/")
                        && !key.substring(trimDir.length() + 1).contains("/")) {
                    count++;
                }
            }
        }
        return count;
    }

}
