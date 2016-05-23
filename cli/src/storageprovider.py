#!/usr/bin/python

# Copyright (c) 2012-13 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import common
import json
from argparse import ArgumentParser
from common import SOSError


class StorageProvider(object):

    URI_STORAGEPROVIDER_LIST = '/vdc/storage-providers'
    URI_STORAGEPROVIDER_DETAILS = '/vdc/storage-providers/{0}'
    URI_STORAGEPROVIDER_DEACTIVATE = '/vdc/storage-providers/{0}/deactivate'
    URI_STORAGEPROVIDER_SCAN = '/vdc/storage-providers/scan'

    INTERFACE_TYPE_LIST = ["hicommand", "smis", "vplex", "cinder",
                            "scaleioapi", "ddmc","ibmxiv" ,"xtremio", "ceph" ]

    def __init__(self, ipAddr, port):

        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def create(
            self, name, ip_address, port, user_name, passwd,
            use_ssl, interface, sio_cli, element_manager_url, secondary_username, secondary_password):

        body = json.dumps(
            {
                'name': name,
                'ip_address': ip_address,
                'port_number': port,
                'user_name': user_name,
                'password': passwd,
                'use_ssl': use_ssl,
                'interface_type': interface,
                'sio_cli': sio_cli,
                'element_manager_url': element_manager_url,
                'secondary_username': secondary_username,
                'secondary_password': secondary_password
            }
        )

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            StorageProvider.URI_STORAGEPROVIDER_LIST,
            body)

        o = common.json_decode(s)
        return o

    def show_by_uri(self, uri, xml=False):

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            'GET', StorageProvider.URI_STORAGEPROVIDER_DETAILS.format(uri),
            None, None)
        o = common.json_decode(s)

        if(not o["inactive"]):
            if(xml):
                (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port, 'GET',
                    StorageProvider.URI_STORAGEPROVIDER_DETAILS.format(uri),
                    None, None, xml)
                return s
            else:
                return o
        return None

    def show(self, name, xml=False):
        device_uri = self.query_by_name(name)
        return self.show_by_uri(device_uri, xml)

    def scan(self):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            'POST', StorageProvider.URI_STORAGEPROVIDER_SCAN,
            None, None)
        o = common.json_decode(s)
        return o

    def update(self, name, new_name, ip_address, port, user_name, passwd,
            use_ssl, interface, element_manager_url, secondary_username, secondary_password):
        storageprovider_uri = self.query_by_name(name)

        body = json.dumps(
            {
                'name': new_name,
                'ip_address': ip_address,
                'port_number': port,
                'user_name': user_name,
                'password': passwd,
                'use_ssl': use_ssl,
                'interface_type': interface,
                'element_manager_url': element_manager_url,
                'secondary_username': secondary_username,
                'secondary_password': secondary_password
            }
        )

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "PUT",
            StorageProvider.URI_STORAGEPROVIDER_DETAILS.format(
            storageprovider_uri), body)

        o = common.json_decode(s)
        return o

    def delete_by_uri(self, uri):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            StorageProvider.URI_STORAGEPROVIDER_DEACTIVATE.format(uri),
            None)

    def delete(self, name):
        device_uri = self.query_by_name(name)
        return self.delete_by_uri(device_uri)

    def query_by_name(self, name):
        if (common.is_uri(name)):
            return name

        try:
            providers = self.list_storageproviders()
        except:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Storage provider with name: " +
                           name + " not found")

        for provider in providers:
            storageprovider = self.show_by_uri(
                provider['id'])
            if(storageprovider is not None and
               storageprovider['name'] == name):
                return storageprovider['id']
        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "Storage provider with name: " + name + " not found")

    def list_storageproviders(self):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, 'GET',
            StorageProvider.URI_STORAGEPROVIDER_LIST, None)
        o = common.json_decode(s)
        if (not o or "storage_provider" not in o):
            return []
        else:
            return_list = o['storage_provider']
            if(return_list and len(return_list) > 0):
                return return_list
            else:
                return []

    def list_storageproviders_with_details(self, interface_type=None):
        result = []
        providers = self.list_storageproviders()
        for provider in providers:
            storageprovider = self.show_by_uri(
                provider['id'])
            if(storageprovider):
                if(interface_type is None):
                    result.append(storageprovider)
                else:
                    if (interface_type == storageprovider['interface']):
                        result.append(storageprovider)
        return result


