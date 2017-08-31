#!/usr/bin/python

# Copyright (c) 2013-14 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

'''
This module contains the computelement implementation
'''

import common
import json
from common import SOSError
from common import TableGenerator
from computesystem import ComputeSystem


class ComputeElement(object):

    URI_COMPUTE_ELEMENT = "/vdc/compute-elements"
    URI_COMPUTE_ELEMENT_ID = URI_COMPUTE_ELEMENT + "/{0}"
    URI_GET_COMPUTE_ELEMENTS = "/vdc/compute-systems/{0}/compute-elements"
    URI_COMPUTE_ELEMENT_REGISTER = URI_COMPUTE_ELEMENT_ID + "/register"
    URI_COMPUTE_ELEMENT_DEREGISTER = URI_COMPUTE_ELEMENT_ID + "/deregister"
    '''
    Constructor: takes IP address and port of the ViPR instance. These are
    needed to make http requests for REST API
    '''

    def __init__(self, ipAddr, port):
        self.__ipAddr = ipAddr
        self.__port = port
    '''
    Gets the data for a compute element.
    '''
    def show_compute_element(self, name, computesystem, xml=False):
        uri = self.query_compute_element(name, computesystem)
        return self.computelement_show_by_uri(uri, xml)

    '''
    Gets the ids and self links for all compute elements.
    '''
    def list_compute_element(self, name):
        uri = ComputeSystem(self.__ipAddr,
                                     self.__port).query_computesystem(name)
        (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port,
                    "GET",
                    ComputeElement.URI_GET_COMPUTE_ELEMENTS.format(uri),
                            None)

        o = common.json_decode(s)
        return o['compute_element']

    '''
    Register compute system compute element
    '''
    def register_compute_element(self, name, computesystem):
        uri = self.query_compute_element(name, computesystem)

        (s, h) = common.service_json_request(self.__ipAddr,
                    self.__port, "POST",
                    ComputeElement.URI_COMPUTE_ELEMENT_REGISTER.format(uri),
                    None)
        return None
        '''
    Allows the user to deregister a registered compute element so that it is no
     longer used by the system. This simply sets the registration_status of
     the compute element to UNREGISTERED.
    '''
    def deregister_compute_element(self, name, computesystem):
        uri = self.query_compute_element(name, computesystem)

        (s, h) = common.service_json_request(self.__ipAddr,
                        self.__port, "POST",
                    ComputeElement.URI_COMPUTE_ELEMENT_DEREGISTER.format(uri),
                        None)
        return None

    def query_compute_element(self, name, computesystemname):
        '''
        Returns the UID of the ComputeElement specified by the name
        '''

        computelements = ComputeSystem(self.__ipAddr,
                    self.__port).get_compute_elements(computesystemname)

        for computelement in computelements:
            if (computelement['name'] == name):
                return computelement['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "computelement " + name + ": not found")

        return
    '''
    Gets the data for a compute element.
    '''
    def computelement_show_by_uri(self, uri, xml=False):
        '''
        Makes a REST API call to retrieve details of a ComputeElement
        based on its UUID
        '''

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            ComputeElement.URI_COMPUTE_ELEMENT_ID.format(uri),
            None, None, xml)

        if(not xml):
            o = common.json_decode(s)

            if('inactive' in o):
                if(o['inactive']):
                    return None
        else:
            return s

        return o

    def get_compute_elements(self, name):

        uri = self.query_compute_element(name)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            ComputeElement.URI_GET_COMPUTE_ELEMENTS.format(uri), None)

        o = common.json_decode(s)
        return o['compute_element']
    #CLASS - END


def show_compute_element_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Compute Pool show cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show an computelement')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computelementname>',
                                dest='name',
                                help='Name of computelement',
                                required=True)
    mandatory_args.add_argument('-computesystem', '-cs',
                                metavar='<computesystemname>',
                                dest='computesystem',
                                help='Name of computesystem',
                                required=True)

    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    show_parser.set_defaults(func=computelement_show)


