#!/bin/sh

# Grab your own objectId
USER_OBJECTID=$(az ad signed-in-user show --query id -o tsv)

# Deploy the Key Vault bicep template, passing both IDs
az deployment sub create \
  --location eastus \
  --template-file keyvault-rg.bicep \
  --parameters readerObjectId=$USER_OBJECTID