def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Storage provider create CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Creates a storage provider')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                help='Name of storage provider',
                                metavar='<name>',
                                dest='name',
                                required=True)
    mandatory_args.add_argument('-provip', '-providerip',
                               help='IP address of storage provider',
                               dest='providerip',
                               metavar='<provider ipaddress>',
                               required=True)
    mandatory_args.add_argument('-provport',
                               type=int,
                               dest='providerport',
                               metavar='<provider port>',
                               help='Port number of storage provider',
                               required=True)
    mandatory_args.add_argument('-u', '-user',
                               dest='user',
                               metavar='<username>',
                               help='Username of storage provider',
                               required=True)
    create_parser.add_argument('-ssl', '-usessl',
                               dest='usessl',
                               action='store_true',
                               help='Use SSL or not')
    mandatory_args.add_argument('-if', '-interface',
                               dest='interface',
                               metavar='<interface>',
                               help='Interface of storage provider',
                               choices=StorageProvider.INTERFACE_TYPE_LIST,
                               required=True)
    create_parser.add_argument('-sio_cli',
                               dest='sio_cli',
                               metavar='<cli path>',
                               help='ScaleIO CLI path')
    create_parser.add_argument('-element_manager_url',
                               dest='element_manager_url',
                               metavar='<Element Manager URL>',
                               help='Specify an element manager URL')
    create_parser.add_argument('-secondary_username',
                               dest='secondary_username',
                               metavar='<Secondary Username>',
                               help='Specify a secondary username to be used')
    create_parser.set_defaults(func=storageprovider_create)


'''
Function to pre-process all the checks necessary for create storage system
and invoke the create call after pre-propcessing is passed
'''


def storageprovider_create(args):

    obj = StorageProvider(args.ip, args.port)
    passwd = None
    if (args.user and len(args.user) > 0):
        passwd = common.get_password("storage provider")
    
    secondary_password = None
    if (args.secondary_username and len(args.secondary_username) > 0):
        secondary_password = common.get_password("secondary password")

    try:
        if (not args.usessl):
            args.usessl = False

        res = obj.create(args.name, args.providerip, args.providerport,
                         args.user, passwd, args.usessl, args.interface, args.sio_cli, args.element_manager_url,
                         args.secondary_username, secondary_password)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "create",
            "storageprovider",
            e.err_text,
            e.err_code)


def update_parser(subcommand_parsers, common_parser):
    # update command parser
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Storage provider update CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Creates a storage provider')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                help='Name of storage provider',
                                metavar='<name>',
                                dest='name',
                                required=True)
    mandatory_args.add_argument('-newname',
                                help='New name of storage provider',
                                metavar='<name>',
                                dest='newname',
                                required=True)
    mandatory_args.add_argument('-provip', '-providerip',
                               help='IP address of storage provider',
                               dest='providerip',
                               metavar='<provider ipaddress>',
                               required=True)
    mandatory_args.add_argument('-provport',
                               type=int,
                               dest='providerport',
                               metavar='<provider port>',
                               help='Port number of storage provider',
                               required=True)
    mandatory_args.add_argument('-u', '-user',
                               dest='user',
                               metavar='<username>',
                               help='Username of storage provider',
                               required=True)
    update_parser.add_argument('-ssl', '-usessl',
                               dest='usessl',
                               action='store_true',
                               help='Use SSL or not')
    mandatory_args.add_argument('-if', '-interface',
                               dest='interface',
                               metavar='<interface>',
                               choices=StorageProvider.INTERFACE_TYPE_LIST,
                               help='Interface of storage provider',
                               required=True)
    update_parser.add_argument('-element_manager_url',
                               dest='element_manager_url',
                               metavar='<Element Manager URL>',
                               help='Specify an element manager URL')
    update_parser.add_argument('-secondary_username',
                               dest='secondary_username',
                               metavar='<Secondary Username>',
                               help='Specify a secondary username to be used')
    update_parser.set_defaults(func=storageprovider_update)


