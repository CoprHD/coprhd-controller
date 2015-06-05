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

package com.emc.storageos.datadomain.restapi;

import com.emc.storageos.datadomain.restapi.errorhandling.DataDomainApiException;

/**
 * Created by zeldib on 2/4/14.
 *
 * Utility to parse export options for the DataDomain NFS system:
 *
 * <options> ::= <option> ' ' <options> ;
 *
 *              <option> ::= ro | rw |
 *                           		root_squash | no_root_squash |
 *                           		all_squash | no_all_squash |
 *                           		secure | insecure |
 *                           		anonuid=<N> | anongid=<N>
 *                          		;
 *  Using the space to separate the option.
 *  For example
 *  <options>
 *       rw no_root_squash no_all_squash secure
 *   <option
 *
 */


public class DDOptionInfo {

    public String permission;
    public String security;
    public String rootMapping;
    public String userMapping;
    public String anonUid;
    public String anonGid;
    public boolean secureConnection;

    static public DDOptionInfo parseOptions(String options) throws DataDomainApiException{
        DDOptionInfo info = new DDOptionInfo();
        String optionArr[] = options.split("[ \b]+");
        info.secureConnection = false;
        info.security = DataDomainApiConstants.DEFAULT_SECURITY;
        for( String option : optionArr) {
            switch (option) {
                case DataDomainApiConstants.PERMISSION_RO: {
                    info.permission = DataDomainApiConstants.PERMISSION_RO;
                    break;
                }
                case DataDomainApiConstants.PERMISSION_RW : {
                    info.permission = DataDomainApiConstants.PERMISSION_RW;
                    break;
                }
                case DataDomainApiConstants.ROOT_SQUASH : {
                    info.rootMapping = DataDomainApiConstants.ROOT_SQUASH;
                    break;
                }
                case DataDomainApiConstants.NO_ROOT_SQUASH : {
                    info.rootMapping = DataDomainApiConstants.NO_ROOT_SQUASH;
                    break;
                }
                case DataDomainApiConstants.ALL_SQUASH : {
                    info.userMapping = DataDomainApiConstants.ALL_SQUASH;
                    break;
                }
                case DataDomainApiConstants.NO_ALL_SQUASH : {
                    info.userMapping = DataDomainApiConstants.NO_ALL_SQUASH;
                    break;
                }
                case DataDomainApiConstants.SECURE: {
                    info.secureConnection = true;
                    break;
                }
                default: {
                    if ( option.startsWith(DataDomainApiConstants.ANONYMOUS_UID) ) {
                        String[] tokens = option.split("=");
                        if(tokens.length == 3){
                            info.anonUid = tokens[2];
                        }
                        else {
                            throw DataDomainApiException.exceptions.failedProcessExportOption(option);
                        }
                    }
                    else if ( option.startsWith(DataDomainApiConstants.ANONYMOUS_GID) ) {
                        String[] tokens = option.split("=");
                        if(tokens.length == 3){
                            info.anonGid = tokens[2];
                        }
                        else {
                            throw DataDomainApiException.exceptions.failedProcessExportOption(option);
                        }
                    } else if (option.startsWith(DataDomainApiConstants.SECURITY_TYPE_OPTION)) {
                        info.security = option.substring(DataDomainApiConstants.SECURITY_TYPE_OPTION.length());
                    }
                    else {
                        throw DataDomainApiException.exceptions.failedProcessExportOption(option);
                    }
                }
            }
             
        }
        return info;
    }
}
