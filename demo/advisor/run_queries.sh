#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ../../scripts/env.sh

NUM_ITERATIONS=10000

# Using Advisor test queries.
# See https://microsoft.sharepoint.com/teams/TrustNoOne/SiteAssets/TrustNoOne%20Notebook/System%20Center.one#Test%20Queries&section-id={12DE3A93-45E8-4DB5-ABD0-8C4A9706BF74}&page-id={5F9AB198-500C-4441-8B44-E02E14F4FBC3}&end
# Query 2
#QUERY="*%3A*+Computer"
# Query 9
QUERY="MG%3A%228347a770-14b0-400e-8881-3a5771d49f89%22%0A"
# Query 10
#QUERY="*"

export KEYS_STRING=""
# Currently only one (optional) argument:
# name of field to include key string for.
# TODO: Upgrade this to handle a list of field names, and get the values
# from SecureCipherUtil.Keys.txt
if [ "$#" -eq 1 ]; then
  FIELD_NAME="$1"
  if [ "${FIELD_NAME}" == "MG" ]; then
    KEYS_STRING="&Keys=MG:A4E45BF7AF38387D1418D44F96DF582B" 
  else
    echo No TNO key entry found for field named ${FIELD_NAME}. Exiting.
	exit 1
  fi
fi
echo KEYS_STRING = ${KEYS_STRING}

# # Curl output goes into ./query_output
# # Force the user to manage the contents.
# if [ -e "./query_output" ]; then
# 	echo "Directory query_output already exists. Exiting..." 1>&2
# 	exit 1
# fi
rm -rf ./query_output
mkdir ./query_output

START_TIME_CLOCK=`date "+%H:%M:%S"`
echo Starting at ${START_TIME_CLOCK} - time since epoch start ${START_TIME_EPOCH}
echo queryNum,numDocs,timeEpoch,timeStart,totalTime,curlStatus,lookupTime,connectTime,pretransfterTime,starttransferTime

START_TIME_EPOCH_NANO=`date "+%s.%N"`
ITERATION=1
while [[ ${ITERATION} -le ${NUM_ITERATIONS} ]]; do
  
  # -s -S : Don't show progress info, but do show errors.
  curl -s -S -o temp_stats.txt "http://${SOLR_SERVER_HOSTNAME}${SOLR_SERVER_PORT}/solr/collection1/admin/mbeans?stats=true"
  
  # Using XMLStarlet for XML processing
  # http://xmlstar.sourceforge.net/doc/UG/xmlstarlet-ug.html
  #
  # Get the value of numDocs
  # <int name="numDocs">0</int>
  # Path determined using: $ xmlstarlet.exe el -v temp_stats.txt | grep numDocs
  NUMDOCS=`xmlstarlet.exe sel --text --template --value-of "(response/lst/lst/lst/lst/int[@name='numDocs'])[1]" temp_stats.txt`
  
  CURRENT_TIME_EPOCH_NANO=`date "+%s.%N"`
  
  # -s -S : Don't show progress info, but do show errors.
  CURL_OUTPUT=`curl -s -S --write-out "%{time_total},%{http_code},%{time_namelookup},%{time_connect},%{time_pretransfer},%{time_starttransfer}\n" -o query_output/query.${ITERATION}.txt "http://${SOLR_SERVER_HOSTNAME}${SOLR_SERVER_PORT}/solr/collection1/select?q=${QUERY}&wt=json&indent=true&facet=true&facet.field=ObjectFullName&facet.field=MG&facet.field=WorkflowName&rows=0&facet.mincount=1&facet.date=TimeGenerated&facet.date.start=2013-03-10T00:00:00Z&facet.date.end=2014-12-17T00:00:00Z&facet.date.gap=%2B1HOUR${KEYS_STRING}"`

  # Do this calculation after run curl, even though it is reporting the time that start curl.
  # Don't want to include the time to spawn a subshell and run awk.
  TIME_SINCE_START=`echo - | awk "{ print ${CURRENT_TIME_EPOCH_NANO} - ${START_TIME_EPOCH_NANO} }"`

  echo ${ITERATION},${NUMDOCS},${CURRENT_TIME_EPOCH_NANO},${TIME_SINCE_START},${CURL_OUTPUT}
  
  sleep 1

  let ITERATION=ITERATION+1 
done
