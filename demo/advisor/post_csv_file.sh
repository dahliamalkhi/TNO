#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ../../scripts/env.sh

# # There should be exactly one argument
# if [ "$#" -ne 1 ]; then
#   echo "Please specify the path to the CSV file to upload (and nothing else). Exiting..."
#   exit 1
# fi

INPUT_FILE=$1
POST_COMMENT=$2

# Check that curl is installed
if [ ! hash curl 2>/dev/null ]; then 
  echo "Please install curl from Cygwin installer - found under the Net category. Exiting..." 
  exit 1
fi

# Currently including the data file in the HTTP POST (as would have to remotely)
# Since are submitting locally, also have the option of have Solr read the file directly, for speed.
# See https://wiki.apache.org/solr/UpdateCSV for details.

#curl --data-binary @${INPUT_FILE} -H 'Content-type:text/plain; charset=utf-8' 'http://localhost:8983/solr/update/csv?commit=true'

# For now, enumerate the field names to overcome the fact that the files all have 'Id' when 'id' is the required unique key.
# TODO: Understand why this issue does not occur in the Advisor environment, which the CSV files come from.
# -s -S Do not output progress info, but do report errors.
curl -s -S --write-out "${POST_COMMENT},%{time_total},%{http_code},%{time_namelookup},%{time_connect},%{time_pretransfer},%{time_starttransfer}\n" --data-binary @${INPUT_FILE} -H 'Content-type:text/plain; charset=utf-8'  -o /dev/null "http://${SOLR_SERVER_HOSTNAME}:8983/solr/update/csv?header=false&skipLines=1&fieldnames=MG,ManagementGroupName,ObjectId,ObjectFullName,HealthServiceId,WorkflowName,WorkflowDisplayName,RuleId,ObjectName,CounterName,InstanceName,SampleValue,Min,Max,Percentile95,SampleCount,TimeGenerated,TenantId,RootObjectName,ObjectDisplayName,ObjectType,SourceSystem,id,Type"
#&commit=true
