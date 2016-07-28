#!/usr/bin/python
# Copyright (c)2012 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import json
import getpass
import sys
import common

from common import SOSError
from common import TableGenerator


class Networksystem(object):

    '''
    The class definition for operations on 'Networksystem'.
    '''

    # Commonly used URIs for the 'Networksystems' module
    URI_SERVICES_BASE = ''
    URI_NETWORKSYSTEMS = URI_SERVICES_BASE + \
        '/vdc/network-systems'
    URI_NETWORKSYSTEM = URI_NETWORKSYSTEMS + '/{0}'
    URI_NETWORKSYSTEM_DISCOVER = URI_NETWORKSYSTEMS + \
        '/{0}/discover'
    URI_NETWORKSYSTEM_FCENDPOINTS = URI_NETWORKSYSTEMS + \
        '/{0}/fc-endpoints'
    URI_NETWORKSYSTEM_VDCREFERENCES = URI_NETWORKSYSTEMS + \
        '/san-references/{0},{1}'
    URI_RESOURCE_DEACTIVATE = '{0}/deactivate'
    URI_NETWORKSYSTEM_REGISTER = URI_NETWORKSYSTEMS + \
        '/{0}/register'
    URI_NETWORKSYSTEM_DEREGISTER = URI_NETWORKSYSTEMS + \
        '/{0}/deregister'
    URI_NETWORKSYSTEM_ALLIAS = URI_NETWORKSYSTEM + '/san-aliases'
    URI_NETWORKSYSTEM_ALLIAS_REMOVE = URI_NETWORKSYSTEM_ALLIAS + '/remove'

    URI_NETWORKSYSTEM_SAN_FABRIC = URI_NETWORKSYSTEM + \
                                    '/san-fabrics/{1}/san-zones'
    URI_NETWORKSYSTEM_SAN_FABRIC_LIST = URI_NETWORKSYSTEM + '/san-fabrics'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
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
            "Networksystem " +
            name +
            " not found: ")

    def networksystem_list(self):
        '''
        Returns all the networksystems in a vdc
        Parameters:
        Returns:
                JSON payload of networksystem list
        '''

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Networksystem.URI_NETWORKSYSTEMS,
                                             None)

        o = common.json_decode(s)

        if (not o):
            return {}
        systems = o['network_system']

        if(not isinstance(systems, list)):
            return [systems]
        return systems

    def networksystem_show(self, label, xml=False):
        '''
        Makes a REST API call to retrieve details of a networksystem
        based on its UUID
        '''
        uri = self.networksystem_query(label)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Networksystem.URI_NETWORKSYSTEM.format(uri),
            None, None, False)

        o = common.json_decode(s)

        if('inactive' in o):
            if(o['inactive']):
                return None

        if(not xml):
            return o

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Networksystem.URI_NETWORKSYSTEM.format(uri),
            None, None, xml)

        return s

    def networksystem_create(self, label, type, deviceip, deviceport,
                             username, password, smisip, smisport,
                             smisuser, smispw, smisssl):
        '''
        creates a networksystem
        parameters:
            label:  label of the networksystem
        Returns:
            JSON payload response
        '''

        try:
            check = self.networksystem_show(label)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                var = dict()
                params = dict()

                parms = {'name': label,
                         'system_type': type,
                         'ip_address': deviceip,
                         'port_number': deviceport,
                         'user_name': username,
                         'password': password,
                         }
                if(smisip):
                    parms['smis_provider_ip'] = smisip
                if(smisport):
                    parms['smis_port_number'] = smisport
                if (smisuser):
                    parms['smis_user_name'] = smisuser
                if (smispw):
                    parms['smis_password'] = smispw
                if (smisssl):
                    parms['smis_use_ssl'] = smisssl

                body = json.dumps(parms)
                (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port,
                    "POST",
                    Networksystem.URI_NETWORKSYSTEMS,
                    body)
                o = common.json_decode(s)
                return o
            else:
                raise e

        if(check):
            raise SOSError(SOSError.ENTRY_ALREADY_EXISTS_ERR,
                           "Networksystem with name " + label +
                           " already exists")

    def networksystem_delete(self, label):
        '''
        Makes a REST API call to delete a networksystem by its UUID
        '''
        uri = self.networksystem_query(label)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            Networksystem.URI_RESOURCE_DEACTIVATE.format(
                Networksystem.URI_NETWORKSYSTEM.format(uri)),
            None)
        return str(s) + " ++ " + str(h)

    def networksystem_register(self, label):
        '''
        Makes a REST API call to delete a networksystem by its UUID
        '''
        uri = self.networksystem_query(label)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            Networksystem.URI_NETWORKSYSTEM_REGISTER.format(uri),
            None)
        o = common.json_decode(s)

        return o

    def networksystem_deregister(self, label):
        '''
        Makes a REST API call to delete a networksystem by its UUID
        '''
        uri = self.networksystem_query(label)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            Networksystem.URI_NETWORKSYSTEM_DEREGISTER.format(uri),
            None)
        o = common.json_decode(s)

        return o

    def networksystem_discover(self, label):
        '''
        Makes a REST API call to discover a networksystem by its UUID
        '''

        uri = self.networksystem_query(label)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            Networksystem.URI_NETWORKSYSTEM_DISCOVER.format(uri), None)

        o = common.json_decode(s)

        return o

    def networksystem_listconnections(self, label):
        '''
        Makes a REST API call to list connections of a switch
        '''
        uri = self.networksystem_query(label)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Networksystem.URI_NETWORKSYSTEM_FCENDPOINTS.format(uri), None)

        o = common.json_decode(s)

        return o

    def networksystem_vdcreferences(self, initiator, target):
        '''
        Makes a REST API call to list vdc references
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Networksystem.URI_NETWORKSYSTEM_VDCREFERENCES.format(
                initiator, target), None)

        o = common.json_decode(s)

        return o
    '''
        prepare the get alias payload
    '''
    def get_alias_param(self, wwnaliases, fabricid=None):
        if(wwnaliases):
            copyEntries = []
            for alias in wwnaliases:
                try:
                    wwn_alias = dict()
                    aliasParam = alias.split(",")
                    wwn_alias['name'] = aliasParam[1]
                    wwn_alias['address'] = aliasParam[0]
                    copyEntries.append(wwn_alias)
                except Exception as e:
                    raise SOSError(SOSError.CMD_LINE_ERR,
                               " Please provide valid format " +
                               "wwn:alias1 wwn:alias2 ")
            parms = {
                 'wwn_alias': copyEntries
                 }
        if(fabricid != None):
            parms['fabric_id'] = fabricid

        return parms
    '''
    get restapi uri for network aliases
    '''
    def get_alias_resturi(self, nsuri, fabricid):
        requri = None
        if(fabricid != None):
            requri = \
             Networksystem.URI_NETWORKSYSTEM_FABRIC_WWNS_ALLIASES.format(
            nsuri, fabricid)
        else:
            requri = Networksystem.URI_NETWORKSYSTEM_WWNS_ALLIASES.format(
                                                                    nsuri)
        return requri
    '''
        Makes a REST API call to add alias to networksystem or given fabric
    '''
    def networksystem_alias_add(self, networkname,
                            wwnaliases, fabricid=None):
        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)
        params = obj.get_alias_param(wwnaliases, fabricid)
        body = json.dumps(params)

        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "POST",
                Networksystem.URI_NETWORKSYSTEM_ALLIAS.format(nsuri),
                body)
        return common.json_decode(s)

    '''
        Makes a REST API call to remove alias to networksystem or given fabric
    '''
    def networksystem_alias_remove(self, networkname,
                            wwnaliases, fabricid=None):
        obj = Networksystem(self.__ipAddr, self.__port)

        nsuri = obj.networksystem_query(networkname)
        body = json.dumps(obj.get_alias_param(wwnaliases, fabricid))


        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "POST",
                Networksystem.URI_NETWORKSYSTEM_ALLIAS_REMOVE.format(nsuri),
                body)
        return common.json_decode(s)
    '''
        Makes a REST API call to update alias to networksystem or given fabric
    '''
    def networksystem_alias_update(self, networkname, alias,
                            old_wwn, new_wwn, fabricid=None):

        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)

        copyEntries = []
        wwn_alias = dict()
        wwn_alias['name'] = alias
        wwn_alias['address'] = old_wwn
        wwn_alias['new_address'] = new_wwn
        copyEntries.append(wwn_alias)

        parms = {
                 'wwn_alias_update': copyEntries
        }
        if(fabricid != None):
            parms['fabric_id'] = fabricid

        body = json.dumps(parms)


        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "PUT",
                Networksystem.URI_NETWORKSYSTEM_ALLIAS.format(nsuri),
                body)
        return common.json_decode(s)

    '''
        Makes a REST API call to show aliases of a networksystem \
        or given fabric
    '''
    def networksystem_alias_show(self, networkname, fabricid=None, xml=False):

        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)
        req_api = None
        if(fabricid != None):
            req_api = Networksystem.URI_NETWORKSYSTEM_ALLIAS + \
                         '?fabric-id={1}'
            req_api = req_api.format(nsuri, fabricid)
        else:
            req_api = Networksystem.URI_NETWORKSYSTEM_ALLIAS.format(nsuri)
        if(xml == False):
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "GET",
                                             req_api,
                                             None)

            return common.json_decode(s)
        else:
            (s, h) = common.service_json_request(self.__ipAddr,
                            self.__port,
                            "GET",
                            req_api,
                            None, None, xml)
            return s

    '''
        Makes a REST API call to add sanzone of a networksystem \
        or given fabric
    '''
    def networksystem_san_fabirc_add(self, networkname, fabricid, sanname,
                                                             wwwpnlist):
        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)

        zonesParam = []
        zonesParam.append({'name': sanname, 'members': wwwpnlist})
        params = dict()
        params['san_zone'] = zonesParam

        body = json.dumps(params)

        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "POST",
            Networksystem.URI_NETWORKSYSTEM_SAN_FABRIC.format(nsuri,\
                                                             fabricid),
            body)
        return common.json_decode(s)

    '''
        Makes a REST API call to remove sanzone of a networksystem \
        or given fabric
    '''
    def networksystem_san_fabric_remove(self, networkname, fabricid,
                                                    sanname, wwwpnlist):
        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)

        zonesParam = []
        zonesParam.append({'name': sanname, 'members': wwwpnlist})
        params = dict()
        params['san_zone'] = zonesParam

        body = json.dumps(params)
        req_rest_api = Networksystem.URI_NETWORKSYSTEM_SAN_FABRIC.format(
                                                       nsuri, fabricid)
        req_rest_api = req_rest_api + '/remove'

        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "POST",
                                            req_rest_api, body)
        return common.json_decode(s)

    '''
        Makes a REST API call to update sanzone of a networksystem \
        or given fabric
    '''
    def networksystem_san_fabric_update(self, networkname,
                                              fabricid,
                                              sanname,
                                              addwwwpns,
                                              removewwpns):
        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)

        zonesParam = []

        addMembers = []
        if(addwwwpns != None):
            addMembers = addwwwpns
        removeMembers = []
        if(removewwpns != None):
            removeMembers = removewwpns

        zonesParam.append(
                {'name': sanname, 'add': addMembers, 'remove': removeMembers})
        updateParam = dict()
        updateParam['san_zone_update'] = zonesParam
        body = json.dumps(updateParam)

        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "PUT",
        Networksystem.URI_NETWORKSYSTEM_SAN_FABRIC.format(nsuri, \
                                                    fabricid), body)
        return common.json_decode(s)

    '''
        Makes a REST API call to show sanzone of a networksystem \
        or given fabric
    '''
    def networksystem_san_fabric_show(self, networkname, fabricid, xml=False):
        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)
        return obj.networksystem_san_fabric_show_by_uri(nsuri, fabricid, xml)

    def networksystem_san_fabric_show_by_uri(self, nsuri, fabricid,
                                              xml=False):

        if(xml == False):
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
            "GET",
            Networksystem.URI_NETWORKSYSTEM_SAN_FABRIC.format(nsuri, \
                                                            fabricid),
            None)

            return common.json_decode(s)
        else:
            (s, h) = common.service_json_request(self.__ipAddr,
            self.__port,
            "GET",
            Networksystem.URI_NETWORKSYSTEM_SAN_FABRIC.format(nsuri, \
                                                              fabricid),
            None, None, xml)
            return s

        '''Makes a REST API call to Activate current active zoneset
           of the given fabric specified on a network system'''
    def networksystem_san_fabric_activate(self, networkname, fabricid):
        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)

        req_rest_api = Networksystem.URI_NETWORKSYSTEM_SAN_FABRIC.format(
                                                       nsuri, fabricid)
        req_rest_api = req_rest_api + '/activate'
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                        "POST",
                                        req_rest_api,
                                        None)
        return common.json_decode(s)

    '''
        Makes a REST API call return list san fabric of networksystem"
    '''
    def networksystem_san_fabric_list(self, networkname):
        obj = Networksystem(self.__ipAddr, self.__port)
        nsuri = obj.networksystem_query(networkname)
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
            "GET",
            Networksystem.URI_NETWORKSYSTEM_SAN_FABRIC_LIST.format(nsuri),
            None)

        o = common.json_decode(s)
        return o["fabric"]

# Create routines


def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Networksystem Create CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create a networksystem')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of Networksystem',
                                metavar='<networksystemname>',
                                dest='name',
                                required=True)

    mandatory_args.add_argument('-type', '-t',
                                help='Type of Network System',
                                metavar='<type>',
                                dest='type',
                                required=True)

    mandatory_args.add_argument('-deviceip', '-dip',
                                help='Device IP of Network System',
                                metavar='<deviceip>',
                                dest='deviceip',
                                required=True)

    mandatory_args.add_argument('-deviceport', '-dp',
                                help='Device Port of Network System',
                                metavar='<deviceport>',
                                dest='deviceport',
                                required=True)

    mandatory_args.add_argument('-user',
                                help='User of Network System',
                                metavar='<user>',
                                dest='user',
                                required=True)

    create_parser.add_argument('-smisip',
                               help='smis IP of Network System',
                               metavar='<smisip>',
                               dest='smisip')

    create_parser.add_argument('-smisport',
                               help='smis port of Network System',
                               metavar='<smisport>',
                               dest='smisport')

    create_parser.add_argument('-smisuser',
                               help='smis user of Network System',
                               metavar='<smisuser>',
                               dest='smisuser')

    create_parser.add_argument('-smisssl',
                               dest='smisssl',
                               action='store_true',
                               help='user SMIS SSL')

    create_parser.set_defaults(func=networksystem_create)


def networksystem_create(args):
    obj = Networksystem(args.ip, args.port)
    try:
        passwd = None
        smispassword = None
        if (args.user and len(args.user) > 0):
            passwd = common.get_password("network system")

        if (args.smisuser and len(args.smisuser) > 0):
            smispassword = common.get_password("SMIS for network system")

        res = obj.networksystem_create(
            args.name, args.type, args.deviceip, args.deviceport,
            args.user, passwd, args.smisip, args.smisport,
            args.smisuser, smispassword, args.smisssl)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code, "Networksystem " +
                           args.name + ": Create failed\n" + e.err_text)
        else:
            raise e


# NEIGHBORHOOD Delete routines

def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR NetworksystemDelete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a networksystem')

    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of networksystem',
                                dest='name',
                                metavar='networksystemname',
                                required=True)

    delete_parser.set_defaults(func=networksystem_delete)


def networksystem_delete(args):
    obj = Networksystem(args.ip, args.port)
    try:
        res = obj.networksystem_delete(args.name)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Networksystem delete failed: " + e.err_text)
        else:
            raise e

# NEIGHBORHOOD Show routines


def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Networksystem Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show a Networksystem')

    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of Networksystem',
                                dest='name',
                                metavar='networksystemname',
                                required=True)

    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=networksystem_show)


def networksystem_show(args):
    obj = Networksystem(args.ip, args.port)
    try:
        res = obj.networksystem_show(args.name, args.xml)
        if((res) and (args.xml)):
            return common.format_xml(res)

        if(res):
            return common.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Networksystem " + args.name +
                           " show failed: " + e.err_text)
        else:
            raise e


def discover_parser(subcommand_parsers, common_parser):
    # show command parser
    discover_parser = subcommand_parsers.add_parser(
        'discover',
        description='ViPR Networksystem discover CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Discover a Networksystem')

    mandatory_args = discover_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of Networksystem',
                                dest='name',
                                metavar='networksystemname',
                                required=True)

    discover_parser.set_defaults(func=networksystem_discover)


def networksystem_discover(args):
    obj = Networksystem(args.ip, args.port)
    try:
        res = obj.networksystem_discover(args.name)

        return common.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Networksystem " + args.name + " show failed: " +
                           e.err_text)
        else:
            raise e


def vdcreferences_parser(subcommand_parsers, common_parser):
    # show command parser
    vdcreferences_parser = subcommand_parsers.add_parser(
        'vdcreferences',
        description='ViPR Networksystem vdc references CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Discover a Networksystem')

    mandatory_args = vdcreferences_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-initiator', '-in',
                                help='name of initiator',
                                dest='initiator',
                                metavar='initiator',
                                required=True)

    mandatory_args.add_argument('-target', '-tg',
                                help='name of target',
                                dest='target',
                                metavar='target',
                                required=True)

    '''vdcreferences_parser.add_argument('-tenant',
                                help='name of Tenant',
                                dest='tenant',
                                metavar='tenant')'''

    vdcreferences_parser.set_defaults(func=networksystem_vdcreferences)


def networksystem_vdcreferences(args):

    obj = Networksystem(args.ip, args.port)
    try:
        result = obj.networksystem_vdcreferences(args.initiator, args.target)
        uris = result['fc_vdc_reference']

        output = []
        outlst = []
        for uri in uris:

            from exportgroup import ExportGroup
            obj = ExportGroup(args.ip, args.port)
            groupdtls = obj.export_group_show(uri['groupUri'])

            if(groupdtls):
                groupname = groupdtls['name']
                uri['group_name'] = groupname

            from volume import Volume
            obj = Volume(args.ip, args.port)
            volumedtls = obj.volume_show(uri['volumeUri'])

            if(volumedtls):
                volumename = volumedtls['name']
                uri['volume_name'] = volumename

            output.append(uri)

        if(len(output) > 0):
            if(args.verbose):
                return output
            elif(args.long):
                from common import TableGenerator
                TableGenerator(
                    output,
                    ['id',
                     'vdcName',
                     'group_name',
                     'volume_name',
                     'inactive']).printTable()
            else:
                from common import TableGenerator
                TableGenerator(
                    output,
                    ['vdcName',
                     'group_name',
                     'volume_name']).printTable()

    except SOSError as e:
        raise e


def listconnections_parser(subcommand_parsers, common_parser):
    # show command parser
    listconnections_parser = subcommand_parsers.add_parser(
        'list-connections',
        description='ViPR Networksystem List connections CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List connections of  a Networksystem')

    mandatory_args = listconnections_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of Networksystem',
                                dest='name',
                                metavar='networksystemname',
                                required=True)

    listconnections_parser.add_argument(
        '-verbose', '-v',
        action='store_true',
        help='List Networksystems with details',
        dest='verbose')

    listconnections_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List Networksystems with more details in tabular form',
        dest='long')

    listconnections_parser.set_defaults(func=networksystem_listconnections)


def networksystem_listconnections(args):

    obj = Networksystem(args.ip, args.port)
    try:
        result = obj.networksystem_listconnections(args.name)
        output = result['fc_endpoint']

        if(len(output) > 0):
            if(args.verbose):
                return output
            elif(args.long):
                from common import TableGenerator
                TableGenerator(
                    output,
                    ['fabric_id',
                     'remote_port_name',
                     'remote_node_name',
                     'switch_interface',
                     'switch_name',
                     'fabric_wwn']).printTable()
            else:
                from common import TableGenerator
                TableGenerator(
                    output,
                    ['fabric_id',
                     'remote_port_name',
                     'remote_node_name']).printTable()

    except SOSError as e:
        raise e
# NEIGHBORHOOD List routines


def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Networksystem List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List of networksystems')

    list_parser.add_argument('-verbose', '-v',
                             action='store_true',
                             help='List Networksystems with details',
                             dest='verbose')

    list_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List Networksystems with more details in tabular form',
        dest='long')

    list_parser.set_defaults(func=networksystem_list)


def networksystem_list(args):
    obj = Networksystem(args.ip, args.port)
    try:
        uris = obj.networksystem_list()
        output = []
        outlst = []
        for uri in uris:
            temp = obj.networksystem_show(uri['id'])
            if(temp):
                output.append(temp)

        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            elif(args.long):
                from common import TableGenerator
                TableGenerator(
                    output,
                    ['name',
                     'system_type',
                     'ip_address',
                     'registration_status',
                     'job_discovery_status']).printTable()
            else:
                from common import TableGenerator
                TableGenerator(output, ['name']).printTable()

    except SOSError as e:
        raise e


def register_parser(subcommand_parsers, common_parser):
    # show command parser
    register_parser = subcommand_parsers.add_parser(
        'register',
        description='ViPR Networksystem register CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Register a Networksystem')

    mandatory_args = register_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of Networksystem',
                                dest='name',
                                metavar='networksystemname',
                                required=True)

    register_parser.set_defaults(func=networksystem_register)


def networksystem_register(args):
    obj = Networksystem(args.ip, args.port)
    try:
        res = obj.networksystem_register(args.name)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "register",
            "networksystem",
            e.err_text,
            e.err_code)


def deregister_parser(subcommand_parsers, common_parser):
    # show command parser
    deregister_parser = subcommand_parsers.add_parser(
        'deregister',
        description='ViPR Networksystem deregister CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Deregister a Networksystem')

    mandatory_args = deregister_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of Networksystem',
                                dest='name',
                                metavar='networksystemname',
                                required=True)

    deregister_parser.set_defaults(func=networksystem_deregister)


def networksystem_deregister(args):
    obj = Networksystem(args.ip, args.port)
    try:
        res = obj.networksystem_deregister(args.name)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "deregister",
            "networksystem",
            e.err_text,
            e.err_code)


# Common parameters for continuous copies parser.
def wwn_aliases_sub_common_parser(cc_common_parser):
    mandatory_args = cc_common_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<networksystemname>',
                                dest='name',
                                help='Name of networksystem',
                                required=True)
    mandatory_args.add_argument('-fabricid', '-fid',
                                  metavar='<fabricid>',
                                  dest='fabricid',
                                  help='Fabric name or id ')
    mandatory_args.add_argument('-aliases', '-als',
                                metavar='<wwn_aliases>',
                                dest='aliases',
        help='wwn_address and aliases name eg: wwn,alias1 wwn,alias2..etc',
                                nargs='+',
                                required=True)


# aliase command parser routines
def wwn_aliases_parser(subcommand_parsers, common_parser):
    aliases_parser = subcommand_parsers.add_parser(
        'aliases',
        description='ViPR networksystem aliases of wwn CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='alias for wwn of a specified network system')
    subcommand_parsers = aliases_parser.add_subparsers(
        help='Use one of the commands')

    # add wwn aliases
    add_wwn_aliases_parser = subcommand_parsers.add_parser(
        'add',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Adds one or more aliases to the specified network system',
        description='ViPR aliases add CLI usage.')
    # Add aliases parameter for networksystem

    wwn_aliases_sub_common_parser(add_wwn_aliases_parser)
    add_wwn_aliases_parser.set_defaults(func=networksystem_aliases_add)

    # remove wwn aliases
    remove_wwn_aliases_parser = subcommand_parsers.add_parser(
        'remove',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR aliases remove CLI usage.',
        help='Removes one or more aliases from the specified network system \
        or the given fabric')
    # remove aliases parameter for networksystem
    wwn_aliases_sub_common_parser(remove_wwn_aliases_parser)
    remove_wwn_aliases_parser.set_defaults(func=networksystem_aliases_remove)

    # update wwn aliases
    update_wwn_aliases_parser = subcommand_parsers.add_parser(
        'update',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR aliases update CLI usage.',
        help='change aliases address to this new address')
    # update alias to new wwwn address
    mandatory_args = update_wwn_aliases_parser.add_argument_group(
                                                'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<networksystemname>',
                                dest='name',
                                help='Name of networksystem',
                                required=True)
    mandatory_args.add_argument('-fabricid', '-fid',
                                  metavar='<fabricid>',
                                  dest='fabricid',
                                  help='Fabric name or id ')
    mandatory_args.add_argument('-alias', '-als',
                                metavar='<alias_name>',
                                dest='alias',
                                help='name of alias',
                                required=True)
    mandatory_args.add_argument('-oldwwn', '-ow',
                                metavar='<old_wwn_address>',
                                dest='oldwwn',
                                help='old WWN address',
                                required=True)
    mandatory_args.add_argument('-newwwn', '-nw',
                                metavar='<new_wwn_address>',
                                dest='newwwn',
                                help='new WWN address',
                                required=True)
    update_wwn_aliases_parser.set_defaults(func=networksystem_aliases_update)

    # show wwn aliases
    show_wwn_aliases_parser = subcommand_parsers.add_parser(
                                'show',
                                parents=[common_parser],
                                conflict_handler='resolve',
        help='Returns a list of alias for the specified \
        network device of the given fabricId',
                        description='ViPR aliases list CLI usage.')
    # show aliases parameter for networksystem
    mandatory_args = show_wwn_aliases_parser.add_argument_group(
                                            'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<networksystemname>',
                                dest='name',
                                help='Name of networksystem',
                                required=True)
    mandatory_args.add_argument('-fabricid', '-fid',
                                  metavar='<fabricid>',
                                  dest='fabricid',
                                  help='Fabric name or id ')
    show_wwn_aliases_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    show_wwn_aliases_parser.set_defaults(func=networkysystem_aliases_show)


def networksystem_aliases_add(args):
    # aliases add command parser
    obj = Networksystem(args.ip, args.port)
    try:
        obj.networksystem_alias_add(args.name,args.aliases,args.fabricid)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "add",
            "aliases",
            e.err_text,
            e.err_code)
    return


def networksystem_aliases_remove(args):
    # aliases remove command parser
    obj = Networksystem(args.ip, args.port)
    try:
        obj.networksystem_alias_remove(args.name, args.aliases, args.fabricid)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "remove",
            "aliases",
            e.err_text,
            e.err_code)
    return


def networksystem_aliases_update(args):
    # aliases update command parser
    obj = Networksystem(args.ip, args.port)
    try:
        obj.networksystem_alias_update(args.name, args.alias,
                 args.oldwwn, args.newwwn, args.fabricid)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "update",
            "aliases",
            e.err_text,
            e.err_code)
    return


def networkysystem_aliases_show(args):
    # aliases show command parser
    obj = Networksystem(args.ip, args.port)
    try:
        res = obj.networksystem_alias_show(args.name, args.fabricid, args.xml)
        if(res):
            if(args.xml == True):
                return common.format_xml(res)
            else:
                return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise(
                                        "show",
                                        "aliases",
                                        e.err_text,
                                        e.err_code)
    return


#Common parameters for san_fabric parser.
def san_zone_sub_common_parser(cc_common_parser, mandatory_args):
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

'''
def san_zone_parser(subcommand_parsers, common_parser):
    aliases_parser = subcommand_parsers.add_parser(
        'sanzone',
        description='ViPR networksystem sanzone CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='network system VSANs and fabrics')
    subcommand_parsers = aliases_parser.add_subparsers(
        help='Use one of the commands')

    # add VSAN to a networksystem
    add_san_fabric_parser = subcommand_parsers.add_parser(
        'add',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Add SAN zone to network system VSAN or fabric',
        description='ViPR sanzone add CLI usage.')
    mandatory_args = add_san_fabric_parser.add_argument_group(
                                        'mandatory arguments')
    san_zone_sub_common_parser(add_san_fabric_parser, mandatory_args)
    mandatory_args.add_argument('-sanzonename', '-zn',
                                metavar='<san_zone_name>',
                                dest='sanzonename',
                                help='Name of sanzone',
                                required=True)
    mandatory_args.add_argument('-wwpn', '-wp',
                                metavar='<wwpn>',
                                dest='wwpn',
    help='list of WWPN addresses of a san zone ex: wwpn1 wwpn2...',
                                nargs='+',
                                required=True)
    add_san_fabric_parser.set_defaults(func=networksystem_san_fabric_add)

    # remove VSAN from networksystem
    remove_san_fabirc_parser = subcommand_parsers.add_parser(
        'remove',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR sanzone remove CLI usage.',
        help='Remove SAN zone from  network system VSAN or fabric')
    mandatory_args = remove_san_fabirc_parser.add_argument_group(
                                        'mandatory arguments')
        # remove VSAN common parser
    san_zone_sub_common_parser(remove_san_fabirc_parser, mandatory_args)
    mandatory_args.add_argument('-sanzonename', '-zn',
                                metavar='<san_zone_name>',
                                dest='sanzonename',
                                help='Name of san zone',
                                required=True)
    mandatory_args.add_argument('-wwpn', '-wp',
                                metavar='<wwpn>',
                                dest='wwpn',
    help='list of WWPN addresses of a san zone ex: wwpn1 wwpn2 etc',
                                nargs='+',
                                required=True)
    remove_san_fabirc_parser.set_defaults(func=networksystem_san_fabric_remove)

    # update VSAN of networksystem's fabric id
    update_san_fabric_parser = subcommand_parsers.add_parser(
        'update',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR sanzone update CLI usage.',
        help='Update SAN zones details for network system VSAN or fabric')

    mandatory_args = update_san_fabric_parser.add_argument_group(
                                                'mandatory arguments')
    san_zone_sub_common_parser(update_san_fabric_parser, mandatory_args)

    mandatory_args.add_argument('-sanzonename', '-zn',
                                metavar='<san_zone_name>',
                                dest='sanzonename',
                                help='Name of sanzone',
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
    update_san_fabric_parser.set_defaults(func=networksystem_san_zone_update)

    # activate VSAN of networksystem's fabric id
    activate_san_fabirc_parser = subcommand_parsers.add_parser(
        'activate',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR sanzone activate CLI usage.',
        help='Activate current active zoneset of the given \
        fabric specified on a network system ')
    mandatory_args = activate_san_fabirc_parser.add_argument_group(
                                                    'mandatory arguments')
    san_zone_sub_common_parser(activate_san_fabirc_parser, mandatory_args)
    activate_san_fabirc_parser.set_defaults(
                                func=networksystem_san_zone_activate)
    # show VSAN of networksystem's fabric id
    show_san_fabirc_parser = subcommand_parsers.add_parser(
        'show',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR sanzone show CLI usage.',
        help='Shows active zones in a network system fabric or VSAN')
    # remove aliases parameter for networksystem
    mandatory_args = show_san_fabirc_parser.add_argument_group(
                                                    'mandatory arguments')

    san_zone_sub_common_parser(show_san_fabirc_parser, mandatory_args)
    show_san_fabirc_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    show_san_fabirc_parser.set_defaults(
                                func=networksystem_san_zone_show)

    #list VSAN of networksystem
    list_san_fabirc_parser = subcommand_parsers.add_parser(
        'list',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR sanzone list CLI usage.',
        help='returns a list of the VSAN or fabric of a network system')
    # remove aliases parameter for networksystem
    mandatory_args = list_san_fabirc_parser.add_argument_group(
                                                    'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<networkname>',
                                dest='name',
                                help='Name of networksystem',
                                required=True)
    list_san_fabirc_parser.add_argument('-verbose', '-v',
                             dest='verbose',
                             help='List filesystems with details',
                             action='store_true')
    list_san_fabirc_parser.add_argument('-long', '-l',
                             dest='long',
                             help='List filesystems having more headers',
                             action='store_true')
    list_san_fabirc_parser.set_defaults(
                                func=networksystem_san_zone_list)


def networksystem_san_fabric_add(args):
    # aliases add command parser
    obj = Networksystem(args.ip, args.port)
    try:
        obj.networksystem_san_fabirc_add(args.name,
                        args.fabricid, args.sanzonename, args.wwpn)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "add",
            "sanzone",
            e.err_text,
            e.err_code)
    return


def networksystem_san_fabric_remove(args):
    # aliases remove command parser
    obj = Networksystem(args.ip, args.port)
    try:
        obj.networksystem_san_fabric_remove(args.name, args.fabricid,
                                            args.sanzonename, args.wwpn)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "remove",
            "sanzone",
            e.err_text,
            e.err_code)
    return


