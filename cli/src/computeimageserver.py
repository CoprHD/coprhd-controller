#!/usr/bin/python

# Copyright (c) 2013-14 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

'''
This module contains the compute image server implementation
'''

import common
import json
from common import SOSError
from urihelper import singletonURIHelperInstance


class ComputeImageServers(object):

    URI_COMPUTE_IMAGE_SERVERS = "/compute/imageservers"
    URI_COMPUTE_IMAGE_SERVER_ID = URI_COMPUTE_IMAGE_SERVERS + "/{0}"
    URI_COMPUTE_IMAGE_SERVER_DELETE = URI_COMPUTE_IMAGE_SERVER_ID + "/deactivate"

    '''
    Constructor: takes IP address and port of the ViPR instance. These are
    needed to make http requests for REST API
    '''

    def __init__(self, ipAddr, port):
        self.__ipAddr = ipAddr
        self.__port = port


    # Get the list of image servers
    def list_imageservers(self):
        '''
        Makes REST API call and retrieves compute Image servers
        Returns:
            List of image server in JSON response payload
        '''

        uri = ComputeImageServers.URI_COMPUTE_IMAGE_SERVERS

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET", uri, None)
        o = common.json_decode(s)

        if("compute_imageserver" in o):
            return common.get_list(o, 'compute_imageserver')
        return []       

    '''
    Creates and discovers a Compute image server in ViPR
    '''

    def create_computeimageserver(self, name,imageserverip,
                imageserversecondip,username, password, 
                tftpbootdir, osinstalltimeout ,sshtimeout, imageimporttimeout):

        parms = {
                 'name': name,
                 'imageserver_user': username,
                 'imageserver_password': password,
                 'imageserver_ip': imageserverip,
                 'imageserver_secondip': imageserversecondip,
                 'tftpBootDir': tftpbootdir,
                 'osinstall_timeout': osinstalltimeout ,
                 'ssh_timeout': sshtimeout,
                 'imageimport_timeout': imageimporttimeout
                 }
        
        
        body = json.dumps(parms)
        print body
        
        (s, h) = common.service_json_request(self.__ipAddr,
                                self.__port, "POST",
                                self.URI_COMPUTE_IMAGE_SERVERS,
                                body)
        return common.json_decode(s)

    ''''
    Deletes a Compute image server and the discovered information about it from ViPR
    '''
    def delete_computeimageserver(self, computeimageserver):
        uri = self.query_computeimageserver(computeimageserver)

        (s, h) = common.service_json_request(self.__ipAddr,
                        self.__port, "POST",
                        ComputeImageServers.URI_COMPUTE_IMAGE_SERVER_DELETE.format(uri),
                        None)
        return #common.json_decode(s)
    
    def query_computeimageserver(self, name):
        '''
        Returns the UID of the compute image server specified by the name
        '''
        #computeimageserevers = self.list_computeimageserver()
        computeimageserevers = self.list_imageservers()

        for computeimageserever in computeimageserevers:
            if (computeimageserever['name'] == name):
                return computeimageserever['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "computeimageserever " + name + ": not found")

        return


    '''
    Gets a list of all (active) ViPR Compute Image Server.
    '''
    def list_computeimageserver(self):
        (s, h) = common.service_json_request(
                            self.__ipAddr, self.__port,
                            "GET",
                            ComputeImageServers.URI_COMPUTE_IMAGE_SERVERS,
                            None)

        o = common.json_decode(s)
        return o['compute_imageserver']    
    
    '''
    Updates the Compute image server, and (re)discovers it
    '''
    def update_computeimageserver(self, name, label,imageserverip, imageserversecondip,
                username, password, tftpbootdir, osinstalltimeout, sshtimeout, imageimporttimeout):

        parms = {}
        
        if(label):
            parms['name'] = label
        if(imageserverip):
            parms['imageserver_ip'] = imageserverip
        if(username):
            parms['imageserver_user'] = username
        if(password):
            parms['imageserver_password'] = password
        if(imageserversecondip):
            parms['imageserver_secondip'] = imageserversecondip
        if(tftpbootdir):
            parms['tftpBootDir'] = tftpbootdir
        if(osinstalltimeout):
            parms['osinstall_timeout'] = osinstalltimeout
        if(sshtimeout):
            parms['ssh_timeout'] = sshtimeout
        if(imageimporttimeout):
            parms['imageimport_timeout'] = imageimporttimeout            

        uri = self.query_computeimageserver(name)
        body = json.dumps(parms)
        (s, h) = common.service_json_request(self.__ipAddr,
                                self.__port, "PUT",
                                self.URI_COMPUTE_IMAGE_SERVER_ID.format(uri),
                                body)
        return common.json_decode(s)

    def computeimageserver_show(self, name, xml=False):
            uri = self.query_computeimageserver(name)
            return self.computeimageserver_show_by_uri(uri, xml)
        
    def computeimageserver_show_by_uri(self, uri, xml=False):
        '''
        Makes a REST API call to retrieve details of a Compute image server
        based on its UUID
        '''
        (s, h) = common.service_json_request(
                       self.__ipAddr, self.__port, "GET",
                       ComputeImageServers.URI_COMPUTE_IMAGE_SERVER_ID.format(uri),
                       None, None, xml)
        if(xml == False):
            o = common.json_decode(s)
            if(o['inactive']):
                return None
            else:
                return o
        #it is xml payload
        else:
            return s   
    
 
#
# Compute Image server Main parser routine
#

def computeimageserver_parser(parent_subparser, common_parser):
    # main Compute Image Server parser
    parser = parent_subparser.add_parser('computeimageserver',
                description='ViPR Compute Image Server CLI usage',
                parents=[common_parser],
                conflict_handler='resolve',
                help='Operations on  Compute Image Server')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Command')

    # create command parser
    create_computeimageserver_parser(subcommand_parsers, common_parser)
    
    # delete command parser
    delete_computeimageserver_parser(subcommand_parsers, common_parser)    

    # list command parser
    list_parser(subcommand_parsers, common_parser)
    
    # show command parser
    show_computeimageserver_parser(subcommand_parsers, common_parser)
    
    # update command parser
    update_computeimageserver_parser(subcommand_parsers, common_parser)
    
    
def create_computeimageserver_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Compute image server Create cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create an computeimageserver')
    compute_image_server_sub_common_parser(create_parser)
    create_parser.set_defaults(func=computeimageserver_create)

def computeimageserver_create(args):
        try:
            obj = ComputeImageServers(args.ip, args.port)
    
            passwd = None
            if (args.user and len(args.user) > 0):
                passwd = common.get_password("computeimageserver")
    
            obj.create_computeimageserver(args.name,args.imageserverip,
                            args.imageserversecondip, args.user, passwd,
                            args.tftpbootdir, args.osinstalltimeout ,args.sshtimeout , args.imageimporttimeout)
    
        except SOSError as e:
            raise common.format_err_msg_and_raise("create", "computeimageserver",
                                                  e.err_text, e.err_code)   
        
def compute_image_server_sub_common_parser(cc_common_parser):
    mandatory_args = cc_common_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computeimageservername>',
                                dest='name',
                                help='Name of compute image server',
                                required=True)
    mandatory_args.add_argument('-imageserverip', '-iip',
                                metavar='<imageserverip>',
                                dest='imageserverip',
                                help='FQDN or IP address of the server that serves Compute Images',
                                required=True)
    mandatory_args.add_argument('-imageserversecondip', '-issip',
                                  metavar='<imageserversecondipaddress>',
                                  dest='imageserversecondip',
                                  help='second ipaddress of computeimageserver',
                                  required=True)
    mandatory_args.add_argument('-user','-u', 
                                  dest='user',
                                  metavar='<username>',
                                  help='Username of compute image server',
                                  required=True)
    mandatory_args.add_argument('-tftpbootdir','-tftd',
                                  dest='tftpbootdir',
                                  metavar='<tftpbootdirectory>',
                                  help='tft boot directory of compute image server',
                                  required=True)
    mandatory_args.add_argument('-osinstalltimeout', '-itm', 
                                  dest='osinstalltimeout',
                                  metavar='<osinstalltimeoutseconds>',
                                  help='osinstalltimeout seconds of compute image server',
                                  required=True)
    mandatory_args.add_argument('-sshtimeout', '-sshtm', 
                                  dest='sshtimeout',
                                  metavar='<sshtimeoutseconds>',
                                  help='sshtimeout seconds of compute image server',
                                  default = 20 ,
                                  required=False)
    mandatory_args.add_argument('-imageimporttimeout', '-iitm', 
                                  dest='imageimporttimeout',
                                  metavar='<imageimporttimeoutseconds>',
                                  help='imageimporttimeout seconds of compute image server',
                                  default= 1800 ,
                                  required=False)


 
