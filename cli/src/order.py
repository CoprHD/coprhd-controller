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
from threading import Timer
from common import SOSError
import time
from urihelper import singletonURIHelperInstance


'''
The class definition for the Order operations in the UI Service
'''


class Order(object):

    COMPONENT_TYPE = "order"

    isTimeout = False
    timeout = 300    # seconds

    def __init__(self, ipAddr, port):
        '''
            Constructor: takes IP address and port of the ViPR instance.
            These are needed to make http requests for REST API
            '''
        self.__ipAddr = ipAddr
        self.__port = port

    '''
        Get the root of the catalog
        '''

    def get_orders(self):
        command = singletonURIHelperInstance.getUri(
            self.COMPONENT_TYPE,
            "list")
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             command,
                                             None)
        o = common.json_decode(s)

        return o

    def show_by_uri(self, uri, xml=False):
        return (
            common.show_resource(
                self.__ipAddr,
                self.__port,
                self.COMPONENT_TYPE,
                uri,
                True,
                xml)
        )

    def show_execution_by_uri(self, uri, xml):
        command = singletonURIHelperInstance.getUri(
            self.COMPONENT_TYPE,
            "show-execution")
        if (xml is True):
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                                 "GET",
                                                 command.format(uri),
                                                 None, None, xml)
            return s
        else:
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                                 "GET",
                                                 command.format(uri),
                                                 None, None)
            o = common.json_decode(s)

            return o

    # Timeout handler for synchronous operations
    def timeout_handler(self):
        self.isTimeout = True

    # Blocks the opertaion until the order is complete/error out/timeout
    def block_until_complete(self, uri):
        t = Timer(self.timeout, self.timeout_handler)
        t.start()
        while (True):
            orderDetails = self.show_by_uri(uri)

            if(orderDetails):
                if(orderDetails["status"] == "SUCCESS"):
                    # cancel the timer and return
                    t.cancel()
                    break

                if (orderDetails["status"] == "SCHEDULED"):
                    t.cancel()
                    print "Order is Scheduled"
                    break

                if (orderDetails["status"] == "APPROVAL"):
                    t.cancel()
                    print "Order requires approval to continue"
                    break

                if(orderDetails["status"] == "ERROR"):
                    print "Order produced an error"
                    t.cancel()
                    break

            if(self.isTimeout):
                print "Operation timed out"
                self.isTimeout = False
                break

            time.sleep(10)

        return

'''
Preprocessor for the order list operation
'''


def order_list(args):
    orderObj = Order(args.ip, args.uiPort)
    from common import TableGenerator
    try:
        orderList = orderObj.get_orders()
        orderDetailList = []

        if (len(orderList) > 0):
            for item in orderList:
                orderDetail = orderObj.show_by_uri(item['id'])
                if(orderDetail['inactive'] is False):
                    orderDetail['service'] = None
                    orderDetail['execution'] = None
                    orderDetailList.append(orderDetail)
            TableGenerator(
                orderDetailList,
                ['id',
                 'orderNumber',
                 'status']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise(
            "list",
            "order",
            e.err_text,
            e.err_code)

'''
Preprocessor for the order show operation
'''


def order_show(args):
    orderObj = Order(args.ip, args.uiPort)
    try:

        orderDetails = orderObj.show_by_uri(args.uri, args.xml)

        if(orderDetails is None):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Could not find the matching order")

        if (args.waitForExecution is True):
            orderObj.block_until_complete(args.uri)
            # refresh order details
            orderDetails = orderObj.show_by_uri(args.uri, args.xml)

        if(args.xml):
            return common.format_xml(orderDetails)

        return common.format_json_object(orderDetails)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "show",
            "order",
            e.err_text,
            e.err_code)

    return

'''
Preprocessor for the order execution operation
'''


def order_execution(args):
    orderObj = Order(args.ip, args.uiPort)
    try:
        orderExecutionList = orderObj.show_execution_by_uri(args.uri, args.xml)

        if(args.xml):
            return common.format_xml(orderExecutionList)

        return common.format_json_object(orderExecutionList)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "execution",
            "order",
            e.err_text,
            e.err_code)

    return


# list command parser
def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Order list CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List orders')

    list_parser.set_defaults(func=order_list)

# show command parser


def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Order show CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show order details')

    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-id', '-uri',
                                metavar='<uri>',
                                dest='uri',
                                help='URI of the Order',
                                required=True)

    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.add_argument(
        '-waitForExecution',
        dest='waitForExecution',
        action='store_true',
        help='Wait until order is no longer executing to return response')

    show_parser.set_defaults(func=order_show)

# execution command parser


def execution_parser(subcommand_parsers, common_parser):
    execution_parser = subcommand_parsers.add_parser(
        'show-execution',
        description='ViPR Order execution CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show order execution details')

    mandatory_args = execution_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-id', '-uri',
                                metavar='<uri>',
                                dest='uri',
                                help='URI of the Order',
                                required=True)

    execution_parser.add_argument('-xml',
                                  dest='xml',
                                  action='store_true',
                                  help='XML response')

    execution_parser.set_defaults(func=order_execution)

#
# Orders Main parser routine
#


def order_parser(parent_subparser, common_parser):

    parser = parent_subparser.add_parser('order',
                                         description='ViPR order CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on Order')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # execution parser
    execution_parser(subcommand_parsers, common_parser)

