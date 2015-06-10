#!/usr/bin/python

#
# Copyright (c) 2013 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#

import common
from common import SOSError
from urihelper import singletonURIHelperInstance

'''
    The class definition for the asset options in the service catalog
'''


class AssetOptions(object):

    COMPONENT_TYPE = "assetoptions"

    def __init__(self, ipAddr, port):
        '''
            Constructor: takes IP address and port of the ViPR instance. These
            are needed to make http requests for REST API
            '''
        self.__ipAddr = ipAddr
        self.__port = port

    def list(self, type, params):
        command = singletonURIHelperInstance.getUri(
            self.COMPONENT_TYPE, "list")
        requestUrl = command.format(type)

        paramsDict = common.toDict(params)

        query = None
        if (params is not None):
            for key, value in paramsDict.iteritems():
                if (query is None):
                    query = "?" + key + "=" + value
                else:
                    query = query + "&" + key + "=" + value
            if (query is not None):
                requestUrl = requestUrl + query

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             requestUrl,
                                             None, None)
        o = common.json_decode(s)
        return o

# list command parser


def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Asset Options List CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists asset options')

    mandatory_args = list_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-t', '-type',
                             dest='type',
                             metavar='<type>',
                             help='Type of Asset Options to list',
                             required=True)
    list_parser.add_argument(
        '-params', nargs="+", metavar="<name value>",
        dest="params",
        help="List of name value pairs to retrieve the options." +
        "Separated by spaces.")
    list_parser.set_defaults(func=assetoptions_list)

'''
Preprocessor for hosts list operation
'''


def assetoptions_list(args):

    assetOptionsList = None
    assetOptionsObj = AssetOptions(args.ip, args.uiPort)
    from common import TableGenerator

    try:

        if (args.params is not None and len(args.params) % 2 != 0):
            raise SOSError(
                SOSError.CMD_LINE_ERR,
                "List of parameter name/value pairs is not even")

        assetOptionsList = assetOptionsObj.list(args.type, args.params)

        if(len(assetOptionsList) > 0):
            TableGenerator(assetOptionsList, ['key', 'value']).printTable()
    except SOSError as e:
        common.format_err_msg_and_raise("list", "assetoptions",
                                        e.err_text, e.err_code)

# Asset Options Main parser routine


def assetoptions_parser(parent_subparser, common_parser):

    parser = parent_subparser.add_parser(
        'assetoptions',
        description='ViPR Asset Options CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations for assetoptions')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # list command parser
    list_parser(subcommand_parsers, common_parser)
