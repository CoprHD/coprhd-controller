/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.datadomain.restapi;

import java.net.URI;

/**
 * Defines constants relative to using the VPlex HTTP Element Manager API.
 */
public class DataDomainApiConstants {

    public static final long B_TO_KB_SHIFT = 10;
    public static final long B_TO_MB_SHIFT = 20;
    public static final long B_TO_GB_SHIFT = 30;
    public static final long B_TO_TB_SHIFT = 40;
    public static final long B_TO_PB_SHIFT = 50;

    public static final long KB = 1024;
    public static final long MB = KB << B_TO_KB_SHIFT;
    public static final long GB = MB << B_TO_KB_SHIFT;
    public static final long TB = GB << B_TO_KB_SHIFT;
    public static final long PB = TB << B_TO_KB_SHIFT;

    public static final int STATS_FIRST_PAGE = 0;
    public static final int STATS_PAGE_SIZE = 2;
    public static final String COLLECTION_EPOCH = "collection_epoch";
    public static final String ASCENDING_SORT = COLLECTION_EPOCH;
    public static final String DESCENDING_SORT = "-" + COLLECTION_EPOCH;

    public static final int EXPORT_ADD = 0;
    public static final int EXPORT_DELETE = 1;

    public static final int PATH_DOES_NOT_EXIST = 0;
    public static final int PATH_EXISTS = 1;
    public static final int FILE_DELETED = 1;

    public static final int SVC_CODE_SUCCESS = 0;

    // Fraction of hard limit
    public static double DD_MTREE_SOFT_LIMIT = 0.8;

    // Number of Max Mtrees currently supported by DDOS
    public static final int DD_MAX_MTREE_LIMIT = 100;

    public static final String TOTAL_LOGICAL_CAPACITY = "TOTAL_LOGICAL_CAPACITY";
    public static final String USED_LOGICAL_CAPACITY = "USED_LOGICAL_CAPACITY";
    public static final String AVAILABLE_LOGICAL_CAPACITY = "AVAILABLE_LOGICAL_CAPACITY";
    public static final String TOTAL_PHYSICAL_CAPACITY = "TOTAL_PHYSICAL_CAPACITY";
    public static final String USED_PHYSICAL_CAPACITY = "USED_PHYSICAL_CAPACITY";
    public static final String AVAILABLE_PHYSICAL_CAPACITY = "AVAILABLE_PHYSICAL_CAPACITY";
    public static final String COMPRESSION_FACTOR = "COMPRESSION_FACTOR";
    public static final String NUMBER_MTREES = "NUMBER_MTREES";
    public static final String SYSTEM_QUOTA = "SYSTEM_QUOTA";

    // Constants define the headers required when making HTTP requests to the
    // VPlex Management Station using the Element Manager API.
    public static final String AUTH_TOKEN = "X-DD-AUTH-TOKEN";
    public static final String DETAILS = "details";
    public static final String CODE = "code";
    public static final String FS_PATH_BASE = "/data/col1/";

    // Export options
    public static final String ANONYMOUS = "nobody";
    public static final String SECURITY_TYPE_OPTION = "sec=";
    public static final String ROOT = "root";
    public static final String SECURE = "secure"; // Allow connections from ports below 1024
    public static final String INSECURE = "insecure";
    public static final String ROOT_SQUASH = "root_squash"; // Map root to anonymous
    public static final String NO_ROOT_SQUASH = "no_root_squash";
    public static final String ALL_SQUASH = "all_squash"; // Map all users to anonymous
    public static final String NO_ALL_SQUASH = "no_all_squash";
    // Set anonymous uid -- eg anonuid=<uid>, default = 65534
    public static final String ANONYMOUS_UID = "anonuid";
    // Set anonymous gid -- eg anongid=<gid>, default = 65534
    public static final String ANONYMOUS_GID = "anongid";
    public static final String NFS_PROTOCOL = "NFS";
    public static final String CIFS_PROTOCOL = "CIFS";
    public static final String DEFAULT_SECURITY = "sys";
    public static final String PERMISSION_RO = "ro";
    public static final String PERMISSION_RW = "rw";

