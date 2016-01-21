#!/usr/bin/env python

# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

import json
import common
import sys

from common import SOSError
from common import TableGenerator


class IPsecManager(object):

    URI_SERVICES_BASE = ''
    URI_IPSEC = URI_SERVICES_BASE + '/ipsec'
    
    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance.
        These are needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port
		
    def rotate_ipsec_key(self):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                    "POST",
                                    IPsecManager.URI_IPSEC,
                                    None)
        return s

    def get_ipsec_status(self, xml=False):
        if(xml == False):
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                                 "GET",
                                                 IPsecManager.URI_IPSEC,
                                                 None)
            return common.json_decode(s)
        else:
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                                 "GET",
                                                 IPsecManager.URI_IPSEC,
                                                 None, None, xml)
            return s
        
			
def rotate_ipsec_key_parser(subcommand_parsers, common_parser):
    # add command parser
    rotate_ipsec_key_parser = subcommand_parsers.add_parser('rotate-key',
                    description='ViPR IPsec key rotation CLI usage',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Rotates or generates the IPsec keys.')

    rotate_ipsec_key_parser.set_defaults(func=rotate_ipsec_key)


def rotate_ipsec_key(args):
    try:
        res = IPsecManager(args.ip, args.port).rotate_ipsec_key()
        if(not res or res == ''):
            print 'Failed to rotate the IPsec key. Reason : ' + res
        else:
            print 'Successfully rotated the IPsec key. New IPsec configuration version is ' + res
            
    except SOSError as e:
        common.format_err_msg_and_raise("rotate", "IPsec key",
                                        e.err_text, e.err_code)

def get_ipsec_status_parser(subcommand_parsers, common_parser):
    # add command parser
    ipsec_status_parser = subcommand_parsers.add_parser('status',
                    description='ViPR IPsec status CLI usage',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Gets the IPsec status and current configuration version.')

    ipsec_status_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    ipsec_status_parser.set_defaults(func=get_ipsec_status)


def get_ipsec_status(args):
    try:
        res = IPsecManager(args.ip, args.port).get_ipsec_status(args.xml)
        if(not res or res == ''):
            print 'Failed to get the IPsec status. Reason : ' + res
        else:
            if(args.xml == True):
                return common.format_xml(res)
            else:
                return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("status", "IPsec",
                                        e.err_text, e.err_code)
										
def ipsec_parser(parent_subparser, common_parser):

    parser = parent_subparser.add_parser('ipsec',
                    description='ViPR IPsec CLI usage',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Operations on IPsec')
    
    subcommand_parsers = parser.add_subparsers(help='Use one of sub-commands')
		
    # rotate IPsec key command parser
    rotate_ipsec_key_parser(subcommand_parsers, common_parser)

    # get IPsec status command parser
    get_ipsec_status_parser(subcommand_parsers, common_parser)
