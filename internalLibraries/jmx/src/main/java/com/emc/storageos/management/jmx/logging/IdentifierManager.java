/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.jmx.logging;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class IdentifierManager {
    private static final String PRODUCT_IDENT_PATH = "/opt/storageos/etc/product";
    private static final String PRODUCT_BASE_PATH = "/opt/storageos/etc/productbase";
    private static final String GIT_REVISION_PATH = "/opt/storageos/etc/gitrevision";
    private static final String GIT_BRANCH_PATH = "/opt/storageos/etc/gitbranch";
    private static final String GIT_REPO_PATH = "/opt/storageos/etc/gitrepo";
    private static final String PLATFORM_PATH = "/opt/storageos/etc/platform";
    private static final String KERNEL_PATH = "/opt/storageos/etc/kernel";
    private String _productIdent;
    private String _productBase;
    private String _gitRevision;
    private String _gitBranch;
    private String _gitRepository;
    private String _platform;
    private String _kernelVersion;
    private String _ipAddress;

    private static IdentifierManager instance;

    public static IdentifierManager getInstance() {
        if (instance == null) {
            instance = new IdentifierManager();
        }
        return instance;
    }

    private IdentifierManager() {
        String productIdent = readFile(PRODUCT_IDENT_PATH);
        _productIdent = (productIdent == null) ? "Unknown" : productIdent;

        String productBase = readFile(PRODUCT_BASE_PATH);
        _productBase = (productBase == null) ? "Unknown" : productBase;

        String gitRevision = readFile(GIT_REVISION_PATH);
        _gitRevision = (gitRevision == null) ? "Unknown" : gitRevision;

        String gitBranch = readFile(GIT_BRANCH_PATH);
        _gitBranch = (gitBranch == null) ? "Unknown" : gitBranch;

        String gitRepository = readFile(GIT_REPO_PATH);
        _gitRepository = (gitRepository == null) ? "Unknown" : gitRepository;

        String platform = readFile(PLATFORM_PATH);
        _platform = (platform == null) ? "Unknown" : platform;

        String kernelVersion = readFile(KERNEL_PATH);
        _kernelVersion = (kernelVersion == null) ? "Unknown" : kernelVersion;

        try {
            _ipAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            _ipAddress = "Unknown";
        }
    }

    /**
     * Read file content
     * 
     * @param path file path
     * @return file content
     */
    private static String readFile(String path) {
        BufferedReader br = null;
        String content = null;
        try {
            br = new BufferedReader(new FileReader(path));
            content = br.readLine();
        } catch (Exception e) {
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ex) {
            }
        }

        return content;
    }

    /**
     * Retrieve DeviceType
     * 
     * @return
     */
    public String findDeviceType() {

        return "Storage Management";
    }

    /**
     * Retrieve the Operating System
     * 
     * @return
     */
    public String findOS() {
        return "ViPR OS";
    }

    /**
     * Retrieve the storageos version from /.product_ident
     */
    public String findOSVersion() {
        return _productIdent;
    }

    /**
     * Retrieve git revision from /opt/storageos/etc/gitrevision
     * 
     * @return
     */
    public String findGitRevision() {
        return _gitRevision;
    }

    /**
     * Retrieve the Device State
     * 
     * @return
     */
    public BigInteger findDeviceState() {

        return new BigInteger("2");
    }

    /**
     * Retrieve the platform info from /opt/storageos/etc/platform
     * 
     * @return
     */
    public String findPlatform() {

        return _platform;
    }

    public String findProductIdent() {
        return _productIdent;
    }

    public String findProductBase() {
        return _productBase;
    }

    public String findGitBranch() {
        return _gitBranch;
    }

    public String findGitRepo() {
        return _gitRepository;
    }

    public String findKernelVersion() {
        return _kernelVersion;
    }

    public String findIpAddress() {
        return _ipAddress;
    }
}
