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
import sys
from common import SOSError
from tenant import Tenant
from vcenterdatacenter import VcenterDatacenter
from common import TableGenerator
from project import Project


class Bucket(object):

    '''
    The class definition for operations on 'Bucket'.
    '''
    #URI_BUCKET_CREATE = "/object/buckets"
    URI_BUCKET_SHOW = '/object/buckets/{0}'
    URI_BUCKET_DEACTIVATE = '/object/buckets/{0}/deactivate'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the SOS instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

        '''
        
        '''

    def bucket_create(self, name, project, owner , varray,vpool , retention, softquota , hardquota):
        
        
        
        from project import Project
        proj_obj = Project(self.__ipAddr, self.__port)
        project_uri = proj_obj.project_query(project)

        from virtualpool import VirtualPool
        vpool_obj = VirtualPool(self.__ipAddr, self.__port)
        vpool_uri = vpool_obj.vpool_query(vpool, "object")
        
        from virtualarray import VirtualArray
        varray_obj = VirtualArray(self.__ipAddr, self.__port)
        varray_uri = varray_obj.varray_query(varray)
        
        uri = "/object/buckets" + "?project=" + project_uri
        

        request = {
            'name': name,
            'varray': varray_uri,
            'vpool': vpool_uri
        }
        if(owner):
            request["owner"] = owner
        
       
        
        if(retention):
            request["retention"] = retention
        
        if(softquota):
            request["soft_quota"] = softquota
        if(hardquota):
            request["hard_quota"] = hardquota
        
        
        
        
        

        body = json.dumps(request)
        

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            uri,
            body)
        o = common.json_decode(s)
        return o


       
    #Routine to delete Bucket 
    
    
    def bucket_delete(self,tenant, project, name , forceDelete=False):
        
        
        if(name):
            
            
                                   
            bucket_uri = self.get_bucket_uri(tenant, project, name)
            
             
            
            
            
            request = dict()
            if(forceDelete):
                
                request ['forceDelete']  = False
            body = json.dumps(request)
            
       

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST", Bucket.URI_BUCKET_DEACTIVATE.format(bucket_uri), body)
        return
    
    
    def bucket_show(self , tenant, project, name , xml=False):

        if(name):
            
            
                                   
            bucket_uri = self.get_bucket_uri(tenant, project, name)
            
            

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Bucket.URI_BUCKET_SHOW.format(bucket_uri),
                                             None, None, xml)

        if(not xml):
            o = common.json_decode(s)
            if('inactive' in o):
                if(o['inactive']):
                    return None
        else:
            return s
        return o
    
    
    
        
    def get_bucket_uri(self, tenant, project, name):
        
        #if(name.startswith('urn:storageos:Bucket')):
        #   return name
        from project import Project
        proj_obj = Project(self.__ipAddr, self.__port)
        project_uri = proj_obj.project_query(tenant+"/"+project)
        
        
        
        resources = proj_obj.project_resource_show(tenant+"/"+project,xml=False)
       
        for resource in resources['project_resource']:
            
            if (resource is not None and resource['name'] == name):
                
                return resource['id']
            
        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "bucket  " + name + ": not found")
    
    
    #routine for bucket update 
    
    def bucket_update(self, name, project, varray, softquota, hardquota, retention , tenant):
        '''
        update bucket with softquota , hardquota , retention
        Parameters:
            name      : Name of the bucket
            tenant    : name of tenant
            
        Returns:
            result of the action.
        '''
        
        parms = {}
        
        
        
        if(softquota):
            parms['soft_quota'] = softquota
            
        if(hardquota):
            parms['hard_quota'] = hardquota
            
        if(retention):
            parms['retention'] = retention
            

        
        if(name):
            bucket_uri = self.get_bucket_uri( tenant, project, name)
            
            


        body = json.dumps(parms)
       
        common.service_json_request(self.__ipAddr, self.__port, "PUT",
                                    Bucket.URI_BUCKET_SHOW.format(bucket_uri),
                                    body)
        return
         

   
# create command parser
def create_parser(subcommand_parsers, common_parser):

    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Bucket Create CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create a Bucket')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument(
        '-name', '-n',
        metavar='<name>',
        dest='name',
        help='name for the bucket',
        required=True)
    mandatory_args.add_argument('-project', '-pr',
                               metavar='<project>',
                               dest='project',
                               help='name of the project',
                               default=None)
    mandatory_args.add_argument('-varray', '-va',
                               metavar='<varray>',
                               dest='varray',
                               help='name of the varray')
    mandatory_args.add_argument('-vpool', '-vpool',
                               help='name of a vpool',
                               dest='vpool',
                               metavar='<vpool>')
    mandatory_args.add_argument('-softquota', '-squota',
                               help='soft quota size',
                               dest='softquota',
                               metavar='<softquota>')
    mandatory_args.add_argument('-hardquota', '-hquota',
                               help='hard quota size',
                               dest='hardquota',
                               metavar='<hardquota>')
    
    mandatory_args.add_argument('-retention', '-ret',
                               help='retention period',
                               dest='retention',
                               metavar='<retention_period>')
    mandatory_args.add_argument('-owner', '-own',
                               help='ECS Owner',
                               dest='owner',
                               metavar='<owner>')
    create_parser.set_defaults(func=bucket_create)