    // Share options
    public static final String BROWSE_ALLOW = "allow";
    public static final String BROWSE_DENY = "deny";
    public static final String SHARE_READ = "read";
    public static final String SHARE_CHANGE = "change";
    public static final String SHARE_FULL = "full";

    // Stats query parameters
    public static final String PAGE = "page";
    public static final String PAGE_SIZE = "size";
    public static final String DATA_VIEW = "data_view";
    public static final String INTERVAL = "interval";
    public static final String REQUESTED_INTERVAL_ONLY = "requested_interval_only";
    public static final String SORT = "sort";
    public static final String FILTER = "filter";

    // Constants defining HTTP resource paths.
    public static final String DATADOMAIN_PATH = "/rest/v1.0";
    public static final String DATADOMAIN_AUTH = DATADOMAIN_PATH + "/auth";
    public static final String DATADOMAIN_SERVICE = DATADOMAIN_PATH + "/system";
    public static final String DATADOMAIN_SYSTEM_LIST = DATADOMAIN_PATH + "/dd-systems";

    private static final String DATADOMAIN_SYSTEM_ = DATADOMAIN_SYSTEM_LIST + "/%s";

    public static final String DATADOMAIN_SYSTEM(String system) {
        return String.format(DATADOMAIN_SYSTEM_, system);
    }

    private static final String DATADOMAIN_MTREES_ = DATADOMAIN_SYSTEM_ + "/mtrees";

    public static final String DATADOMAIN_MTREES(String system) {
        return String.format(DATADOMAIN_MTREES_, system);
    }

    private static final String DATADOMAIN_MTREE_ = DATADOMAIN_MTREES_ + "/%s";

    public static final String DATADOMAIN_MTREE(String system, String id) {
        return String.format(DATADOMAIN_MTREE_, system, id);
    }

    private static final String DATADOMAIN_EXPORTS_ = DATADOMAIN_SYSTEM_ + "/protocols/nfs/exports";

    public static final String DATADOMAIN_EXPORTS(String system) {
        return String.format(DATADOMAIN_EXPORTS_, system);
    }

    private static final String DATADOMAIN_EXPORT_ = DATADOMAIN_EXPORTS_ + "/%s";

    public static final String DATADOMAIN_EXPORT(String system, String id) {
        return String.format(DATADOMAIN_EXPORT_, system, id);
    }

    private static final String DATADOMAIN_SHARES_ = DATADOMAIN_SYSTEM_ + "/protocols/cifs/shares";

    public static final String DATADOMAIN_SHARES(String system) {
        return String.format(DATADOMAIN_SHARES_, system);
    }

    private static final String DATADOMAIN_SHARE_ = DATADOMAIN_SHARES_ + "/%s";

    public static final String DATADOMAIN_SHARE(String system, String id) {
        return String.format(DATADOMAIN_SHARE_, system, id);
    }

    private static final String DATADOMAIN_SNAPSHOTS_ = DATADOMAIN_SYSTEM_ + "/snapshots";

    public static final String DATADOMAIN_SNAPSHOTS(String system) {
        return String.format(DATADOMAIN_SNAPSHOTS_, system);
    }

    private static final String DATADOMAIN_SNAPSHOT_ = DATADOMAIN_SNAPSHOTS_ + "/%s";

    public static final String DATADOMAIN_SNAPSHOT(String system, String id) {
        return String.format(DATADOMAIN_SNAPSHOT_, system, id);
    }

    private static final String DATADOMAIN_SYSTEM_STATS_CAPACITY = DATADOMAIN_SYSTEM_ + "/stats/capacity";

    public static final String DATADOMAIN_SYSTEM_STATS_CAPACITY(String system) {
        return String.format(DATADOMAIN_SYSTEM_STATS_CAPACITY, system);
    }

    private static final String DATADOMAIN_MTREE_STATS_ = DATADOMAIN_MTREE_ + "/stats";

    public static final String DATADOMAIN_MTREE_STATS(String system, String mtreeId) {
        return String.format(DATADOMAIN_MTREE_STATS_, system, mtreeId);
    }

    private static final String DATADOMAIN_MTREE_STATS_CAPACITY = DATADOMAIN_MTREE_ + "/stats/capacity";

