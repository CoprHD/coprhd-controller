/**
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

package com.emc.storageos.management.backup.util;

import com.google.common.base.Preconditions;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipUtil {

    private static final Logger log = LoggerFactory.getLogger(ZipUtil.class);
    // Defines separator for ZipEntry, because ZipEntry determine directory by result of method "endsWith("/")"
    private static final String SEPARATOR = "/";
    private static final int DELAY_THRESHOLD_IN_SECOND = 5;

    private ZipUtil() { }

    /**
     * Compresses the given directory and all its sub-directories into a ZIP file with
     * default compress level.
     * <p>
     * The ZIP file must not be a directory.
     *
     * @param sourceDir
     *          source directory.
     * @param targetZip
     *          ZIP file that will be created or overwritten.
     */
    public static void pack(final File sourceDir, final File targetZip) throws IOException {
        pack(sourceDir, targetZip, Deflater.DEFAULT_COMPRESSION);
    }

    /**
     * Compresses the given directory and all its sub-directories into a ZIP file with specified
     * compress level.
     * <p>
     * The ZIP file must not be a directory.
     *
     * @param sourceDir
     *          source directory.
     * @param targetZip
     *          ZIP file that will be created or overwritten.
     */
    public static void pack(final File sourceDir, final File targetZip, final int compressionLevel)
            throws IOException {
        Preconditions.checkArgument(sourceDir != null
                && sourceDir.exists()
                && sourceDir.isDirectory(),
                "Source directory is not exist: %s", sourceDir.getAbsolutePath());
        long startTime = System.currentTimeMillis();
        ZipOutputStream zipOut = null;
        try {
            zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(targetZip)));
            zipOut.setLevel(compressionLevel);
            pack(sourceDir, sourceDir.getParent()+File.separator, zipOut);
        } finally {
            IOUtils.closeQuietly(zipOut);
            long interval = (System.currentTimeMillis()-startTime)/1000;
            if (interval >= DELAY_THRESHOLD_IN_SECOND) {
                long folderSize = FileUtils.sizeOfDirectory(sourceDir);
                log.info(String.format("Zip folder: %s from %d to %d bytes, took %s seconds",
                        sourceDir.getAbsolutePath(), folderSize, targetZip.length(), interval));
            }
        }
    }

    private static void pack(final File sourceDir,
                             final String prefixPath,
                             final ZipOutputStream zipOut)
            throws IOException {
        File[] files = sourceDir.listFiles();
        if (files == null)
            return;
        for (File file : files) {
            String zipEntryName = file.getPath().replace(prefixPath, "");
            log.debug("Packing file: {}", zipEntryName);
            if (file.isDirectory())
                zipEntryName += SEPARATOR;
            ZipEntry zipEntry = new ZipEntry(zipEntryName);
            zipOut.putNextEntry(zipEntry);
            if (file.isFile()) {
                zipEntry.setSize(file.length());
                zipEntry.setTime(file.lastModified());
                FileUtils.copyFile(file, zipOut);
            }
            zipOut.closeEntry();
            if (file.isDirectory())
                pack(file, prefixPath, zipOut);
        }
    }

    /**
     * Unpacks a ZIP file to the given directory.
     * <p>
     * The output directory must not be a file.
     *
     * @param sourceZip
     *          The instance of input ZIP file.
     * @param targetDir
     *          output directory (created automatically if not found).
     */
    public static void unpack(File sourceZip, final File targetDir) throws IOException {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(sourceZip);
            Enumeration<? extends ZipEntry> entriesEnum = zipFile.entries();
            while (entriesEnum.hasMoreElements()) {
                unpackEntry(zipFile, entriesEnum.nextElement(), targetDir);
            }
        } finally {
            try {
                if (zipFile != null)
                    zipFile.close();
            } catch (IOException e) {
                // Just ignore it
            }

        }
    }

    private static void unpackEntry(ZipFile zipFile, ZipEntry zipEntry, File targetDir)
            throws IOException {
        InputStream is = null;
        try {
            log.debug("Unpacking file: {}", zipEntry.getName());
            is = zipFile.getInputStream(zipEntry);
            File file = new File(targetDir, zipEntry.getName());
            if (zipEntry.isDirectory())
                file.mkdirs();
            else
                FileUtils.copyInputStreamToFile(is, file);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }





}
