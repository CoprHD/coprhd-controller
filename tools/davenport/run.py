#!/usr/bin/env python2

import argparse
import sys

import davenport.server as server
import davenport.dummy as dummy


def parse_args(original_args):
    parser = argparse.ArgumentParser()
    parser.add_argument('--vipr', '-v', required=True, type=str)
    parser.add_argument('--user', '-u', required=True, type=str)
    parser.add_argument('--password', '-p', required=True, type=str)
    parser.add_argument('--dummy', '-d', action='store_true', default=False)
    parser.add_argument('--debug', '-e', action='store_true', default=False)
    parsed_args = parser.parse_args(original_args)
    return parsed_args


if __name__ == '__main__':
    print('Start server...')
    args = parse_args(sys.argv[1:])
    print('Arguments are parsed...')
    if args.dummy:
        dummy.run(args.debug)
    else:
        server.vipr['host'] = args.vipr
        server.vipr['user'] = args.user
        server.vipr['password'] = args.password
        server.run(args.debug)
