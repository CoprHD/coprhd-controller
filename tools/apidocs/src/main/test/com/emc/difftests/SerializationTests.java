/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.difftests;

import com.emc.apidocs.model.ApiMethod;
import com.emc.apidocs.model.ApiService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SerializationTests {

    public static void main(String[] args) throws Exception {

        ApiService service = new ApiService();
        service.javaClassName  = "Dave";
        service.path = "/wobble";

        ApiMethod method = new ApiMethod();
        method.httpMethod = "GET";
        method.path = "/wobble/fred";

        service.addMethod(method);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String asJson = gson.toJson(service);

        System.out.println(asJson);

        ApiService back = gson.fromJson(asJson, ApiService.class);
        System.out.println(back.getFqJavaClassName()+ " "+back.methods.size());
    }
}
