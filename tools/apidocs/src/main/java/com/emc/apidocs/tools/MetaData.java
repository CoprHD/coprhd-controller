/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.tools;

import com.emc.apidocs.DocReporter;
import com.emc.apidocs.KnownPaths;
import com.emc.apidocs.model.ApiMethod;
import com.emc.apidocs.model.ApiService;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.List;

public class MetaData {
    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final OutputStream outputStream;

    public static void save(File file, List<ApiService> apiServices) {
        try (FileOutputStream metadataFile = new FileOutputStream(file)) {
            save(metadataFile, apiServices);
        } catch (IOException e) {
            DocReporter.printError("Error loading MetaData from " + file);
            throw new RuntimeException(e);
        }
    }

    public static void save(OutputStream outputStream, List<ApiService> apiServices) {
        try {
            removeApiServiceLink(apiServices);

            String asJson = gson.toJson(apiServices);
            IOUtils.write(asJson.getBytes(), outputStream);

            DocReporter.printWarning("Written Meta Data to " + KnownPaths.getOutputFile("meta_data.json"));
        } catch (IOException e) {
            DocReporter.printError("Error Dumping Meta Data " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            addApiServiceLink(apiServices);
        }
    }

    public static List<ApiService> load(File file) {
        try {
            return load(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            DocReporter.printError("Error loading MetaData from InputStream " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static List<ApiService> load(InputStream inputStream) {
        ApiService[] services = gson.fromJson(new InputStreamReader(inputStream), ApiService[].class);
        List<ApiService> apiServices = Lists.newArrayList(services);
        addApiServiceLink(apiServices);

        return apiServices;
    }

    public MetaData(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    // ApiMethods contain a link to the parent service, but this causes cyclic issues with JSON
    // These methods should be used with caution as they modify the actual service information
    private static void removeApiServiceLink(List<ApiService> apiServices) {
        for (ApiService service : apiServices) {
            for (ApiMethod method : service.methods) {
                method.apiService = null;
            }
        }
    }

    private static void addApiServiceLink(List<ApiService> apiServices) {
        for (ApiService apiService : apiServices) {
            for (ApiMethod method : apiService.methods) {
                method.apiService = apiService;
            }
        }
    }
}
