# Copyright 2015 EMC Corporation
# All Rights Reserved

'''
Created on Apr 1, 2014

@author: bonduj
'''

import json
import common
import sys

from common import SOSError
from common import TableGenerator


class VirtualDatacenter(object):

    URI_SERVICES_BASE = ''
    URI_VDC = URI_SERVICES_BASE + '/vdc'
    URI_VDC_GET = URI_VDC + '/{0}'
    URI_VDC_SECURITYKEY = URI_VDC + '/secret-key'
    URI_VDC_DISCONNECT = URI_VDC_GET + '/disconnect'
    URI_VDC_RECONNECT = URI_VDC_GET + '/reconnect'
    URI_VDC_GET_ROLE = URI_VDC + '/role-assignments'

    VIRTUAL_DATACENTER_ROLES = ['SYSTEM_ADMIN',
                                'PROJECT_ADMIN',
                                'TENANT_ADMIN',
                                'SECURITY_ADMIN']

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance.
        These are needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port
    # add a vdc to ViPR Geo env

    def vdc_add(self, name, endpoint, key, certfile, description):

        parms = {
            'name': name,
            'api_endpoint': endpoint,
            'secret_key': key,
        }
        if(certfile):
            try:
                certificatefs = open(certfile, 'r').read()
                parms['certificate_chain'] = certificatefs

            except IOError as e:
                raise SOSError(e.errno, e.strerror)
        if(description):
            parms['description'] = description

        body = json.dumps(parms)

        (s, h) = common.service_json_request(self.__ipAddr,
                                             self.__port,
                                             "POST",
                                             VirtualDatacenter.URI_VDC,
                                             body)
        o = common.json_decode(s)
        return o
    # update a vdc of a ViPR Geo env

    def vdc_update(self, name, label, endpoint, key,
                   certificatefile, privatekeyfile, description):
        uri = self.vdc_query(name)
        parms = {}
        if(name):
            parms["name"] = label
        if(endpoint):
            parms["api_endpoint"] = endpoint
        if(key):
            parms["key"] = key
        if(certificatefile is not None or privatekeyfile is not None):
            key_and_certificate = dict()
            if(certificatefile):
                try:
                    certificatefs = open(certificatefile, 'r').read()
                    key_and_certificate['certificate_chain'] = certificatefs
                except IOError as e:
                    raise SOSError(e.errno, e.strerror)
            if(privatekeyfile):
                try:
                    privatekey = open(privatekeyfile, 'r').read()
                    key_and_certificate['private_key'] = privatekey
                except IOError as e:
                    raise SOSError(e.errno, e.strerror)
            parms['key_and_certificate'] = key_and_certificate

        if(description):
            parms['description'] = description

        body = json.dumps(parms)
        (s, h) = common.service_json_request(self.__ipAddr,
                            self.__port,
                            "PUT",
                            VirtualDatacenter.URI_VDC_GET.format(uri),
                            body)
        o = common.json_decode(s)
        return o

    def vdc_show(self, name, xml=False):
        uri = self.vdc_query(name)
        return self.vdc_show_by_uri(uri, xml)
    # add role of vdc

    def vdc_add_role(self, name, role, subject_id, group):

        if(subject_id):
            objecttype = 'subject_id'
            objectname = subject_id
        else:
            objecttype = 'group'
            objectname = group
        parms = {
            "add": [{"role": [role], objecttype: objectname}]
        }

        body = json.dumps(parms)
        common.service_json_request(self.__ipAddr, self.__port,
                                    "PUT",
                                    VirtualDatacenter.URI_VDC_GET_ROLE,
                                    body)
    # remove role of vdc

    def vdc_remove_role(self, name, role, subject_id, group):
        if(subject_id):
            objecttype = 'subject_id'
            objectname = subject_id
        else:
            objecttype = 'group'
            objectname = group
        parms = {
            "remove": [{"role": [role], objecttype: objectname}]
        }
        body = json.dumps(parms)
        common.service_json_request(self.__ipAddr,
                            self.__port,
                            "PUT",
                            VirtualDatacenter.URI_VDC_GET_ROLE,
                            body)

    # disconnect vdc from ViPR Geo Env
    def vdc_disconnect(self, name):
        uri = self.vdc_query(name)
        common.service_json_request(self.__ipAddr,
                            self.__port,
                            "POST",
                            VirtualDatacenter.URI_VDC_DISCONNECT.format(uri),
                            None)
        return
    # reconnect vdc into ViPR Geo Env

    def vdc_reconnect(self, name):
        uri = self.vdc_query(name)
        common.service_json_request(self.__ipAddr,
                            self.__port,
                            "POST",
                            VirtualDatacenter.URI_VDC_RECONNECT.format(uri),
                            None)
        return

    # show vdc details
    def vdc_show_by_uri(self, uri, xml=False):

        (s, h) = common.service_json_request(self.__ipAddr,
                            self.__port,
                            "GET",
                            VirtualDatacenter.URI_VDC_GET.format(uri),
                            None, None)
        o = common.json_decode(s)
        inactive = common.get_node_value(o, 'inactive')

        if(inactive == True):
            return None
        if(xml == True):
            (s, h) = common.service_json_request(self.__ipAddr,
                            self.__port,
                            "GET",
                            VirtualDatacenter.URI_VDC_GET.format(uri),
                            None, None, xml)
            return s
        else:
            return o
    # get assigned role of a vdc by uri

    def vdc_get_role_by_uri(self, uri, xml=False):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                            "GET",
                            VirtualDatacenter.URI_VDC_GET_ROLE.format(uri),
                            None, None)
        o = common.json_decode(s)
        return o

    def vdc_get_role(self, name):
        uri = self.vdc_query(name)
        return self.vdc_get_role_by_uri(uri)

    # query vdc id
    def vdc_query(self, name):
        if (common.is_uri(name)):
            return name
        
        vdclist = self.vdc_get_list()
        # each vdc name also should unique in vipr geo system
        for vdc in vdclist:
            if(vdc["name"] == name):
                return vdc["id"]
        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "VirtualDataCenter with name: " + name + " not found")
    # delete vdc from ViPR Geo Env

    def vdc_delete(self, name):
        uri = self.vdc_query(name)
        common.service_json_request(self.__ipAddr, self.__port,
                                    "DELETE",
                                    VirtualDatacenter.URI_VDC_GET.format(uri),
                                    None)
    # return list of vdc's in ViPR Geo Inv

    def vdc_get_list(self):
        (s, h) = common.service_json_request(self.__ipAddr,
                                             self.__port,
                                             "GET",
                                             VirtualDatacenter.URI_VDC,
                                             None)
        o = common.json_decode(s)
        if(not o or "virtual_data_center" not in o):
            return []
        else:
            return_list = o['virtual_data_center']
            if(return_list and len(return_list) > 0):
                return return_list
            else:
                return []
