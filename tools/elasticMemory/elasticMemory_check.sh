#!/bin/bash

_usage() {
    echo "Usage: $0 {hostname|ip_address}" >&2
    exit 2
}

_fatal() {
     echo "Error: $0: $*" >&2
     exit 1
}

[ $# = 1 ]       || _usage
input=$(ssh ${1} 'cat /proc/meminfo; ps -efww')
[ -n "${input}" ] || _fatal "Failed to obtian input from ${1}"

TotM=$(echo "${input}" | sed -rn 's,MemTotal:.*\s([0-9]+)\s.*kB,\1,p')                || _fatal "Failed to read MemTotal"
MaxM=$(echo "${input}" | sed -rn 's,.*/bin/(\S+)\s.*(-Xmx\S+).*,\1 \2,p')             || _fatal "Failed to read MaxMemory"
MinM=$(echo "${input}" | sed -rn 's,.*/bin/(\S+)\s.*(-Xms\S+).*,\1 \2,p')             || _fatal "Failed to read MinMemory"
YGen=$(echo "${input}" | sed -rn 's,.*/bin/(\S+)\s.*(-Xmn\S+).*,\1 \2,p')             || _fatal "Failed to read YoungGenMemory"
Perm=$(echo "${input}" | sed -rn 's,.*/bin/(\S+)\s.*(-XX:MaxPermSize=\S+).*,\1 \2,p') || _fatal "Failed to read MaxPermSizeMemory"

cat <<EOF
*** MemTotal: $(( ${TotM} / 1024 )) MB

*** MaxMemory
${MaxM}

*** MinMemory
${MinM}

*** YounGenMemory
${YGen}

*** MaxPermSizeMemory
${Perm}
EOF


