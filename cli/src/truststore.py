#!/usr/bin/python
# Copyright (c)2015 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import json
import common

from common import SOSError


class TrustStore(object):

    '''
    The class definition for operations on 'TrustStore'.
    '''

    # Commonly used URIs for the 'truststores' module
    URI_TRUSTSTORE = '/vdc/truststore'
    URI_TRUSTSTORE_SETTINGS = URI_TRUSTSTORE + '/settings'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def truststore_show(self, xml=False):
        '''
        Makes a REST API call to retrieve details of a truststore
        '''

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            TrustStore.URI_TRUSTSTORE,
            None, None, xml)

        if(xml is False):
            o = common.json_decode(s)
            if('inactive' in o):
                if(o['inactive'] is True):
                    return None
                else:
                    return o
            else:
                return o
        else:
            return s

    def truststore_get_settings(self, xml=False):
        '''
        Makes a REST API call to retrieve setttings of a truststore
        '''

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            TrustStore.URI_TRUSTSTORE_SETTINGS,
            None, None, xml)

        if(xml is False):
            o = common.json_decode(s)
            if('inactive' in o):
                if(o['inactive'] is True):
                    return None
                else:
                    return o
            else:
                return o
        else:
            return s

    def truststore_update_certificates(self, certstoadd=None,
                                       certstoremove=None):
        '''
        creates a truststore
        parameters:
            certstoadd : certificates to add
            certstoremove : certificates to remove
        Returns:
            JSON payload response
        '''
        requestParams = dict()
        addcert = None
        remcert = None

        if(certstoadd):
            try:
                f1 = open(certstoadd, 'r')
                addcert = f1.read()
            except IOError as e:
                raise SOSError(e.errno, e.strerror)


        if(certstoremove):
            try:
                f2 = open(certstoremove, 'r')
                remcert = f2.read()
            except IOError as e:
                raise SOSError(e.errno, e.strerror)

        addlist = []
        remlist = []

        if(addcert):
            addlist.append(addcert)

        if(remcert):
            remlist.append(remcert)

        requestParams = {
            'add': addlist,
            'remove': remlist
        }

        body = json.dumps(requestParams)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "PUT", TrustStore.URI_TRUSTSTORE, body)
        o = common.json_decode(s)
        return o

    def truststore_update_settings(self, acceptallcertificates):
        '''
        creates a truststore
        parameters:
            acceptallcertificates:  trust all certificates setting
        Returns:
            JSON payload response
        '''
        requestParams = dict()

        requestParams = {
            'accept_all_certificates': acceptallcertificates
        }

        body = json.dumps(requestParams)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "PUT", TrustStore.URI_TRUSTSTORE_SETTINGS, body)
        o = common.json_decode(s)
        return o


# VTRUSTSTORE Show routines


def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR truststore Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show a truststore')

    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=truststore_show)


def truststore_show(args):
    obj = TrustStore(args.ip, args.port)
    try:
        res = obj.truststore_show(args.xml)
        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "truststore",
                                        e.err_text, e.err_code)


def show_settings_parser(subcommand_parsers, common_parser):
    # show command parser
    show_settings_parser = subcommand_parsers.add_parser(
        'show-settings',
        description='ViPR truststore Show Settings CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show a truststore settings')

    show_settings_parser.add_argument('-xml',
                                      dest='xml',
                                      action='store_true',
                                      help='XML response')

    show_settings_parser.set_defaults(func=truststore_show_settings)


def truststore_show_settings(args):
    obj = TrustStore(args.ip, args.port)
    try:
        res = obj.truststore_get_settings(args.xml)
        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show settings", "truststore",
                                        e.err_text, e.err_code)


def update_parser(subcommand_parsers, common_parser):
    # allow command parser
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR truststore update CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update the truststore')

    #arggroup = update_parser.add_mutually_exclusive_group(required=True)
    update_parser.add_argument(
        '-addcertificatevaluefile', '-acvf',
        help='path of file with certificate to be added',
        metavar='<addcertificatevaluefile>',
        dest='addcertificatevaluefile')

    update_parser.add_argument(
        '-removecertificatevaluefile', '-rcvf',
        help='path of file with certificate to be removed',
        metavar='<removecertificatevaluefile>',
        dest='removecertificatevaluefile')

    update_parser.set_defaults(func=truststore_update)


def truststore_update(args):
    obj = TrustStore(args.ip, args.port)
    try:
        if((args.addcertificatevaluefile is None) and
           (args.removecertificatevaluefile is None)):
            raise SOSError(
                SOSError.CMD_LINE_ERR,
                "Atleast one of the two parameters" +
                " -addcertificatevaluefile -removecertificatevaluefile " +
                "is mandatory")

        obj.truststore_update_certificates(args.addcertificatevaluefile,
                                           args.removecertificatevaluefile)
    except SOSError as e:
        common.format_err_msg_and_raise("update", "truststore",
                                        e.err_text, e.err_code)


# VIRTUALARRAY List routines


def update_settings_parser(subcommand_parsers, common_parser):
    # update command parser
    update_settings_parser = subcommand_parsers.add_parser(
        'update-settings',
        description='ViPR truststore Update Settings CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update the settings of a truststore')

    mandatory_args = update_settings_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-acceptallcertificates',
                                help='Boolean to accept all certificates',
                                dest='acceptallcertificates',
                                metavar='<acceptallcertificates>',
                                choices=['true', 'false'],
                                required=True)

    update_settings_parser.set_defaults(func=truststore_update_settings)


def truststore_update_settings(args):
    obj = TrustStore(args.ip, args.port)
    try:
        obj.truststore_update_settings(args.acceptallcertificates)
    except SOSError as e:
        common.format_err_msg_and_raise("update settings", "truststore",
                                        e.err_text, e.err_code)


#
# truststore Main parser routine
#
def truststore_parser(parent_subparser, common_parser):
    # main truststore parser
    parser = parent_subparser.add_parser(
        'truststore',
        description='ViPR truststore CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on truststore')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # update command parser
    update_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # show settings command parser
    show_settings_parser(subcommand_parsers, common_parser)

    # update settings command parser
    update_settings_parser(subcommand_parsers, common_parser)
