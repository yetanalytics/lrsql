#!/bin/sh

SUB_ID=$(az account show --query id -o tsv)

jq --arg sub "$SUB_ID" '
  walk(
    if type == "string" and test("/subscriptions/<subId>/") 
    then sub("<subId>"; $sub) 
    else . end
  )
' parameters.json.template > parameters.json