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


class KeyStore(object):

    '''
    The class definition for operations on 'KeyStore'.
    '''

    # Commonly used URIs for the 'keystores' module
    URI_KEYSTORE = '/vdc/keystore'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def keystore_show(self, xml=False):
        '''
        Makes a REST API call to retrieve details of a keystore
        '''

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            KeyStore.URI_KEYSTORE,
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


    def keystore_update(self, certificatefile, privatekeyfile, selfsign):
        '''
        creates a keystore
        parameters:
            label:  label of the keystore
        Returns:
            JSON payload response
        '''
        requestParams = dict()

        certificate_chain = None
        privatekey = None

        if(certificatefile):
            try:
                f1 = open(certificatefile, 'r')
                certificate_chain = f1.read()
            except IOError as e:
                raise SOSError(e.errno, e.strerror)

        if(privatekeyfile):
            try:
                f2 = open(privatekeyfile, 'r')
                privatekey = f2.read()
            except IOError as e:
                raise SOSError(e.errno, e.strerror)

        requestParams['system_selfsigned'] = selfsign

        requestParams['key_and_certificate'] = {
            'certificate_chain': certificate_chain,
            'private_key': privatekey
        }

        body = json.dumps(requestParams)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "PUT", KeyStore.URI_KEYSTORE, body)
        o = common.json_decode(s)
        return o


# KEYSTORE Show routines


def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR keystore Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show a keystore')

    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=keystore_show)


def keystore_show(args):
    obj = KeyStore(args.ip, args.port)
    try:
        res = obj.keystore_show(args.xml)
        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "keystore",
                                        e.err_text, e.err_code)


def update_parser(subcommand_parsers, common_parser):
    # update command parser
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR keystore Update CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update a keystore')

    update_parser.add_argument(
        '-certificatevaluefile', '-cvf',
        help='path of file with certificate to be added',
        metavar='<certificatevaluefile>',
        dest='certificatevaluefile')

    update_parser.add_argument(
        '-privatekeyvaluefile', '-pkvf',
        help='path of file with private key to be added',
        metavar='<privatekeyvaluefile>',
        dest='privatekeyvaluefile')

    update_parser.add_argument('-selfsign', '-ss',
        help='Use self sign or not',
        dest='selfsign',
        action='store_true')

    update_parser.set_defaults(func=keystore_update)


def keystore_update(args):
    obj = KeyStore(args.ip, args.port)
    try:
        if(not args.selfsign):
            if((args.certificatevaluefile is None) or 
               (args.privatekeyvaluefile is None)):
                raise SOSError(SOSError.CMD_LINE_ERR,
                    "-privatekeyvaluefile and -certificatevaluefile "+
                    "should both be specified when selfsign is false.")
                
        obj.keystore_update(args.certificatevaluefile,
                            args.privatekeyvaluefile,
                            args.selfsign)
    except SOSError as e:
        common.format_err_msg_and_raise("update", "keystore",
                                        e.err_text, e.err_code)


#
# keystore Main parser routine
#
def keystore_parser(parent_subparser, common_parser):
    # main keystore parser
    parser = parent_subparser.add_parser('keystore',
                                         description='ViPR keystore CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on keystore')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # update command parser
    update_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)
