/*
 * Copyright 2015 EMC Corporation
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.keystone;

public class KeystoneConstants {

    public static String KEYSTONE = "keystone";
    public static String AUTH_TOKEN = "X-Auth-Token";

    public static String BASE_URI_V2 = "/v2.0/";
    public static String URI_TOKENS = BASE_URI_V2 + "tokens";
    public static String URI_ENDPOINTS = BASE_URI_V2 + "endpoints";
    public static String URI_TENANTS = BASE_URI_V2 + "tenants";
    public static String URI_SERVICES = BASE_URI_V2 + "OS-KSADM/services";
    public static String VALIDATE_TOKEN = URI_TOKENS + "/%1$s";

}
