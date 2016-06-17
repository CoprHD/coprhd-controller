#!/bin/bash
ostype=`uname`
    
for arg in "$@"; do
    [ "$arg" != "${arg#pid=}" ] && ddpid="${arg#pid=}"
    [ "$arg" != "${arg#if=}" ] && infile="${arg#if=}"
    [ "$arg" != "${arg#of=}" ] && outfile="${arg#of=}"
done
    
    
if [ -f "$infile" ]; then
    totalbytes=`du -D -k "$infile" | tr ' ' '\t' | cut -f 1`
    totalbytes=`expr $totalbytes \* 1024` # busybox's "du" mode doesn't support -b
else
    totalbytes=`blockdev --getsize64 "$infile" | tr ' ' '\t'`
fi

if [ -f "$outfile" ]; then
    bytescopied=`du -D -k "$outfile" | tr ' ' '\t' | cut -f 1`
    bytescopied=`expr $bytescopied \* 1024` # busybox's "du" mode doesn't support -b
else
    bytescopied=`blockdev --getsize64 "$outfile" | tr ' ' '\t'`
fi
    
dd_is_running() {
    # this COULD potentially fail if the same PID is assigned to another dd process inside
    # one loop iteration. If that ever happens... :D
if [ "$ostype" = "Linux" ] && mount | grep /proc >/dev/null; then
    if [ "$ddpid" != "" ] && \
        [ -d /proc/${ddpid} ] && \
        cat /proc/${ddpid}/cmdline 2>/dev/null | grep "dd" >/dev/null; then
        return 0
    else
        return 1
    fi
else # fallback to using ps
    if [ "$ddpid" != "" ] && \
        ps xau | grep " $ddpid " | grep "dd" >/dev/null; then
        return 0
    else
        return 1
    fi
fi
}

print_percent() {
    percent=`expr $bytescopied \* 100 / $totalbytes`
    percent=`echo -n " $percent" | tail -c 3`
    echo -e -n "$percent"
        echo -e -n 
}
    
    
poll_dd() {
    dd_is_running && {
        kill -USR1 $ddpid 2>/dev/null
    }
}
    
    
dd_is_running && {
    print_percent
    exit
}

if [ $bytescopied == $totalbytes ]; then
    # The migration is complete, the process has exited
    # and all bytes have been copied.
    echo -e -n "100"
else
    # Something went wrong, the process exited but the
    # migration is unfinished. Return -1 to indicate
    # an error
    echo -e -n "-1"
fi
