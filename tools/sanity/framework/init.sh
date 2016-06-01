#!/usr/bin/env bash
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#

source $BASEDIR/framework/vars.sh
source $BASEDIR/framework/functions.sh

parse_command_line_args $*

echo $COLOR_WHITE
echo "Starting ViPR Sanity..."
echo
date
echo "------------------------------"
echo "Test Context: $TEST_CONTEXT"
echo "Test Suite:   $TEST_SUITE"
echo "Test Case:    $TEST_CASE"
echo "------------------------------"
echo $COLOR_RESET 