# Start Parser definitions


def vdc_add_parser(subcommand_parsers, common_parser):
    # add command parser
    add_parser = subcommand_parsers.add_parser('add',
                 description='ViPR VirtualDataCenter add CLI usage',
                 parents=[common_parser],
                 conflict_handler='resolve',
                 help='adding a New VirtualDataCenter \
                            to Existing ViPR Geo System')
    mandatory_args = add_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                help='Name of VirtualDataCenter',
                                metavar='<vdcname>',
                                dest='name',
                                required=True)
    mandatory_args.add_argument('-key', '-k',
                                help='the secure key of the VirtualDataCenter',
                                dest='key',
                                metavar='<key>',
                                required=True)
    mandatory_args.add_argument('-endpoint', '-ep',
                            help='the VIP of VirtualDataCenter to be added',
                            dest='endpoint',
                            metavar='<endpoint>',
                            required=True)
    mandatory_args.add_argument('-certfile', '-cf',
                            help='path of file with certificate to be added',
                            dest='certfile',
                            metavar='<certificatefile>',
                            required=True)
    add_parser.add_argument('-description', '-desc',
                                help='Description of VirtualDataCenter',
                                metavar='<description>',
                                dest='description',
                                required=False)

    add_parser.set_defaults(func=vdc_add)
