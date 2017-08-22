#!/usr/bin/python

# Copyright (c) 2017 DellEMC Corporation
# All Rights Reserved

import common
from common import SOSError

class RemoteReplicationSet(object):
    '''
    Encapsulate operations around remote replication set
    '''

    # Remote replication set URIs
    URI_REMOTEREPLICATIONSET_LIST = '/vdc/block/remote-replication-sets'
    URI_REMOTEREPLICATIONSET_DETAILS = '/vdc/block/remote-replication-sets/{0}'

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
        remote replication set based on its UUID
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            RemoteReplicationSet.URI_REMOTEREPLICATIONSET_DETAILS.format(uri), None)
        return common.json_decode(s)

    def list(self):
        '''
        Makes a REST API call to retrieve list of all remote replication sets
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            RemoteReplicationSet.URI_REMOTEREPLICATIONSET_LIST,
            None)
        o = common.json_decode(s)

        if (not o or "remote_replication_set" not in o):
            return []

        return common.get_node_value(o, 'remote_replication_set')

    def query_by_name(self, name):
        '''
        Return URI of remote repliation set of specified name
        '''
        rrsets = self.list()
        for rrset in rrsets:
            rrset = self.show_by_uri(rrset['id'])
            if (name == rrset['name']):
                return rrset['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       'Remote repliation set not found with name (device label): '
                       + name)