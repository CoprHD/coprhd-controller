#!/usr/bin/python

# Copyright 2015 EMC Corporation
# All Rights Reserved

# Copyright (c) 2012 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.


import common
import json
from common import SOSError
from virtualarray import VirtualArray
import sys


class Network(object):

    '''
    The class definition for operations on 'Network'.
    '''
    # Commonly used URIs for the 'Network' module
    URI_NETWORKS = '/vdc/networks'
    URI_NETWORK = URI_NETWORKS + '/{0}'
    URI_NETWORK_ENDPOINTS = URI_NETWORK + '/endpoints'
    URI_NETWORK_ENDPOINT = URI_NETWORK_ENDPOINTS + '/{1}'
    URI_VIRTUALARRAY_NETWORK = '/vdc/varrays/{0}/networks'
    URI_NETWORK_DEACTIVATE = '/vdc/networks/{0}/deactivate'
    URI_NETWORK_REGISTER = '/vdc/networks/{0}/register'
    URI_NETWORK_DEREGISTER = '/vdc/networks/{0}/deregister'
    URI_NETWORK_SEARCH = '/vdc/networks/search?name={0}'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    '''
    search the network by given name
    '''

    def query_by_name(self, nwName):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            self.URI_NETWORK_SEARCH.format(nwName), None)

        o = common.json_decode(s)
        if not o:
            return None

        resources = common.get_node_value(o, "resource")
        if(len(resources) > 0):
            component_uri = resources[0]['id']
            nw_details = self.show_by_uri(component_uri, False)
            if(nw_details is not None):
                return component_uri

        return None

    # Lists networks
    def list_networks(self, varray):
        '''
        Makes REST API call to list networks in a varray
        Parameters:
            varray: name of varray
        Returns:
            List of network uuids in JSONo response payload
        '''
        varray_uri = None
        if(varray):
            varray_obj = VirtualArray(self.__ipAddr, self.__port)
            varray_uri = varray_obj.varray_query(varray)

        return self.list_by_uri(varray_uri)

    # Get the list of network given a varray uri
    def list_by_uri(self, varray_uri):
        '''
        Makes REST API call and retrieves networks based on varray UUID
        Parameters:
            project_uri: UUID of varray
        Returns:
            List of network UUIDs in JSON response payload
        '''
        if(varray_uri):
            uri = Network.URI_VIRTUALARRAY_NETWORK.format(varray_uri)
        else:
            uri = Network.URI_NETWORKS

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET", uri, None)
        o = common.json_decode(s)
        return o['network']

    def list_by_hrefs(self, hrefs):
        return common.list_by_hrefs(self.__ipAddr, self.__port, hrefs)

    # Shows network information given its name
    def show(self, name, xml=False):
        '''
        Retrieves network details based on network name
        Parameters:
            name: name of the network.
        Returns:
            Network details in JSON response payload
        '''

        turi = self.query_by_name(name)
        tz = self.show_by_uri(turi)
        if ((tz and tz['name'] == name) or (tz and tz['id'] == name)):
            if(xml):
                tz = self.show_by_uri(turi, xml)
            return tz

        raise SOSError(
            SOSError.NOT_FOUND_ERR, "Network " +
            str(name) + ": not found")

    def assign(self, name, varray=None):
        '''
        Retrieves network details based on network name
        Parameters:
            name: name of the network.
            varray: varray to be assigned
        Returns:
            Network details in JSON response payload
        '''

        turi = self.query_by_name(name)
        nuri = None
        nlst = []
        if(varray):
            varray_obj = VirtualArray(self.__ipAddr, self.__port)
            for item in varray:
                nuri = varray_obj.varray_query(item)
                nlst.append(nuri)

        if(len(nlst) > 0):
            parms = {
                'varrays': nlst
            }
        else:
            parms = {
                'varrays': []
            }

        body = json.dumps(parms)
        common.service_json_request(self.__ipAddr, self.__port,
                                    "PUT",
                                    Network.URI_NETWORK.format(turi),
                                    body)

    # Shows network information given its uri
    def show_by_uri(self, uri, xml=False):
        '''
        Makes REST API call and retrieves network details based on UUID
        Parameters:
            uri: UUID of network
        Returns:
            Network details in JSON response payload
        '''

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "GET",
                                             Network.URI_NETWORK.format(uri),
                                             None)
        o = common.json_decode(s)
        if('inactive' in o):
            if(o['inactive']):
                return None

        if(xml):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "GET", Network.URI_NETWORK.format(uri), None, None, xml)
            return s

        return o

    # Creates a network given varray, name and type
    def create(self, name, nwtype, varrays=None, endpoints=None):
        '''
        Makes REST API call to create network
        Parameters:
            name: name of network
            type: type of transport protocol. FC, IP or Ethernet
            varrays : List of varrays to be associated
            endpoints : List of endpoints to be added to network
        Returns:
            Created task details in JSON response payload
        '''

        networkId = self.query_by_name(name)
        if(networkId):
            raise SOSError(
                SOSError.ENTRY_ALREADY_EXISTS_ERR,
                "Network with name " + name + " already exists")

        request = dict()
        request['name'] = name
        request['transport_type'] = nwtype

        if(varrays):
            request['varrays'] = self.getVarrayList(varrays)

        if(endpoints):
            request['endpoints'] = self.getEndPointList(endpoints)

        body = json.dumps(request)
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                             Network.URI_NETWORKS,
                                             body)
        o = common.json_decode(s)
        return o

    def getVarrayList(self, varrays):
        varray_obj = VirtualArray(self.__ipAddr, self.__port)
        return varray_obj.convertNamesToUris(varrays)

    def getEndPointList(self, endpoints):
        endpointList = []
        if(endpoints):
            for endpoint in endpoints:
                endpointList.append(endpoint)

        return endpointList

    # Update a network and varray info
    def update(self, currentName, newName, varraystoadd=None,
               varraystoremove=None, endpointstoadd=None,
               endpointstoremove=None):
        '''
        Makes REST API call to update network information
        Parameters:
            currentName: name of the network to be updated
            newName: New name for the network
            varraystoadd: List of varrays to be associated
            varraystoremove : List of varrays to be disassociated
            endpointstoadd : List of end points to be added
            endpointstoremove : List of end  points to be removed

        Returns
            Created task details in JSON response payload
        '''

        networkId = self.query_by_name(currentName)
        if(networkId is None):
            raise SOSError(
                SOSError.ENTRY_ALREADY_EXISTS_ERR,
                "Network with name " + currentName + " does not exist")

        # varray changes
        addvarrayList = self.getVarrayList(varraystoadd)
        removevarrayList = self.getVarrayList(varraystoremove)

        # endpoint changes
        addendpointList = self.getEndPointList(endpointstoadd)
        removeendpointList = self.getEndPointList(endpointstoremove)

        requestParams = {
            'name': newName,
            'varray_assignment_changes': {
                'add': {'varrays': addvarrayList},
                'remove': {'varrays': removevarrayList}
            },
            'endpoint_changes': {'add': addendpointList,
                                 'remove': removeendpointList
                                 }
        }

        body = json.dumps(requestParams)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "PUT", Network.URI_NETWORK.format(networkId), body)
        o = common.json_decode(s)
        return o

    # adds an endpoint to a network
    def add_endpoint(self, name, endpoint):
        '''
        Adds endpoint to a network
        Parameters:
            varray: name of the varray
            name: name of network
            endpoint: endpoint
        '''

        endpoint_exists = True

        try:
            tz = self.show(name)
            if ("endpoints" in tz):
                endpoints = tz['endpoints']
                if(endpoint in endpoints):
                    endpoint_exists = True
                else:
                    endpoint_exists = False
            else:
                endpoint_exists = False
        except SOSError as e:
            raise e

        if(endpoint_exists):
            raise SOSError(
                SOSError.ENTRY_ALREADY_EXISTS_ERR,
                "Endpoint: " +
                endpoint + " already added to " + name + " network.")

        network_uri = self.query_by_name(name)

        body = json.dumps({'endpoints': [endpoint],
                           'op': 'add'})

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "PUT", Network.URI_NETWORK_ENDPOINTS.format(network_uri),
            body)
        o = common.json_decode(s)
        return o

    # removes an endpoint from a network
    def remove_endpoint(self, name, endpoint):
        '''
        Adds endpoint to a network
        Parameters:
            name: name of network
            endpoint: endpoint
        '''

        network_uri = self.query_by_name(name)

        body = json.dumps({'endpoints': [endpoint],
                           'op': 'remove'})

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "PUT", Network.URI_NETWORK_ENDPOINTS.format(network_uri),
            body)
        o = common.json_decode(s)
        return o

    # Deletes a network given a network name
    def delete(self, name):
        '''
        Deletes a network based on network name
        Parameters:
            name: name of network
        '''

        network_uri = self.query_by_name(name)
        if(network_uri is None):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Network with name " + name + " not found")

        return self.delete_by_uri(network_uri)

    # Deletes a network given a network uri
    def delete_by_uri(self, uri):
        '''
        Deletes a network based on network uri
        Parameters:
            uri: uri of network
        '''

        common.service_json_request(self.__ipAddr, self.__port,
                                    "POST",
                                    Network.URI_NETWORK_DEACTIVATE.format(uri),
                                    None)
        return

    def register(self, name):
        '''
        register a network
        Parameters:
            name: name of network
        '''

        network_uri = self.query_by_name(name)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Network.URI_NETWORK_REGISTER.format(network_uri),
            None)
        o = common.json_decode(s)
        return o

    def deregister(self, name):
        '''
        register a network
        Parameters:
            name: name of network
        '''

        network_uri = self.query_by_name(name)

        common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Network.URI_NETWORK_DEREGISTER.format(network_uri),
            None)


