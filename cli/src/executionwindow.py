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
import json
from common import SOSError
from urihelper import singletonURIHelperInstance

'''
The class definition for the Execution Window operations in the UI Service
'''


class ExecutionWindow(object):

    COMPONENT_TYPE = "executionwindow"

    TYPE_LIST = ['DAILY', 'WEEKLY', 'MONTHLY']
    LENGTH_TYPE_LIST = ['MINUTES', 'HOURS', 'DAYS']

    def __init__(self, ipAddr, port):
        '''
            Constructor: takes IP address and port of the ViPR instance.
            These are needed to make http requests for REST API
            '''
        self.__ipAddr = ipAddr
        self.__port = port

    def list(self):
        command = singletonURIHelperInstance.getUri(self.COMPONENT_TYPE,
                                                    "list")
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             command,
                                             None)
        o = common.json_decode(s)

        return o

    def show_by_uri(self, uri, xml=False):
        return common.show_resource(self.__ipAddr, self.__port,
                                    self.COMPONENT_TYPE, uri, True, xml)

    def create(self, name, tenantname, type, hourOfDay, length, lengthType, dayOfWeek,
               dayOfMonth,minuteOfHour, lastDayOfMonth=False):

        new_request={
            "tenant": tenantname,
            "hour_of_day_in_utc": hourOfDay,
            "minute_of_hour_in_utc": minuteOfHour,
            "execution_window_length": length,
            "execution_window_length_type": lengthType,
            "execution_window_type": type,
            "day_of_week": dayOfWeek,
            "day_of_month": dayOfMonth,
            "last_day_of_month": lastDayOfMonth,
            "name": name
        }


        if(minuteOfHour is not None):
           new_request["minute_of_hour_in_utc"] = minuteOfHour

        if (tenantname is not None):
            from tenant import Tenant
            tenant_obj = Tenant(self.__ipAddr, self.__port)
            try:
                tenant_uri = tenant_obj.tenant_query(None)
            except SOSError as e:
                raise e

            new_request["tenant"] = tenant_uri

        body = json.dumps(new_request)
        
        command = singletonURIHelperInstance.getUri(self.COMPONENT_TYPE,
                                                    "create")

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                             command,
                                             body)
        o = common.json_decode(s)
        return o

    def update(self, uri, name, type, hourOfDay, length,
               lengthType, dayOfWeek, dayOfMonth, lastDayOfMonth=False):

        request = dict()

        if (name is not None):
            request["label"] = name

        if (type is not None):
            request["executionWindowType"] = type

        if (hourOfDay is not None):
            request["hourOfDayInUTC"] = hourOfDay

        if (length is not None):
            request["executionWindowLength"] = length

        if (lengthType is not None):
            request["executionWindowLengthType"] = lengthType

        if (dayOfWeek is not None):
            request["dayOfWeek"] = dayOfWeek

        if (dayOfMonth is not None):
            request["dayOfMonth"] = dayOfMonth

        if (lastDayOfMonth is not None):
            request["lastDayOfMonth"] = lastDayOfMonth

        body = json.dumps(request)

        command = singletonURIHelperInstance.getUri(self.COMPONENT_TYPE,
                                                    "show")
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "PUT",
                                             command.format(uri),
                                             body)
        o = common.json_decode(s)
        return o

    def delete(self, uri):
        command = singletonURIHelperInstance.getUri(self.COMPONENT_TYPE,
                                                    "show")
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "DELETE",
                                             command.format(uri),
                                             None)
        return s


'''
Preprocessor for the executionwindow list operation
'''


def executionwindow_list(args):
    executionWindowObj = ExecutionWindow(args.ip, args.uiPort)
    from common import TableGenerator
    try:
        executionWindowList = executionWindowObj.list()
        detailList = []
        if (len(executionWindowList) > 0):
            for item in executionWindowList:
                detail = executionWindowObj.show_by_uri(item['id'])
                detailList.append(detail)
            TableGenerator(detailList, [
                'id', 'label', 'executionWindowType',
                'executionWindowLengthType', 'dayOfMonth', 'dayOfWeek'
                 ]).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("list", "executionwindow",
                                        e.err_text, e.err_code)

'''
Preprocessor for the executionwindow show operation
'''


def executionwindow_show(args):
    executionWindowObj = ExecutionWindow(args.ip, args.uiPort)
    try:

        executionWindowDetails = executionWindowObj.show_by_uri(args.uri,
                                                                args.xml)

        if(executionWindowDetails is None):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Could not find the matching execution window")

        if(args.xml):
            return common.format_xml(executionWindowDetails)

        return common.format_json_object(executionWindowDetails)

    except SOSError as e:
        common.format_err_msg_and_raise("show", "executionwindow",
                                        e.err_text, e.err_code)
    return

'''
Preprocessor for the executionwindow create operation
'''


