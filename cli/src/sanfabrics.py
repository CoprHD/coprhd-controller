#!/usr/bin/python

# Copyright (c) 2013-14 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

'''
This module contains the sanfabrics implementation
'''

import common
import json

from common import SOSError
from common import TableGenerator

from networksystem import Networksystem


class SanFabrics(object):

    URI_SAN_FABRICS_LIST = "/vdc/network-systems/{0}/san-fabrics"
    URI_SAN_FABRICS_ZONE_LIST = URI_SAN_FABRICS_LIST + "/{1}/san-zones"

    SWICH_TYPE_LIST = ['brocade', 'mds']
    '''
    Constructor: takes IP address and port of the ViPR instance. These are
    needed to make http requests for REST API
    '''

    def __init__(self, ipAddr, port):
        self.__ipAddr = ipAddr
        self.__port = port

    def networksystem_query(self, name):
        '''
        Returns the UID of the networksystem specified by the name
        '''
        if (common.is_uri(name)):
            return name

        systems = self.networksystem_list()
        for system in systems:
            if (system['name'] == name):
                ret = self.networksystem_show(system['id'])
                if(ret):
                    return system['id']

        raise SOSError(
            SOSError.NOT_FOUND_ERR,
            "Networksystem " + name + " not found: ")

    '''
    Returns a list of the VSAN or Fabric names \
         configured on this network system
    '''
    def san_fabrics_list(self, networkname):
        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
            "GET",
            SanFabrics.URI_SAN_FABRICS_LIST.format(nsuri),
            None)
        o = common.json_decode(s)
        return o["fabric"]

    '''
    Returns a list of the active zones (and their zone members)
                                        for the specified
    '''
    def show_fabrics_zones_by_uri(self, nsuri, fabricid, xml=False, excludealiases=False):

        urisanfabric= SanFabrics.URI_SAN_FABRICS_ZONE_LIST
        if(excludealiases == True ):
            urisanfabric = urisanfabric + "?exclude-aliases=true"
        if(xml == False):
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
            "GET",
            urisanfabric.format(nsuri, fabricid),
            None)
            return common.json_decode(s)
        else:
            (s, h) = common.service_json_request(self.__ipAddr,
            self.__port,
            "GET",
            urisanfabric.format(nsuri, fabricid),
            None, None, xml)
            return s

    def san_fabrics_zones_list(self, networkname, fabricid, xml=False , excludealiases=False):
        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)
        return self.show_fabrics_zones_by_uri(nsuri, fabricid, xml ,excludealiases )

    '''
    Returns a list of the active zones (and their zone members)
                                        for the specified
    '''
    def show_san_fabrics_zone(self, name, fabricid, sanzone, xml=False):
        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(name)

        restapi = SanFabrics.URI_SAN_FABRICS_ZONE_LIST.format(nsuri, fabricid)
        restapi = restapi + "?zone-name=" + sanzone + "&exclude-members=false"
        if(xml == False):
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                                    "GET",
                                                    restapi,
                                                    None)
            return common.json_decode(s)
        else:
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                                    "GET",
                                                    restapi,
                                                    None, None, xml)
            return s

    '''updated the active zoneset of the
            VSAN or fabric specified on a network system'''

    def san_fabrics_activate(self, networkname, fabricid):
        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)

        req_rest_api = SanFabrics.URI_SAN_FABRICS_ZONE_LIST.format(nsuri,
                                                             fabricid)
        req_rest_api = req_rest_api + '/activate'
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                        "POST",
                                        req_rest_api,
                                        None)
        return common.json_decode(s)

    '''
        update san fabrics
    '''
    def san_fabric_zone_update(self, operation, networkname,
                                              fabricid,
                                              sanname,
                                              addwwwpns,
                                              removewwpns):
        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)
        req_rest_api = SanFabrics.URI_SAN_FABRICS_ZONE_LIST.format(nsuri,
                                                             fabricid)
        zonesParam = []
        updateParam = dict()
        req_type = "POST"
        if("add" == operation):
            req_type = "POST"
            zonesParam.append({'name': sanname, 'members': addwwwpns})
            updateParam['san_zone'] = zonesParam
        elif("remove" == operation):
            req_type = "POST"
            req_rest_api = req_rest_api + '/remove'
            zonesParam.append({'name': sanname, 'members': removewwpns})
            updateParam['san_zone'] = zonesParam
        else:
            req_type = "PUT"
            addMembers = []
            if(addwwwpns != None):
                addMembers = addwwwpns
            removeMembers = []
            if(removewwpns != None):
                removeMembers = removewwpns

            zonesParam.append(
                {'name': sanname, 'add': addMembers, 'remove': removeMembers})
            updateParam['san_zone_update'] = zonesParam

        body = json.dumps(updateParam)
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                        req_type,
                                        req_rest_api,
                                        body)
        return common.json_decode(s)


