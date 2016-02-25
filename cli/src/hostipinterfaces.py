#!/usr/bin/python

#
# Copyright (c) 2013 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#

import common
import json
from common import SOSError
from host import Host
import sys

'''
The class definition for the operation on the ViPR HostIPInterface
'''


class HostIPInterface(object):
    # Indentation START for the class

    '''
    /compute/ip-interfaces/search
    /compute/ip-interfaces/{id}
    /compute/ip-interfaces/{id}/deactivate
    /compute/ip-interfaces/{id}/exports
    '''
    # All URIs for the Host ip-interface operations
    URI_IPINTERFACE_DETAILS = "/compute/ip-interfaces/{0}"
    URI_IPINTERFACE_DETAILS_BULK = "/compute/ip-interfaces/bulk"
    URI_HOST_LIST_IPINTERFACES = "/compute/hosts/{0}/ip-interfaces"
    URI_IPINTERFACE_DEACTIVATE = "/compute/ip-interfaces/{0}/deactivate"

    IPINTERFACE_PROTOCOL_LIST = ['IPV4', 'IPV6']

    __hostObject = None

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port
        self.__hostObject = Host(self.__ipAddr, self.__port)

    '''
    Returns the ip-interface URI for matching the name of the ip-interface
    '''

    def query_by_name(self, ipinterfaceName):

        # Get the list of ip-interfaces
        ipinterfaceList = self.list_all()

        # Match the name and return uri
        for ipinterface in ipinterfaceList:
            if(ipinterface['name'] == ipinterfaceName):
                return ipinterface['id']

        raise SOSError(
            SOSError.NOT_FOUND_ERR,
            "Ip-interface with name '" +
            ipinterfaceName +
            "' not found")

    '''
    Returns the ip-interface URI for matching the name of the ip-interface
    '''

    def query_by_ipaddress(self, ipInterfaceIp, hostName, tenant):

        # Get the list of ip-interfaces
        hostUri = self.get_host_uri(hostName, tenant)
        ipinterfaceList = self.get_host_object().list_ipinterfaces(hostUri)

        # Match the name and return uri
        for ipinterface in ipinterfaceList:
            ipinterfaceDetails = self.show_by_uri(ipinterface['id'])
            if(ipinterfaceDetails and
               ipinterfaceDetails['ip_address'] == ipInterfaceIp):
                return ipinterface['id']

        raise SOSError(
            SOSError.NOT_FOUND_ERR,
            "Ip-interface with IP-Address '" +
            ipInterfaceIp +
            "' not found")

    """
    ipinterface create operation
    """

    def create(self, hostlabel, protocol, ipAddress,
               netMask, prefixLength, scopeId, name, tenant):

        hostUri = self.get_host_uri(hostlabel, tenant)

        request = {'protocol': protocol,
                   'ip_address': ipAddress,
                   #'netmask '      : netMask,
                   #'prefix_length' :prefixLength,
                   #'scope_id'      : scopeId
                   }

        if(netMask):
            request['netmask'] = netMask

        if(prefixLength):
            request['prefix_length'] = prefixLength

        if(scopeId):
            request['scopeid'] = scopeId

        if(name):
            request['name'] = name

        body = json.dumps(request)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            HostIPInterface.URI_HOST_LIST_IPINTERFACES.format(hostUri),
            body)
        o = common.json_decode(s)
        return o

    """
    ipinterface update operation
    """

    def update(self, hostName, ipinterfaceAddress, newprotocol,
               newipAddress, newNetMask, newPrefixLength, newScopeId, tenant):

        ipinterfaceUri = self.query_by_ipaddress(ipinterfaceAddress, hostName, tenant)

        request = dict()

        if(newprotocol):
            request['protocol'] = newprotocol

        if(newipAddress):
            request['ip_address'] = newipAddress

        if(newNetMask):
            request['netmask'] = newNetMask

        if(newPrefixLength):
            request['prefix_length'] = newPrefixLength

        if(newScopeId):
            request['scope_id'] = newScopeId

        body = json.dumps(request)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "PUT",
            HostIPInterface.URI_IPINTERFACE_DETAILS.format(ipinterfaceUri),
            body)
        o = common.json_decode(s)
        return o

    """
    ipinterface delete operation
    """

    def delete(self, hostName, interfaceAddress, tenant):

        ipinterfaceUri = self.query_by_ipaddress(interfaceAddress, hostName, tenant)

        '''
        Makes a REST API call to delete a ipinterface by its UUID
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            HostIPInterface.URI_IPINTERFACE_DEACTIVATE.format(ipinterfaceUri),
            None)
        return

    '''
    Lists all ipinterfaces present in the system
    returns list of ipinterface elements
    <ipinterface>
     <name>...</name>
     <id>...</id>
     <link rel="..." href="..." />
    </ipinterface>
    '''

    def list_all(self):

        ipinterfacesList = []
        # Get all hosts in the system
        hostUris = self.__hostObject.list_host_uris()

        # Get the list of ipinterfaces from each host
        if(hostUris.__len__() > 0):
            for host in hostUris:
                tempipinterfaceList = self.__hostObject.list_ipinterfaces(host)
                if(tempipinterfaceList.__len__() > 0):
                    for tempipinterface in tempipinterfaceList:
                        ipinterfacesList.append(tempipinterface)
        else:
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "No ip-interfaces found in the system")

        return ipinterfacesList

    '''
    Lists all ipinterface uris present in the system
    '''

    def list_all_by_uri(self):

        ipinterfacesList = []
        # Get all hosts in the system
        hostUris = self.__hostObject.list_host_uris()

        # Get the list of ipinterfaces from each host
        if(hostUris.__len__() > 0):
            for host in hostUris:
                tempipinterfaceList = self.__hostObject.list_ipinterfaces(host)
                if(tempipinterfaceList.__len__() > 0):
                    for tempipinterface in tempipinterfaceList:
                        ipinterfacesList.append(tempipinterface['id'])
        else:
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "No hosts found in the system")

        return ipinterfacesList

    '''
    Gets the list  of ipinterface names
    '''

    def list_all_by_name(self):
        ipinterfacesList = []
        # Get all hosts in the system
        hostUris = self.__hostObject.list_host_uris()

        # Get the list of ipinterfaces from each host
        if(hostUris.__len__() > 0):
            for hostUri in hostUris:
                tempipinterfaceList = self.__hostObject.list_ipinterfaces(
                    hostUri)
                if(len(tempipinterfaceList) > 0):
                    for tempipinterface in tempipinterfaceList:
                        ipinterfacesList.append(tempipinterface['name'])
        else:
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "No hosts found in the system")

        return ipinterfacesList

    """
    Gets details of list of ipinterfaces
    """

    def show(self, ipinterfaceList):
        ipinterfaceListDetails = []
        if(ipinterfaceList is not None):
            for ipinterface in ipinterfaceList:
                ipinterfaceUri = ipinterface['id']
                hostDetail = self.show_by_uri(ipinterfaceUri)
                if(hostDetail is not None and len(hostDetail) > 0):
                        ipinterfaceListDetails.append(hostDetail)

        return ipinterfaceListDetails

    """
    Gets ipinterface details matching the protocol type
    """

    def show_by_protocol(self, ipinterfaceList, protocol):
        ipinterfaceListDetails = []
        if(ipinterfaceList is not None):
            for ipinterface in ipinterfaceList:
                ipinterfaceUri = ipinterface['id']
                ipinterfaceDetail = self.show_by_uri(ipinterfaceUri)
                if(ipinterfaceDetail and
                   len(ipinterfaceDetail) > 0 and
                   ipinterfaceDetail['protocol'] == protocol):
                        ipinterfaceListDetails.append(ipinterfaceDetail)

        return ipinterfaceListDetails

    """
    Gets ipinterface details matching the name
    """

    def show_by_name(self, ipinterfaceList, name, xml):
        ipinterfaceListDetails = None
        if(ipinterfaceList is not None):
            for ipinterface in ipinterfaceList:
                ipinterfaceUri = ipinterface['id']
                ipinterfaceDetail = self.show_by_uri(ipinterfaceUri)
                if(ipinterfaceDetail is not None and len(ipinterfaceDetail) > 0
                   and ipinterfaceDetail['name'] == name):
                    if(xml):
                        ipinterfaceListDetails = self.show_by_uri(
                            ipinterfaceUri,
                            xml)
                    else:
                        ipinterfaceListDetails = ipinterfaceDetail
                    break

        return ipinterfaceListDetails

    """
    Gets details of the ipinterface for a given uri
    """

    def show_by_uri(self, uri, xml=False):
        '''
        Makes a REST API call to retrieve details of a Host based on its UUID
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            HostIPInterface.URI_IPINTERFACE_DETAILS.format(uri),
            None, None)
        o = common.json_decode(s)
        inactive = common.get_node_value(o, 'inactive')

        if(inactive):
            return None
        if(xml):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "GET",
                HostIPInterface.URI_IPINTERFACE_DETAILS.format(uri),
                None, None, xml)
            return s
        else:
            return o

    '''
    Given the name of the host, returns the hostUri/id
    '''

    def get_host_uri(self, hostName, tenant=None):
        return self.__hostObject.query_by_name(hostName, tenant)

    def get_host_object(self):
        return self.__hostObject

    def list_tasks(self, host_name, ipaddress, task_id=None, tenant=None):

        ipinterfaceUri = self.query_by_ipaddress(ipaddress, host_name, tenant)

        ipinterface = self.show_by_uri(ipinterfaceUri)
        if(ipinterface['ip_address'] == ipaddress):
            if(not task_id):
                return common.get_tasks_by_resourceuri(
                    "ipinterface", ipinterfaceUri,
                    self.__ipAddr, self.__port)

            else:
                res = common.get_task_by_resourceuri_and_taskId(
                    "ipinterface", ipinterfaceUri, task_id,
                    self.__ipAddr, self.__port)
                if(res):
                    return res
        raise SOSError(
            SOSError.NOT_FOUND_ERR,
            "Ipinterface  with Ip Address : " +
            ipaddress +
            " not found")


    # Indentation END for the class
