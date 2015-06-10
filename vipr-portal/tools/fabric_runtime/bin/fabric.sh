#
# Control script which replaces init.d script when running locally
#

DIR=`dirname $0`
. ${DIR}/setEnv.sh

NILE_DIR="${NILE_HOME}/opt/nile"
NILE_LOGS="${RUNTIME_ROOT}/logs"

sig=9

if [ ! -e $NILE_LOGS ]; then
    mkdir $NILE_LOGS
fi

_startproc() {
	echo STARTING ${1}
	${NILE_DIR}/bin/${1}${2} file:${NILE_DIR}/conf/${1%%svc}-conf.xml 1>"${NILE_LOGS}/${1}.out" 2>"${NILE_LOGS}/${1}.out" &
	sleep 0.5
}

_startprocsudo() {
	echo STARTING ${1}
	${NILE_DIR}/bin/${1}${2} file:${NILE_DIR}/conf/${1%%svc}-conf.xml 1>"${NILE_LOGS}/${1}.out" 2>"${NILE_LOGS}/${1}.out" &
	sleep 0.5
}


_killproc() {

	if [ -e "${RUNTIME_ROOT}/run/${2}.pid" ] ; then
		echo "Stopping ${2} `cat ${RUNTIME_ROOT}/run/${2}.pid`" 
		
		kill -9 `cat ${RUNTIME_ROOT}/run/${2}.pid`
		
		rm ${RUNTIME_ROOT}/run/${2}.pid
    fi

    # Sometimes things get disconnected, so make sure we kill any orphan services that we didn't know about
    foundPid=`ps -aclx | grep "${2}" | awk '{print $2}' | head -1`
    if [ -z "${foundPid}" ] ; then
	    echo "${2} Stopped"
    else
	    kill -9 $foundPid
	    echo "WARNING : Killed ${2} ophanded process with PID ${foundPid}"
    fi
}

_checkproc() {
	foundPid=`ps -aclx | grep "${1}" | awk '{print $2}' | head -1`

	if [ -e "${RUNTIME_ROOT}/run/${1}.pid" ] ; then
		recordedPid=`cat ${RUNTIME_ROOT}/run/${1}.pid`
	
		if [ "$foundPid" == "$recordedPid" ] ; then
			echo ${1} Running
		else
			if [ -z foundPid ] ; then
				echo ${1} Running : WARNING ${1}.pid=${recordedPid} actual PID=${foundPid}
			else
				echo ${1} Stopped : WARNING ${1}.pid=${recordedPid} but process cannot be found
			fi
		fi
    else
		if [ -z foundPid ] ; then 
			echo ${1} Running : WARNING ${1}.pid not found, actual PID=${foundPid}
		else
			echo "${1} Stopped"
		fi    
	fi
}

# Actions
#
case ${1} in
start)
    echo "Starting Fabric services"
    _startproc chealthsvc ${2}
    _startproc fabricapisvc ${2}
    echo " (logs are in ${NILE_LOGS}) "
    ;;
stop)
    echo "Stopping Fabric services"
	_killproc -${sig} chealthsvc
	_killproc -${sig} fabricapisvc

	$DIR/fix.sh	
    ;;
status)
	_checkproc chealthsvc
	_checkproc fabricapisvc
    ;;

reload|restart)
    $0 stop
    $0 start
    ;;
*)
    echo "Usage: $0 [ start | stop  ]"
esac

