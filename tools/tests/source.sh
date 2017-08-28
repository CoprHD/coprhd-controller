#!/bin/bash
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

_envlines() {
    env -0 | while IFS= read -r -d $'\0' line ; do
       [[ "${line}" =~ ^(PATH|SHELL|PS1|LOGNAME|USER|TERM|_)=.* ]] && continue
       echo -n "${line}"
       echo -en "\x00"
    done
}

_envvars() {
    _envlines | while IFS= read -r -d $'\0' line ; do
       echo "${line%%=*}"
    done
}

_unsetvars() {
    for line in $(_envvars) ; do
        unset "${line%%=*}"
    done
}

_unsetvars
SANITY_CONF="${1}"
source "${SANITY_CONF}" 1>/dev/null || { echo "Failed to source ${SANITY_CONF} " >&2 ; exit 1 ; }
_envlines
