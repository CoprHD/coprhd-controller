#!/usr/bin/python

# Copyright 2015 EMC Corporation
# All Rights Reserved

# Copyright (c) 2012 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import common
from common import SOSError


class Monitor(object):

    '''
    The class definition for operations on 'Monitoring'.
    '''
    # Commonly used URIs for the 'Monitoring' module
    URI_MONITOR = '/monitoring/events.{0}/?time_bucket={1}'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the SOS instance.
        These are needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def get_events(self, output_format, time_frame):
        '''
        Makes REST API call to get the events
        during the given time frame
        Parameters:
            time_frame: string in yyyy-mm-ddThh:mm
        Returns:
            Event details in response payload
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "GET",
                                             Monitor.URI_MONITOR.format(
                                                 output_format,
                                                 time_frame),
                                             None)
        if(output_format == "json"):
            o = common.json_decode(s)
            return o

        return s


def monitor_get_events(args):
    obj = Monitor(args.ip, args.port)
    try:
        if(int(args.year) <= 1900):
            print("error: year=" + args.year +
                  " is before 1900, it require year >= 1900")
            return

        time_frame = common.get_formatted_time_string(args.year,
                                                      args.month, args.day,
                                                      args.hour, args.minute)

        res = obj.get_events(args.format, time_frame)

        if (args.format == "json"):
            return common.format_json_object(res)
        return res

    except ValueError as e:
        raise SOSError(SOSError.CMD_LINE_ERR, "error: " + str(e))
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR,
                           "Unable to get requested usage events")
        else:
            raise e


def monitor_parser(parent_subparser, common_parser):
    # main monitoring parser
    parser = parent_subparser.add_parser('monitor',
                                         description='SOS monitoring' +
                                         ' CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Get monitoring events for' +
                                         ' the given time bucket')
    mandatory_args = parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-year', '-y',
                                help='Year', dest='year',
                                metavar='<year>', required=True)
    mandatory_args.add_argument('-month', '-mon',
                                metavar='<month>', dest='month',
                                help='month of the year {1 - 12}',
                                required=True)
    mandatory_args.add_argument('-day', '-d',
                                metavar='<day>', dest='day',
                                help='day of the month {01 - 31}',
                                required=True)
    mandatory_args.add_argument('-hour', '-hr',
                                metavar='<hour>', dest='hour',
                                help='hour of the day {00 - 23}',
                                required=True)
    parser.add_argument('-format', '-f',
                        metavar='<format>', dest='format',
                        help='response format: xml or json (default:json)',
                        choices=['xml', 'json'],
                        default="json")
    parser.add_argument('-minute', '-min',
                        metavar='<minute>', dest='minute',
                        help='minute of the hour {00 - 59}')

    parser.set_defaults(func=monitor_get_events)
