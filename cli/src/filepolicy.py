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
import tag
import json
import socket
import commands
from common import SOSError
from threading import Timer
import schedulepolicy
import virtualpool
import host
import pprint
from ppretty import ppretty 


class FilePolicy(object):

    '''
    The class definition for operations on 'Fileshare'.
    '''
    # Commonly used URIs for the 'Fileshare' module
    URI_FILE_POLICIES='/file/file-policies'
    URI_FILE_POLICY_SHOW= URI_FILE_POLICIES + '/{0}'
    URI_FILE_POLICY_DELETE= URI_FILE_POLICIES+'/{0}'



    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port


    #Vnasserver Query
    def filepolicy_query(self, name):
        
        if (common.is_uri(name)):
            return name

        policies = self.list_file_polices()

        for policy in policies:
            if(policy['name'] == name):
                filepolicy = self.filepolicy_show_by_uri(policy['id'], False)
                return filepolicy

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "filepolicy " + name + ": not found")
    
    def list_file_polices(self):
        '''
        Makes REST API call and retrieves vnasserver 
        Parameters:
            
        Returns:
            List of FilePolicies UUIDs in JSON response payload
        '''
        uri = FilePolicy.URI_FILE_POLICIES

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET", uri, None)
        o = common.json_decode(s)
        returnlst = []

        if( "file_policy" in o):
            return common.get_list(o, 'file_policy')
        return returnlst        


    def filepolicy_show(self, label, xml=False):

        filepolicy = self.filepolicy_query(label)
        return filepolicy


    def filepolicy_show_by_uri(self, uri, xml=False):

        if(xml):
            (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                    FilePolicy.URI_FILE_POLICY_SHOW.format(uri),
                    None, None, xml)
            return s

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                    "GET", FilePolicy.URI_FILE_POLICY_SHOW.format(uri), None)

        
        o = common.json_decode(s)
        inactive = common.get_node_value(o, 'inactive')

        if(inactive == True):
            return None
        return o

    def filepolicy_delete_by_uri(self, uri):
        '''
        Deletes a filepolicy based on project UUID
        Parameters:
            uri: UUID of filepolicy
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                    "DELETE", FilePolicy.URI_FILE_POLICY_DELETE.format(uri), None)
        return

    def filepolicy_delete(self, name):
        '''
        Deletes a filepolicy based on policy name
        Parameters:
            name: name of filepolicy
        '''
        project_uri = self.filepolicy_query(name)
        return self.filepolicy_delete_by_uri(filepolicy_uri)

def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Filepolicy List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List filepolicies')
    mandatory_args = list_parser.add_argument_group('mandatory arguments')

    list_parser.set_defaults(func=filepolicy_list)


def filepolicy_list(args):
    obj = FilePolicy(args.ip, args.port)
    result = obj.list_file_polices()

    from common import TableGenerator
    TableGenerator(result, ['name']).printTable()

def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Filepolicy Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='show filepolicies')

    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of Filepolicy',
                                required=True)

    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=filepolicy_show)

def filepolicy_show(args):
    obj = FilePolicy(args.ip, args.port)
    try:
        res = obj.filepolicy_show(args.name, args.xml)
        if(res):
            if (args.xml == True):
                return common.format_xml(res)
            return common.format_json_object(res)
    except SOSError as e:
        raise e



def create_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR FilePolicy Create CLI usage.',
        parents=[common_parser], conflict_handler='resolve',
        help='Create a filesystem')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<policy_name>',
                                dest='name',
                                help='Name of the policy',
                                required=True)
     mandatory_args.add_argument('-type', '-t',
                                metavar='<policy_type>',
                                dest='policy_type',
                                help='Type of the policy, valid values are : file_snapshot, file_replication, file_quota',
                                required=True)
    create_parser.add_argument('-description', '-dc',
                               metavar='<policy_description>',
                               dest='description',
                               help='Policy Description')
    mandatory_args.add_argument('-priority', '-pr',
                                metavar='<priority>', dest='priority',
                                help='Priority of the policy',
                                required=True)
    mandatory_args.add_argument('-tenants_access', '-ta',
                                metavar='<is_access_to_tenants>', dest='tenants_access',
                                help='Tenants access',
                                required=False)
    create_parser.set_defaults(func=filepolicy_create)

    def filepolicy_create(subcommand_parsers, common_parser):
        print "create file policy- CALLED"




# FilePolicy Delete routines

def delete_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Filesystem Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a filesystem')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<policy-name>',
                                dest='name',
                                help='Name of FilePolicy',
                                required=True)
    delete_parser.set_defaults(func=filepolicy_delete)


def filepolicy_delete(subcommand_parsers, common_parser):
    obj = FilePolicy(args.ip, args.port)
    try:
        if(not args.name):
            args.name = ""
        obj.filepolicy_delete(args.name)

    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "FilePolicy delete failed: " + e.err_text)
        else:
            raise e


#
# FilePolicy Main parser routine
#
def filepolicy_parser(parent_subparser, common_parser):
    # main project parser

    parser = parent_subparser.add_parser(
        'filepolicy',
        description='ViPR filepolicy CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on filepolicy')
    subcommand_parsers = parser.add_subparsers(help='Use one of subcommands')    

     # list command parser
    list_parser(subcommand_parsers,common_parser)

    #show command parser
    show_parser(subcommand_parsers, common_parser)

    '''
    # create command parser
    create_parser(subcommand_parsers, common_parser)
    '''
    # delete command parser
    delete_parser(subcommand_parsers, common_parser)
