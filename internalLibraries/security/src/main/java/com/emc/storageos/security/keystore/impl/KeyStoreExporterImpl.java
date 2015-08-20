/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.keystore.impl;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.keystore.KeyStoreExporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation class for KeyStoreExporter interface
 */
public class KeyStoreExporterImpl implements KeyStoreExporter {
    private static final Logger log = LoggerFactory.getLogger(KeyStoreExporterImpl.class);

    private static final String STORAGEOS_NAME = "storageos";
    private static final String FILE_LOCK_PATH = "/tmp/keystorelock";

    @Autowired
    private CoordinatorClient coordinator;

    private String keystorePath;
    private String keystorePassword;
    private String owner = STORAGEOS_NAME;
    private String group = STORAGEOS_NAME;

    private RandomAccessFile lockFile;
    private FileChannel lockChannel;
    private FileLock lock;

    public KeyStoreExporterImpl() {
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void setKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setOwnerGroup(String groupName) {
        this.group = groupName;
    }

    @Override
    public void export() throws KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException, InterruptedException {
        log.info("Start exporting keyStore to local file {}", keystorePath);
        lock();
        try {
            if (isKeystoreFileValid()) {
                log.info("Keystore file {} found, no need to generate again.", keystorePath);
                return;
            }
            saveKeystore();
            setKeyStorePermission();
        } finally {
            unlock();
        }
        log.info("Exported keystore successfully");
    }

    private void lock() throws IOException {
        lockFile = new RandomAccessFile(FILE_LOCK_PATH, "rw");
        lockChannel = lockFile.getChannel();
        lock = lockChannel.lock();
        log.info("Acquired lock successfully");
    }

    private void unlock() throws IOException {
        try {
            lock.release();
        } finally {
            lockChannel.close();
            lockFile.close();
        }
        log.info("Released lock");
    }

    private boolean isKeystoreFileValid() {
        File keystoreFile = new File(keystorePath);
        return keystoreFile.exists() && keystoreFile.length() > 0;
    }

    private void saveKeystore() throws KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException, InterruptedException {
        KeyStore keyStore = KeyStoreUtil.getViPRKeystore(coordinator);
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(keystorePath);
            keyStore.store(stream, keystorePassword.toCharArray());
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    /**
     * Set owner and file permission for the keystore file
     */
    private void setKeyStorePermission() throws IOException {
        Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
        perms.add(PosixFilePermission.OWNER_READ);
        File keystoreFile = new File(keystorePath);
        setFilePermissions(keystoreFile.toPath(), owner, group, perms);
    }

    /**
     * Sets the file permissions on the specified path. The group of the file is the
     * user's group
     * 
     * @param path the path for which to set permissions
     * @param owner the owner of the specified path
     * @param group group name of the specified path
     * @param permissions the permissions to set
     */
    private void setFilePermissions(Path path, String owner, String groupName,
            Set<PosixFilePermission> permissions) throws IOException {
        UserPrincipalLookupService lookupService =
                FileSystems.getDefault().getUserPrincipalLookupService();
        UserPrincipal user = lookupService.lookupPrincipalByName(owner);
        GroupPrincipal group = lookupService.lookupPrincipalByGroupName(groupName);
        PosixFileAttributeView attributeView = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        attributeView.setGroup(group);
        attributeView.setOwner(user);
        Files.setPosixFilePermissions(path, permissions);
    }
}