# Start Parser definitions
def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR host ip-interface create CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Creates a host ip-interface')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        help='Host label in which ip-interface needs to be created',
        metavar='<hostlabel>',
        dest='hostlabel',
        required=True)
    mandatory_args.add_argument(
        '-pl', '-protocol ',
        choices=HostIPInterface.IPINTERFACE_PROTOCOL_LIST,
        dest='protocol',
        help='ip-interface protocol',
        required=True)

    mandatory_args.add_argument(
        '-ipadr', '-ipaddress',
        help='IPAddress of the ip-interface node',
        dest='ipaddress',
        metavar='<ipaddress>',
        required=True)

    create_parser.add_argument('-nm', '-netmask',
                               help='Netmask of the ip-interface',
                               dest='netmask',
                               metavar='<netmask>')

    create_parser.add_argument('-pxl', '-prefixlength',
                               help='prefix length of the ip-interface',
                               dest='prefixlength',
                               metavar='<prefixlength>')

    create_parser.add_argument('-sid', '-scopeid',
                               help='scope id of the ip-interface',
                               dest='scopeid',
                               metavar='<scopeid>')
    
    create_parser.add_argument('-tenantname', '-tn',
                               help='Tenant Name',
                               dest='tenant',
                               metavar='<tenantname>')

    mandatory_args.add_argument('-n', '-name',
                               help='name of the ip-interface',
                               dest='name',
                               metavar='<name>',
                               required=True)

    create_parser.set_defaults(func=ipinterface_create)