# Network Create routines
def create_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Network Create CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create a network')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of network',
                                metavar='<network>',
                                dest='name',
                                required=True)
    mandatory_args.add_argument('-transport_type', '-t',
                                help='Type of transport protocol',
                                choices=["FC", "IP", "Ethernet", "ScaleIO"],
                                dest='transport_type',
                                required=True)

    create_parser.add_argument(
        '-varrays',
        help='Name of one or more varrays to be associated with the network',
        metavar='<varrays>',
        dest='varrays',
        nargs="+")

    create_parser.add_argument(
        '-endpoints',
        help='Name of one or more endpoints to be added to the network',
        metavar='<endpoints>',
        dest='endpoints',
        nargs="+")

    create_parser.set_defaults(func=network_create)


'''
Network creation command
Parameter name : Name of the network to be created
Parameter transport_type : Type of transport protocol for the network
Parameter varrays : List of varrays to be associated with the network
Parameter endpoints : List of endpoints to be added to the network
'''


def network_create(args):
    obj = Network(args.ip, args.port)
    try:
        obj.create(args.name, args.transport_type,
                   args.varrays, args.endpoints)
    except SOSError as e:
        common.format_err_msg_and_raise("create", "network",
                                        e.err_text, e.err_code)

# Network Update routines


def update_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Network Update CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update a network')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of network to be updated',
                                metavar='<network>',
                                dest='name',
                                required=True)

    update_parser.add_argument('-newname', '-nn',
                               metavar='<newname>',
                               dest='newname',
                               help='New Name for the network',
                               required=False)

    update_parser.add_argument(
        '-varray_add', '-vaadd',
        metavar='<varray_add>',
        dest='varray_add',
        help='List of new varrays to be associated with the network',
        nargs="+")

    update_parser.add_argument(
        '-varray_remove', '-varemove',
        metavar='<varray_remove>',
        dest='varray_remove',
        help='List of varrays to be dis-associated from the network',
        nargs="+")

    update_parser.add_argument(
        '-endpoint_add', '-epadd',
        metavar='<endpoint_add>',
        dest='endpoint_add',
        help='List of new endpoints to be associated with the network',
        nargs="+")

    update_parser.add_argument(
        '-endpoint_remove', '-epremove',
        metavar='<endpoint_remove>',
        dest='endpoint_remove',
        help='List of endpoints to be dis-associated from the network',
        nargs="+")

    update_parser.set_defaults(func=network_update)


