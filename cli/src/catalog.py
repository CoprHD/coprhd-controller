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
The class definition for the mount operation in the service catalog
'''


class Catalog(object):

    COMPONENT_TYPE = "catalog"

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    '''
    Get the root of the catalog
    '''
    def get_catalog(self, uriPath, xml=False):

        if (uriPath is None):
            uriPath = ""
        elif (uriPath.startswith("/") is False):
            uriPath = "/" + uriPath

        command = singletonURIHelperInstance.getUri(
            self.COMPONENT_TYPE, "catalog")
        port = self.__port
        if(len(uriPath) == 0):
            port = common.getenv('VIPR_UI_PORT')
        (s, h) = common.service_json_request(self.__ipAddr, port, "GET",
                                             command.format(uriPath),
                                             None)
        o = common.json_decode(s)
        inactive = common.get_node_value(o, 'inactive')

        if (inactive):
            return None
        if (xml):
            (s, h) = common.service_json_request(self.__ipAddr, port, "GET",
                                                 command.format(uriPath),
                                                 None, None, xml)
            return s
        else:
            return o

    def get_category(self, uri, xml=False):
        command = singletonURIHelperInstance.getUri(
            self.COMPONENT_TYPE, "get-category")
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             command.format(uri),
                                             None, None)
        o = common.json_decode(s)
        inactive = common.get_node_value(o, 'inactive')

        if (inactive):
            return None
        if (xml):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "GET",
                command.format(uri), None, None, xml)
            return s
        else:
            return o

    def get_service(self, uri, xml=False):
        command = singletonURIHelperInstance.getUri(
            self.COMPONENT_TYPE, "get-service")
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             command.format(uri),
                                             None, None)
        o = common.json_decode(s)
        inactive = common.get_node_value(o, 'inactive')

        if (inactive):
            return None
        if (xml):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "GET", command.format(uri),
                None, None, xml)
            return s
        else:
            return o

    def get_descriptor(self, uri):
        command = singletonURIHelperInstance.getUri(
            self.COMPONENT_TYPE, "get-descriptor")
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             command.format(uri),
                                             None, None)
        o = common.json_decode(s)

        return o

    def execute(self, path, uri, params):

        if (path is not None):
            command = singletonURIHelperInstance.getUri(
                self.COMPONENT_TYPE, "catalog")
            requestUrl = command.format(path)
        else:
            command = singletonURIHelperInstance.getUri(
                self.COMPONENT_TYPE, "get-service")
            requestUrl = command.format(uri)

        paramsDict = common.toDict(params)
        #If the label object is null, then it shows an error
        if(not(paramsDict.has_key("label"))):
            paramsDict["label"]=""        

        query = None
        if (params is not None):
            for key, value in paramsDict.iteritems():
                if (query is None):
                    query = "?" + key + "=" + value
                else:
                    query = query + "&" + key + "=" + value
            if (query is not None):
                requestUrl = requestUrl + query
        # The requestUrl has '' as a  string instead of an empty string.
        requestUrl=requestUrl.replace("''","")  

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST", requestUrl, None, None)
        o = common.json_decode(s)

        return o


'''
Preprocessor for the catalog get-category operation
'''


def catalog_get_category(args):
    catalogObj = Catalog(args.ip, args.uiPort)
    try:
        if (args.path):
            categoryDetails = catalogObj.get_catalog(args.path, args.xml)
        elif (args.urn):
            categoryDetails = catalogObj.get_category(args.urn, args.xml)
        else:
            categoryDetails = catalogObj.get_catalog(None, args.xml)

        if(categoryDetails is None):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Could not find the matching catalog category")

        if(args.xml):
            return common.format_xml(categoryDetails)

        return common.format_json_object(categoryDetails)
    except SOSError as e:
        common.format_err_msg_and_raise("get-category", "catalog",
                                        e.err_text, e.err_code)

    return

'''
Preprocessor for the catalog get-service operation
'''


def catalog_get_service(args):
    catalogObj = Catalog(args.ip, args.uiPort)
    try:
        if (args.path is not None):
            serviceDetails = catalogObj.get_catalog(args.path, args.xml)
        else:
            serviceDetails = catalogObj.get_service(args.urn, args.xml)

        if(serviceDetails is None):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Could not find the matching catalog service")

        if(args.xml):
            return common.format_xml(serviceDetails)

        return common.format_json_object(serviceDetails)
    except SOSError as e:
        common.format_err_msg_and_raise("get-service", "catalog",
                                        e.err_text, e.err_code)

    return

'''
Preprocessor for the catalog get-descriptor operation
'''


def catalog_get_descriptor(args):
    catalogObj = Catalog(args.ip, args.uiPort)
    try:
        descriptorDetails = catalogObj.get_descriptor(args.urn)

        if(descriptorDetails is None):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Could not find the matching catalog service descriptor")

        return common.format_json_object(descriptorDetails)

    except SOSError as e:
        common.format_err_msg_and_raise("get-descriptor", "catalog",
                                        e.err_text, e.err_code)

    return

'''
Preprocessor for the catalog execute operation
'''


def catalog_execute(args):
    catalogObj = Catalog(args.ip, args.uiPort)
    try:

        if (args.params is not None and len(args.params) % 2 != 0):
            raise SOSError(
                SOSError.CMD_LINE_ERR,
                "List of parameter name/value pairs is not even")

        orderDetails = catalogObj.execute(args.path, args.urn, args.params)

        if(args.xml):
            return common.format_xml(orderDetails)
        return common.format_json_object(orderDetails)
    except SOSError as e:
        common.format_err_msg_and_raise("execute", "catalog",
                                        e.err_text, e.err_code)
    return

# get-category command parser


def get_category_parser(subcommand_parsers, common_parser):
    get_category_parser = subcommand_parsers.add_parser(
        'get-category',
        description='ViPR Catalog get-category CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show Catalog Category details')
    get_category_parser.add_argument('-xml',
                                     dest='xml',
                                     action='store_true',
                                     help='XML response')
    mutex_group = get_category_parser.add_mutually_exclusive_group(
        required=False)

    mutex_group.add_argument('-id', '-urn',
                             metavar='<urn>',
                             dest='urn',
                             help='URN of Catalog Category')
    mutex_group.add_argument('-path',
                             metavar='<path>',
                             dest='path',
                             help='Path to Catalog Category')

    get_category_parser.set_defaults(func=catalog_get_category)

# get-service command parser


def get_service_parser(subcommand_parsers, common_parser):
    get_service_parser = subcommand_parsers.add_parser(
        'get-service',
        description='ViPR Catalog get-service CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show Catalog Service details')

    get_service_parser.add_argument('-xml',
                                    dest='xml',
                                    action='store_true',
                                    help='XML response')
    mutex_group = get_service_parser.add_mutually_exclusive_group(
        required=True)

    mutex_group.add_argument('-id', '-urn',
                             metavar='<urn>',
                             dest='urn',
                             help='URN of Catalog Service')
    mutex_group.add_argument('-path',
                             metavar='<path>',
                             dest='path',
                             help='Path to Catalog Service')
    get_service_parser.set_defaults(func=catalog_get_service)

# get-descriptor command parser


def get_descriptor_parser(subcommand_parsers, common_parser):
    get_descriptor_parser = subcommand_parsers.add_parser(
        'get-descriptor',
        description='ViPR Catalog get-descriptor CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show Catalog Service Descriptor details')

    mutex_group = get_descriptor_parser.add_mutually_exclusive_group(
        required=True)

    mutex_group.add_argument('-id', '-urn',
                             metavar='<urn>',
                             dest='urn',
                             help='URN of Catalog Service')
    get_descriptor_parser.set_defaults(func=catalog_get_descriptor)

# execute command parser


def execute_parser(subcommand_parsers, common_parser):
    execute_parser = subcommand_parsers.add_parser(
        'execute',
        description='ViPR Catalog execute CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Execute Catalog Service')
    execute_parser.add_argument('-xml',
                                dest='xml',
                                action='store_true',
                                help='XML response')
    mutex_group = execute_parser.add_mutually_exclusive_group(required=True)

    mutex_group.add_argument('-id', '-urn',
                             metavar='<urn>',
                             dest='urn',
                             help='URN of Catalog Service')
    mutex_group.add_argument('-path',
                             metavar='<path>',
                             dest='path',
                             help='Path to Catalog Service')
    execute_parser.add_argument(
        '-params',
        nargs="+",
        metavar="<name value>",
        dest="params",
        help="List of name value pairs to execute service: " +
        "Separated by spaces.")
    execute_parser.set_defaults(func=catalog_execute)

# Catalog Main parser routine


def catalog_parser(parent_subparser, common_parser):

    parser = parent_subparser.add_parser(
        'catalog',
        description='ViPR catalog CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Catalog')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # get-category command parser
    get_category_parser(subcommand_parsers, common_parser)

    # get-service command parser
    get_service_parser(subcommand_parsers, common_parser)

    # get-descriptor parser
    get_descriptor_parser(subcommand_parsers, common_parser)

    # execute parser
    execute_parser(subcommand_parsers, common_parser)