def executionwindow_create(args):
    executionWindowObj = ExecutionWindow(args.ip, args.port)
    try:

        if (args.type == "WEEKLY" and args.dayOfWeek is None):
            raise SOSError(SOSError.CMD_LINE_ERR,
                           "-dayOfWeek is required when -type is WEEKLY")
        if (args.type == "DAILY" and args.hourOfDay is None):
            raise SOSError(SOSError.CMD_LINE_ERR,
                           "-hourOfDay is required when -type is DAILY")

            

        if (args.type == "MONTHLY" and (args.dayOfMonth is None and
                                        args.lastDayOfMonth is None)):
            raise SOSError(SOSError.CMD_LINE_ERR,
                           "-dayOfMonth or -lastDayOfMonth is required " +
                           "when -type is MONTHLY")

        if(args.dayOfWeek and args.type != "WEEKLY"):
            raise SOSError(SOSError.CMD_LINE_ERR,
                           "-dayOfWeek is valid only when -type is WEEKLY")

        if(args.dayOfMonth and args.type != "MONTHLY"):
            raise SOSError(SOSError.CMD_LINE_ERR,
                           "-dayOfMonth is valid only when -type is MONTHLY")

        if(args.lastDayOfMonth and args.type != "MONTHLY"):
            raise SOSError(SOSError.CMD_LINE_ERR,
                "-lastDayOfMonth is valid only when -type is MONTHLY")

        executionWindowObj.create(args.name, args.tenantname, args.type, args.hourOfDay,
                                  args.length, args.lengthType,
                                  args.dayOfWeek, args.dayOfMonth, args.minuteOfHour,
                                  args.lastDayOfMonth)

    except SOSError as e:
        common.format_err_msg_and_raise("create", "executionwindow",
                                        e.err_text, e.err_code)

    return

'''
Preprocessor for the executionwindow update operation
'''


def executionwindow_update(args):
    executionWindowObj = ExecutionWindow(args.ip, args.uiPort)
    try:

        if (args.type == "WEEKLY" and args.dayOfWeek is None):
            raise SOSError(SOSError.CMD_LINE_ERR,
                           "-dayOfWeek is required when -type is WEEKLY")

        if (args.type == "MONTHLY" and (args.dayOfMonth is None and
                                        args.lastDayOfMonth is None)):
            raise SOSError(SOSError.CMD_LINE_ERR,
                           "-dayOfMonth or -lastDayOfMonth is required" +
                           " when -type is MONTHLY")

        if(args.dayOfWeek and args.type != "WEEKLY"):
            raise SOSError(SOSError.CMD_LINE_ERR,
                           "-dayOfWeek is valid only when -type is WEEKLY")

        if(args.dayOfMonth and args.type != "MONTHLY"):
            raise SOSError(SOSError.CMD_LINE_ERR,
                           "-dayOfMonth is valid only when -type is MONTHLY")

        if(args.lastDayOfMonth and args.type != "MONTHLY"):
            raise SOSError(SOSError.CMD_LINE_ERR,
                "-lastDayOfMonth is valid only when -type is MONTHLY")

        executionWindowObj.update(args.uri, args.name, args.type,
                                  args.hourOfDay, args.length, args.lengthType,
                                  args.dayOfWeek, args.dayOfMonth,
                                  args.lastDayOfMonth)

    except SOSError as e:
        common.format_err_msg_and_raise("update", "executionwindow",
                                        e.err_text, e.err_code)

    return

'''
Preprocessor for the executionwindow delete operation
'''


def executionwindow_delete(args):
    executionWindowObj = ExecutionWindow(args.ip, args.uiPort)
    try:
        executionWindowObj.delete(args.uri)
    except SOSError as e:
        common.format_err_msg_and_raise("delete", "executionwindow",
                                        e.err_text, e.err_code)

    return

# list command parser


def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser('list',
        description='ViPR Execution Window list CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Execution Window list')

    list_parser.set_defaults(func=executionwindow_list)

# show command parser


def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser('show',
        description='ViPR Execution Window show CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Execution Window show')

    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-id', '-uri',
                             metavar='<uri>',
                             dest='uri',
                             help='URI of the Execution Window',
                             required=True)

    show_parser.add_argument('-xml',
                               dest='xml',
                               action='store_true',
                               help='XML response')

    show_parser.set_defaults(func=executionwindow_show)

# create command parser