    public static final String DATADOMAIN_MTREE_STATS_CAPACITY(String system, String mtreeId) {
        return String.format(DATADOMAIN_MTREE_STATS_CAPACITY, system, mtreeId);
    }

    private static final String DATADOMAIN_MTREE_STATS_PERFORMANCE = DATADOMAIN_MTREE_ + "/stats/performance";

    public static final String DATADOMAIN_MTREE_STATS_PERFORMANCE(String system, String mtreeId) {
        return String.format(DATADOMAIN_MTREE_STATS_PERFORMANCE, system, mtreeId);
    }

    public static final URI URI_DATADOMAIN_PATH = URI.create(DATADOMAIN_PATH);
    public static final URI URI_DATADOMAIN_AUTH = URI.create(DATADOMAIN_AUTH);
    public static final URI URI_DATADOMAIN_SERVICE = URI.create(DATADOMAIN_SERVICE);
    public static final URI URI_DATADOMAIN_SYSTEM_LIST = URI.create(DATADOMAIN_SYSTEM_LIST);

    private static final String DATADOMAIN_NETWORKS_ = DATADOMAIN_SYSTEM_ + "/networks";

    public static final String DATADOMAIN_NETWORKS(String system) {
        return String.format(DATADOMAIN_NETWORKS_, system);
    }

    private static final String DATADOMAIN_NETWORK_ = DATADOMAIN_NETWORKS_ + "/%s";

    public static final String DATADOMAIN_NETWORK(String system, String networkId) {
        return String.format(DATADOMAIN_NETWORK_, system, networkId);
    }

    public static final String newDataDomainBase(String ip, int port) {
        return String.format("https://%1$s:%2$d/", ip, port);
    }

    public static final URI newDataDomainBaseURI(String ip, int port) {
        return URI.create(newDataDomainBase(ip, port));
    }

    public static final URI URI_DATADOMAIN_SYSTEM(String system) {
        return URI.create(DATADOMAIN_SYSTEM(system));
    }

    public static final URI URI_DATADOMAIN_MTREES(String system) {
        return URI.create(DATADOMAIN_MTREES(system));
    }

    public static final URI URI_DATADOMAIN_MTREE(String system, String id) {
        return URI.create(DATADOMAIN_MTREE(system, id));
    }

    public static final URI URI_DATADOMAIN_EXPORTS(String system) {
        return URI.create(DATADOMAIN_EXPORTS(system));
    }

    public static final URI URI_DATADOMAIN_EXPORT(String system, String id) {
        return URI.create(DATADOMAIN_EXPORT(system, id));
    }

    public static final URI URI_DATADOMAIN_SHARES(String system) {
        return URI.create(DATADOMAIN_SHARES(system));
    }

    public static final URI URI_DATADOMAIN_SHARE(String system, String id) {
        return URI.create(DATADOMAIN_SHARE(system, id));
    }

    public static final URI URI_DATADOMAIN_SNAPSHOTS(String system) {
        return URI.create(DATADOMAIN_SNAPSHOTS(system));
    }

    public static final URI URI_DATADOMAIN_SNAPSHOT(String system, String id) {
        return URI.create(DATADOMAIN_SNAPSHOT(system, id));
    }

    public static final URI URI_DATADOMAIN_NETWORKS(String system) {
        return URI.create(DATADOMAIN_NETWORKS(system));
    }

    public static final URI URI_DATADOMAIN_NETWORK(String system, String network) {
        return URI.create(DATADOMAIN_NETWORK(system, network));
    }

    public static final URI URI_DATADOMAIN_SYSTEM_STATS_CAPACITY(String systemId) {
        return URI.create(DATADOMAIN_SYSTEM_STATS_CAPACITY(systemId));
    }

    public static final URI URI_DATADOMAIN_MTREE_STATS(String system, String id) {
        return URI.create(DATADOMAIN_MTREE_STATS(system, id));
    }

    public static final URI URI_DATADOMAIN_MTREE_STATS_CAPACITY(String system, String id) {
        return URI.create(DATADOMAIN_MTREE_STATS_CAPACITY(system, id));
    }

    public static final URI URI_DATADOMAIN_MTREE_STATS_PERFORMANCE(String system, String id) {
        return URI.create(DATADOMAIN_MTREE_STATS_PERFORMANCE(system, id));
    }

}