'''
Function to pre-process all the checks necessary for create storage provider
and invoke the create call after pre-propcessing is passed
'''


def storageprovider_update(args):

    obj = StorageProvider(args.ip, args.port)
    passwd = None
    if (args.user and len(args.user) > 0):
        passwd = common.get_password("storage provider")

    try:
        
        if (not args.usessl):
            args.usessl = False
            
        secondary_password = None
        if (args.secondary_username and len(args.secondary_username) > 0):
            secondary_password = common.get_password("secondary password")

        res = obj.update(args.name, args.newname, args.providerip,
                         args.providerport, args.user, passwd,
                         args.usessl, args.interface, args.element_manager_url,
                         args.secondary_username, secondary_password)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "update",
            "storageprovider",
            e.err_text,
            e.err_code)


def delete_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser(
        'delete', description='ViPR Storage provider delete CLI usage ',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Deletes a storage provider')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of storage provider',
                                required=True)
    delete_parser.set_defaults(func=storageprovider_delete)


def storageprovider_delete(args):
    obj = StorageProvider(args.ip, args.port)
    try:
        obj.delete(args.name)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "delete",
            "storageprovider",
            e.err_text,
            e.err_code)


def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser(
        'show', description='ViPR Storage provider show CLI usage ',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show details of storage provider')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of storage provider',
                                required=True)
    show_parser.add_argument('-xml',
                             dest='xml',
                             help='Display in XML format',
                             action='store_true')
    show_parser.set_defaults(func=storageprovider_show)


def storageprovider_show(args):
    obj = StorageProvider(args.ip, args.port)
    try:
        output = obj.show(args.name, args.xml)
        if(args.xml):
            return common.format_xml(output)
        else:
            return common.format_json_object(output)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "show",
            "storageprovider",
            e.err_text,
            e.err_code)


def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list', description='ViPR Storage provider list CLI usage ',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists storage providers')

    list_parser.add_argument('-if', '-interface',
                             dest='interface',
                             metavar="<interface>",
                             choices=StorageProvider.INTERFACE_TYPE_LIST,
                             help='Interface of storage provider')
    list_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             action='store_true',
                             help='Lists storage providers with details')
    list_parser.add_argument('-l', '-long',
                             dest='long',
                             action='store_true',
                             help='Lists storage providers in a large table')
    list_parser.set_defaults(func=storageprovider_list)


def storageprovider_list(args):
    obj = StorageProvider(args.ip, args.port)
    from common import TableGenerator
    try:
        output = obj.list_storageproviders_with_details(args.interface)
        if(output and len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            elif(args.long):
                TableGenerator(
                        output, [
                            'name', 'interface',
                            'ip_address', 'port_number', 'use_ssl',
                            'job_scan_status', 'registration_status',
                            'compatibility_status']).printTable()
            else:
                TableGenerator(
                        output, ['name', 'interface']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise(
            "list",
            "storageprovider",
            e.err_text,
            e.err_code)


def scan_parser(subcommand_parsers, common_parser):
    scan_parser = subcommand_parsers.add_parser(
        'scan', description='ViPR Storage provider scan CLI usage ',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Scans storage providers')
    scan_parser.set_defaults(func=storageprovider_scan)


def storageprovider_scan(args):
    obj = StorageProvider(args.ip, args.port)
    try:
        obj.scan()
    except SOSError as e:
        common.format_err_msg_and_raise(
            "scan",
            "storageprovider",
            e.err_text,
            e.err_code)


def storageprovider_parser(parent_subparser, common_parser):
    # main storage system parser

    parser = parent_subparser.add_parser(
        'storageprovider',
        description='ViPR Storage provider CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on storage provider')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    create_parser(subcommand_parsers, common_parser)

    update_parser(subcommand_parsers, common_parser)

    delete_parser(subcommand_parsers, common_parser)

    show_parser(subcommand_parsers, common_parser)

    scan_parser(subcommand_parsers, common_parser)

    list_parser(subcommand_parsers, common_parser)