def network_update(args):
    obj = Network(args.ip, args.port)

    # validate input
    if(args.newname is None and args.varray_add is None
       and args.varray_remove is None and args.endpoint_add is None and
       args.endpoint_remove is None):
        raise SOSError(
            SOSError.CMD_LINE_ERR, sys.argv[0] + " " + sys.argv[1] +
            " " + sys.argv[2] + ": error:" + "At least one of the arguments :"
            "-newname -varray_add "
            "-varray_remove -endpoint_add -endpoint_remove"
            " should be provided to update the network")
    try:
        obj.update(
            args.name, args.newname, args.varray_add, args.varray_remove,
            args.endpoint_add, args.endpoint_remove)
    except SOSError as e:
        common.format_err_msg_and_raise("update", "network",
                                        e.err_text, e.err_code)


# Network Delete routines

def delete_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Network Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a network')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of network',
                                metavar='<network>',
                                dest='name',
                                required=True)

    delete_parser.set_defaults(func=network_delete)


def network_delete(args):
    obj = Network(args.ip, args.port)
    try:
        obj.delete(args.name)
    except SOSError as e:
        common.format_err_msg_and_raise("delete", "network",
                                        e.err_text, e.err_code)


# Network Show routines

def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Network Show CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show details of network')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of network',
                                metavar='<network>',
                                dest='name',
                                required=True)
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    show_parser.set_defaults(func=network_show)


