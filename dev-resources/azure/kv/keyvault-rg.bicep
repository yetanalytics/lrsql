targetScope = 'subscription'

param rgName string = 'lrsql-secrets-rg'
param location string = 'eastus'
param kvName string = 'lrsql-secrets-kv'
param enableRbac bool = true
param purgeProtection bool = true
param tags object = {}
param readerObjectId string

resource rg 'Microsoft.Resources/resourceGroups@2024-03-01' = {
  name: rgName
  location: location
  tags: tags
}

module kv './keyvault.bicep' = {
  name: 'deploy-kv'
  scope: rg
  params: {
    kvName: kvName
    location: location
    enableRbac: enableRbac
    purgeProtection: purgeProtection
    readerObjectId: readerObjectId
    tags: tags
  }
}
