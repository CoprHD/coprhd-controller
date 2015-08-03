#!/usr/bin/python

# Copyright (c) 2015 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

'''
This module contains the block-ip view and delete implementation

'''

import common
import json
from common import SOSError

class BlockIP(object):

    '''
    The class definition for operations on 'block ip'.
    '''

    URI_BLOCKIPS = '/config/block-ips'


    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    '''
    Get block ip list information
    '''

    def blockip_list(self):
        (s, h) = common.service_json_request(self.__ipAddr,
                self.__port, "GET", BlockIP.URI_BLOCKIPS, None)
        o = common.json_decode(s)
        if(o):
            return o

        return None

    '''
    Delete specified ip from block ip list, * for deleting all ips
    '''

    def blockip_delete(self, ip):

        delete_uri = BlockIP.URI_BLOCKIPS + "/" + ip
        (s, h) = common.service_json_request(self.__ipAddr,
                            self.__port, "DELETE", delete_uri, None)
        return None


# block ip Delete routines
def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Block IP Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='remove IP(s) from block list')

    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-blockip',
                                dest='blockip',
                                metavar='blockip',
                                required=True)

    delete_parser.set_defaults(func=blockip_delete)

def blockip_delete(args):
    obj = BlockIP(args.ip, args.port)
    try:
        obj.blockip_delete(args.blockip)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "block ip delete failed: " + e.err_text)
        else:
            raise e


# block ip List routines
def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Block IP List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List subtenants of a Tenant')

    list_parser.set_defaults(func=blockip_list)


def blockip_list(args):
    obj = BlockIP(args.ip, args.port)

    try:
        res = obj.blockip_list()
        return common.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Blockip list failed: " + e.err_text)
        else:
            raise e


    #CLASS - END

def blockip_parser(parent_subparser, common_parser):
    parser = parent_subparser.add_parser('blockip',
                                         description='vipr block ip CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on block ips')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)
