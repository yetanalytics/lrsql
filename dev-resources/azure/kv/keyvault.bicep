@description('Key Vault name (3–24 lowercase letters/digits/hyphens)')
param kvName string
param location string = resourceGroup().location
param enableRbac bool = true
param purgeProtection bool = true
param tags object = {}
param readerObjectId string

resource kv 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: kvName
  location: location
  tags: union(tags, { component: 'keyvault' })
  properties: {
    tenantId: subscription().tenantId
    enableRbacAuthorization: enableRbac
    enablePurgeProtection: purgeProtection
    sku: { family: 'A', name: 'standard' }
  }
}

resource kvSecretsOfficerRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(kv.id, readerObjectId, 'KeyVaultSecretsOfficer')
  scope: kv
  properties: {
    roleDefinitionId: subscriptionResourceId(
      'Microsoft.Authorization/roleDefinitions',
      'b86a8fe4-44ce-4948-aee5-eccb2c155cd7' // Secrets Officer
    )
    principalId: readerObjectId
    principalType: 'User'
  }
}

output keyVaultId string = kv.id
output keyVaultUri string = 'https://${kv.name}.vault.azure.net/'