def list_compute_element_parser(subcommand_parsers, common_parser):
    # show command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Compute System list CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='lists an computelements')
    mandatory_args = list_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computesystemname>',
                                dest='name',
                                help='Name of computesystem',
                                required=True)

    list_parser.add_argument('-verbose', '-v',
                             action='store_true',
                             help='List ComputeElements with details',
                             dest='verbose')
    list_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List ComputeElements with more details in tabular form',
        dest='long')
    list_parser.set_defaults(func=computelement_list)


def register_compute_element_parser(subcommand_parsers, common_parser):
    # show command parser
    register_parser = subcommand_parsers.add_parser(
        'register',
        description='ViPR Compute System register cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Register an computelement')
    mandatory_args = register_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computelementname>',
                                dest='name',
                                help='Name of computelement',
                                required=True)
    mandatory_args.add_argument('-computesystem', '-cs',
                                metavar='<computesystemname>',
                                dest='computesystem',
                                help='Name of computesystem',
                                required=True)
    register_parser.set_defaults(func=computelement_register)


def deregister_compute_element_parser(subcommand_parsers, common_parser):
    # show command parser
    deregister_parser = subcommand_parsers.add_parser(
        'deregister',
        description='ViPR Compute System deregister cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Deregister an computelement')
    mandatory_args = deregister_parser.add_argument_group(
                                            'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computelementname>',
                                dest='name',
                                help='Name of computelement',
                                required=True)
    mandatory_args.add_argument('-computesystem', '-cs',
                                metavar='<computesystemname>',
                                dest='computesystem',
                                help='Name of computesystem',
                                required=True)
    deregister_parser.set_defaults(func=computelement_deregister)


def computelement_show(args):
    try:
        obj = ComputeElement(args.ip, args.port)
        res = obj.show_compute_element(args.name, args.computesystem, args.xml)
        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)

    except SOSError as e:
        raise common.format_err_msg_and_raise("show", "computelement",
                                              e.err_text, e.err_code)


def computelement_list(args):
    try:
        obj = ComputeElement(args.ip, args.port)
        uris = obj.list_compute_element(args.name)
        output = []
        for uri in uris:
            temp = obj.computelement_show_by_uri(uri['id'], False)
            if(temp):
                output.append(temp)

        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            elif(args.long):
                TableGenerator(output,
                               ['name', 'system_type',
                                'num_of_cores',
                                'number_of_processors',
                                 'processor_speed', 'ram',
                                 'registration_status',
                                'job_discovery_status',
                                'available']).printTable()
            else:
                TableGenerator(output, ['name', 'system_type',
                                        'registration_status',
                                        'job_discovery_status',
                                        'available']).printTable()
    except SOSError as e:
        raise common.format_err_msg_and_raise("list", "computelement",
                                              e.err_text, e.err_code)


def computelement_query(self, name):
    '''
        Returns the UID of the computelement specified by the name
    '''
    computelements = self.computelement_list()

    for computelement in computelements:
        if (computelement['name'] == name):
            return computelement['id']

    raise SOSError(SOSError.NOT_FOUND_ERR,
                   "computelement " + name + ": not found")


def computelement_register(args):
    try:
        obj = ComputeElement(args.ip, args.port)
        obj.register_compute_element(args.name, args.computesystem)

    except SOSError as e:
        raise common.format_err_msg_and_raise("register", "computelement",
                                              e.err_text, e.err_code)


def computelement_deregister(args):
    try:
        obj = ComputeElement(args.ip, args.port)
        obj.deregister_compute_element(args.name, args.computesystem)

    except SOSError as e:
        raise common.format_err_msg_and_raise("deregister", "computelement",
                                              e.err_text, e.err_code)


# ComputeElement Main parser routine
#
def computelement_parser(parent_subparser, common_parser):

    # main export group parser
    parser = parent_subparser.add_parser(
        'computelement',
        description='ViPR Compute Pool CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on ComputeElement')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # show command parser
    show_compute_element_parser(subcommand_parsers, common_parser)

    # list command parser
    list_compute_element_parser(subcommand_parsers, common_parser)

    #register compute element command parser
    register_compute_element_parser(subcommand_parsers, common_parser)

    #deregister compute element command parser
    deregister_compute_element_parser(subcommand_parsers, common_parser)
