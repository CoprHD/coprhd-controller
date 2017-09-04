verify_failures() {
  INVOKE_FAILURE_FILE=/opt/storageos/logs/invoke-test-failure.log
  FAILURES=${1}

  # cat ${INVOKE_FAILURE_FILE}

  for failure_check in `echo ${FAILURES} | sed 's/:/ /g'`
  do

    # Remove any trailing &# used to represent specific failure occurrences    
    failure_check=${failure_check%&*}

    grep ${failure_check} ${INVOKE_FAILURE_FILE} > /dev/null
    if [ $? -ne 0 ]; then
      echo 
      echo "FAILED: Failure injection ${failure_check} was not encountered during the operation execution."
      echo 
      incr_fail_count
    fi
  done

  # delete the invoke test failure file.  It will get recreated.
  rm -f ${INVOKE_FAILURE_FILE}
}
