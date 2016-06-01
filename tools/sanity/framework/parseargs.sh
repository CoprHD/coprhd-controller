#!/usr/bin/env bash
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#

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
            TEST_CONTEXT=$1
            TEST_SUITE=$2
            TEST_CASE=$3
            if [ "$TEST_SUITE"x = "x" ]; then TEST_SUITE="all"; fi
            if [ "$TEST_CASE"x = "x" ]; then TEST_CASE="all"; fi
            break
            ;;
    esac
    shift
done
