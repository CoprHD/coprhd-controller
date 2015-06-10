#
# Control script which replaces init.d script when running locally
#

sudo echo 

DIR=`dirname $0`
. ${DIR}/setEnv.sh

STORAGEOS_DIR="${INSTALL_HOME}/storageos"
STORAGEOS_LOGS="${RUNTIME_ROOT}/logs"

sig=9

if [ ! -e $STORAGEOS_LOGS ]; then
    mkdir $STORAGEOS_LOGS
fi

_startproc() {
	echo STARTING ${1}
	${STORAGEOS_DIR}/bin/${1}${2} file:${STORAGEOS_DIR}/conf/${1%%svc}-conf.xml 1>"${STORAGEOS_LOGS}/${1}.out" 2>"${STORAGEOS_LOGS}/${1}.out" &
	sleep 0.5
}

_startprocsudo() {
	echo STARTING ${1}
	sudo ${STORAGEOS_DIR}/bin/${1}${2} file:${STORAGEOS_DIR}/conf/${1%%svc}-conf.xml 1>"${STORAGEOS_LOGS}/${1}.out" 2>"${STORAGEOS_LOGS}/${1}.out" &
	sleep 0.5
}


_killproc() {

	if [ -e "${RUNTIME_ROOT}/run/${2}.pid" ] ; then
		echo "Stopping ${2} `cat ${RUNTIME_ROOT}/run/${2}.pid`" 
		
		sudo kill -9 `cat ${RUNTIME_ROOT}/run/${2}.pid`
		
		sudo rm ${RUNTIME_ROOT}/run/${2}.pid
    fi

    # Sometimes things get disconnected, so make sure we kill any orphan services that we didn't know about
    foundPid=`ps -aclx | grep "${2}" | awk '{print $2}' | head -1`
    if [ -z "${foundPid}" ] ; then
	    echo "${2} Stopped"
    else
	    sudo kill -9 $foundPid
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
echo
echo NOTE: Services are using Sudo so you MAY be asked for your password
echo
# Actions
#
case ${1} in
start)
    echo "Starting StorageOS services"
    _startproc authsvc ${2}
    _startproc coordinatorsvc ${2}
    _startproc dbsvc ${2}
    _startproc apisvc ${2}
    _startproc controllersvc ${2}
    _startprocsudo syssvc ${2}
    echo " (logs are in ${STORAGEOS_LOGS}) "
    ;;
runapi|api)
    echo "Starting StorageOS api services"
    _startproc authsvc ${2}
    _startproc coordinatorsvc ${2}
    _startproc dbsvc ${2}
    _startproc apisvc ${2}
    echo " (logs are in ${STORAGEOS_LOGS}) "

    ;;
runcontroller|controller)
    echo "Starting StorageOS controller service"
    _startproc controllersvc ${2}
    echo " (logs are in ${STORAGEOS_LOGS}) "

    ;;
rundb|db)
    echo "Starting StorageOS DB services"
    _startproc coordinatorsvc ${2}
    _startproc dbsvc ${2}
    echo " (logs are in ${STORAGEOS_LOGS}) "

    ;;
runcoordinate|coordinator)
    _startproc coordinatorsvc ${2}
    ;;

runvasa|vasa)
    echo "Starting VASA Services"
    _startproc vasasvc ${2}
    echo " (logs are in ${STORAGEOS_LOGS}) "
    ;;
runobject|object)
    echo "Starting Object Services"
    _startproc objcontrolsvc ${2}
    echo " (logs are in ${STORAGEOS_LOGS}) "
    ;;
stop)
    echo "Stopping StorageOS services"
	_killproc -${sig} vasasvc
	_killproc -${sig} authsvc 
	_killproc -${sig} syssvc 
	_killproc -${sig} controllersvc 
	_killproc -${sig} apisvc
	_killproc -${sig} dbsvc
	_killproc -${sig} coordinatorsvc
	_killproc -${sig} objcontrolsvc

	$DIR/fix.sh	
    ;;
status)
	_checkproc vasasvc
	_checkproc authsvc 
	_checkproc syssvc 
	_checkproc controllersvc 
	_checkproc apisvc
	_checkproc dbsvc
	_checkproc coordinatorsvc
    ;;

reload|restart)
    $0 stop
    $0 start
    ;;
*)
    echo "Usage: $0 [ start | startcoverage | stop | status | restart | runapi | api | runcontroller | controller | cassvc ]"
esac