'''
Preprocessor for the ipinterface create operation
'''


def ipinterface_create(args):

    ipinterfaceObj = HostIPInterface(args.ip, args.port)
    try:
        ipinterfaceObj.create(
            args.hostlabel,
            args.protocol,
            args.ipaddress,
            args.netmask,
            args.prefixlength,
            args.scopeid,
            args.name,
            args.tenant)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "create",
            "ip-interface",
            e.err_text,
            e.err_code)


# list command parser
def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR host ip-interface List CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists ip-interfaces')

    mandatory_args = list_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        dest='hostlabel',
        metavar='<hostlabel>',
        help='Host for which ip-interfaces to be listed',
        required=True)
    list_parser.add_argument('-pl', '-protocol ',
                             choices=HostIPInterface.IPINTERFACE_PROTOCOL_LIST,
                             dest='protocol',
                             help='ip-interface protocol')
    list_parser.add_argument('-tenantname', '-tn',
                               help='Tenant Name',
                               dest='tenant',
                               metavar='<tenantname>')
    list_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             action='store_true',
                             help='Lists ip-interfaces with details')
    list_parser.add_argument('-l', '-long',
                             dest='largetable',
                             action='store_true',
                             help='Lists ip-interfaces in a large table')
    list_parser.set_defaults(func=ipinterface_list)


