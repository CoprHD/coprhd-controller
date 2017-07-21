/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.restcli.command;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.URI;

import com.emc.storageos.driver.vmax3.restengine.RestClient;
import com.emc.storageos.driver.vmax3.restengine.RestHandler;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.restcli.Util;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;

public class CliCommandRest extends CliCommand {

    private RestClient restClient;
    private AuthenticationInfo authenticationInfo;
    private String user;
    private String pass;
    private String url;
    private String restParam;
    private RestMethod method = RestMethod.GET;

    private boolean jsonFinePrint = true;
    private static final String INVALID_PARAM = "Invalid parameter: ";

    /**
     * 
     */
    public CliCommandRest() {
        super();

    }

    private void init() {
        authenticationInfo = new AuthenticationInfo(null, null, null, user, pass);
        this.restClient = new RestHandler(authenticationInfo).getRestClient();
    }

    @Override
    public void usage() {
        System.out.println("Description:\n\tSend raw RESTful API requests.");
        System.out.println("Usage:");
        System.out.println("\trestcli rest [--get] [--fineprint|--nofineprint] [--user USERNAME] [--pass PASSWORD] --url URL");
        System.out
                .println("\trestcli rest --post [--fineprint|--nofineprint] [--user USERNAME] [--pass PASSWORD] --url URL --param <TEXT_FILE|->");
        System.out.println("\trestcli rest --put [--fineprint|--nofineprint] [--user USERNAME] [--pass PASSWORD] --url URL");
        System.out.println("\trestcli rest --delete [--fineprint|--nofineprint] [--user USERNAME] [--pass PASSWORD] --url URL");
    }

    public void run(String[] args) {
        if (args.length < 2) {
            usage();
            return;
        }
        ClientResponse cr = null;
        String jestr;
        try {
            parseRestArgs(args);
            init();
            switch (this.method) {
                case GET:
                    cr = restClient.get(URI.create(this.url));
                    break;
                case DELETE:
                    cr = restClient.delete(URI.create(this.url));
                    break;
                case POST:
                    cr = restClient.post(URI.create(this.url), this.restParam);
                    break;
                case PUT:
                    cr = restClient.put(URI.create(this.url), this.restParam);
                    break;
                default:
                    throw new IllegalArgumentException("unsupported REST action: " + this.method.name());
            }
            jestr = cr.getEntity(String.class);
            int status = cr.getStatus();
            if (status != 200) {
                System.out.println("Status-Code: " + cr.getStatus());
                if (status == 400) {
                    if (this.method == RestMethod.POST) {
                        System.out.println("rest param: " + this.restParam);
                    }
                }
            } else {
                if (this.jsonFinePrint) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    JsonParser jp = new JsonParser();
                    JsonElement je = jp.parse(jestr);
                    jestr = gson.toJson(je);
                }
            }
            System.out.println(jestr);
        } catch (Exception e) {
            Util.printException(e);
            usage();
        } finally {
            if (cr != null) {
                cr.close();
            }
        }
    }

    private void parseRestArgs(String[] args) {
        String paramFile = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--fineprint":
                    this.jsonFinePrint = true;
                    break;
                case "--nofineprint":
                    this.jsonFinePrint = false;
                    break;
                case "--user":
                    this.user = args[++i];
                    break;
                case "--pass":
                    this.pass = args[++i];
                    break;
                case "--url":
                    this.url = args[++i];
                    break;
                case "--get":
                    this.method = RestMethod.GET;
                    break;
                case "--post":
                    this.method = RestMethod.POST;
                    break;
                case "--put":
                    this.method = RestMethod.PUT;
                    break;
                case "--delete":
                    this.method = RestMethod.DELETE;
                    break;
                case "--param":
                    boolean ex = false;
                    FileReader fr = null;
                    BufferedReader br = null;
                    try {
                        paramFile = args[++i];
                        if (paramFile.equals("-")) {
                            System.out.println("Reading rest param from STDIN: ....");
                            br = new BufferedReader(new InputStreamReader(System.in));
                        } else {
                            System.out.println("Reading rest param from file " + paramFile + ": ....");
                            fr = new FileReader(paramFile);
                            br = new BufferedReader(fr);
                        }
                        this.restParam = "";
                        String s = "";
                        while ((s = br.readLine()) != null) {
                            this.restParam += s;
                        }
                        System.out.println("rest param: " + this.restParam);
                        Gson gson = new Gson();
                        gson.fromJson(this.restParam, Object.class);
                    } catch (Exception e) {
                        Util.printException(e);
                        ex = true;
                    } finally {
                        try {
                            if (br != null) {
                                br.close();
                            }
                            if (fr != null) {
                                fr.close();
                            }
                        } catch (Exception e) {
                        }
                        if (ex) {
                            throw new IllegalArgumentException("Invalid json string for \"--param\": " + restParam);
                        }
                    }
                    break;
                default:
                    throw new IllegalArgumentException(INVALID_PARAM + args[i]);
            }
        }

        // Validation 1: check if the RESTful endpoint can use this method.
        // Validation 2: check if the method's parameters are correct.
        switch (this.method) {
            case POST:
                if (this.restParam == null) {
                    throw new IllegalArgumentException(INVALID_PARAM + "\"--param\" is required by action \"--post\".");
                }
                break;
            case PUT:
                if (this.restParam == null) {
                    throw new IllegalArgumentException(INVALID_PARAM + "\"--param\" is required by action \"--put\".");
                }
                break;
            default:
                break;
        }
    }
}
