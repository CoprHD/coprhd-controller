#!/usr/bin/python

# Copyright 2015 EMC Corporation
# All Rights Reserved

# Copyright (c) 2013-14 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

'''
This module contains the quota view and update implementation
Any module wanting to add the quota support should make use of this module

Currently it is being used by Project, Tenant and VirtualPool modules
'''

import common
import json
from common import SOSError
from urihelper import singletonURIHelperInstance


class Quota(object):

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    '''
    Gets the quota information for the given resource uri
    Parameter resource_type :
    Type of the resource ( can be project, tenant etc )
    Parameter resource_uri : Uri of the resource
    '''

    def __get(self, resource_type, resource_uri):

        query_uri = self.__get_quota_uri(resource_type, resource_uri)
        (s, h) = common.service_json_request(self.__ipAddr,
                self.__port, "GET", query_uri, None)
        o = common.json_decode(s)
        if(o):
            return o

        return None

    '''
    Appends quota attributes to the resource_details if the quota is enabled
    Parameter resource_type :
    Type of the resource ( can be project, tenant etc )
    Parameter resource_uri : Uri of the resource
    Parameter resiurce_details : Details of the resource
    '''

    def append_quota_attributes(
            self, resource_type, resource_uri, resource_detais):

        quota_info = None
        try:
            quota_info = self.__get(resource_type, resource_uri)
        except SOSError as e:
            if("HTTP code: 403" in e.err_text):
                pass
            else:
                raise e
        if(quota_info):
            is_quota_enabled = quota_info["enabled"]
            if(is_quota_enabled is True):
                resource_detais["quota_gb"] = quota_info["quota_gb"]
                resource_detais[
                    "quota_current_capacity"] = quota_info[
                    "current_capacity"]

    '''
    Updates the quota for a given resource uri
    Parameter resource_type :
    Type of the resource ( can be project, tenant etc )
    Parameter resource_uri : Uri of the resource
    Parameter enableQuota : Boolean value true|false
    Parameter quotaCapacityInGb : Capacity in GB, integer value
    '''

    def update(self, enableQuota, quotaCapacityInGb,
               resource_type, resource_uri):
        # code to update the quota
        query_uri = self.__get_quota_uri(resource_type, resource_uri)

        body = json.dumps({
            "quota_enabled": enableQuota,
            "quota_gb": quotaCapacityInGb
        })

        (s, h) = common.service_json_request(self.__ipAddr,
                            self.__port, "PUT", query_uri, body)
        o = common.json_decode(s)
        if(o):
            return o

        return None

    def __get_quota_uri(self, resource_type, resource_uri):
        return (
            singletonURIHelperInstance.getUri(
                resource_type,
                "quota").format(
                resource_uri)
        )

    #CLASS - END

# Update parser arguments


def add_update_parser_arguments(update_parser):
        update_parser.add_argument('-quota_enable', "-qe",
                dest='quota_enable',
                help='Boolean value to enable/disable quota',
                choices=['true', 'false'])
        update_parser.add_argument('-quota_capacity', "-qc",
                                   dest='quota_capacity',
                                   metavar='<quota_capacity>',
                                   help='Quota capacity in GB')
