#!/usr/bin/python

# Copyright (c) 2017 DellEMC Corporation
# All Rights Reserved

import common
from common import SOSError

class RemoteReplicationGroup(object):
    '''
    Encapsulate operations around remote replication group
    '''

    # Remote replication group URIs
    URI_REMOTEREPLICATIONGROUP_LIST = '/vdc/block/remote-replication-groups'
    URI_REMOTEREPLICATIONGROUP_DETAILS = '/vdc/block/remote-replication-groups/{0}'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def show_by_uri(self, uri):
        '''
        Makes a REST API call to retrieve details of a
        remote replication group based on its UUID
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            RemoteReplicationGroup.URI_REMOTEREPLICATIONGROUP_DETAILS.format(uri), None)
        return common.json_decode(s)

    def list(self):
        '''
        Makes a REST API call to retrieve list of all remote replication groups
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            RemoteReplicationGroup.URI_REMOTEREPLICATIONGROUP_LIST,
            None)
        o = common.json_decode(s)

        if (not o or "remote_replication_group" not in o):
            return []

        return common.get_node_value(o, 'remote_replication_group')

    def query_by_name(self, name):
        '''
        Return URI of remote repliation group of specified name
        '''
        rrgroups = self.list()
        for rrgroup in rrgroups:
            rrgroup = self.show_by_uri(rrgroup['id'])
            if (name == rrgroup['name']):
                return rrgroup['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       'Remote repliation group not found with name (device label): '
                       + name)