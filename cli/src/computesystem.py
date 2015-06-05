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
This module contains the computesystem implementation
'''

import common
import json
from common import SOSError
from common import TableGenerator


class ComputeSystem(object):

    URI_COMPUTE_SYSTEM = "/vdc/compute-systems"
    URI_COMPUTE_SYSTEM_ID = URI_COMPUTE_SYSTEM + "/{0}"
    URI_COMPUTE_SYSTEM_DELETE = URI_COMPUTE_SYSTEM_ID + "/deactivate"
    URI_GET_COMPUTE_ELEMENTS = URI_COMPUTE_SYSTEM + "/{0}/compute-elements"
    URI_COMPUTE_SYSTEM_DISCOVER = URI_COMPUTE_SYSTEM_ID + "/discover"
    URI_COMPUTE_SYSTEM_REGISTER = URI_COMPUTE_SYSTEM_ID + "/register"
    URI_COMPUTE_SYSTEM_DEREGISTER = URI_COMPUTE_SYSTEM_ID + "/deregister"
    '''
    Constructor: takes IP address and port of the ViPR instance. These are
    needed to make http requests for REST API
    '''

    def __init__(self, ipAddr, port):
        self.__ipAddr = ipAddr
        self.__port = port

    '''
    Creates and discovers a Compute System in ViPR
    '''

    def create_computesystem(self, type, name, ipaddress,
                portnumber,
                username, password, osinstallnetwork, use_ssl):

        parms = {'name': name,
                 'user_name': username,
                 'password': password,
                 'ip_address': ipaddress,
                 'port_number': portnumber,
                 'system_type': type,
                 'use_ssl': use_ssl
        }
        if(osinstallnetwork):
            parms['os_install_network'] = osinstallnetwork

        body = json.dumps(parms)

        (s, h) = common.service_json_request(self.__ipAddr,
                                self.__port, "POST",
                                self.URI_COMPUTE_SYSTEM,
                                body)
        return common.json_decode(s)

    '''
    Updates the Compute System, and (re)discovers it
    '''
    def update_computesystem(self, name, label, portnumber,
                username, password, osinstallnetwork, use_ssl):

        parms = {}

        if(label):
            parms['name'] = label
        if(portnumber):
            parms['port_number'] = portnumber
        if(username):
            parms['user_name'] = username
        if(password):
            parms['password'] = password
        if(osinstallnetwork):
            parms['os_install_network'] = osinstallnetwork
        if(use_ssl):
            parms['use_ssl'] = use_ssl

        uri = self.query_computesystem(name)
        body = json.dumps(parms)
        (s, h) = common.service_json_request(self.__ipAddr,
                                self.__port, "PUT",
                                self.URI_COMPUTE_SYSTEM_ID.format(uri),
                                body)
        return common.json_decode(s)

    def show_computesystem(self, name, xml=False):
        uri = self.query_computesystem(name)
        return self.computesystem_show_by_uri(uri, xml)

    '''
    Gets a list of all (active) ViPR Compute Systems.
    '''
    def list_computesystem(self):
        (s, h) = common.service_json_request(
                            self.__ipAddr, self.__port,
                            "GET",
                            ComputeSystem.URI_COMPUTE_SYSTEM,
                            None)

        o = common.json_decode(s)
        return o['compute_system']

        '''
    Deletes a Compute System and the discovered information about it from ViPR
    '''
    def delete_computesystem(self, computesystem):
        uri = self.query_computesystem(computesystem)

        (s, h) = common.service_json_request(self.__ipAddr,
                        self.__port, "POST",
                        ComputeSystem.URI_COMPUTE_SYSTEM_DELETE.format(uri),
                        None)
        return common.json_decode(s)

    def discover_computesystem(self, computesystem):
        uri = self.query_computesystem(computesystem)

        (s, h) = common.service_json_request(self.__ipAddr,
                        self.__port, "POST",
                        ComputeSystem.URI_COMPUTE_SYSTEM_DISCOVER.format(uri),
                        None)
        return common.json_decode(s)

    def register_computesystem(self, computesystem):
        uri = self.query_computesystem(computesystem)

        (s, h) = common.service_json_request(self.__ipAddr,
                        self.__port, "POST",
                        ComputeSystem.URI_COMPUTE_SYSTEM_REGISTER.format(uri),
                        None)
        return common.json_decode(s)

    def deregister_computesystem(self, computesystem):
        uri = self.query_computesystem(computesystem)

        (s, h) = common.service_json_request(self.__ipAddr,
                        self.__port, "POST",
                    ComputeSystem.URI_COMPUTE_SYSTEM_DEREGISTER.format(uri),
                        None)

        return common.json_decode(s)

    def query_computesystem(self, name):
        '''
        Returns the UID of the Computesystem specified by the name
        '''

        computesystems = self.list_computesystem()

        for computesystem in computesystems:
            if (computesystem['name'] == name):
                return computesystem['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "computesystem " + name + ": not found")

        return

    def computesystem_show_by_uri(self, uri, xml=False):
        '''
        Makes a REST API call to retrieve details of a Computesystem
        based on its UUID
        '''
        (s, h) = common.service_json_request(
                       self.__ipAddr, self.__port, "GET",
                       ComputeSystem.URI_COMPUTE_SYSTEM_ID.format(uri),
                       None, None, xml)
        if(xml == False):
            o = common.json_decode(s)
            if(o['inactive']):
                return None
            else:
                return o
        #it is xml payload
        else:
            return s

    def get_compute_elements(self, name):

        uri = self.query_computesystem(name)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            ComputeSystem.URI_GET_COMPUTE_ELEMENTS.format(uri), None)

        o = common.json_decode(s)
        return o['compute_element']
    #CLASS - END


#Common parameters for san_fabric parser.
def compute_system_sub_common_parser(cc_common_parser):
    mandatory_args = cc_common_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computesystemname>',
                                dest='name',
                                help='Name of computesystem',
                                required=True)
    mandatory_args.add_argument('-computeip', '-cip',
                                  metavar='<computesystemipaddress>',
                                  dest='computeip',
                                  help='ipaddress of computesystem',
                                  required=True)
    mandatory_args.add_argument('-computeport', '-cpn',
                                  metavar='<computesystemportnumber>',
                                  dest='computeport',
                                  type=int,
                                  help='portnumber of an  computesystem',
                                  required=True)
    mandatory_args.add_argument('-u', '-user',
                                  dest='user',
                                  metavar='<username>',
                                  help='Username of compute system',
                                  required=True)
    mandatory_args.add_argument('-t', '-type',
                                  choices=['ucs'],
                                  dest='type',
                                  help='Type of compute system',
                                  required=True)


def create_computesystem_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Compute System Create cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create an computesystem')
    compute_system_sub_common_parser(create_parser)
    create_parser.add_argument('-osinstallnetwork', '-oin',
                                dest='osinstallnetwork',
                                metavar='<osinstallnetwork>',
                                help='os install network')

    create_parser.add_argument('-ssl', '-usessl',
                               dest='usessl',
                               action='store_true',
                               help='Use SSL or not')
    create_parser.set_defaults(func=computesystem_create)


def show_computesystem_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Compute System show cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show an computesystem')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computesystemname>',
                                dest='name',
                                help='Name of computesystem',
                                required=True)
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    show_parser.set_defaults(func=computesystem_show)


def update_computesystem_parser(subcommand_parsers, common_parser):
    # update command parser
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Compute System update cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update an computesystem')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                help='Name of compute system',
                                metavar='<name>',
                                dest='name',
                                required=True)
    update_parser.add_argument('-label', '-l',
                                metavar='<label>',
                                dest='label',
                                help='new label of computesystem')
    update_parser.add_argument('-computeport', '-cpn',
                                  metavar='<computesystemportnumber>',
                                  dest='computeport',
                                  type=int,
                                  help='portnumber of an  computesystem')
    update_parser.add_argument('-u', '-user',
                                  dest='user',
                                  metavar='<username>',
                                  help='Username of compute system')
    update_parser.add_argument('-osinstallnetwork', '-oin',
                                dest='osinstallnetwork',
                                metavar='<osinstallnetwork>',
                                help='os install network')

    update_parser.add_argument('-ssl', '-usessl',
                               dest='usessl',
                               action='store_true',
                               help='Use SSL or not')

    update_parser.set_defaults(func=computesystem_update)


def list_computesystem_parser(subcommand_parsers, common_parser):
    # show command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Compute System list CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='lists an computesystems')
    list_parser.add_argument('-verbose', '-v',
                             action='store_true',
                             help='List ComputeSystems with details',
                             dest='verbose')
    list_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List ComputeSystems with more details in tabular form',
        dest='long')
    list_parser.set_defaults(func=computesystem_list)


def delete_computesystem_parser(subcommand_parsers, common_parser):
    # show command parser
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Compute System delete cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete an computesystem')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computesystemname>',
                                dest='name',
                                help='Name of computesystem',
                                required=True)
    delete_parser.set_defaults(func=computesystem_delete)


def discover_computesystem_parser(subcommand_parsers, common_parser):
    # show command parser
    discover_parser = subcommand_parsers.add_parser(
        'discover',
        description='ViPR Compute System discover cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Discover an computesystem')
    mandatory_args = discover_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computesystemname>',
                                dest='name',
                                help='Name of computesystem',
                                required=True)
    discover_parser.set_defaults(func=computesystem_discover)


def register_computesystem_parser(subcommand_parsers, common_parser):
    # show command parser
    register_parser = subcommand_parsers.add_parser(
        'register',
        description='ViPR Compute System register cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Register an computesystem')
    mandatory_args = register_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computesystemname>',
                                dest='name',
                                help='Name of computesystem',
                                required=True)
    register_parser.set_defaults(func=computesystem_register)


def deregister_computesystem_parser(subcommand_parsers, common_parser):
    # show command parser
    deregister_parser = subcommand_parsers.add_parser(
        'deregister',
        description='ViPR Compute System deregister cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Deregister an computesystem')
    mandatory_args = deregister_parser.add_argument_group(
                                            'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computesystemname>',
                                dest='name',
                                help='Name of computesystem',
                                required=True)
    deregister_parser.set_defaults(func=computesystem_deregister)


def list_computelement_parser(subcommand_parsers, common_parser):
    # show command parser
    get_element_parser = subcommand_parsers.add_parser(
        'list-compute-elements',
        description='ViPR Compute System list-compute-elements cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Get compute elements of  an computesystem')
    mandatory_args = get_element_parser.add_argument_group(
                                            'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computesystemname>',
                                dest='name',
                                help='Name of computesystem',
                                required=True)
    get_element_parser.add_argument('-verbose', '-v',
                             action='store_true',
                             help='List ComputeSystems with details',
                             dest='verbose')
    get_element_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List ComputeSystems with more details in tabular form',
        dest='long')
    get_element_parser.set_defaults(func=computesystem_get_computelement)


def computesystem_create(args):
    try:
        obj = ComputeSystem(args.ip, args.port)

        passwd = None
        if (args.user and len(args.user) > 0):
            passwd = common.get_password("computesystem")

        obj.create_computesystem(args.type, args.name, args.computeip,
                        args.computeport, args.user, passwd,
                        args.osinstallnetwork, args.usessl)

    except SOSError as e:
        raise common.format_err_msg_and_raise("create", "computesystem",
                                              e.err_text, e.err_code)


def computesystem_show(args):
    try:
        obj = ComputeSystem(args.ip, args.port)
        res = obj.show_computesystem(args.name, args.xml)
        if(res):
            if(args.xml == True):
                return common.format_xml(res)
            else:
                return common.format_json_object(res)

    except SOSError as e:
        raise common.format_err_msg_and_raise("show", "computesystem",
                                              e.err_text, e.err_code)


def computesystem_update(args):
    try:
        obj = ComputeSystem(args.ip, args.port)
        passwd = None
        if (args.user and len(args.user) > 0):
            passwd = common.get_password("computesystem")

        obj.update_computesystem(args.name, args.label,
                    args.computeport, args.user, passwd,
                        args.osinstallnetwork, args.usessl)

    except SOSError as e:
        raise common.format_err_msg_and_raise("update", "computesystem",
                                              e.err_text, e.err_code)


def computesystem_list(args):
    try:
        obj = ComputeSystem(args.ip, args.port)
        uris = obj.list_computesystem()
        output = []
        for uri in uris:
            temp = obj.computesystem_show_by_uri(uri['id'], False)
            if(temp):
                if(args.verbose == False):
                    del temp["service_profile_templates"]
                output.append(temp)
                temp = None
        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            elif(args.long):
                TableGenerator(output,
                        ['name', 'ip_address', 'os_install_network',\
                         'system_type', 'job_discovery_status']).printTable()
            else:
                TableGenerator(output,
                        ['name', 'system_type', 'ip_address',
                                    'job_discovery_status']).printTable()

    except SOSError as e:
        raise common.format_err_msg_and_raise("list", "computesystem",
                                              e.err_text, e.err_code)


def computesystem_query(self, name):
    '''
        Returns the UID of the computesystem specified by the name
    '''
    computesystems = self.computesystem_list()

    for computesystem in computesystems:
        if (computesystem['name'] == name):
            return computesystem['id']

    raise SOSError(SOSError.NOT_FOUND_ERR,
                   "computesystem " + name + ": not found")


def computesystem_delete(args):
    try:
        obj = ComputeSystem(args.ip, args.port)
        obj.delete_computesystem(args.name)

    except SOSError as e:
        raise common.format_err_msg_and_raise("delete", "computesystem",
                                              e.err_text, e.err_code)


def computesystem_discover(args):
    try:
        obj = ComputeSystem(args.ip, args.port)
        obj.discover_computesystem(args.name)

    except SOSError as e:
        raise common.format_err_msg_and_raise("discover", "computesystem",
                                              e.err_text, e.err_code)


def computesystem_register(args):
    try:
        obj = ComputeSystem(args.ip, args.port)
        obj.register_computesystem(args.name)

    except SOSError as e:
        raise common.format_err_msg_and_raise("register", "computesystem",
                                              e.err_text, e.err_code)


def computesystem_deregister(args):
    try:
        obj = ComputeSystem(args.ip, args.port)
        obj.deregister_computesystem(args.name)

    except SOSError as e:
        raise common.format_err_msg_and_raise("deregister", "computesystem",
                                              e.err_text, e.err_code)


def computesystem_get_computelement(args):
    try:
        obj = ComputeSystem(args.ip, args.port)
        output = obj.get_compute_elements(args.name)

        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            elif(args.long):
                TableGenerator(output,
                               ['name', 'system_type'
                                'job_discovery_status']).printTable()
            else:
                TableGenerator(output, ['name',
                                        'system_type',
                                        'job_discovery_status',
                                        'registration_status'
                                    ]).printTable()

    except SOSError as e:
        raise common.format_err_msg_and_raise("get-compute-element",
                                              "computesystem",
                                              e.err_text, e.err_code)


#
# ComputeSystem Main parser routine
#
def computesystem_parser(parent_subparser, common_parser):

    # main export group parser
    parser = parent_subparser.add_parser(
        'computesystem',
        description='ViPR Compute System CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on ComputeSystem')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # create command parser
    create_computesystem_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_computesystem_parser(subcommand_parsers, common_parser)

    # show command parser
    show_computesystem_parser(subcommand_parsers, common_parser)

    # list command parser
    list_computesystem_parser(subcommand_parsers, common_parser)

    # update command parser
    update_computesystem_parser(subcommand_parsers, common_parser)

    # get compute element command parser
    list_computelement_parser(subcommand_parsers, common_parser)

    # discover compute element command parser
    discover_computesystem_parser(subcommand_parsers, common_parser)

    #register compute element command parser
    register_computesystem_parser(subcommand_parsers, common_parser)

    #deregister compute element command parser
    deregister_computesystem_parser(subcommand_parsers, common_parser)