def delete_computeimageserver_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Compute image server delete cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete an computeimageserver')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computeimageservername>',
                                dest='name',
                                help='Name of compute image server',
                                required=True)
    delete_parser.set_defaults(func=computeimageserver_delete)

def computeimageserver_delete(args):
    try:
        obj = ComputeImageServers(args.ip, args.port)
        obj.delete_computeimageserver(args.name)
           
    except SOSError as e:
        raise common.format_err_msg_and_raise("delete", "computeimageserver",
                                                  e.err_text, e.err_code)
                       
    
    
#  Compute Image Server List routines

def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR  Compute Image Server List CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists Compute Image Servers')
    list_parser.add_argument('-verbose', '-v',
                             dest='verbose',
                             help='List Compute Image Server with details',
                             action='store_true')
    list_parser.add_argument('-long', '-l',
                             dest='long',
                             help='List Compute Image Server in table',
                             action='store_true')

    list_parser.set_defaults(func=computeimageserver_list)
    

def show_computeimageserver_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Compute Image Server show cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show an Compute Image Server')
    
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computeimageservername>',
                                dest='name',
                                help='FQDN or IP address of the server that serves Compute Images',
                                required=True)
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    show_parser.set_defaults(func=computeimageserver_show)        

def computeimageserver_show(args):
    obj = ComputeImageServers(args.ip, args.port)
    try:
        res = obj.computeimageserver_show(args.name, args.xml)

        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "computeimageserver",
                                        e.err_text, e.err_code)

    
