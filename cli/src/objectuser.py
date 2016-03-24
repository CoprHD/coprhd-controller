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
from common import TableGenerator
from storagesystem import StorageSystem


class ObjectUser(object):

    '''
    The class definition for operations on 'Object storage system users'.
    '''

    URI_SERVICES_BASE = ''
    URI_STORAGEDEVICES = '/vdc/storage-systems/{0}'
    URI_OBJECTUSER_SECRET_KEYS = URI_STORAGEDEVICES + '/object-user/{1}/secret-keys'


    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the SOS instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port
        

    def objectuser_secretkey_create(self, storagesystem, objectuser, secretkey):
        '''
        Makes a REST API call to retrieve create secret key of an object user
        
        '''
        stsystem_uri = None
        from storagesystem import StorageSystem
        obj = StorageSystem(self.__ipAddr, self.__port)
                 
        stsystem_uri = obj.query_by_name_and_type(storagesystem, "ecs")
        request = {
                  'secret_key' : secretkey
                  }
        body = json.dumps(request)
        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",ObjectUser.URI_OBJECTUSER_SECRET_KEYS.format(
                stsystem_uri, objectuser), body, None)
        o = common.json_decode(s)
        return o


# object user secret key create command parser
def create_secretkey_parser(subcommand_parsers, common_parser):

    create_secretkey_parser = subcommand_parsers.add_parser(
        'create_secretkey',
        description='ViPR Object user secret key create CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create secret key')

    mandatory_args = create_secretkey_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-storagesystem', '-st',
                             metavar='<storagesystem>',
                             dest='storagesystem',
                             help='Name of the storage system',
                             required=True)
    mandatory_args.add_argument('-objectuser', '-ob',
                             metavar='<objectuser>',
                             dest='objectuser',
                             help='The object user id',
                             required=True)
    
    
    create_secretkey_parser.set_defaults(func=objectuser_secretkey_create)

def objectuser_secretkey_create(args):
    obj = ObjectUser(args.ip, args.port)
    secretkey = None
    if (args.objectuser):
        secretkey = common.get_password("SecretKey")
    try:
        res = obj.objectuser_secretkey_create(args.storagesystem, args.objectuser, secretkey)

        
    except SOSError as e:
        common.format_err_msg_and_raise("create_secretkey", "objectuser",
                                        e.err_text, e.err_code)


def objectuser_parser(parent_subparser, common_parser):
    # main project parser

    parser = parent_subparser.add_parser('objectuser',
                                         description='ViPR Objectuser CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on Object User')
    subcommand_parsers = parser.add_subparsers(help='Use one of subcommands')

    # create command parser
    create_secretkey_parser(subcommand_parsers, common_parser)

