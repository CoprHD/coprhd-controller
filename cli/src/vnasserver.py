#!/usr/bin/python
'''
 Copyright (c) 2012 EMC Corporation
 All Rights Reserved

 This software contains the intellectual property of EMC Corporation
 or is licensed to EMC Corporation from third parties.  Use of this
 software and the intellectual property contained therein is expressly
 limited to the terms and conditions of the License Agreement under which
 it is provided by or on behalf of EMC.
'''
import common
import sys
import json
from common import SOSError
from project import Project
from storagesystem import StorageSystem


class VnasServer(object):

    '''
    The class definition for operations on 'VNAS server'.
    '''

    URI_SERVICES_BASE = ''
    URI_STORAGEDEVICES = '/vdc/storage-systems'
    URI_VNSSERVER = '/vdc/vnas-servers'
    URI_VNASSERVER_SHOW = '/vdc/vnas-servers/{0}'
    URI_VNASSERVER_ASSIGN = '/projects/{0}/assign-vnas-servers'
    URI_VNASSERVER_UNASSIGN = '/projects/{0}/unassign-vnas-servers' 
    URI_TENANT = "/tenant"

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the SOS instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port
        
 
    #Vnasserver Query
    def vnasserver_query(self, name):
        
       
        
        if (common.is_uri(name)):
            return name

        uris = self.list_vnasservers()

        for uri in uris:
            vnasserver = self.vnasserver_show(uri, False)
            if(vnasserver):
                if(vnasserver['name'] == name):
                    return vnasserver['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "vnasserver " + name + ": not found")
        
    # Get the list of VNAS servers
    def list_vnasservers(self):
        '''
        Makes REST API call and retrieves vnasserver 
        Parameters:
            
        Returns:
            List of vnasservers UUIDs in JSON response payload
        '''
        uri = VnasServer.URI_VNSSERVER

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET", uri, None)
        o = common.json_decode(s)
        returnlst = []
        for item in o['vnas_server']:
            
            returnlst.append(item['id'])

        return returnlst
    
    
    def list_vnasserver_names(self):
        '''
        Makes REST API call and retrieves vnasserver 
        Parameters:
            
        Returns:
            List of vnasservers UUIDs in JSON response payload
        '''
        uri = VnasServer.URI_VNSSERVER

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET", uri, None)
        o = common.json_decode(s)

        if("vnas_server" in o):
            return common.get_list(o, 'vnas_server' )
        return []     

        
    
    def vnasserver_show(self, label, xml=False):

        uri = self.vnasserver_query(label)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             VnasServer.URI_VNASSERVER_SHOW.format(uri),
                                             None, None, xml)

        if(not xml):
            o = common.json_decode(s)
            if('inactive' in o):
                if(o['inactive']):
                    return None
        else:
            return s
        return o   
    
    def getvnasserverList(self, vnasservers):
        vnasserver_obj = VnasServer(self.__ipAddr, self.__port)
        return vnasserver_obj.convertNamesToUris(vnasservers)
    
    def convertNamesToUris(self, vnasserverNameList):
        vnasserverList = []
        if (vnasserverNameList):
            for vnasserver in vnasserverNameList:
                vnasserver_uri = self.vnasserver_query(vnasserver)
                vnasserverList.append(vnasserver_uri)

        return vnasserverList
    
    
    #assign project routine 
    def assign(self, name, project):
        '''
        Assigns the vnasserver to a Project
        Parameters:
            name    : name of the vnasserver.
            project : project to be assigned .
        Returns:
            
        
        '''
        vnasserverList = self.getvnasserverList(name)
        
        
        request =  {'vnas_server' : vnasserverList }
                   

        if(project):
            proj_object = Project(self.__ipAddr, self.__port)
            pr_uri = proj_object.project_query(project)
                
        body = json.dumps(request)
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "PUT",
                                             VnasServer.URI_VNASSERVER_ASSIGN.format(pr_uri),
                                             body)
        
        if(s is not None and len(s) > 0):
            print s
            o = common.json_decode(s)
            return o
        
        
    def unassign(self, name, project=None):
        '''
        Vnasserver unassign
        Parameters:
            name: name of the vnasserver.
            project : varray to be assigned .
        Returns:
            
        
        '''
        vnasserverList = self.getvnasserverList(name)
        
        
        request =  {'vnas_server' : vnasserverList }

        if(project):
            proj_object = Project(self.__ipAddr, self.__port)
            pro_uri = proj_object.project_query(project)
                
                      
        body = json.dumps(request)
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "PUT",
                                             VnasServer.URI_VNASSERVER_UNASSIGN.format(pro_uri),
                                             body)
        
     
        if(s is not None and len(s) > 0):
            print s
            o = common.json_decode(s)
            return o

    
    def getTenantName(self):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "GET",
                                             VnasServer.URI_TENANT,
                                             None)
        o = common.json_decode(s)
        tenant_name = o['name']
        return tenant_name    
#
# Vns server Main parser routine


# VNAS Server List routines