def computeimageserver_list(args):
    obj = ComputeImageServers(args.ip, args.port)
    from common import TableGenerator
    try:
        computeImageServerList = obj.list_imageservers()
        resultList = []
        
        for iter in computeImageServerList:
            rslt = obj.computeimageserver_show(iter['name'])
            if(rslt is not None):
                resultList.append(rslt)
 
        if(len(resultList) > 0):
            # show a short table
            if(args.verbose is False and args.long is False):
                TableGenerator(computeImageServerList,
                               ['name']).printTable()
            # show a long table
            if(args.verbose is False and args.long is True):
                TableGenerator(
                    resultList,
                    ["name", "imageserverip","imageserversecondip", "tftpBootdir", "imageserver_status"]).printTable()
            # show all items in json format
            if(args.verbose):
                return common.format_json_object(resultList)

        else:
            return
    except SOSError as e:
        raise e
    
def update_computeimageserver_parser(subcommand_parsers, common_parser):
    # update command parser
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Compute image server update cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update an computeimageserver')
    
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computeimageservername>',
                                dest='name',
                                help='FQDN or IP address of the server that serves Compute Images',
                                required=True)    
    update_parser.add_argument('-label', '-l',
                                metavar='<label>',
                                dest='label',
                                help='new label server that serves Compute Images')  
    update_parser.add_argument('-imageserverip', '-iip',
                                metavar='<imageserverip>',
                                dest='imageserverip',
                                help='FQDN or IP address of the server that serves Compute Images')      
    update_parser.add_argument('-imageserversecondip', '-issip',
                                  metavar='<imageserversecondipaddress>',
                                  dest='imageserversecondip',
                                  help='second ipaddress of computeimageserver')
    update_parser.add_argument('-user', '-u',
                                  dest='user',
                                  metavar='<username>',
                                  help='Username of compute image server')
    update_parser.add_argument('-tftpbootdir', '-tftd', 
                                  dest='tftpbootdir',
                                  metavar='<tftpbootdirectory>',
                                  help='tft boot directory of compute image server')
    update_parser.add_argument('-osinstalltimeout','-itm',
                                  dest='osinstalltimeout',
                                  metavar='<osinstalltimeoutseconds>',
                                  help='osinstalltimeout seconds of compute image server')
    update_parser.add_argument('-sshtimeout','-sshtm',
                                  dest='sshtimeout',
                                  metavar='<sshtimeoutseconds>',
                                  help='sshtimeout seconds of compute image server')
    update_parser.add_argument('-imageimporttimeout','-iitm',
                                  dest='imageimporttimeout',
                                  metavar='<imageimporttimeoutseconds>',
                                  help='imageimporttimeout seconds of compute image server')
    
    update_parser.set_defaults(func=computeimageserver_update) 
    
    
def computeimageserver_update(args):
    try:
        obj = ComputeImageServers(args.ip, args.port)
        passwd = None
        if (args.user and len(args.user) > 0):
            passwd = common.get_password("computeimageserver")

        obj.update_computeimageserver(args.name, args.label, args.imageserverip,
                        args.imageserversecondip, args.user, passwd,
                        args.tftpbootdir, args.osinstalltimeout, args.sshtimeout, args.imageimporttimeout)

    except SOSError as e:
        raise common.format_err_msg_and_raise("update", "computeimageserver",
                                              e.err_text, e.err_code)   
        
