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
        

    def objectuser_secretkey_list(self, systemUri, userId):
        '''
        Makes a REST API call to retrieve list all keys of an object user
        '''
        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "GET",ObjectUser.URI_OBJECTUSER_SECRET_KEYS.format(
                systemUri, userId),None, None)
        o = common.json_decode(s)
        return o

    def objectuser_secretkey_create(self, systemUri, userId, secretkey):
        '''
        Makes a REST API call to retrieve create secret key of an object user
        '''
        request = {
                  'secret_key' : secretkey
                  }
        body = json.dumps(request)
        print "only body " + body
        print "Full body  "+ ObjectUser.URI_OBJECTUSER_SECRET_KEYS.format(systemUri, userId)

        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",ObjectUser.URI_OBJECTUSER_SECRET_KEYS.format(
                systemUri, userId), body, None)
        o = common.json_decode(s)
        return o


# object user secret key list command parser
def list_secretkey_parser(subcommand_parsers, common_parser):

    list_secretkey_parser = subcommand_parsers.add_parser(
        'list_secretkey',
        description='ViPR Object user secret key list CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List secret keys')

    mandatory_args = list_secretkey_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-storagesystemUri', '-st',
                             metavar='<storagesystemUri>',
                             dest='storagesystemUri',
                             help='storage system URI',
                             required=True)
    mandatory_args.add_argument('-objectuser', '-ob',
                             metavar='<objectuser>',
                             dest='objectuser',
                             help='obect user id',
                             required=True)

    list_secretkey_parser.set_defaults(func=objectuser_secretkey_list)

def objectuser_secretkey_list(args):
    obj = ObjectUser(args.ip, args.port)
    try:
        res = obj.objectuser_secretkey_list(args.storagesystemUri , args.objectuser)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("list_secretkey", "objectuser",
                                        e.err_text, e.err_code)


# object user secret key create command parser
def create_secretkey_parser(subcommand_parsers, common_parser):

    create_secretkey_parser = subcommand_parsers.add_parser(
        'create_secretkey',
        description='ViPR Object user secret key create CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create secret key')

    mandatory_args = create_secretkey_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-storagesystemUri', '-st',
                             metavar='<storagesystemUri>',
                             dest='storagesystemUri',
                             help='storage system URI',
                             required=True)
    mandatory_args.add_argument('-objectuser', '-ob',
                             metavar='<objectuser>',
                             dest='objectuser',
                             help='obect user id',
                             required=True)
    mandatory_args.add_argument('-secretkey', '-sk',
                             metavar='<secretkey>',
                             dest='secretkey',
                             help='secret key for user specified',
                             required=True)

    create_secretkey_parser.set_defaults(func=objectuser_secretkey_create)

def objectuser_secretkey_create(args):
    obj = ObjectUser(args.ip, args.port)
    try:
        res = obj.objectuser_secretkey_create(args.storagesystemUri, args.objectuser, args.secretkey)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("create_secretkey", "objectuser",
                                        e.err_text, e.err_code)


def objectuser_parser(parent_subparser, common_parser):
    # main objectuser parser
    parser = parent_subparser.add_parser(
        'objectuser',
        description='ViPR object user CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Object user')
    subcommand_parsers = parser.add_subparsers(
        help='Use one of sub commands(list_secretkey, create_secretkey)')

    # list_secretkey command parser
    list_secretkey_parser(subcommand_parsers, common_parser)

    # create_secretkey command parser
    create_secretkey_parser(subcommand_parsers, common_parser)

