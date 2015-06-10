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
The class definition for the Approval operations in the UI Service
'''


class Approval(object):

    COMPONENT_TYPE = "approval"

    def __init__(self, ipAddr, port):
        '''
            Constructor: takes IP address and port of the ViPR instance. These
            are needed to make http requests for REST API
            '''
        self.__ipAddr = ipAddr
        self.__port = port

    def list(self):
        command = singletonURIHelperInstance.getUri(
            self.COMPONENT_TYPE, "approvals")
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            command, None, None)
        o = common.json_decode(s)

        return o

    def list_pending(self):
        command = singletonURIHelperInstance.getUri(
            self.COMPONENT_TYPE, "pending-approvals")
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            command, None, None)
        o = common.json_decode(s)

        return o

    def show_by_uri(self, uri, xml=False):
        return common.show_resource(
            self.__ipAddr, self.__port, self.COMPONENT_TYPE, uri, True, xml)

    def approve(self, uri):
        command = singletonURIHelperInstance.getUri(
            self.COMPONENT_TYPE, "approve")
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            command.format(uri), None, None)
        return s

    def reject(self, uri):
        command = singletonURIHelperInstance.getUri(
            self.COMPONENT_TYPE, "reject")
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            command.format(uri), None, None)
        return s


'''
Preprocessor for the approval list operation
'''


def approval_list(args):
    approvalObj = Approval(args.ip, args.uiPort)
    from common import TableGenerator
    try:
        if (args.pending):
            approvalList = approvalObj.list_pending()
        else:
            approvalList = approvalObj.list()

        if (len(approvalList) > 0):
            TableGenerator(approvalList, ['href', 'id']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("list", "approval",
                                        e.err_text, e.err_code)

'''
Preprocessor for the show list operation
'''


def approval_show(args):
    approvalObj = Approval(args.ip, args.uiPort)
    try:

        approvalDetails = approvalObj.show_by_uri(args.uri, args.xml)

        if(approvalDetails is None):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Could not find the matching approval")

        if(args.xml):
            return common.format_xml(approvalDetails)

        return common.format_json_object(approvalDetails)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "approval",
                                        e.err_text, e.err_code)
    return

'''
Preprocessor for the approval approve operation
'''


def approval_approve(args):
    approvalObj = Approval(args.ip, args.uiPort)
    try:

        approvalObj.approve(args.uri)
    except SOSError as e:
        common.format_err_msg_and_raise("approve", "approval",
                                        e.err_text, e.err_code)
    return

'''
Preprocessor for the approval reject operation
'''


def approval_reject(args):
    approvalObj = Approval(args.ip, args.uiPort)
    try:

        approvalObj.reject(args.uri)
    except SOSError as e:
        common.format_err_msg_and_raise("reject", "approval",
                                        e.err_text, e.err_code)

    return

# list command parser


def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Approval List CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists approval requests')

    list_parser.add_argument('-pending',
                             dest='pending',
                             action='store_true',
                             help='List pending approval requests')

    list_parser.set_defaults(func=approval_list)

# show command parser


def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Approval Request show CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show approval request')

    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-id', '-uri',
                                metavar='<uri>',
                                dest='uri',
                                help='URI of the Approval Request',
                                required=True)

    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=approval_show)

# approve command parser


def approve_parser(subcommand_parsers, common_parser):
    approve_parser = subcommand_parsers.add_parser(
        'approve',
        description='ViPR approve approval request CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Approve approval request')

    mandatory_args = approve_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-id', '-uri',
                                metavar='<uri>',
                                dest='uri',
                                help='URI of the Approval Request',
                                required=True)

    approve_parser.set_defaults(func=approval_approve)

# reject command parser


def reject_parser(subcommand_parsers, common_parser):
    reject_parser = subcommand_parsers.add_parser(
        'reject',
        description='ViPR reject approval request CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Reject approval')

    mandatory_args = reject_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-id', '-uri',
                                metavar='<uri>',
                                dest='uri',
                                help='URI of the Approval Request',
                                required=True)
    reject_parser.set_defaults(func=approval_reject)

# Approval Main parser routine


def approval_parser(parent_subparser, common_parser):

    parser = parent_subparser.add_parser(
        'approval',
        description='ViPR approval CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Approval Requests')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # approve parser
    approve_parser(subcommand_parsers, common_parser)

    # reject parser
    reject_parser(subcommand_parsers, common_parser)
