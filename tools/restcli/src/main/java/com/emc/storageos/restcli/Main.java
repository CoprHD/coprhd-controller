/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.restcli;

import com.emc.storageos.driver.restvmax.rest.RestAPI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;

public class Main {

    private static final String INVALID_PARAM = "Invalid parameter: ";

    private String user;
    private String pass;
    private String url;
    private boolean jsonFinePrint = true;

    private enum Method {
        GET,
        POST,
        PUT,
        DELETE;
    }

    private Method method = Method.GET;

    private enum Command {
        HELP {
        },
        REST {
        },
        SHOW_CF {
        },
        SHOW_SCHEMA {
        },
        DUMP {
        },
        LOAD {
        },
        DELETE {
        },
        LIST {
        },
        CREATE {
        };
    }

    private static void usage() {
        System.out.println("Usage:");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            return;
        }

        Command cmd;
        try {
            cmd = Command.valueOf(args[0].trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid command: " + args[0]);
            usage();
            return;
        }

        Main m = new Main();

        try {
            switch (cmd) {
                default:
                case HELP:
                    usage();
                    break;
                case REST:
                    m.parseRestArgs(args);
                    switch (m.method) {
                        case GET:
                            ClientResponse cr = RestAPI.get(m.url, false, RestAPI.BackendType.VMAX, m.user, m.pass);
                            String jestr = cr.getEntity(String.class);
                            if (m.jsonFinePrint) {
                                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                                JsonParser jp = new JsonParser();
                                JsonElement je = jp.parse(jestr);
                                jestr = gson.toJson(je);
                            }
                            System.out.println(jestr);
                            cr.close();
                            break;
                        default:
                            throw new IllegalArgumentException("unsupported REST action: " + m.method.name());
                    }
                    break;
            }
        } catch (Exception e) {
            System.out.println(e);
            usage();
        }
    }

	private void parseRestArgs(String[] args) {
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--user":
                    user = args[++i];
                    break;
                case "--pass":
                    pass = args[++i];
                    break;
                case "--url":
                    url = args[++i];
                    break;
                case "--get":
                    method = Method.GET;
                    break;
                case "--post":
                    method = Method.POST;
                    break;
                case "--put":
                    method = Method.PUT;
                    break;
                case "--delete":
                    method = Method.DELETE;
                    break;
                case "--fineprint":
                    jsonFinePrint = true;
                    break;
                case "--nofineprint":
                    jsonFinePrint = false;
                    break;
                default:
                    throw new IllegalArgumentException(INVALID_PARAM + args[i]);
            }
        }
    }
}

