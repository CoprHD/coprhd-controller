#!/usr/bin/env python

# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#

import json
import common
import sys

from common import SOSError
from common import TableGenerator


class IpReconfig(object):

    URI_SERVICES_BASE = '/control/cluster'
    URI_IP_INFO = URI_SERVICES_BASE + '/ipinfo'
    URI_IP_RECONFIG_STATUS = URI_SERVICES_BASE + '/ipreconfig_status'
    URI_IP_RECONFIG = URI_SERVICES_BASE + '/ipreconfig'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance.
        These are needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port
		
    def get_ip_info(self):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                    "GET",
                                    IpReconfig.URI_IP_INFO,
                                    None)
        return s

    def reconfig_ip(self, post_operation="reboot"):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                    "POST",
                                    IpReconfig.URI_IP_RECONFIG + "?postOperation=" + post_operation,
                                    None)
        return s

    def get_ip_reconfig_status(self):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "GET",
                                             IpReconfig.URI_IP_RECONFIG_STATUS,
                                             None)
        return s
        
			
def get_ip_info_parser(subcommand_parsers, common_parser):
    # add command parser
    get_ip_info_parser = subcommand_parsers.add_parser('ip-info',
                    description='Get cluster ip information',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='get cluster current ip information')

    get_ip_info_parser.set_defaults(func=get_ip_info)


def get_ip_info(args):
    try:
        res = IpReconfig(args.ip, args.port).get_ip_info()
        if(not res or res == ''):
            print 'Failed to get ip information. Reason : ' + res
        else:
            return common.format_json_object(res)
            
    except SOSError as e:
        common.format_err_msg_and_raise("ipreconfig", "IP info",
                                        e.err_text, e.err_code)


def get_ip_reconfig_status_parser(subcommand_parsers, common_parser):
    # add command parser
    get_ip_reconfig_status_parser = subcommand_parsers.add_parser('status',
                    description='Get ip reconfig status',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='get ip reconfig status')

    get_ip_reconfig_status_parser.set_defaults(func=get_ip_reconfig_status)

def get_ip_reconfig_status(args):
    try:
        res = IpReconfig(args.ip, args.port).get_ip_reconfig_status()
        if(not res or res == ''):
            print 'Failed to get ip reconfig status. Reason : ' + res
        else:
            return common.format_json_object(res)

    except SOSError as e:
        common.format_err_msg_and_raise("ipreconfig", "status",
                                        e.err_text, e.err_code)


def reconfig_ip_parser(subcommand_parsers, common_parser):
    # add command parser
    reconfig_ip_parser = subcommand_parsers.add_parser('reconfig',
                    description='Reconfig ip',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='reconfig ip')

    reconfig_ip_parser.add_argument('-postOperation', '-o',
                             dest='postOperation',
                             action='store_true',
                             help='post operation: reboot or poweroff')

    reconfig_ip_parser.set_defaults(func=reconfig_ip)


def reconfig_ip(args):
    try:
        res = IPsecManager(args.ip, args.port).reconfig_ip(args.postOperation)
        if(not res or res == ''):
            print 'Failed to reconfig ip. Reason : ' + res
        else:
            return common.format_json_object(res)

    except SOSError as e:
        common.format_err_msg_and_raise("Ipreconfig", "reconfig",
                                        e.err_text, e.err_code)
										
def ipreconfig_parser(parent_subparser, common_parser):

    parser = parent_subparser.add_parser('ipreconfig',
                    description='ViPR IP Reconfig CLI usage',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Operations on IP Reconfig')
    
    subcommand_parsers = parser.add_subparsers(help='Use one of sub-commands')
		
    # get ip info command parser
    get_ip_info_parser(subcommand_parsers, common_parser)

    # get ipreconfig status command parser
    get_ip_reconfig_status_parser(subcommand_parsers, common_parser)

    # reconfig IP command parser
    reconfig_ip_parser(subcommand_parsers, common_parser)
