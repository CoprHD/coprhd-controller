#!/usr/bin/env bash
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#

TEST_SUITES_DIR=$BASEDIR/suites
TEST_CONTEXTS_DIR=$BASEDIR/contexts
export VIPR_API_DIR=$BASEDIR/api

COLOR_RED=`tput setaf 1`
COLOR_WHITE=`tput setaf 7`
COLOR_RESET=`tput sgr0`

export PYTHONPATH=$BASEDIR:$VIPR_API_DIR:$PYTHONPATH
export PATH=$VIPR_API_DIR:$PATH