def network_show(args):
    obj = Network(args.ip, args.port)
    try:
        res = obj.show(args.name, args.xml)
        if(res):
            if (args.xml):
                return common.format_xml(res)
            return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "network",
                                        e.err_text, e.err_code)


def assign_parser(subcommand_parsers, common_parser):
    assign_parser = subcommand_parsers.add_parser(
        'assign',
        description='ViPR Network Assign CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Assign varray to network')
    mandatory_args = assign_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of network',
                                metavar='<network>',
                                dest='name',
                                required=True)

    assign_parser.add_argument('-varray', '-va',
                               metavar='<varray>',
                               dest='varray',
                               help='Name of varray',
                               nargs='*')
    assign_parser.set_defaults(func=network_assign)


def network_assign(args):
    obj = Network(args.ip, args.port)
    try:
        obj.assign(args.name, args.varray)
    except SOSError as e:
        common.format_err_msg_and_raise("assign varray", "network",
                                        e.err_text, e.err_code)

# Network List routines


def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Network List CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists networks in a varray')
    list_parser.add_argument('-varray', '-va',
                             metavar='<varray>',
                             dest='varray',
                             help='Name of varray')
    list_parser.add_argument('-verbose', '-v',
                             dest='verbose',
                             help='List networks with details',
                             action='store_true')
    list_parser.add_argument('-long', '-l',
                             dest='long',
                             help='List network in table',
                             action='store_true')

    list_parser.set_defaults(func=network_list)


