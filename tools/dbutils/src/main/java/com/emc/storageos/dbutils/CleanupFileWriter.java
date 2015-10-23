/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbutils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CleanupFileWriter {
    private static BufferedWriter storageFileWriter;
    private static BufferedWriter geoStorageFileWriter;
    private static final String KEYSPACE_STORAGEOS = "StorageOS";
    private static final String KEYSPACE_GEOSTORAGEOS = "GeoStorageOS";
    static final String CLEANUP_FILE_STORAGEOS = "cleanupStorageOS.cql";
    static final String CLEANUP_FILE_GEOSTORAGEOS = "cleanupGeoStorageOS.cql";
    private static final String USAGE_STORAGEOS = "-- please run /opt/storageos/bin/cqlsh -k StorageOS -f cleanupStorageOS.cql";
    private static final String USAGE_GEOSTORAGEOS = "-- please run /opt/storageos/bin/cqlsh -k GeoStorageOS -f cleanupGeoStorageOS.cql localhost 9260";
    private static final Logger log = LoggerFactory.getLogger(CleanupFileWriter.class);

    private CleanupFileWriter(){}

    static void writeTo(String keyspaceName, String lineStr) {
        try {
            BufferedWriter writer = getWriter(keyspaceName);
            writer.write(lineStr);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.out.println(" --> Exception : " + e);
            log.error("Caught Exception: ", e);
        }
    }

    private static BufferedWriter getWriter(String keyspace) throws IOException {
        BufferedWriter writer = null;
        if(KEYSPACE_STORAGEOS.equals(keyspace)) {
            if(storageFileWriter == null) {
                storageFileWriter = init(CLEANUP_FILE_STORAGEOS);
            }
            writer = storageFileWriter;
        } else if(KEYSPACE_GEOSTORAGEOS.equals(keyspace)) {
            if(geoStorageFileWriter == null) {
                geoStorageFileWriter = init(CLEANUP_FILE_GEOSTORAGEOS);
            }
            writer = geoStorageFileWriter;
        }
        return writer;
    }

    private static BufferedWriter init(String fileName) throws IOException {
        // add the command usage .
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
        writer.write(getUsage(fileName));
        writer.newLine();
        writer.flush();
        return writer;
    }

    private static String getUsage(String fileName) {
        if(CLEANUP_FILE_STORAGEOS.equals(fileName)){
            return USAGE_STORAGEOS;
        } else {
            return USAGE_GEOSTORAGEOS;
        } 
    }

    static void close() throws IOException {
        if(storageFileWriter != null) {
            storageFileWriter.close();
        }
        if(geoStorageFileWriter != null) {
            geoStorageFileWriter.close();
        }
    }

    static boolean existingCleanupFiles() {
        File cleaupStorageFile = new File(CLEANUP_FILE_STORAGEOS);
        File cleaupGeoStorageFile = new File(CLEANUP_FILE_GEOSTORAGEOS);
        return cleaupStorageFile.exists() || cleaupGeoStorageFile.exists();
    }

    static String getGeneratedFileNames() {
        StringBuilder generatedFileNameBuilder = new StringBuilder("");
        if(storageFileWriter != null) {
            generatedFileNameBuilder.append(CLEANUP_FILE_STORAGEOS);
        }
        if(geoStorageFileWriter != null) {
            generatedFileNameBuilder.append(", ").append(CLEANUP_FILE_STORAGEOS);
        }
        return generatedFileNameBuilder.toString();
    }
}