def networksystem_san_zone_update(args):
    # aliases remove command parser
    obj = Networksystem(args.ip, args.port)
    try:
        obj.networksystem_san_fabric_update(args.name,
                                       args.fabricid,
                                       args.sanzonename,
                                       args.add_wwpn,
                                       args.remove_wwpn)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "remove",
            "sanzone",
            e.err_text,
            e.err_code)
    return


def networksystem_san_zone_activate(args):
    # aliases remove command parser
    obj = Networksystem(args.ip, args.port)
    try:
        obj.networksystem_san_fabric_activate(args.name,
                                            args.fabricid)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "remove",
            "sanzone",
            e.err_text,
            e.err_code)
    return


def networksystem_san_zone_show(args):
        # aliases show command parser
    obj = Networksystem(args.ip, args.port)
    try:
        res = obj.networksystem_san_fabric_show(args.name, args.fabricid,
                                                           args.xml)
        if(res):
            if(args.xml == True):
                return common.format_xml(res)
            else:
                return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise(
                                        "show",
                                        "sanzone",
                                        e.err_text,
                                        e.err_code)
    return


def networksystem_san_zone_list(args):
    obj = Networksystem(args.ip, args.port)
    try:
        uris = obj.networksystem_san_fabric_list(args.name)
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
                                        "sanzone",
                                        e.err_text,
                                        e.err_code)
'''

#
# Networksystem Main parser routine
#

def networksystem_parser(parent_subparser, common_parser):
    # main networksystem parser
    parser = parent_subparser.add_parser(
        'networksystem',
        description='ViPR Networksystem CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Networksystem')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # discover command parser
    discover_parser(subcommand_parsers, common_parser)

    # list connections command parser
    listconnections_parser(subcommand_parsers, common_parser)

    # register command parser
    register_parser(subcommand_parsers, common_parser)

    # deregister command parser
    deregister_parser(subcommand_parsers, common_parser)

    # san-alias command parser
    wwn_aliases_parser(subcommand_parsers, common_parser)

     # san-zone command parser
    #san_zone_parser(subcommand_parsers, common_parser)
