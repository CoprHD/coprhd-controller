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

package com.emc.storageos.simulators;

import com.emc.storageos.isilon.restapi.IsilonExport;
import com.emc.storageos.isilon.restapi.IsilonSmartQuota;
import java.util.ArrayList;

/*
 * API to handle storage of the objects created
 */
public interface ObjectStore {

    /**
     * Check if the path exists
     * 
     * @param s path to check for
     * @return true if exists, false otherwise
     */
    public boolean checkPath(String s);

    /**
     * Check if quota exists
     * 
     * @param s path to check for
     * @param recursive true if recursion, false otherwise
     * @return true if quota exists
     */
    public boolean checkQuotaExist(String s, boolean recursive);

    /**
     * list all sub directories under given directory
     * 
     * @param dir
     * @return ArrayList of strings with sub directory names
     */
    public ArrayList<String> listDir(String dir);

    /**
     * Create directory
     * 
     * @param dir path to create
     * @param recursive if true, creates the parents recursively
     * @throws Exception
     */
    public void createDir(String dir, boolean recursive) throws Exception;

    /**
     * Delete directory
     * 
     * @param dir path to delete
     * @param recursive if true, deletes all children if any exist
     * @throws Exception
     */
    public void deleteDir(String dir, boolean recursive) throws Exception;

    /**
     *
     * @param path path to create quota for
     * @param limit hard limit value
     * @return identifier for the quota created
     * @throws Exception
     */
    public String createQuota(String path, long limit) throws Exception;

    /**
     * Delete quota
     * 
     * @param id identifier of the quota to be deleted
     * @throws Exception
     */
    public void deleteQuota(String id) throws Exception;

    /**
     * Create export for export object, returns unique id
     * 
     * @param export export object
     * @return identifier of the export created
     * @throws Exception
     */
    public String createExport(IsilonExport export) throws Exception;

    /**
     * Get export by id
     * 
     * @param id export id
     * @return
     * @throws Exception
     */
    public IsilonExport getExport(String id) throws Exception;

    /**
     * Delete export
     * 
     * @param id Identifier of the export to delete
     * @throws Exception
     */
    public void deleteExport(String id) throws Exception;

    /**
     * Modify export
     * 
     * @param id Identifier of the export to delete
     * @param export Export object
     * @throws Exception
     */
    public void modifyExport(String id, IsilonExport export) throws Exception;

    /**
     * Create snapshot
     * 
     * @param name label for the snapshot
     * @param path directory path to snapshot
     * @return identifier of the snapshot created
     * @throws Exception
     */
    public String createSnapshot(String name, String path) throws Exception;

    /**
     * Delete snapshot
     * 
     * @param id identifier for the snapshot to delete
     * @throws Exception
     */
    public void deleteSnapshot(String id) throws Exception;

    /**
     * Get quota by id
     * 
     * @param id quota id
     * @return IsilonSmartQuota
     * @throws Exception
     */
    public IsilonSmartQuota getQuota(String id) throws Exception;

    /**
     * List quotas with start id
     * 
     * @param start start id
     * @param num number per page
     * @return list of IsilonSmartQuota
     */
    public ArrayList<IsilonSmartQuota> listQuotas(String start, int num);

    /**
     *
     * @param dir directory
     * @param recursive if true, count all subFolders recursively
     * @return total number of directories
     */
    public int getDirCount(String dir, boolean recursive);
}