def create_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser('create',
        description='ViPR Execution Window create CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create Execution Window')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name',
                             metavar='<name>',
                             dest='name',
                             help='Name of the Execution Window',
                             required=True)
    mandatory_args.add_argument('-tenantname','-tn',
                             metavar='<tenantname>',
                             dest='tenantname',
                             help='Name of the Tenant',
                             required=True)
    mandatory_args.add_argument('-type',
                             choices=ExecutionWindow.TYPE_LIST,
                             metavar='<type>',
                             dest='type',
                             help='Type of the Execution Window',
                             required=True)
    mandatory_args.add_argument('-hourOfDay',
        type=int,
        choices=range(0, 24),
        metavar='<hourOfDay>',
        dest='hourOfDay',
        help='Hour of day when the execution window should run in UTC (0-23)',
        required=True)
    mandatory_args.add_argument('-length',
                             type=int,
                             metavar='<length>',
                             dest='length',
                             help='Length of time of the execution window',
                             required=True)
    mandatory_args.add_argument('-lengthType',
        choices=ExecutionWindow.LENGTH_TYPE_LIST,
        metavar='<lengthType>',
        dest='lengthType',
        help='Units for the length of the execution window',
        required=True)

    create_parser.add_argument('-dayOfWeek',
        choices=range(1, 8),
        type=int,
        metavar='<dayOfWeek>',
        dest='dayOfWeek',
        help='Weekday for the execution window (only used if type is WEEKLY)')
    create_parser.add_argument('-dayOfMonth',
        choices=range(1, 32),
        type=int,
        metavar='<dayOfMonth>',
        dest='dayOfMonth',
        help="Day of month for the execution window" +
        "(only used if type is MONTHLY)")
    create_parser.add_argument('-lastDayOfMonth',
        choices=range(1, 32),
        type=int,
        metavar='<lastDayOfMonth>',
        dest='lastDayOfMonth',
        help="Use last day of the month for the execution window " +
        "(only used if type is MONTHLY)")
    
    create_parser.add_argument('-minuteOfHour',
        choices=range(1, 60),
        type=int,
        metavar='<minuteOfHour>',
        dest='minuteOfHour',
        help="minute  of Hour for the execution window" +
        "(only used if type is HOURLY)")

    create_parser.set_defaults(func=executionwindow_create)

# update command parser


def update_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser('update',
        description='ViPR Execution Window update CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update Execution Window')

    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-id', '-uri',
                             metavar='<uri>',
                             dest='uri',
                             help='URI of the Execution Window',
                             required=True)
    mandatory_args.add_argument('-newname',
                             metavar='<newname>',
                             dest='name',
                             help='New name of the Execution Window',
                             required=True)
    mandatory_args.add_argument('-type',
                             choices=ExecutionWindow.TYPE_LIST,
                             metavar='<type>',
                             dest='type',
                             help='Type of the Execution Window',
                             required=True)
    mandatory_args.add_argument('-hourOfDay',
        type=int,
        choices=range(0, 24),
        metavar='<hourOfDay>',
        dest='hourOfDay',
        help='Hour of day when the execution window should run in UTC (0-23)',
        required=True)
    mandatory_args.add_argument('-length',
                             type=int,
                             metavar='<length>',
                             dest='length',
                             help='Length of time of the execution window',
                             required=True)
    mandatory_args.add_argument('-lengthType',
        choices=ExecutionWindow.LENGTH_TYPE_LIST,
        metavar='<lengthType>',
        dest='lengthType',
        help='Units for the length of the execution window',
        required=True)
    update_parser.add_argument('-minuteOfHour',
        choices=range(1, 60),
        type=int,
        metavar='<minuteOfHour>',
        dest='minuteOfHour',
        help="minute  of Hour for the execution window" +
        "(only used if type is HOURLY)")
    update_parser.add_argument('-dayOfWeek',
        choices=range(1, 8),
        type=int,
        metavar='<dayOfWeek>',
        dest='dayOfWeek',
        help='Weekday for the execution window (only used if type is WEEKLY)')
    update_parser.add_argument('-dayOfMonth',
        choices=range(1, 32),
        type=int,
        metavar='<dayOfMonth>',
        dest='dayOfMonth',
        help="Day of month for the execution window" +
        " (only used if type is MONTHLY)")
    update_parser.add_argument('-lastDayOfMonth',
        choices=range(1, 32),
        type=int,
        metavar='<lastDayOfMonth>',
        dest='lastDayOfMonth',
        help="Use last day of the month for the execution window" +
        "(only used if type is MONTHLY)")
    update_parser.set_defaults(func=executionwindow_update)

# delete command parser


def delete_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser('delete',
        description='ViPR Execution Window delete CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete Execution Window')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-id', '-uri',
                             metavar='<uri>',
                             dest='uri',
                             help='URI of the Execution Window',
                             required=True)
    delete_parser.set_defaults(func=executionwindow_delete)

#
# Execution Window Main parser routine


def executionwindow_parser(parent_subparser, common_parser):

    parser = parent_subparser.add_parser('executionwindow',
                                description='ViPR executionwindow CLI usage',
                                parents=[common_parser],
                                conflict_handler='resolve',
                                help='Operations on Execution Window')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # create parser
    create_parser(subcommand_parsers, common_parser)

    # update parser
    update_parser(subcommand_parsers, common_parser)

    # delete parser
    delete_parser(subcommand_parsers, common_parser)