'''
Preprocessor for ipinterface list operation
'''


def ipinterface_list(args):

    ipinterfaceList = None
    ipinterfaceObj = HostIPInterface(args.ip, args.port)
    from common import TableGenerator

    try:
        if(args.hostlabel):
            hostUri = ipinterfaceObj.get_host_uri(args.hostlabel, args.tenant)
            ipinterfaceList = ipinterfaceObj.get_host_object(
            ).list_ipinterfaces(
                hostUri)
        else:
            ipinterfaceList = ipinterfaceObj.list_all()

        if(len(ipinterfaceList) > 0):
            ipinterfaceListDetails = []
            if(args.protocol is None):
                ipinterfaceListDetails = ipinterfaceObj.show(ipinterfaceList)
            else:
                ipinterfaceListDetails = ipinterfaceObj.show_by_protocol(
                    ipinterfaceList,
                    args.protocol)

            if(args.verbose):
                return common.format_json_object(ipinterfaceListDetails)
            else:
                if(args.largetable):
                    TableGenerator(
                        ipinterfaceListDetails,
                        ['name',
                         'ip_address',
                         'protocol',
                         'netmask',
                         'prefix_length']).printTable()
                else:
                    TableGenerator(
                        ipinterfaceListDetails,
                        ['name',
                         'ip_address',
                         'protocol']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise(
            "list",
            "ip-interface",
            e.err_text,
            e.err_code)


# show command parser
def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR ip-interface Show CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show ip-interface details')
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-ipadr', '-ipaddress',
                                metavar='<ipaddress>',
                                dest='ipaddress',
                                help='Ip Address of ip-interface',
                                required=True)
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        dest='hostlabel',
        metavar='<hostlabel>',
        help='Host for which ip-interfaces to be searched',
        required=True)
    show_parser.add_argument('-tenantname', '-tn',
                               help='Tenant Name',
                               dest='tenant',
                               metavar='<tenantname>')
    show_parser.set_defaults(func=ipinterface_show)


def ipinterface_show(args):

    try:
        ipinterfaceList = []
        ipinterfaceObj = HostIPInterface(args.ip, args.port)
        interfaceUri = ipinterfaceObj.query_by_ipaddress(
            args.ipaddress,
            args.hostlabel, args.tenant)
        interfaceShow = ipinterfaceObj.show_by_uri(interfaceUri, args.xml)

        if(args.xml):
            return common.format_xml(interfaceShow)
        return common.format_json_object(interfaceShow)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "show",
            "ip-interface",
            e.err_text,
            e.err_code)

    return


def delete_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR ip-interface delete CLI usage ',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Deletes an ip-interface')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-ipadr', '-ipaddress',
                                metavar='<ipaddress>',
                                dest='ipaddress',
                                help='Ip Address of ip-interface',
                                required=True)
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        dest='hostlabel',
        metavar='<hostlabel>',
        help='Host for which ip-interface to be deleted',
        required=True)
    delete_parser.add_argument('-tenantname', '-tn',
                               help='Tenant Name',
                               dest='tenant',
                               metavar='<tenantname>')
    delete_parser.set_defaults(func=ipinterface_delete)


def ipinterface_delete(args):
    try:
        ipinterfaceObj = HostIPInterface(args.ip, args.port)
        ipinterfaceObj.delete(args.hostlabel, args.ipaddress, args.tenant)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "delete",
            "ip-interface",
            e.err_text,
            e.err_code)

    return


'''
Update Host Ipinterface Parser
'''


