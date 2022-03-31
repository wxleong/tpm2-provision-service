#!/bin/bash

PROVISION_SERVICE_URL="http://localhost:1014"

#
# Dependencies check
#

if ! command -v jq >/dev/null 2>&1; then
  echo "jq could not be found. Please install it."
  exit
fi

if ! command -v base64 >/dev/null 2>&1; then
  echo "base64 could not be found. Please install it."
  exit
fi

#
# Start provisioning session
#

script=get-random

curl -k -d \
"{ \
\"script\" : \"${script}\", \
\"args\" : \"{\\\"bytes\\\":8}\" \
}" \
-H "Content-Type: application/json" -X POST ${PROVISION_SERVICE_URL}/api/v1/session/start >session-start-resp.json 2>/dev/null

printf "\nserver session start response:\n%s\n" "`cat session-start-resp.json`"

uuid=`jq -r '.uuid' session-start-resp.json`
seq1=`jq -r '.seq' session-start-resp.json`
cmd=`jq -r '.cmd' session-start-resp.json`
ended=`jq -r '.ended' session-start-resp.json`
emsg=`jq -r '.emsg' session-start-resp.json`

if [ "${ended}" == "true" ]; then
  if [ "${emsg}" != "" ]; then
    printf "\nError received:\n%s\n" "${emsg}"
  fi
  exit
fi

if [ "${seq1}" != "0" ]; then
  printf "\nBad req number, xfer out-of-sync, expecting seq:%s but received seq:%s.\nStopping now...\n" "0" "${seq1}"
  exit
fi
  
#
# Exchange provisioning command-response
#

while : ;
do

  # write and read back from TPM
  ./xfer `echo ${cmd} | base64 --decode | xxd -p -c 9999999` >resp
  
  # encode the response
  resp=`cat resp | xxd -r -p | base64 -w 0`
  #resp="gAEAAAAUAAAAAAAIfUGY9SemIzY="

  seq1=$((seq1+1))

  curl -k -d \
  "{ \
  \"uuid\" : \"${uuid}\", \
  \"seq\" : \"${seq1}\", \
  \"resp\" : \"${resp}\" \
  }" \
  -H "Content-Type: application/json" -X POST ${PROVISION_SERVICE_URL}/api/v1/session/xfer >session-xfer-resp.json 2>/dev/null
  printf "\nserver session xfer response:\n%s\n" "`cat session-xfer-resp.json`"

  uuid=`jq -r '.uuid' session-xfer-resp.json`
  seq2=`jq -r '.seq' session-xfer-resp.json`
  cmd=`jq -r '.cmd' session-xfer-resp.json`
  ended=`jq -r '.ended' session-xfer-resp.json`
  emsg=`jq -r '.emsg' session-xfer-resp.json`

  seq1=$((seq1+1))

  if [ "${ended}" == "true" ]; then
    if [ "${emsg}" != "" ]; then
      printf "\nError received:\n%s\n" "${emsg}"
    fi
    break
  fi

  if [ "${seq1}" != "${seq2}" ]; then
    printf "\nBad req number, xfer out-of-sync, expecting seq:%s but received seq:%s.\nStopping now...\n" "${seq1}" "${seq2}"
    break
  fi
done

#
# Read result and close the session
#

curl -k -d \
"{ \
\"uuid\" : \"${uuid}\" \
}" \
-H "Content-Type: application/json" -X POST ${PROVISION_SERVICE_URL}/api/v1/session/stop >session-stop-resp.json 2>/dev/null

printf "\nserver session stop response:\n%s\n" "`cat session-stop-resp.json`"

result=`jq -r '.result' session-stop-resp.json`
random=`echo ${result} | jq -r '.random'`
decode=`echo ${random} | base64 --decode | xxd -p -c 9999999`

printf "\nreceived random: %s\n" "${decode}"

printf "\n"