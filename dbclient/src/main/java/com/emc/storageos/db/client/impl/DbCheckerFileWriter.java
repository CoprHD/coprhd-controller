/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbCheckerFileWriter {
    private static BufferedWriter storageFileWriter;
    private static BufferedWriter geoStorageFileWriter;
    private static BufferedWriter rebuildIndexFileWriter;
    static final String WRITER_STORAGEOS = "StorageOS";
    static final String WRITER_GEOSTORAGEOS = "GeoStorageOS";
    static final String WRITER_REBUILD_INDEX = "rebuildIndex";
    private final static String FILE_PATH = "/tmp/";
    static final String CLEANUP_FILE_STORAGEOS = FILE_PATH + "cleanup-StorageOS.cql";
    static final String CLEANUP_FILE_GEOSTORAGEOS = FILE_PATH + "cleanup-GeoStorageOS.cql";
    static final String CLEANUP_FILE_REBUILD_INDEX = FILE_PATH + "cleanup-rebuildIndex.file";
    private static final String USAGE_STORAGEOS = "-- please run /opt/storageos/bin/cqlsh -k StorageOS -f " + CLEANUP_FILE_STORAGEOS;
    private static final String USAGE_GEOSTORAGEOS = "-- please run /opt/storageos/bin/cqlsh -k GeoStorageOS -f "
            + CLEANUP_FILE_GEOSTORAGEOS + "localhost 9260";
    private static final String USAGE_REBUILDINDEX = "# please run /opt/storageos/bin/dbutils rebuild_index " + CLEANUP_FILE_REBUILD_INDEX;
    private static final Logger log = LoggerFactory.getLogger(DbCheckerFileWriter.class);

    private DbCheckerFileWriter() {
    }

    static void writeTo(String name, String lineStr) {
        try {
            BufferedWriter writer = getWriter(name);
            writeln(writer, lineStr);
        } catch (IOException e) {
            System.out.println(" --> Exception : " + e);
            log.error("Caught Exception: ", e);
        }
    }

    private static BufferedWriter getWriter(String name) throws IOException {
        if (WRITER_STORAGEOS.equals(name)) {
            storageFileWriter = getAndInit(storageFileWriter, CLEANUP_FILE_STORAGEOS, USAGE_STORAGEOS);
            return storageFileWriter;
        } else if (WRITER_GEOSTORAGEOS.equals(name)) {
            geoStorageFileWriter = getAndInit(geoStorageFileWriter, CLEANUP_FILE_GEOSTORAGEOS, USAGE_GEOSTORAGEOS);
            return geoStorageFileWriter;
        } else if (WRITER_REBUILD_INDEX.equals(name)) {
            rebuildIndexFileWriter = getAndInit(rebuildIndexFileWriter, CLEANUP_FILE_REBUILD_INDEX, USAGE_REBUILDINDEX);
            return rebuildIndexFileWriter;
        }
        return null;
    }

    private static BufferedWriter getAndInit(BufferedWriter writer, String fileName, String usage)
            throws IOException {
        if (writer == null) {
            writer = init(fileName, usage);
        }
        return writer;
    }

    private static BufferedWriter init(String fileName, String usage) throws IOException {
        final Path filePath = FileSystems.getDefault().getPath(fileName);
        cleanupFile(filePath);
        BufferedWriter writer = Files.newBufferedWriter(filePath, Charset.defaultCharset());
        writeln(writer, usage);
        return writer;
    }

    private static void writeln(BufferedWriter writer, String str) throws IOException {
        writer.write(str);
        writer.newLine();
        writer.flush();
    }

    public static void close() {
        try {
            if (storageFileWriter != null) {
                storageFileWriter.close();
            }
            if (geoStorageFileWriter != null) {
                geoStorageFileWriter.close();
            }
            if (rebuildIndexFileWriter != null) {
                rebuildIndexFileWriter.close();
            }
        } catch (IOException e) {
            log.error("Exception happens when closing file, e=", e);
        }
    }

    static boolean existingCleanupFiles() {
        File cleaupStorageFile = new File(CLEANUP_FILE_STORAGEOS);
        File cleaupGeoStorageFile = new File(CLEANUP_FILE_GEOSTORAGEOS);
        File rebuildIndexFile = new File(CLEANUP_FILE_REBUILD_INDEX);
        return cleaupStorageFile.exists() || cleaupGeoStorageFile.exists() || rebuildIndexFile.exists();
    }

    private static void cleanupFile(Path filePath) {
        try {
            Files.delete(filePath);
        } catch (Exception e) {
            log.warn("Failed to delete file, e=" + e);
        }
    }

    public static String getGeneratedFileNames() {
        StringBuilder generatedFileNameBuilder = new StringBuilder("");
        if (storageFileWriter != null) {
            generatedFileNameBuilder.append(CLEANUP_FILE_STORAGEOS);
        }
        if (geoStorageFileWriter != null) {
            generatedFileNameBuilder.append(" ").append(CLEANUP_FILE_GEOSTORAGEOS);
        }
        if (rebuildIndexFileWriter != null) {
            generatedFileNameBuilder.append(" ").append(CLEANUP_FILE_REBUILD_INDEX);
        }
        return generatedFileNameBuilder.toString();
    }
}