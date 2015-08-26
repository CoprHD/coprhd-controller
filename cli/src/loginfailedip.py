#!/usr/bin/python

# Copyright (c) 2015 EMC Corporation
# All Rights Reserved

'''
This module contains the login-failed-ip view and delete implementation

'''

import common
import json
from common import SOSError

class LoginFailedIP(object):

    '''
    The class definition for operations on 'login failed ip'.
    '''

    URI_LOGIN_FAILED_IPS = '/config/login-failed-ips'


    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    '''
    Get login-failed-ip list information
    '''

    def login_failed_ip_list(self):
        (s, h) = common.service_json_request(self.__ipAddr,
                self.__port, "GET", LoginFailedIP.URI_LOGIN_FAILED_IPS, None)
        o = common.json_decode(s)
        if(o):
            return o

        return None

    '''
    Delete specified ip from login-failed-ip list
    '''

    def login_failed_delete(self, ip):

        delete_uri = LoginFailedIP.URI_LOGIN_FAILED_IPS + "/" + ip
        (s, h) = common.service_json_request(self.__ipAddr,
                            self.__port, "DELETE", delete_uri, None)
        return None


# login-failed-ip Delete routines
def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Login-Failed-IP Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='remove IP(s) from login-failed-ip list')

    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-loginfailedip',
                                dest='loginfailedip',
                                metavar='loginfailedip',
                                required=True)

    delete_parser.set_defaults(func=login_failed_ip_delete)

def login_failed_ip_delete(args):
    obj = LoginFailedIP(args.ip, args.port)
    try:
        obj.login_failed_delete(args.loginfailedip)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "login-failed-ip delete failed: " + e.err_text)
        else:
            raise e


# login-failed-ip List routines
def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Login_Failed_IP List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List login failed ips')

    list_parser.set_defaults(func=login_failed_ip_list)


def login_failed_ip_list(args):
    obj = LoginFailedIP(args.ip, args.port)

    try:
        res = obj.login_failed_ip_list()
        return common.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "login-failed-ip list failed: " + e.err_text)
        else:
            raise e


    #CLASS - END

def loginfailedip_parser(parent_subparser, common_parser):
    parser = parent_subparser.add_parser('loginfailedip',
                                         description='vipr login-failed-ip CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on login failed ips')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)