def update_parser(subcommand_parsers, common_parser):
    # create command parser
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Host update CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Updates an ip-interface')

    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument(
        '-ipadr', '-ipaddress',
        help='IP Address of the ipinterface to be updated',
        metavar='<ipaddress>',
        dest='ipaddress',
        required=True)
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        dest='hostlabel',
        metavar='<hostlabel>',
        help='Host for which ip-interfaces to be searched',
        required=True)
    update_parser.add_argument(
        '-npl', '-newprotocol ',
        choices=HostIPInterface.IPINTERFACE_PROTOCOL_LIST,
        dest='newprotocol',
        help='ipinterface protocol')

    update_parser.add_argument('-nipadr', '-newipaddress',
                               help='ipaddress of the ipinterface node ',
                               dest='newipaddress',
                               metavar='<newipaddress>')

    update_parser.add_argument('-nnm', '-newnetmask',
                               help='Netmask for the ip-interface',
                               dest='newnetmask',
                               metavar='<newnetmask>')

    update_parser.add_argument('-npxl', '-newprefixlength',
                               help='prefix length for the ip-interface',
                               dest='newprefixlength',
                               metavar='<newprefixlength>')
    update_parser.add_argument('-tenantname', '-tn',
                               help='Tenant Name',
                               dest='tenant',
                               metavar='<tenantname>')

    update_parser.add_argument('-nsid', '-newscopeid',
                               help='scope id for the ip-interface',
                               dest='newscopeid',
                               metavar='<newscopeid>')

    update_parser.set_defaults(func=ipinterface_update)


'''
Preprocessor for the host update operation
'''


def ipinterface_update(args):

    if(args.newprotocol is None and args.newipaddress is None and
       args.newnetmask is None and
       args.newprefixlength is None and args.newscopeid):
        raise SOSError(SOSError.CMD_LINE_ERR, sys.argv[0] + " " + sys.argv[1] +
                       " " + sys.argv[2] + ": error:" +
                       "At least one of the arguments :"
                       "-newprotocol -newipaddress -newnetmask"
                       "-newprefixlength, -newscopeid"
                       " should be provided to update the Host")

    ipinterfaceObj = HostIPInterface(args.ip, args.port)
    try:
        ipinterfaceObj.update(
            args.hostlabel,
            args.ipaddress,
            args.newprotocol,
            args.newipaddress,
            args.newnetmask,
            args.newprefixlength,
            args.newscopeid,
            args.tenant)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "update",
            "ipinterface",
            e.err_text,
            e.err_code)


def task_parser(subcommand_parsers, common_parser):
    # show command parser
    task_parser = subcommand_parsers.add_parser(
        'tasks',
        description='ViPR host ip interface tasks  CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='check tasks of a host ip interface')

    mandatory_args = task_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-ipadr', '-ipaddress',
                                metavar='<ipaddress>',
                                dest='ipaddress',
                                help='Ip Address of ip-interface',
                                required=True)
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        dest='hostlabel',
        metavar='<hostlabel>',
        help='Host for which ip-interface to be deleted',
        required=True)

    task_parser.add_argument('-id',
                             dest='id',
                             metavar='<id>',
                             help='Task ID')
    
    task_parser.add_argument('-tenantname', '-tn',
                               help='Tenant Name',
                               dest='tenant',
                               metavar='<tenantname>')

    task_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             action="store_true",
                             help='List all tasks')

    task_parser.set_defaults(func=host_ipinterface_list_tasks)


def host_ipinterface_list_tasks(args):
    obj = HostIPInterface(args.ip, args.port)

    try:
        # if(not args.tenant):
        #    args.tenant = ""
        if(args.id):
            res = obj.list_tasks(args.hostlabel, args.ipaddress, args.id, args.tenant)
            if(res):
                return common.format_json_object(res)
        elif(args.hostlabel):
            res = obj.list_tasks(args.hostlabel, args.ipaddress, None, args.tenant)
            if(res and len(res) > 0):
                if(args.verbose):
                    return common.format_json_object(res)
                else:
                    from common import TableGenerator
                    TableGenerator(res, ["module/id", "name",
                                         "state"]).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("get tasks list", "ipinterface",
                                        e.err_text, e.err_code)


#
# Host Main parser routine
#
def ipinterface_parser(parent_subparser, common_parser):

    parser = parent_subparser.add_parser(
        'ipinterface',
        description='ViPR ip-interface CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on ip-interface')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # show parser
    show_parser(subcommand_parsers, common_parser)

    # delete parser
    delete_parser(subcommand_parsers, common_parser)

    # update parser
    update_parser(subcommand_parsers, common_parser)

    # tasks parser
    task_parser(subcommand_parsers, common_parser)
