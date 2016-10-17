#!/usr/bin/python
# Copyright (c)2012 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import json
import common

from common import SOSError


class Objectcos(object):

    '''
    The class definition for operations on 'Objectcos'.
    '''

    # Commonly used URIs for the 'objectcos' module

    URI_SERVICES_BASE = ''
    URI_VPOOLS = URI_SERVICES_BASE + '/{0}/vpools'
    URI_RESOURCE_DEACTIVATE = '{0}/deactivate'
    URI_OBJ_VPOOL = URI_SERVICES_BASE + '/{0}/data-services-vpools'
    URI_VPOOL_INSTANCE = URI_VPOOLS + '/{1}'
    URI_OBJ_VPOOL_INSTANCE = URI_OBJ_VPOOL + '/{1}'
    URI_VPOOL_ACLS = URI_VPOOL_INSTANCE + '/acl'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the SOS instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def objectcos_list(self):
        '''
        Makes a REST API call to retrieve details of a
        objectcos based on its UUID
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, common.OBJCTRL_PORT, "GET",
            Objectcos.URI_OBJ_VPOOL.format('object'), None)

        o = common.json_decode(s)

        if (not o):
            return {}
        else:
            return o['data_services_vpool']

    def objectcos_query(self, name):
        if (common.is_uri(name)):
            return name

        objcoslst = self.objectcos_list()

        for cs in objcoslst:
            cos_res = self.objectcos_show_by_uri(cs['id'])
            if (cos_res['name'] == name):
                return cos_res['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "Object Cos query failed: object cos with name " +
                       name + " not found")

    def objectcos_show_by_uri(self, uri):
        (s, h) = common.service_json_request(
            self.__ipAddr, common.OBJCTRL_PORT, "GET",
            self.URI_OBJ_VPOOL_INSTANCE.format('object', uri), None)

        o = common.json_decode(s)

        return o

    def objectcos_show(self, name):
        uri = self.objectcos_query(name)

        return self.objectcos_show_by_uri(uri)

    def objectcos_add(self, name, description):

        objcoslst = self.objectcos_list()

        for cs in objcoslst:
            if(cs['name'] == name):
                raise SOSError(
                    SOSError.ENTRY_ALREADY_EXISTS_ERR,
                    "Objectcos create failed: object cos " +
                    "with same name already exists")

        parms = dict()
        if (name):
            parms['name'] = name
        if (description):
            parms['description'] = description

        body = None

        if (parms):
            body = json.dumps(parms)

        (s, h) = common.service_json_request(
            self.__ipAddr, common.OBJCTRL_PORT, "POST",
            self.URI_OBJ_VPOOL.format('object'), body)

        o = common.json_decode(s)

        return o

    def objectcos_delete(self, name):
        '''
        Makes a REST API call to delete a objectcos by its UUID
        '''
        uri = self.objectcos_query(name)
        (s, h) = common.service_json_request(
            self.__ipAddr, common.OBJCTRL_PORT, "POST",
            self.URI_OBJ_VPOOL_INSTANCE.format('object', uri) + "/deactivate",
            None)

        return str(s) + " ++ " + str(h)


def add_parser(subcommand_parsers, common_parser):
    # add command parser
    add_parser = subcommand_parsers.add_parser(
        'create',
        description='SOS Objectcos Create CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create an objectcos')

    mandatory_args = add_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                help='name',
                                metavar='<name>',
                                dest='name',
                                required=True)

    mandatory_args.add_argument('-description', '-desc',
                                help='description of object cos',
                                metavar='<description>',
                                dest='description',
                                required=True)

    add_parser.set_defaults(func=objectcos_add)


def objectcos_add(args):
    obj = Objectcos(args.ip, args.port)
    try:
        res = obj.objectcos_add(args.name, args.description)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code, "Objectcos " +
                           args.name + ": Create failed\n" + e.err_text)
        else:
            raise e


# NEIGHBORHOOD Delete routines

def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='SOS Objectcos delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete an objectcos')

    mandatory_args = delete_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                help='name of object  cos',
                                metavar='<name>',
                                dest='name',
                                required=True)

    delete_parser.set_defaults(func=objectcos_delete)


def objectcos_delete(args):
    obj = Objectcos(args.ip, args.port)
    try:
        res = obj.objectcos_delete(args.name)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Objectcos delete failed: " + e.err_text)
        else:
            raise e


def show_parser(subcommand_parsers, common_parser):

    # delete command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='SOS Objectcos show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show an objectcos')

    mandatory_args = show_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                help='name of object cos',
                                metavar='<name>',
                                dest='name',
                                required=True)

    show_parser.set_defaults(func=objectcos_show)


def objectcos_show(args):
    obj = Objectcos(args.ip, args.port)
    try:
        res = obj.objectcos_show(args.name)
        return common.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Objectcos show failed: " + e.err_text)
        else:
            raise e
# NEIGHBORHOOD Show routines


def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='SOS Objectcos List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List an Objectcos')

    list_parser.set_defaults(func=objectcos_list)


def objectcos_list(args):
    obj = Objectcos(args.ip, args.port)
    try:
        res = obj.objectcos_list()

        output = []

        for iter in res:
            tmp = dict()
            tmp['objectcos'] = iter['name']
            output.append(tmp)

        if(res):
            from common import TableGenerator
            TableGenerator(output, ['objectcos']).printTable()

    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Objectcos list failed: " + e.err_text)
        else:
            raise e


#
# Objectcos Main parser routine
#
def objectcos_parser(parent_subparser, common_parser):
    # main objectcos parser
    parser = parent_subparser.add_parser(
        'objectcos',
        description='SOS Objectcos CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Objectcos')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # add command parser
    add_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)