'''
Preprocessor for the vdc add operation
'''


def vdc_add(args):
    try:
        VirtualDatacenter(args.ip, args.port).vdc_add(
            args.name, args.endpoint, args.key, args.certfile,
            args.description)
    except SOSError as e:
        common.format_err_msg_and_raise("add", "vdc",
                                        e.err_text, e.err_code)


def vdc_update_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser('update',
                    description='ViPR VirtualDataCenter update CLI usage',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='update to VirtualDataCenter')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                help='Name of VirtualDataCenter',
                                metavar='<name>',
                                dest='name',
                                required=True)
    update_parser.add_argument('-newname', '-nw',
                               help='newname of a VirtualDataCenter',
                               metavar='<newname>',
                               dest='newname',
                               required=False)
    update_parser.add_argument('-description', '-desc',
                                help='Description of VirtualDataCenter',
                                metavar='<description>',
                                dest='description',
                                required=False)
    '''
    update_parser.add_argument('-key', '-k',
                help='the secure key of the VirtualDataCenter to be updated',
                dest='key',
                metavar='<key>')
    update_parser.add_argument('-endpoint', '-ep',
                help='the VIP of VirtualDataCenter to be updated',
                dest='endpoint',
                metavar='<endpoint>')
    update_parser.add_argument('-certfile', '-cvf',
                        help='path of file with certificate to be added',
                        metavar='<certificatefile>',
                        dest='certfile')
    update_parser.add_argument('-privatekeyfile', '-pkf',
                        help='path of file with private key to be added',
                        metavar='<privatekeyfile>',
                        dest='privatekeyfile')'''

    update_parser.set_defaults(func=vdc_update)

'''
Preprocessor for the vdc update operation
'''


def vdc_update(args):
    try:
        if(args.newname is None and  args.description is None):
            raise SOSError(SOSError.CMD_LINE_ERR, sys.argv[0] +
                           " " + sys.argv[1] + " " + sys.argv[2] +
                           ": error:" +
                "At least one of the arguments:-new or -description ")
        else:
            VirtualDatacenter(args.ip, args.port).vdc_update(
                args.name, args.newname, None, None,
                None, None, args.description)
    except SOSError as e:
        common.format_err_msg_and_raise("update", "vdc",
                                        e.err_text, e.err_code)

'''
Preprocessor for the vdc update operation
'''


def vdc_list_parser(subcommand_parsers, common_parser):
    # get vdc's command parser
    list_parser = subcommand_parsers.add_parser('list',
                description='ViPR VirtualDataCenter list CLI usage',
                parents=[common_parser],
                conflict_handler='resolve',
                help='retrieves the list of VirtualDataCenters')
    list_parser.add_argument('-verbose', '-v',
                action='store_true',
                help='Lists VirtualDataCenter with details',
                dest='verbose')
    list_parser.add_argument('-long', '-l',
                action='store_true',
                help='Lists VirtualDataCenter in a large table',
                dest='long')
    list_parser.set_defaults(func=vdc_list)


def vdc_list(args):
    vdcob = VirtualDatacenter(args.ip, args.port)
    try:
        vdclist = vdcob.vdc_get_list()
        output = []
        for vdc in vdclist:
            vdcdetail = vdcob.vdc_show_by_uri(vdc['id'])
            if(vdcdetail):
                output.append(vdcdetail)
        if(len(output) > 0):
            if(args.verbose == True):
                return common.format_json_object(output)
            elif(args.long == True):
                TableGenerator(output,
                               ['name', 'apiEndpoint', \
                                'connectionStatus']).printTable()
            else:
                TableGenerator(output,
                               ['name', 'apiEndpoint']).printTable()
    except SOSError as e:
        common.format_err_msg_and_raise("list", "vdc",
                                        e.err_text, e.err_code)