def network_list(args):
    obj = Network(args.ip, args.port)
    from common import TableGenerator
    try:
        uris = obj.list_networks(args.varray)
        if(len(uris) > 0):
            output = []
            for item in (uris):
                onetwork = common.show_by_href(args.ip, args.port, item)
                if("varray" in onetwork):
                    ovarray = common.show_by_href(args.ip, args.port,
                                                   onetwork['varray'])
                    onetwork["varray"] = ovarray['name']

                output.append(onetwork)
            # show a short table
            if(args.verbose is False and args.long is False):
                TableGenerator(output,
                               ['module/name', 'transport_type', "varray",
                                "fabric_id",
                                "registration_status"]).printTable()
            # show a long table
            if(args.verbose is False and args.long is True):
                TableGenerator(
                    output,
                    ['module/name', 'transport_type', "varray", "fabric_id",
                     "endpoints", "registration_status"]).printTable()
            # show all items in json format
            if(args.verbose):
                return common.format_json_object(output)

        else:
            return
    except SOSError as e:
        raise e


# Network add/remove endpoint routines

def endpoint_parser(subcommand_parsers, common_parser):
    endpoint_parser = subcommand_parsers.add_parser(
        'endpoint',
        description='ViPR Network endpoint CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='add/remove endpoints')
    subcommand_parsers = endpoint_parser.add_subparsers(
        help='Use one of the commands')

    common_args = common_parser.add_argument_group('mandatory arguments')
    common_args.add_argument('-name', '-n',
                             help='Name of network',
                             metavar='<network>',
                             dest='name',
                             required=True)
    common_args.add_argument('-endpoint', '-e',
                             help='endpoint',
                             metavar='<endpoint>',
                             dest='endpoint',
                             required=True)

    add_parser = subcommand_parsers.add_parser('add',
                                               parents=[common_parser],
                                               conflict_handler='resolve',
                                               help='Add endpoint')
    remove_parser = subcommand_parsers.add_parser('remove',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='Remove endpoint')

    add_parser.set_defaults(func=add_endpoint)

    remove_parser.set_defaults(func=remove_endpoint)


def add_endpoint(args):
    obj = Network(args.ip, args.port)
    try:
        obj.add_endpoint(args.name, args.endpoint)
    except SOSError as e:
        common.format_err_msg_and_raise("add_endpoint", "network",
                                        e.err_text, e.err_code)


def remove_endpoint(args):
    obj = Network(args.ip, args.port)
    try:
        obj.remove_endpoint(args.name, args.endpoint)
    except SOSError as e:
        common.format_err_msg_and_raise("remove_endpoint", "network",
                                        e.err_text, e.err_code)


def register_parser(subcommand_parsers, common_parser):
    register_parser = subcommand_parsers.add_parser(
        'register',
        description='ViPR Network Register CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='register a network')
    mandatory_args = register_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of network',
                                metavar='<network>',
                                dest='name',
                                required=True)
    register_parser.set_defaults(func=network_register)


def network_register(args):
    obj = Network(args.ip, args.port)
    try:
        obj.register(args.name)
    except SOSError as e:
        common.format_err_msg_and_raise("register", "network",
                                        e.err_text, e.err_code)


def deregister_parser(subcommand_parsers, common_parser):
    deregister_parser = subcommand_parsers.add_parser(
        'deregister',
        description='ViPR Network Deregister CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Deregister a network')
    mandatory_args = deregister_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of network',
                                metavar='<network>',
                                dest='name',
                                required=True)
    deregister_parser.set_defaults(func=network_deregister)


def network_deregister(args):
    obj = Network(args.ip, args.port)
    try:
        obj.deregister(args.name)
    except SOSError as e:
        common.format_err_msg_and_raise("deregister", "network",
                                        e.err_text, e.err_code)


#
# Network Main parser routine
#
def network_parser(parent_subparser, common_parser):
    # main project parser

    parser = parent_subparser.add_parser('network',
                                         description='ViPR Network CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on Network')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # update command parser
    update_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # varray assign command parser
    assign_parser(subcommand_parsers, common_parser)

    # register network command parser
    register_parser(subcommand_parsers, common_parser)

    # deregister command parser
    deregister_parser(subcommand_parsers, common_parser)

    # endpoint add/remove command parser
    endpoint_parser(subcommand_parsers, common_parser)
