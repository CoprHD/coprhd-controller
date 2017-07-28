/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.restcli.command;

import com.emc.storageos.driver.univmax.DriverUtil;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;

public class CliCommandRest extends CliCommand {

    private String user;
    private String pass;
    private boolean useSsl = true;
    private String host;
    private int port = 0;
    private String endPoint;
    private String restParam;
    private RestMethod method = RestMethod.GET;

    private boolean jsonFinePrint = true;
    private static final String INVALID_PARAM = "Invalid parameter: ";

    @Override
    public void usage() {
        System.out.println("Description:\n\tSend raw RESTful API requests.");
        System.out.println("Usage:");
        System.out.println("\trestcli rest [--get] [--fineprint|--nofineprint] [--user USERNAME] [--pass PASSWORD]" +
                " [--ssl|--nossl] --host IP[|NAME] [--port PORT] --endpoint ENDPOINT");
        System.out.println("\trestcli rest --post [--fineprint|--nofineprint] [--user USERNAME] [--pass PASSWORD]" +
                " [--ssl|--nossl] --host IP[|NAME] [--port PORT] --param <TEXT_FILE|-> --endpoint ENDPOINT");
        System.out.println("\trestcli rest --put [--fineprint|--nofineprint] [--user USERNAME] [--pass PASSWORD]" +
                " [--ssl|--nossl] --host IP[|NAME] [--port PORT] --param <TEXT_FILE|-> --endpoint ENDPOINT");
        System.out.println("\trestcli rest --delete [--fineprint|--nofineprint] [--user USERNAME] [--pass PASSWORD]" +
                " [--ssl|--nossl] --host IP[|NAME] [--port PORT] --endpoint ENDPOINT");
    }

    public void run(String[] args) {
        if (args.length < 2) {
            usage();
            return;
        }
        try {
            parseRestArgs(args);
            RestClient client = new RestClient(useSsl, host, port, user, pass);
            String rstr;
            switch (this.method) {
                case GET:
                    rstr = client.getJsonString(endPoint);
                    break;
                case DELETE:
                    rstr = client.deleteJsonString(endPoint);
                    break;
                case POST:
                    rstr = client.postJsonString(endPoint, restParam);
                    break;
                case PUT:
                    rstr = client.putJsonString(endPoint, restParam);
                    break;
                default:
                    throw new IllegalArgumentException("unsupported REST action: " + this.method.name());
            }
            if (this.jsonFinePrint) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                JsonParser jp = new JsonParser();
                JsonElement je = jp.parse(rstr);
                rstr = gson.toJson(je);
            }
            System.out.println(rstr);
        } catch (Exception e) {
            System.out.println(DriverUtil.getStackTrace(e));
            usage();
        }
    }

    private void parseRestArgs(String[] args) {
        String paramFile;
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
                case "--ssl":
                    this.useSsl = true;
                    break;
                case "--nossl":
                    this.useSsl = false;
                    break;
                case "--host":
                    this.host = args[++i];
                    break;
                case "--port":
                    this.port = Integer.valueOf(args[++i]);
                    break;
                case "--endpoint":
                    this.endPoint = args[++i];
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
                    } catch (Exception e) {
                        System.out.println(DriverUtil.getStackTrace(e));
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
                    }
                    if (ex) {
                        throw new IllegalArgumentException("Invalid json string for \"--param\": " + restParam);
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

    enum RestMethod {
        GET, POST, PUT, DELETE;
    }
}