def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR vNAS server List CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists vNAS servers')
    list_parser.add_argument('-verbose', '-v',
                             dest='verbose',
                             help='List vNAS servers with details',
                             action='store_true')
    list_parser.add_argument('-long', '-l',
                             dest='long',
                             help='List vNAS server in table',
                             action='store_true')

    list_parser.set_defaults(func=vnasserver_list)
    
    
def vnasserver_list(args):
    obj = VnasServer(args.ip, args.port)
    from common import TableGenerator
    from project import Project
    try:
        vnasServerList = obj.list_vnasserver_names()
        resultList = []
        for iter in vnasServerList:
            rslt = obj.vnasserver_show(iter['name'])
            
            #rslt['parent_nas']['name'] should be printed under PARENT_NAS_SERVER.
            #Updating it as a new element in the map
            if('parent_nas' in rslt) and ('name' in rslt['parent_nas']):
                rslt['parent_nas_server'] = rslt['parent_nas']['name']
                
            pr_object = None
            if ('project' in rslt) and ('id' in rslt['project']):
                pr_object = Project(args.ip, args.port).project_show_by_uri(rslt['project']['id']) 
            
            st_object = None
            if ('storage_device' in rslt) and ('id' in rslt['storage_device']):
                st_object = StorageSystem(args.ip, args.port).show_by_uri(rslt['storage_device']['id']) 
                
            if pr_object and ('name' in pr_object):
                rslt['project_name'] = pr_object['name']
                
            if st_object and ('name' in st_object):
                rslt['storage_system'] = st_object['name']
            if(rslt is not None):
                resultList.append(rslt)
         
        
        if(len(resultList) > 0):
            # show a short table
            if(args.verbose is False and args.long is False):
                TableGenerator(vnasServerList,
                               ["name"]).printTable()
            
            # show a long table
            if(args.verbose is False and args.long is True):
                TableGenerator(
                    resultList,
                    ["nas_name","parent_nas_server","nas_state","storage_domains","project_name","protocols", "storage_system"]).printTable()
                    
            # show all items in json format
            if(args.verbose):
                return common.format_json_object(resultList)

        else:
            return
    except SOSError as e:
        raise e
    
    
def show_parser(subcommand_parsers, common_parser):

    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Vnasserver Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show a Vnasserver')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='name of a the vnasserver',
                                required=True)
    
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=vnasserver_show)


def vnasserver_show(args):
    obj = VnasServer(args.ip, args.port)
    try:
        res = obj.vnasserver_show(args.name, args.xml)

        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "vnasserver",
                                        e.err_text, e.err_code)


def assign_parser(subcommand_parsers, common_parser):
    assign_parser = subcommand_parsers.add_parser(
        'assign',
        description='ViPR Vnasserver Assign to Project CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Assign vnasserver to project')
    mandatory_args = assign_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='List of vnasservers',
                                metavar='<vnas_server>',
                                dest='name',
                                required=True ,
                                nargs='+')

    mandatory_args.add_argument('-project', '-pr',
                               metavar='<project>',
                               dest='project',
                               help='Name of project',
                               required=True)
    
    mandatory_args.add_argument('-tenant', '-tn',
                               metavar='<tenant>',
                               dest='tenant',
                               help='Name of tenant',
                               required=True)
    assign_parser.set_defaults(func=vnasserver_assign)


def vnasserver_assign(args):
    obj = VnasServer(args.ip, args.port)
    project_name = args.tenant + "/" + args.project
    try:
        obj.assign(args.name, project_name)
    except SOSError as e:
        common.format_err_msg_and_raise("assign project", "vnasserver",
                                        e.err_text, e.err_code)


def unassign_parser(subcommand_parsers, common_parser):
    unassign_parser = subcommand_parsers.add_parser(
        'unassign',
        description='ViPR Vnasserver Un-Assign to Project CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Un-Assign vnasserver to project')
    mandatory_args = unassign_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='List of vnasservers',
                                metavar='<vnas_server>',
                                dest='name',
                                required=True ,
                                nargs='+')

    mandatory_args.add_argument('-project', '-pr',
                               metavar='<project>',
                               dest='project',
                               help='Name of project',
                               required=True)
    unassign_parser.set_defaults(func=vnasserver_unassign)


def vnasserver_unassign(args):
    obj = VnasServer(args.ip, args.port)
    try:
        obj.unassign(args.name, args.project)
    except SOSError as e:
        common.format_err_msg_and_raise("unassign project", "vnasserver",
                                        e.err_text, e.err_code)

        
def vnasserver_parser(parent_subparser, common_parser):
  
    parser = parent_subparser.add_parser('vnasserver',
                description='ViPR vNAS Server CLI usage',
                parents=[common_parser],
                conflict_handler='resolve',
                help='Operations on vNAS Server')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Command')


    # list command parser
    list_parser(subcommand_parsers, common_parser)
    
    #show command parser
    show_parser(subcommand_parsers, common_parser)
    
    #assign command parser
    assign_parser(subcommand_parsers, common_parser)
    
    #unassign command parser 
    unassign_parser(subcommand_parsers, common_parser)
    

