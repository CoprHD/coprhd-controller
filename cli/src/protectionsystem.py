#!/usr/bin/python

# Copyright (c) 2012-13 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.


# import python system modules

import common
import argparse
import sys
import os
from common import SOSError
from virtualarray import VirtualArray
import json
from tenant import Tenant
import getpass


class ProtectionSystem(object):

    '''
    The class definition for operations on
    'RecoverPoint' and other protection systems.
    '''

    URI_PROTECTION_SYSTEMS = "/vdc/protection-systems"
    URI_PROTECTION_SYSTEM = URI_PROTECTION_SYSTEMS + "/{0}"
    URI_PROTECTION_SYSTEM_UPDATE = URI_PROTECTION_SYSTEMS + "/{0}"
    URI_PROTECTION_SYSTEMS_DISCOVER_ALL = URI_PROTECTION_SYSTEMS + "/discover"
    URI_PROTECTION_SYSTEM_DISCOVER = URI_PROTECTION_SYSTEMS + "/{0}/discover"
    URI_PROTECTION_SYSTEM_DISCOVER_UNMANAGED_CGS = URI_PROTECTION_SYSTEMS + "/{0}/discover?namespace=UNMANAGED_CGS"
    URI_PROTECTION_SYSTEM_CONNECTIVITY = URI_PROTECTION_SYSTEMS + \
                                         "/{0}/connectivity"
    URI_PROTECTION_SYSTEM_DEACTIVATE = URI_PROTECTION_SYSTEMS + \
                                         "/{0}/deactivate"
    PROTECTION_TYPE = ['rp', 'continuous_copy', 'snapshot', 'full_copy']

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def ps_list_uris(self):
        '''
        This function will give us the list of RecoverPoint uris
        separated by comma.
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                        "GET", self.URI_PROTECTION_SYSTEMS, None)

        o = common.json_decode(s)
        return common.get_node_value(o, 'protection_system')

    def ps_list(self):
        '''
        this function is wrapper to the rp_list_uris
        to give the list of RecoverPoint uris.
        '''
        uris = self.ps_list_uris()
        return uris

    def ps_show_uri(self, uri, xml=False):
        '''
        This function will take uri as input and returns with
        all parameters of RecoverPoint like lable, urn and type.
        parameters
            uri : unique resource identifier.
        return
            returns with object contain all details of Recovry Point.
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                        "GET",
                        self.URI_PROTECTION_SYSTEM.format(uri), None, None)

        o = common.json_decode(s)
        if(o['inactive']):
            return None

        if(xml == False):
            return o

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                        "GET",
                        self.URI_PROTECTION_SYSTEM.format(uri),
                        None, None, xml)
        return s

    def ps_show(self, name, type, xml=False):
        '''
        This function is wrapper to  with rp_show_uri.
        It will take rp name as input and do query for uri,
        and displays the details of given rp name.
        parameters
            name : Name of the RecoverPoint.
            type : Type of the Protection system { 'rp'}
        return
            returns with object contain all details of RecoverPoint.
        '''
        uri = self.ps_query(name)
        ps = self.ps_show_uri(uri, xml)
        return ps

    def ps_list_by_hrefs(self, hrefs):
        return common.list_by_hrefs(self.__ipAddr, self.__port, hrefs)

    def ps_create(self, name, deviceip, deviceport,
                  username, type, registration_mode):
        '''
        This is the function will
        create the RecoverPoint with given name and type.
        It will send REST API request to ViPR instance.
        parameters:
            name : Name of the Protection system.
            type : Type of the Protection system { 'rp'}
        return
            returns with RecoverPoint object with all details.
        '''
        # check for existance of Protection system.
        try:
            status = self.ps_show(name, type)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                passwd = None
                if (username and len(username) > 0):
                    if sys.stdin.isatty():
                        passwd = getpass.getpass(
                        prompt="Enter password of the protection system: ")
                    else:
                        passwd = sys.stdin.readline().rstrip()

                    if (len(passwd) > 0):
                        if sys.stdin.isatty():
                            confirm_passwd = getpass.getpass(
                                        prompt="Retype password: ")
                        else:
                            confirm_passwd = sys.stdin.readline().rstrip()
                        if (confirm_passwd != passwd):
                            raise SOSError(SOSError.CMD_LINE_ERR,
                                           sys.argv[0] + " " + sys.argv[1] +
                                           " " + sys.argv[2] +
                                           ": error: Passwords mismatch")
                    else:
                        raise SOSError(SOSError.CMD_LINE_ERR,
                                       sys.argv[0] + " " + sys.argv[1] +
                                       " " + sys.argv[2] +
                                       ": error: Invalid password")

                parms = dict()
                if (name):
                    parms['name'] = name
                if (deviceip):
                    parms['ip_address'] = deviceip
                if (deviceport):
                    parms['port_number'] = deviceport
                if(username):
                    parms['user_name'] = username
                if(passwd):
                    parms['password'] = passwd
                if(type):
                    parms['system_type'] = type
                if(registration_mode):
                    parms['registration_mode'] = registration_mode

                body = json.dumps(parms)
                (s, h) = common.service_json_request(
                        self.__ipAddr, self.__port,
                        "POST", self.URI_PROTECTION_SYSTEMS, body)
                o = common.json_decode(s)
                return o
            else:
                raise e
        if(status):
            raise SOSError(SOSError.ENTRY_ALREADY_EXISTS_ERR,
                           "Protection system with name " +
                           name + " (" + type + ") " + "already exists")

    def ps_update(self, name, deviceip, deviceport,
                  username, type, clusterid, varraysToAdd, varraysToRemove):
        '''
        This is the function will
        create the RecoverPoint with given name and type.
        It will send REST API request to ViPR instance.
        parameters:
            name : Name of the protection system.
            type : Type of the protection system { 'rp' }
            username : Username to access protection system (usually "admin")
            deviceip : IP address of management access of protection system
            deviceport : Port to access protection system
            clusterid : ID of cluster (RecoverPoint specific)
            varraysToAdd : Assign virtual arrays to cluster
            varraysToRemove : Unassign virtual arrays to cluster
            Note:  If clusterid is used, CLI supports changing only one cluster
                   virtual array assignments per command.
        return
            returns with protection system object with all details.
        '''
        # check for existance of Protection system.
        passwd = None
        if (username and len(username) > 0):
            if sys.stdin.isatty():
                passwd = getpass.getpass(
                prompt="Enter password of the protection system: ")
            else:
                passwd = sys.stdin.readline().rstrip()

            if (len(passwd) > 0):
                if sys.stdin.isatty():
                    confirm_passwd = getpass.getpass(
                                    prompt="Retype password: ")
                else:
                    confirm_passwd = sys.stdin.readline().rstrip()
                if (confirm_passwd != passwd):
                    raise SOSError(SOSError.CMD_LINE_ERR,
                        sys.argv[0] + " " + sys.argv[1] +
                        " " + sys.argv[2] + ": error: Passwords mismatch")
            else:
                raise SOSError(SOSError.CMD_LINE_ERR,
                               sys.argv[0] + " " + sys.argv[1] +
                               " " + sys.argv[2] + ": error: Invalid password")

        parms = dict()
        if (deviceip):
            parms['ip_address'] = deviceip
        if (deviceport):
            parms['port_number'] = deviceport
        if(username):
            parms['user_name'] = username
        if(passwd):
            parms['password'] = passwd
        if (clusterid):
            # varray changes
            varray_obj = VirtualArray(self.__ipAddr, self.__port)
            addvarrayList = varray_obj.convertNamesToUris(varraysToAdd)
            removevarrayList = varray_obj.convertNamesToUris(varraysToRemove)

            parms = {
                'varray_assignment_changes': [ {'add':
                                                {'varrays': addvarrayList},
                                                'remove':
                                                {'varrays': removevarrayList},
                                                'cluster_id':
                                                 clusterid
                                               }
                                             ]
            }

        uri = self.ps_query(name)

        body = json.dumps(parms)
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                "PUT", self.URI_PROTECTION_SYSTEM_UPDATE.format(uri), body)
        o = common.json_decode(s)
        return o

    def ps_delete_uri(self, uri):
        '''
        This function will take
        uri as input and deletes that particular RecoverPoint
        from ViPR database.
        parameters:
            uri : unique resource identifier for given RP name.
        return
            return with status of the delete operation.
            false incase it fails to do delete.
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                            "POST",
                            self.URI_PROTECTION_SYSTEM_DEACTIVATE.format(uri),
                            None)
        return str(s) + " ++ " + str(h)

    def ps_delete(self, name, type):
        uri = self.ps_query(name)
        res = self.ps_delete_uri(uri)
        return res

    def ps_discover(self, name):
        '''
        This function will take uri as input
        and deletes that particular RecoverPoint
        from ViPR database.
        parameters:
            uri : unique resource identifier for given RP name.
        return
            return with status of the delete operation.
            false incase it fails to do delete.
        '''
        if (name is not None):
            uri = self.ps_query(name)
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                            "POST",
                            self.URI_PROTECTION_SYSTEM_DISCOVER.format(uri),
                            None)
        else:
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                            "POST",
                            self.URI_PROTECTION_SYSTEMS_DISCOVER_ALL,
                            None)
        return common.json_decode(s)

    def ps_discover_unmanaged_cgs(self, name):
        '''
        Discover the unmanaged CGs of a RP protection system based
        on its name
        Parameters:
            name: name of protection system
        Returns:
            Success or failure exception
        '''
        if (name is not None):
            uri = self.ps_query(name)
            (s, h) = common.service_json_request(self.__ipAddr, self.__port, 
                            "POST", 
                            self.URI_PROTECTION_SYSTEM_DISCOVER_UNMANAGED_CGS.format(uri),
                            None)
        return common.json_decode(s)

    def ps_connectivity(self, name):
        '''
        This function will take name of protection system as input and
        show the connectivity with storage system.
        parameters:
            name : name of the protection system.
        return
            return with connectivity with storage system.
        '''
        uri = self.ps_query(name)
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                    "GET",
                    self.URI_PROTECTION_SYSTEM_CONNECTIVITY.format(uri),
                    None)

        return common.json_decode(s)

    def ps_query(self, name):
        '''
        This function will take the RecoverPoint
        name and type of RecoverPoint
        as input and get uri of the first occurance of given RecoverPoint.
        parameters:
             name : Name of the Protection system.
        return
            return with uri of the given Protection system.
        '''
        if (common.is_uri(name)):
            return name

        uris = self.ps_list_uris()
        for uri in uris:
            rp = common.show_by_href(self.__ipAddr, self.__port, uri)
            if(rp):
                if (rp['name'] == name):
                    return rp['id']
        raise SOSError(SOSError.SOS_FAILURE_ERR, "RecoverPoint:" + name +
                       ": not found")


# Protection system Create routines
def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser('create',
            description='ViPR Protection system Create cli usage',
            parents=[common_parser],
            conflict_handler='resolve',
            help='Create a Protection system')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of Protection system',
                                metavar='<name>',
                                dest='name',
                                required=True)
    mandatory_args.add_argument('-deviceip', '-dip',
                                help='Protection system device IP',
                                metavar='<deviceip>',
                                dest='deviceip',
                                required=True)
    mandatory_args.add_argument('-deviceport', '-dp',
                                help='Protection system device Port',
                                metavar='<deviceport>',
                                dest='deviceport',
                                required=True)
    mandatory_args.add_argument('-username', '-un',
                                help='Protection system accessing user',
                                metavar='<username>',
                                dest='username',
                                required=True)
    create_parser.add_argument('-type', '-t',
            help='Type of the Protection system (default:rp)',
            default='rp',
            dest='type',
            metavar='<pstype>',
            choices=ProtectionSystem.PROTECTION_TYPE)
    create_parser.add_argument('-registration_mode', "-rm",
                               help='registration_mode',
                               dest='registration_mode',
                               metavar='<registration_mode>')

    create_parser.set_defaults(func=ps_create)


def ps_create(args):
    try:
        obj = ProtectionSystem(args.ip, args.port)

        if(not common.validate_port_number(args.deviceport)):
            errorMessage = \
            "-deviceport " + str(args.deviceport) + \
            " is not a valid port number"
            raise SOSError(SOSError.CMD_LINE_ERR, errorMessage)

        res = obj.ps_create(args.name,
                            args.deviceip,
                            args.deviceport,
                            args.username,
                            args.type,
                            args.registration_mode)
    except SOSError as e:
        common.format_err_msg_and_raise("create",
                                        "protectionsystem " +
                                        args.name +
                                        " (" + args.type +
                                        ") ", e.err_text, e.err_code)

# Protection system update routines


def update_parser(subcommand_parsers, common_parser):
    # create command parser
    update_parser = subcommand_parsers.add_parser('update',
                    description='ViPR Protection system update cli usage',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='update a Protection system')

    mandatory_args = update_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                help='Name of Protection system',
                                metavar='<name>',
                                dest='name',
                                required=True)

    update_parser.add_argument('-deviceip', '-dip',
                               help='Protection system device IP',
                               metavar='<deviceip>',
                               dest='deviceip')
    update_parser.add_argument('-deviceport', '-dp',
                               help='Protection system device Port',
                               metavar='<deviceport>',
                               dest='deviceport')
    update_parser.add_argument('-username', '-un',
                               help='Protection system accessing user',
                               metavar='<username>',
                               dest='username')

    update_parser.add_argument('-type', '-t',
        help='Type of the Protection system (default:rp)',
        default='rp',
        dest='type',
        metavar='<pstype>',
        choices=ProtectionSystem.PROTECTION_TYPE)

    varrayupdate_args = update_parser.add_argument_group('Virtual array assignment arguments')

    varrayupdate_args.add_argument('-clusterid', '-cid',
                               help='Protection system cluster ID or cluster name',
                               metavar='<clusterid>',
                               dest='clusterid')

    varrayupdate_args.add_argument('-varray_add', '-vaadd',
                               metavar='<varray_add>',
                               dest='varray_add',
                               help='List of new varrays to be associated' +
                               ' with the cluster',
                               nargs="+")

    varrayupdate_args.add_argument('-varray_remove', '-varemove',
                               metavar='<varray_remove>',
                               dest='varray_remove',
                               help='List of varrays to be dis-associated' +
                               ' from the cluster',
                               nargs="+")

    update_parser.set_defaults(func=ps_update)


def ps_update(args):
    try:
        obj = ProtectionSystem(args.ip, args.port)
        if(args.deviceport):
            if(not common.validate_port_number(args.deviceport)):
                errorMessage = "-deviceport " + \
                str(args.deviceport) + " is not a valid port number"
                raise SOSError(SOSError.CMD_LINE_ERR, errorMessage)
                # At least one of the arguments

        res = obj.ps_update(args.name,
                            args.deviceip,
                            args.deviceport,
                            args.username,
                            args.type,
			    args.clusterid,
			    args.varray_add,
			    args.varray_remove
                            )
    except SOSError as e:
        common.format_err_msg_and_raise("update",
                    "protectionsystem " + args.name +
                    " (" + args.type +
                    ") ", e.err_text, e.err_code)

# Protection system Delete routines


def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser('delete',
                description='ViPR Protection system Delete CLI usage',
                parents=[common_parser],
                conflict_handler='resolve',
                help='Delete a Protection system')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of Protection system',
                                dest='name',
                                metavar='<psname>',
                                required=True)
    delete_parser.add_argument('-type', '-t',
            help='Type of the protection system(default:rp)',
            default='rp',
            dest='type',
            metavar='<pstype>',
            choices=ProtectionSystem.PROTECTION_TYPE)
    delete_parser.set_defaults(func=ps_delete)


def ps_delete(args):
    obj = ProtectionSystem(args.ip, args.port)
    try:
        obj.ps_delete(args.name, args.type)
       # return "Protection system " + args.name + " of type " + args.type + ":
       # Deleted"
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR,
                           "Protection system " + args.name +
                           " (" + args.type + ") " +
                           ": Delete failed\n" + e.err_text)
        else:
            common.format_err_msg_and_raise("delete",
                                            "protectionsystem",
                                            e.err_text, e.err_code)


# Protection system discover routines

def discover_parser(subcommand_parsers, common_parser):
    # discover command parser
    discover_parser = subcommand_parsers.add_parser('discover',
                description='ViPR Protection system discover CLI usage',
                parents=[common_parser],
                conflict_handler='resolve',
                help='discover a Protection system')
    discover_parser.add_argument('-name', '-n',
                                 help='name of Protection system',
                                 dest='name',
                                 metavar='<psname>')

    discover_parser.set_defaults(func=ps_discover)


def ps_discover(args):
    obj = ProtectionSystem(args.ip, args.port)
    try:
        obj.ps_discover(args.name)
       # return "Protection system " + args.name + " of type " + args.type + ":
       # Deleted"
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR,
                           "Protection system " +
                           ": discover failed\n" + e.err_text)
        else:
            common.format_err_msg_and_raise("discover",
                                            "protectionsystem",
                                            e.err_text, e.err_code)

# Protection system unmanaged CGS discover routines

def um_cgs_discover_parser(subcommand_parsers, common_parser):
    um_cg_discover_parser = subcommand_parsers.add_parser(
        'discover_unmanagedcg',
        description='ViPR Protection system discover unmanaged CG CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Discover unmanaged CG details')
        
    um_cg_discover_parser.add_argument('-name', '-n',
                                 help='name of Protection system',
                                 dest='name',
                                 metavar='<psname>')

    um_cg_discover_parser.set_defaults(func=ps_discover_unmanaged_cgs)


def ps_discover_unmanaged_cgs(args):
    obj = ProtectionSystem(args.ip, args.port)
    try:
        res = obj.ps_discover_unmanaged_cgs(args.name)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "discover unmanaged CGs",
            "protectionsystem",
            e.err_text,
            e.err_code)

# Protection system connectivity routines


def connectivity_parser(subcommand_parsers, common_parser):
    # connectivity command parser
    connectivity_parser = subcommand_parsers.add_parser('connectivity',
        description='ViPR Protection system connectivity CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='connectivity a Protection system')
    connectivity_parser.add_argument('-name', '-n',
                                     help='name of Protection system',
                                     dest='name',
                                     metavar='<psname>',
                                     required=True)

    connectivity_parser.set_defaults(func=ps_connectivity)


def ps_connectivity(args):
    obj = ProtectionSystem(args.ip, args.port)
    try:
        res = obj.ps_connectivity(args.name)
        return common.format_json_object(res)
       # return "Protection system " + args.name + " of type " + args.type + ":
       # Deleted"
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR,
                            "Protection system " +
                             ": connectivity failed\n" +
                              e.err_text)
        else:
            common.format_err_msg_and_raise("connectivity",
                                             "protectionsystem",
                                              e.err_text, e.err_code)

# Protection system Show routines


def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser('show',
            description='ViPR protection system Show CLI usage',
            parents=[common_parser],
            conflict_handler='resolve',
            help='Show details of a Protection system')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of Protection system',
                                dest='name',
                                metavar='<psname>',
                                required=True)
    show_parser.add_argument('-type', '-t',
                             help='Type of the protection system(default:rp)',
                             default='rp',
                             dest='type',
                             metavar='<pstype>',
                             choices=ProtectionSystem.PROTECTION_TYPE)
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    show_parser.set_defaults(func=ps_show)


def ps_show(args):

    obj = ProtectionSystem(args.ip, args.port)
    try:
        res = obj.ps_show(args.name, args.type, args.xml)
        if(args.xml):
            return common.format_xml(res)
        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show",
                                         "protectionsystem",
                                          e.err_text,
                                           e.err_code)


# Protection system List routines

def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser('list',
                description='ViPR Protection system List CLI usage',
                parents=[common_parser],
                conflict_handler='resolve',
                help='List Protection system')
    list_parser.add_argument('-type', '-t',
                             help='Type of the protection system(default:rp)',
                             default='rp',
                             dest='type',
                             metavar='<pstype>',
                             choices=['rp'])
    list_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             help='List Protection system with details',
                             action='store_true')
    list_parser.add_argument('-long', '-l',
            action='store_true',
            help='List protection system with details in table format',
            dest='long')
    list_parser.set_defaults(func=ps_list)


def ps_list(args):
    obj = ProtectionSystem(args.ip, args.port)
    try:
        output = []
        uris = obj.ps_list()
        if(len(uris) > 0):
            for item in obj.ps_list_by_hrefs(uris):
                output.append(item)
        if(args.verbose == True):
            return common.format_json_object(output)
        if(len(output) > 0):
            if(args.long == True):
                from common import TableGenerator
                TableGenerator(output, ['name', 'system_type',
                                        'ip_address',
                                        'port_number',
                                        'installation_id',
                                        'job_discovery_status']).printTable()

            else:
                from common import TableGenerator
                TableGenerator(output, ['name', 'system_type',
                                         'ip_address',
                                          'port_number']).printTable()

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR,
                            "Protection system list failed\n" + e.err_text)
        else:
            common.format_err_msg_and_raise("list",
                 "protectionsystem", e.err_text, e.err_code)

#
# Protection system Main parser routine
#


def protectionsystem_parser(parent_subparser, common_parser):

    # main Protection system parser
    parser = parent_subparser.add_parser('protectionsystem',
            description='ViPR Protection system cli usage',
            parents=[common_parser],
            conflict_handler='resolve',
            help='Operations on Protection system')
    subcommand_parsers = parser.add_subparsers(help='Use one of commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # list command parser
    update_parser(subcommand_parsers, common_parser)

    # list command parser
    discover_parser(subcommand_parsers, common_parser)

    # unmanaged CGs discover  command parser
    um_cgs_discover_parser(subcommand_parsers, common_parser)

    # list command parser
    connectivity_parser(subcommand_parsers, common_parser)