def bucket_create(args):
    obj = Bucket(args.ip, args.port)
    try:
        if(args.vpool or args.varray):
            if(args.vpool is None or args.varray is None):
                print ("Both vpool and varray details are required")
                return
        obj.bucket_create(args.name, args.project,args.owner,
                           args.varray, args.vpool,args.retention , args.softquota, args.hardquota)
    except SOSError as e:
        common.format_err_msg_and_raise("create", "bucket",
                                        e.err_text, e.err_code)

# delete command parser


def delete_parser(subcommand_parsers, common_parser):

    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Bucket Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a Bucket')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='name of a the bucket',
                                required=True)
    mandatory_args.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='name of tenant',
                               default=None)
    mandatory_args.add_argument('-project', '-pr',
                               dest='project',
                               metavar='<project>',
                               help='name of Project')
    mandatory_args.add_argument('-project', '-pr',
                               dest='project',
                               metavar='<project>',
                               help='name of Project')
    delete_parser.add_argument('-forceDelete', '-fd',
                               dest='forcedelete',
                               choices = ["true" , "false"],
                               metavar='<forcedelete>',
                               help='force Delete option ')

    delete_parser.set_defaults(func=bucket_delete)


def bucket_delete(args):
    obj = Bucket(args.ip, args.port)
    try:
        obj.bucket_delete(args.tenant , args.project , args.name, args.forcedelete)
        return
    except SOSError as e:
        common.format_err_msg_and_raise("delete", "bucket",
                                        e.err_text, e.err_code)

# show command parser


def show_parser(subcommand_parsers, common_parser):

    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Bucket Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show a Bucket')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='name of a the bucket',
                                required=True)
    mandatory_args.add_argument('-project', '-tn',
                             metavar='<project>',
                             dest='project',
                             help='name of project',
                             default=None)
    mandatory_args.add_argument('-tenant', '-tn',
                             metavar='<tenant>',
                             dest='tenant',
                             help='name of tenant',
                             default=None)
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=bucket_show)


def bucket_show(args):
    obj = Bucket(args.ip, args.port)
    try:
        res = obj.bucket_show(args.tenant , args.project , args.name, args.xml)

        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "bucket",
                                        e.err_text, e.err_code)



# update command parser
def update_parser(subcommand_parsers, common_parser):

    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Bucket Update CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update a Bucket')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='name of a the bucket',
                                required=True)

    
    mandatory_args.add_argument('-project', '-pr',
                               metavar='<project>',
                               dest='project',
                               help='name of project ')
    update_parser.add_argument('-varray', '-va',
                               metavar='<varray>',
                               dest='varray',
                               help='varray')
    update_parser.add_argument('-softquota', '-sfquota',
                               help='softquota',
                               dest='softquota',
                               metavar='<softquota>')
    update_parser.add_argument('-hardquota', '-hdquota',
                               help='hardquota ',
                               dest='hardquota',
                               metavar='<hardquota>')
    update_parser.add_argument('-retention', '-ret',
                               help='retention in days',
                               dest='retention',
                               metavar='<retention_period>')
    mandatory_args.add_argument('-tenant', '-tn',
                               help='tenant',
                               dest='tenant',
                               metavar='<tenant>')

    update_parser.set_defaults(func=bucket_update)


def bucket_update(args):
    obj = Bucket(args.ip, args.port)
    try:
        if(args.varray is None and args.project is None ):
            raise SOSError(
                SOSError.CMD_LINE_ERR, sys.argv[0] + " " + sys.argv[1] +
                " " + sys.argv[2] + ": error:" + "Enter Project and Varray")

        

        obj.bucket_update(args.name, args.project, args.varray,
                           args.softquota, args.hardquota , args.retention , args.tenant)
    except SOSError as e:
        common.format_err_msg_and_raise("update", "bucket",
                                        e.err_text, e.err_code)





def bucket_parser(parent_subparser, common_parser):
    # main bucket parser
    parser = parent_subparser.add_parser(
        'bucket',
        description='ViPR Bucket CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Buckets')
    subcommand_parsers = parser.add_subparsers(
        help='Use one of sub commands(create, list, show, delete, update)')

    # create command parser
    create_parser(subcommand_parsers, common_parser)
    
    show_parser(subcommand_parsers, common_parser)
    
    delete_parser(subcommand_parsers, common_parser)
    
    update_parser(subcommand_parsers, common_parser)

    