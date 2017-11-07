#!/usr/bin/pytho

# Copyright (c) 2017 EMC Corporation
# All Rights Reserved
#


import json
import sys

from common import SOSError
import common
from network import Network
from storagesystem import StorageSystem
from storageport import Storageport


class Storageportgroup(object):

    '''
    Class definition for operations on 'Storage port group'
    '''
    URI_STORAGEPORTGROUP_LIST = '/vdc/storage-systems/{0}/storage-port-groups'
    URI_STORAGEPORTGROUP_DETAILS = '/vdc/storage-systems/{0}/storage-port-groups/{1}'
    URI_STORAGEPORTGROUP_REGISTER \
        = '/vdc/storage-systems/{0}/storage-port-groups/{1}/register'
    URI_STORAGEPORTGROUP_DEREGISTER = '/vdc/storage-systems/{0}/storage-port-groups/{1}/deregister'
    URI_STORAGEPORTGROUP_DELETE = '/vdc/storage-systems/{0}/storage-port-groups/{1}/deactivate'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

        '''Creates a storage port group.
        Parameters:
            storage_device_name   : Name of storage device.
            serial_number : Serial# of storage device.
            storage_device_type : Type of storage device
            portgroupname      : name of a port group
            storageports       : storage ports members
        Returns:
            JSON response payload
        '''

    def storageportgroup_add(self, storage_device_name, serial_number,
                        storage_device_type, portgroup_name, storageports):

        storage_system = StorageSystem(self.__ipAddr, self.__port)
        storage_system_uri = None

        if(serial_number):
            storage_system_uri \
                = storage_system.query_by_serial_number_and_type(
                    serial_number, storage_device_type)
        elif(storage_device_name):
            storage_system_uri = storage_system.query_by_name_and_type(
                storage_device_name,
                storage_device_type)

        addports = []
	for portname in storageports:
	    print portname
	    storage_port = Storageport(self.__ipAddr, self.__port)
	    porturi = storage_port.storageport_query(storage_system_uri, portname)
	    print porturi
            addports.append(porturi)
        body = json.dumps(
                          {'name': portgroup_name,
                           'storage_ports': addports
                           })

        common.service_json_request(self.__ipAddr, self.__port,
             "POST",
             Storageportgroup.URI_STORAGEPORTGROUP_LIST.format(storage_system_uri),
             body)

    def storageportgroup_register_uri(self, sspuri, spguri):

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Storageportgroup.URI_STORAGEPORTGROUP_REGISTER.format(sspuri, spguri),
            None)
        return common.json_decode(s)

    
    def storageportgroup_list_uri(self, duri):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Storageportgroup.URI_STORAGEPORTGROUP_LIST.format(duri), None)

        o = common.json_decode(s)

        return_list = o['storage_port_group']

        if(len(return_list) > 0):
            return return_list
        else:
            return []

    '''
        Returns details of a storage port group

         based on its name or UUID
        Parameters:
            uriportgroup    :{UUID of storage port group}
        Returns:
            a JSON payload of port group details
        Throws:
            SOSError - if id or name not found
    '''

    def storageportgroup_show_uri(self, ssuri, spguri, xml=False):
        '''
        Makes a REST API call to retrieve details of
        a storage port group based on its UUID
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Storageportgroup.URI_STORAGEPORTGROUP_DETAILS.format(ssuri, spguri))

        if(xml is False):
            o = common.json_decode(s)
            inactive = common.get_node_value(o, 'inactive')
            if(inactive):
                return None
            else:
                return o
        return s

    '''
        Deletes a storage port group by uri
    '''

    def storageportgroup_delete_uri(self, sduri, uriportgroup):
        '''
        Makes a REST API call to delete a storage port group by its UUID
        '''
        common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Storageportgroup.URI_STORAGEPORTGROUP_DELETE.format(sduri, uriportgroup),
            None)
        return 0

    def storageportgroup_deregister_uri(self, sduri, spguri):
        common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Storageportgroup.URI_STORAGEPORTGROUP_DEREGISTER.format(sduri, spguri),
            None)

    
    def storageportgroup_register(self, serialNumber, storagedeviceName,
                             storagedeviceType, portgroupname):
        # process

        ssuri = self.storagesystem_query(
            storagedeviceName,
            serialNumber,
            storagedeviceType)

        if(ssuri is not None):
            if(portgroupname is not None):
                spguri = self.storageportgroup_query(ssuri, portgroupname)
                if (spguri is not None):
                    self.storageportgroup_register_uri(ssuri, spguri)
                else :
                    raise SOSError(
                        SOSError.NOT_FOUND_ERR,
                        "Storage port group: " +
                        portgroupname +
                        " is not found")

            else:
                raise SOSError(
		    SOSError.NOT_FOUND_ERR,
		    "Storage port group name is not provided")

        return None

    def storageportgroup_deregister(self,
                               storagedeviceName,
                               serialNumber,
                               storagedeviceType,
                               portgroupName):
        ssuri = self.storagesystem_query(storagedeviceName,
                                         serialNumber,
                                         storagedeviceType)
        spguri = self.storageportgroup_query(ssuri, portgroupName)
        return self.storageportgroup_deregister_uri(ssuri, spguri)

    def storagesystem_query(self,
                            storagedeviceName,
                            serialNumber,
                            storagedeviceType):
        urideviceid = None
        storagesystemObj = StorageSystem(self.__ipAddr, self.__port)
        if(serialNumber):
            urideviceid = storagesystemObj.query_by_serial_number_and_type(
                serialNumber,
                storagedeviceType)

        elif(storagedeviceName):
            urideviceidTemp = storagesystemObj.show(
                name=storagedeviceName,
                type=storagedeviceType)
            urideviceid = urideviceidTemp['id']
        else:
            return

        return urideviceid

    def storageportgroup_list(self, storagedeviceName,
                         serialNumber, storagedeviceType):
        urideviceid = self.storagesystem_query(
            storagedeviceName,
            serialNumber,
            storagedeviceType)
        if(urideviceid):
            return self.storageportgroup_list_uri(urideviceid)

    def storageportgroup_show_id(self, ssuri, spguri, xml=False):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Storageportgroup.URI_STORAGEPORTGROUP_DETAILS.format(ssuri, spguri),
            None,
            None,
            xml)

        if(xml is False):
            return common.json_decode(s)
        else:
            return s

    def storageportgroup_show(self, storagedeviceName,
                         serialNumber, storagedeviceType, portgroupname, xml):
        ssuri = self.storagesystem_query(
            storagedeviceName,
            serialNumber,
            storagedeviceType)
        spguri = self.storageportgroup_query(ssuri, portgroupname)
        return self.storageportgroup_show_id(ssuri, spguri, xml)

    def storageportgroup_delete(self, storagedeviceName,
                           serialNumber, storagedeviceType, portgroupname):
        ssuri = self.storagesystem_query(
            storagedeviceName,
            serialNumber,
            storagedeviceType)
        spguri = self.storageportgroup_query(ssuri, portgroupname)

        return self.storageportgroup_delete_uri(ssuri, spguri)

    def storageportgroup_query(self, ssuri, portgroupname):
        if(ssuri is not None):
            portgroupuris = self.storageportgroup_list_uri(ssuri)
            if(portgroupname is not None):
                for portgroupuri in portgroupuris:
                    sportgroup = self.storageportgroup_show_id(ssuri, portgroupuri['id'])
                    if(sportgroup['name'] == portgroupname):
                        return portgroupuri['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "Storage port group with name: " + portgroupname + " not found")

    def command_validation(self, devicetype):
        if(devicetype != 'vmax'):
            raise SOSError(
                SOSError.CMD_LINE_ERR,
                devicetype +
                " is not supported")
        return


def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser('create',
        description='ViPR storage port group create CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Add a storage port group to storagesystem')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')

    arggroup = create_parser.add_mutually_exclusive_group(required=True)
    arggroup.add_argument('-storagesystem', '-ss',
                          help='Name of Storagesystem or ' +
                          'Storage system where this port group belongs',
                          dest='storagesystem',
                          metavar='<storagesystemname>')

    arggroup.add_argument('-serialnumber', '-sn',
                          metavar="<serialnumber>",
                          help='Serial Number of the storage system',
                          dest='serialnumber')

    mandatory_args.add_argument('-t', '-type',
                                choices=['vmax'],
                                dest='type',
                                help='Type of storage system',
                                required=True)

    mandatory_args.add_argument('-n', '-portgroupname',
                                dest='portgroupname',
                                help='Name of the storage port group',
                                required=True)
    
    mandatory_args.add_argument('-p', '-storageports',
                                 dest='storageports',
                                 help='Storage ports members',
                                 nargs='+',
                                required=True)

    create_parser.set_defaults(func=storageportgroup_create)


def storageportgroup_create(args):

    if(args.type != 'vmax'):
        errorMessage = "For device type " + args.storagetype + \
            ", storage port group create is not supported." + \
            " Supported only for 'vmax' type systems."
        common.format_err_msg_and_raise(
            "create", "storageportgroup", errorMessage,
            SOSError.CMD_LINE_ERR)

    try:
        storage_portgroup = Storageportgroup(args.ip, args.port)
        storage_portgroup.storageportgroup_add(args.storagesystem,
            args.serialnumber,
            args.type,
            args.portgroupname,
            args.storageports)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "create", "storageportgroup", e.err_text, e.err_code)


def resgister_parser(subcommand_parsers, common_parser):
    # register command parser
    register_parser = subcommand_parsers.add_parser('register',
                                                    description='ViPR' +
                                                    'Storageportgroup' +
                                                    ' register CLI' +
                                                    ' usage',
                                                    parents=[common_parser],
                                                    conflict_handler='resolve',
                                                    help='Storageport ' +
                                                    'registration or \n ' +
                                                    'register the discovered' +
                                                    ' storage port group with the' +
                                                    ' passed id, on the \
     registered storage system with the passed id.')
    mandatory_args = register_parser.add_argument_group('mandatory arguments')

    arggroup = register_parser.add_mutually_exclusive_group(required=True)
    arggroup.add_argument('-storagesystem', '-ss',
                          help='Name of Storagesystem or ' +
                          'Storage system where this port group belongs',
                          dest='storagesystem',
                          metavar='<storagesystemname>')
    arggroup.add_argument('-serialnumber', '-sn',
                          metavar="<serialnumber>",
                          help='Serial Number of the storage system',
                          dest='serialnumber')

    mandatory_args.add_argument('-t', '-type',
                                choices=StorageSystem.SYSTEM_TYPE_LIST,
                                dest='type',
                                metavar="<storagesystem type>",
                                help='Type of storage system',
                                required=True)

    register_parser.add_argument('-portgroupname', '-n',
                                 help='Port group name to be registered',
                                 metavar='<portgroupname>',
                                 dest='portgroupname')

    register_parser.set_defaults(func=storageportgroup_register)


def storageportgroup_register(args):
    # get uri of a storage device by name
    obj = Storageportgroup(args.ip, args.port)
    try:
        obj.storageportgroup_register(
            args.serialnumber,
            args.storagesystem,
            args.type,
            args.portgroupname)

    except SOSError as e:
        raise e



def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser('list',
                                                description='ViPR' +
                                                ' Storageportgroup' +
                                                ' List CLI usage',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='List storageportgroup' +
                                                ' for a storagesystem')
    mandatory_args = list_parser.add_argument_group('mandatory arguments')
    arggroup = list_parser.add_mutually_exclusive_group(required=True)
    arggroup.add_argument('-storagesystem', '-ss',
                          help='Name of Storagesystem',
                          dest='storagesystem',
                          metavar='<storagesystemname>',
                          )
    arggroup.add_argument('-serialnumber', '-sn',
                          metavar="<serialnumber>",
                          help='Serial Number of the storage system',
                          dest='serialnumber')

    mandatory_args.add_argument('-t', '-type',
                                choices=StorageSystem.SYSTEM_TYPE_LIST,
                                dest='type',
                                help='Type of storage system',
                                metavar="<storagesystemtype>",
                                required=True)

    list_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             help='List Storageportgroup with details',
                             action='store_true')

    list_parser.add_argument('-long', '-l',
                             dest='long',
                             help='List Storageportgroup in table with details',
                             action='store_true')

    list_parser.set_defaults(func=storageportgroup_list)


def storageportgroup_list(args):

    # get uri of a storage device by name
    obj = Storageportgroup(args.ip, args.port)
    try:
        uris = obj.storageportgroup_list(
            args.storagesystem,
            args.serialnumber,
            args.type)

        if(len(uris) > 0):
            output = []
            ssuri = obj.storagesystem_query(
                args.storagesystem,
                args.serialnumber,
                args.type)
            for portgroup in uris:
                is_active_obj = obj.storageportgroup_show_id(ssuri, portgroup['id'])
                if(is_active_obj):
                    storageports = is_active_obj['storage_ports']['storage_port']
                    ports = []
                    for storageport in storageports:
                        ports.append(storageport['name'])
                    is_active_obj['storageports'] = ports
                    is_active_obj['portgroupname'] = is_active_obj['name']
                    output.append(is_active_obj)
            if(args.verbose is True):
                return common.format_json_object(output)
            else:
                from common import TableGenerator
                TableGenerator(
                    output, ['portgroupname',
                        'storageports',
                        'registration_status']).printTable()
                
    except SOSError as e:
            raise e


def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser('show',
                                                description='ViPR' +
                                                ' Storageportgroup Show CLI usage',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='Show details' +
                                                ' of a Storageportgroup')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-portgroupname', '-n',
                                help='name of storageportgroup',
                                dest='portgroupname',
                                metavar='<portgroupname>',
                                required=True)
    arggroup = show_parser.add_mutually_exclusive_group(required=True)
    arggroup.add_argument('-storagesystem', '-ss',
                          help='Name of Storagesystem',
                          dest='storagesystem',
                          metavar='<storagesystemname>',
                          )
    arggroup.add_argument('-serialnumber', '-sn',
                          metavar="<serialnumber>",
                          help='Serial Number of the storage system',
                          dest='serialnumber')

    mandatory_args.add_argument('-t', '-type',
                                choices=StorageSystem.SYSTEM_TYPE_LIST,
                                dest='type',
                                metavar="<storagesystemtype>",
                                help='Type of storage system',
                                required=True)
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    show_parser.set_defaults(func=storageportgroup_show)


def storageportgroup_show(args):

    portgroup_obj = Storageportgroup(args.ip, args.port)
    try:
        # get uri's of device and portgroup
        res = portgroup_obj.storageportgroup_show(
            args.storagesystem,
            args.serialnumber,
            args.type,
            args.portgroupname,
            args.xml)
        if(res):
            return common.format_json_object(res)
        else:
            SOSError(
                SOSError.NOT_FOUND_ERR,
                "storageport name : " +
                args.name +
                "Not Found")
    except SOSError as e:
        raise e


def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser('delete',
                                                  description='ViPR' +
                                                  ' Storageportgroup Remove' +
                                                  ' CLI usage',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='Remove a registered' +
                                                  ' storage portgroup, so that it' +
                                                  ' is no longer present in' +
                                                  ' the system')

    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    arggroup = delete_parser.add_mutually_exclusive_group(required=True)
    arggroup.add_argument('-storagesystem', '-ss',
                          help='Name of Storagesystem',
                          dest='storagesystem',
                          metavar='<storagesystemname>')
    arggroup.add_argument('-serialnumber', '-sn',
                          metavar="<serialnumber>",
                          help='Serial Number of the storage system',
                          dest='serialnumber')
    mandatory_args.add_argument('-t', '-type',
                                choices=StorageSystem.SYSTEM_TYPE_LIST,
                                dest='type',
                                metavar="<storagesystemtype>",
                                help='Type of storage system',
                                required=True)
    mandatory_args.add_argument('-portgroupname', '-n',
                                help='Name of Storageportgroup',
                                metavar='<portgroupname>',
                                dest='portgroupname',
                                required=True)
    delete_parser.set_defaults(func=storageportgroup_delete)


def storageportgroup_delete(args):
    obj = Storageportgroup(args.ip, args.port)
    try:
        obj.storageportgroup_delete(
            args.storagesystem,
            args.serialnumber,
            args.type,
            args.portgroupname)
        return
    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Storage port group" + args.portgroupname +
                           " : Delete failed\n" + e.err_text)
        else:
            raise e


def deregister_parser(subcommand_parsers, common_parser):
    # delete command parser
    deregister_parser = subcommand_parsers.add_parser('deregister',
                                                      description='ViPR' +
                                                      ' Storageportgroup' +
                                                      ' deregister CLI usage',
                                                      parents=[common_parser],
                                                      conflict_handler='re' +
                                                      'solve',
                                                      help='Allows the user' +
                                                      ' to deregister a' +
                                                      ' registered storage' +
                                                      ' port group so that it is' +
                                                      ' no longer used by' +
                                                      ' the system.\
                                                      \n sets the' +
                                                      ' registration_status' +
                                                      ' of' +
                                                      ' the storage port group to' +
                                                      ' UNREGISTERED')

    mandatory_args = deregister_parser.add_argument_group(
        'mandatory arguments')
    arggroup = deregister_parser.add_mutually_exclusive_group(required=True)
    mandatory_args.add_argument('-portgroupname', '-n',
                                help='Name of Storageportgroup',
                                metavar='<portgroupname>',
                                dest='portgroupname',
                                required=True)
    arggroup.add_argument('-storagesystem', '-ss',
                          help='Name of Storagesystem',
                          dest='storagesystem',
                          metavar='<storagesystemname>')
    arggroup.add_argument('-serialnumber', '-sn',
                          metavar="<serialnumber>",
                          help='Serial Number of the storage system',
                          dest='serialnumber')
    mandatory_args.add_argument('-t', '-type',
                                choices=StorageSystem.SYSTEM_TYPE_LIST,
                                dest='type',
                                metavar="<storagesystemtype>",
                                help='Type of storage system',
                                required=True)

    deregister_parser.set_defaults(func=storageportgroup_deregister)


def storageportgroup_deregister(args):
    obj = Storageportgroup(args.ip, args.port)
    try:
        obj.storageportgroup_deregister(
            args.storagesystem,
            args.serialnumber,
            args.type,
            args.portgroupname)

        return
    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Storage port group " + args.portgroupname +
                           " : Deregister failed\n" + e.err_text)
        else:
            raise e

#
# Storage port group Main parser routine
#


def storageportgroup_parser(parent_subparser, common_parser):
    # main storage device parser

    parser = parent_subparser.add_parser('storageportgroup',
                                         description='ViPR' +
                                         ' Storage port group CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on storage port group')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    #create command parser
    create_parser(subcommand_parsers, common_parser)

    # register command parser
    resgister_parser(subcommand_parsers, common_parser)

    # deregister command parser
    deregister_parser(subcommand_parsers, common_parser)

    # deactivate command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

