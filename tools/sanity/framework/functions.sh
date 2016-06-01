#!/usr/bin/env bash
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#

show_usage()
{
    echo "${COLOR_WHITE}"
    cat "$BASEDIR/framework/docs/usage.txt"
    echo "${COLOR_RESET}"
}

ERROR_MESSAGE=""
show_error() 
{
    echo "${COLOR_RED}"
    echo ">>>>>"
    echo "ERROR: $ERROR_MESSAGE"
    echo "<<<<<${COLOR_RESET}"
    show_usage
    exit 2;
}

load_test_context()
{
    echo "Loading $TEST_CONTEXT test context"
    sleep 1
    echo "."
    sleep 1
    echo "."
}

run_tests()
{
    echo "Executing $TEST_SUITE test suite for $TEST_CONTEXT"
    echo
    cd "$TEST_SUITES_DIR/$TEST_SUITE"
    export PYTHONPATH="$TEST_SUITES_DIR/$TEST_SUITE":"$TEST_SUITES_DIR/$TEST_SUITE/tests":$PYTHONPATH
    python -m unittest $TEST_SUITE
}

parse_command_line_args()
{
    [ "$#" -eq "0" ] && {
        show_usage
        exit 2;
    }

    while [ $# -ne 0 ]
    do
        case $1 in
            clean )
                echo "${COLOR_WHITE}clean up everything possible${COLOR_RESET}"
                exit 0;
                ;;
            config )
                if [ $# -ne 3 ]
                then
                    ERROR_MESSAGE="Invalid config command: $*"
                    show_error
                else
                    shift
                    echo "${COLOR_WHITE}"
                    echo "Setting key: $1"
                    shift
                    echo "Setting value: $1"
                    echo "${COLOR_RESET}"
                fi
                exit 0;
                ;;
            mock )
                echo "${COLOR_WHITE}run tests in mock mode, to show test structure${COLOR_RESET}"
                exit 0;
                ;;
            prime )
                echo "${COLOR_WHITE}prime a sanity context, but don't run tests${COLOR_RESET}"
                exit 0;
                ;;
            status )
                echo "${COLOR_WHITE}"
                echo "STATUS:        sanity is currently running"
                echo "TEST CONTEXT:  vmaxblock"
                echo "TEST SUITE:    export"
                echo "TEST CASE:     host add_initiator"
                echo "${COLOR_RESET}"
                exit 0;
                ;;
            *)
                export TEST_CONTEXT=$1
                export TEST_SUITE=$2
                export TEST_CASE=$3
                if [ "$TEST_SUITE"x = "x" ]; then TEST_SUITE="all"; fi
                if [ "$TEST_CASE"x = "x" ]; then TEST_CASE="all"; fi
                break
                ;;
        esac
        shift
    done
}