'''
Preprocessor for the vdc add operation
'''


def vdc_show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser('show',
                    description='ViPR VirtualDataCenter show CLI usage',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='show VirtualDataCenter details')
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of virtual datacenter',
                                metavar='<vdcname>',
                                dest='name',
                                required=True)
    show_parser.set_defaults(func=vdc_show)


def vdc_show(args):
    try:
        res = VirtualDatacenter(args.ip, args.port).vdc_show(args.name,
                                                             args.xml)
        if(res):
            if(args.xml == True):
                return common.format_xml(res)
            else:
                return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "vdc", e.err_text,
                                        e.err_code)
'''
Preprocessor for the vdc add operation
'''


def vdc_disconnect_parser(subcommand_parsers, common_parser):

    disconnect_parser = subcommand_parsers.add_parser('disconnect',
                        description='ViPR disconnect CLI usage',
                        parents=[common_parser],
                        conflict_handler='resolve',
                        help='disconnect to VirtualDataCenter')
    mandatory_args = disconnect_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of VirtualDataCenter',
                                metavar='<name>',
                                dest='name',
                                required=True)
    disconnect_parser.set_defaults(func=vdc_disconnect)


def vdc_disconnect(args):
    try:
        VirtualDatacenter(args.ip, args.port).vdc_disconnect(args.name)
    except SOSError as e:
        common.format_err_msg_and_raise("disconnect", "vdc",
                                        e.err_text, e.err_code)
'''
Preprocessor for the vdc reconnect operation
'''


def vdc_reconnect_parser(subcommand_parsers, common_parser):
    # add command parser
    reconnect_parser = subcommand_parsers.add_parser('reconnect',
                    description='ViPR VirtualDataCenter reconnect CLI usage',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='reconnect to VirtualDataCenter')

    mandatory_args = reconnect_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                help='Name of VirtualDataCenter',
                                metavar='<name>',
                                dest='name',
                                required=True)
    reconnect_parser.set_defaults(func=vdc_reconnect)


def vdc_reconnect(args):
    try:
        VirtualDatacenter(args.ip, args.port).vdc_reconnect(args.name)
    except SOSError as e:
        common.format_err_msg_and_raise("reconnect", "vdc",
                                        e.err_text, e.err_code)
'''
Preprocessor for the vdc delete operation
'''


def vdc_delete_parser(subcommand_parsers, common_parser):
    # add command parser
    delete_parser = subcommand_parsers.add_parser('delete',
                    description='ViPR VirtualDataCenter delete CLI usage',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='delete to VirtualDataCenter from ViPR Geo System')

    mandatory_args = delete_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of VirtualDataCenter',
                                metavar='<name>',
                                dest='name',
                                required=True)
    delete_parser.set_defaults(func=vdc_delete)


def vdc_delete(args):
    try:
        VirtualDatacenter(args.ip, args.port).vdc_delete(args.name)
    except SOSError as e:
        common.format_err_msg_and_raise("delete", "vdc",
                                        e.err_text, e.err_code)
#
# Host Main parser routine
#


def vdc_parser(parent_subparser, common_parser):

    parser = parent_subparser.add_parser('vdc',
                    description='ViPR virtualdatacenter CLI usage',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Operations on VirtualDataCenter')
    subcommand_parsers = parser.add_subparsers(
        help='use one of sub-commands')

    # add command parser
    vdc_add_parser(subcommand_parsers, common_parser)
    # list command parser
    vdc_list_parser(subcommand_parsers, common_parser)
    # show command parser
    vdc_show_parser(subcommand_parsers, common_parser)
    # update command parser
    vdc_update_parser(subcommand_parsers, common_parser)
    # disconnect command parser
    vdc_disconnect_parser(subcommand_parsers, common_parser)
    # reconnect command parser
    vdc_reconnect_parser(subcommand_parsers, common_parser)
    # delete command parser
    vdc_delete_parser(subcommand_parsers, common_parser)