#Common parameters for san_fabric parser.
def san_zone_sub_common_parser(mandatory_args):
    mandatory_args.add_argument('-name', '-n',
                                metavar='<networksystemname>',
                                dest='name',
                                help='Name of networksystem',
                                required=True)
    mandatory_args.add_argument('-fabricid', '-fid',
                                  metavar='<fabricid>',
                                  dest='fabricid',
                                  help='The name of the fabric or VSAN',
                                  required=True)


def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR sanfabrics list CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Returns a list of the VSAN or Fabric names \
         configured on network system')
    mandatory_args = list_parser.add_argument_group(
                                                    'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<networkname>',
                                dest='name',
                                help='Name of networksystem',
                                required=True)
    list_parser.add_argument('-verbose', '-v',
                             dest='verbose',
                             help='List sanfabrics with details',
                             action='store_true')
    list_parser.add_argument('-long', '-l',
                             dest='long',
                             help='List sanfabrics having more headers',
                             action='store_true')
    list_parser.set_defaults(
                                func=san_fabrics_list)


def san_fabrics_list(args):
    obj = SanFabrics(args.ip, args.port)
    try:
        uris = obj.san_fabrics_list(args.name)
        output = []
        for uri in uris:
            param = {'fabricname': uri}
            output.append(param)

        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            elif(args.long):
                TableGenerator(
                    output,
                    ['fabricname']).printTable()
            else:
                TableGenerator(output, ['fabricname']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise(
                                        "list",
                                        "sanfabrics",
                                        e.err_text,
                                        e.err_code)


def list_san_zones_parser(subcommand_parsers, common_parser):
    # list command parser
    list_zones_parser = subcommand_parsers.add_parser(
        'list-sanzones',
        description='ViPR sanfabrics list-zones cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='list of the active zones in a Fabric or VSAN')
    mandatory_args = list_zones_parser.add_argument_group(
                                        'mandatory arguments')
    san_zone_sub_common_parser(mandatory_args)
    list_zones_parser.add_argument('-verbose', '-v',
                             dest='verbose',
                             help='List sanzones of a Fabric or VSAN',
                             action='store_true')
    list_zones_parser.add_argument('-long', '-l',
                             dest='long',
                             help='List sanzones of Fabric or VSAN',
                             action='store_true')
    list_zones_parser.add_argument('-excludealiases', '-exal',
                             dest='excludealiases',
                             help='This excludes the aliases',
                             action='store_true')    
    
    list_zones_parser.set_defaults(func=list_fabric_san_zones)


def list_fabric_san_zones(args):
    obj = SanFabrics(args.ip, args.port)
    try:
        zones = obj.san_fabrics_zones_list(args.name, args.fabricid, None, args.excludealiases)
        sanzone = zones['san_zone']

        output = []
        strwwp = ""

        for zone in sanzone:
            param = {'name': zone['name']}
            members = zone['members']
            #get zone and then process wwn
            for member in members:
                strwwp = strwwp + member['wwn']
                strwwp = strwwp + " "
            param["wwn_members"] = strwwp
            strwwp = ""
            output.append(param)

        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(zones)
            elif(args.long):
                TableGenerator(
                    output,
                    ['name', 'wwn_members']).printTable()
            else:
                TableGenerator(output,
                            ['name']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise(
                                        "list-sanzones",
                                        "sanfabrics",
                                        e.err_text,
                                        e.err_code)


def show_parser(subcommand_parsers, common_parser):
    show_san_fabrics_parser = subcommand_parsers.add_parser(
        'show',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR sanfabrics show cli usage.',
        help='Shows active zone-set of fabric or VSAN')
    mandatory_args = show_san_fabrics_parser.add_argument_group(
                                                    'mandatory arguments')
    san_zone_sub_common_parser(mandatory_args)
    show_san_fabrics_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    show_san_fabrics_parser.set_defaults(
                                func=show_sanfabrics)


def show_sanfabrics(args):
        # fabric show command parser
    obj = SanFabrics(args.ip, args.port)
    try:
        res = obj.san_fabrics_zones_list(args.name, args.fabricid,
                                                           args.xml)
        if(res):
            if(args.xml == True):
                return common.format_xml(res)
            else:
                return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise(
                                        "show",
                                        "sanfabrics",
                                        e.err_text,
                                        e.err_code)
    return


def activate_parser(subcommand_parsers, common_parser):
    activate_san_fabircs_parser = subcommand_parsers.add_parser(
        'activate',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR sanzone activate CLI usage.',
        help='Activate current active zone-set of Fabric or VSAN')
    mandatory_args = activate_san_fabircs_parser.add_argument_group(
                                                    'mandatory arguments')
    san_zone_sub_common_parser(mandatory_args)
    activate_san_fabircs_parser.set_defaults(
                                func=san_fabrics_activate)


def san_fabrics_activate(args):
    # active command parser
    obj = SanFabrics(args.ip, args.port)
    try:
        obj.san_fabrics_activate(args.name,
                                            args.fabricid)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "activate",
            "sanfabrics",
            e.err_text,
            e.err_code)
    return


def update_parser(subcommand_parsers, common_parser):
    update_san_fabric_parser = subcommand_parsers.add_parser(
        'update',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR sanfabrics update CLI usage.',
        help='Updating the Fabric or VSAN by add, modify or delete\
                            san-zone in Fabric or VSAN')
    mandatory_args = update_san_fabric_parser.add_argument_group(
                                                'mandatory arguments')
    mandatory_args.add_argument('-operation', '-op',
                                choices=["add", "modify", "delete"],
                                dest='operation',
                                metavar='<san-zone-operation>',
                                help='san-zone operation',
                                required=True)

    san_zone_sub_common_parser(mandatory_args)

    mandatory_args.add_argument('-sanzonename', '-zn',
                                metavar='<san_zone_name>',
                                dest='sanzonename',
                                help='Name of san zone',
                                required=True)

    update_san_fabric_parser.add_argument('-add_wwpn', '-awp',
        metavar='<add_wwpn>',
        dest='add_wwpn',
        help='A list of WWPN addresses of a san zone ex:wwpn1 wwpn2 etc',
        nargs='+')
    update_san_fabric_parser.add_argument('-remove_wwpn', '-awp',
        metavar='<remove_wwpn>',
        dest='remove_wwpn',
        help='A list of WWPN addresses of a san zone ex:wwpn1 wwpn2 etc',
        nargs='+')
    update_san_fabric_parser.set_defaults(func=san_fabric_zone_update)


def san_fabric_zone_update(args):
    # sanfabric zone command parser
    obj = SanFabrics(args.ip, args.port)
    try:
        obj.san_fabric_zone_update(args.operation, args.name,
                                       args.fabricid,
                                       args.sanzonename,
                                       args.add_wwpn,
                                       args.remove_wwpn)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "update",
            "sanfabrics",
            e.err_text,
            e.err_code)
    return


def show_sanzone_parser(subcommand_parsers, common_parser):
    show_san_zone_fabrics_parser = subcommand_parsers.add_parser(
        'get-sanzone',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR sanfabrics get-sanzone cli usage.',
        help='show the details of sanzone in Fabric or VSAN')
    # show san-zone parser
    mandatory_args = show_san_zone_fabrics_parser.add_argument_group(
                                                    'mandatory arguments')
    san_zone_sub_common_parser(mandatory_args)
    mandatory_args.add_argument('-sanzonename', '-zn',
                                metavar='<san_zone_name>',
                                dest='sanzonename',
                                help='Name of san zone',
                                required=True)

    show_san_zone_fabrics_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    show_san_zone_fabrics_parser.set_defaults(
                                func=show_san_zones)


def show_san_zones(args):
        #  show command parser
    obj = SanFabrics(args.ip, args.port)
    try:
        res = obj.show_san_fabrics_zone(args.name, args.fabricid,
                                         args.sanzonename, args.xml)
        if(res):
            if(args.xml == True):
                return common.format_xml(res)
            else:
                return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise(
                                        "get-sanzone",
                                        "sanfabrics",
                                        e.err_text,
                                        e.err_code)
    return


#
# ComputeImage Main parser routine
#
def san_fabrics_parser(parent_subparser, common_parser):

    # main export group parser
    parser = parent_subparser.add_parser(
        'sanfabrics',
        description='ViPR sanfabrics CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on sanfabrics')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # create command parser
    list_parser(subcommand_parsers, common_parser)

    # list san-zones parser
    list_san_zones_parser(subcommand_parsers, common_parser)

    # show sanfabrics parser
    show_parser(subcommand_parsers, common_parser)

    #activate san-zone set
    activate_parser(subcommand_parsers, common_parser)

    # update sanfabrics parser
    update_parser(subcommand_parsers, common_parser)

    #show sanzone  parser
    show_sanzone_parser(subcommand_parsers, common_parser)
