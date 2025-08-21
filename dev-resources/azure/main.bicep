targetScope = 'subscription'

@description('Name of the resource group to create')
param rgName string

@description('Azure region for the resource group and resources')
@allowed([
  'eastus'
  'eastus2'
  'westus'
  'westus2'
  'centralus'
  'westeurope'
  'northeurope'
])
param location string = 'eastus'

@description('Name prefix for postgres.')
param namePrefix string = 'lrsqldev'

@description('Admin username (not email).')
param pgAdminUser string = 'lrsqladmin'

@secure()
@description('Admin password.')
param pgAdminPassword string

@description('Postgres major version.')
@allowed(['15','16','17'])
param pgVersion string = '17'

// ---- Cheap/dev defaults ----
@description('Compute SKU (Burstable B1ms = cheapest).')
param pgSkuName string = 'Standard_B1ms'

@description('Storage size (GiB). 32 GiB is the minimum and cheapest.')
param pgStorageSizeGB int = 32

@description('Storage type: Premium SSD.')
@allowed(['Premium_LRS','PremiumV2_LRS'])
param pgStorageType string = 'Premium_LRS'

@description('Storage performance tier (Premium SSD). P4 ~ 120 IOPS.')
@allowed(['P1','P2','P3','P4','P6','P10','P15','P20','P30','P40','P50','P60','P70','P80'])
param pgStorageTier string = 'P4'

// ---- Simple public networking (dev) ----
@description('Enable public network access to the server (dev/simple).')
param publicNetworkAccess bool = true

@description('(Optional) allow Azure services to connect (0.0.0.0 firewall rule).')
param allowAzureServices bool = false

@description('(Optional) client IPv4s to permit; each entry creates a firewall rule (start=end).')
param allowedClientIps string = '[]'

resource rg 'Microsoft.Resources/resourceGroups@2021-04-01' = {
  name: rgName
  location: location
}

// Deploy child resources into the new RG
module resources './resources.bicep' = {
  name: 'lrsql-resources'
  scope: rg
  params: {
    location: location
    namePrefix: namePrefix
    pgAdminUser: pgAdminUser
    pgAdminPassword: pgAdminPassword
    pgVersion: pgVersion
    pgSkuName: pgSkuName
    pgStorageSizeGB: pgStorageSizeGB
    pgStorageType: pgStorageType
    pgStorageTier: pgStorageTier
    publicNetworkAccess: publicNetworkAccess
    allowAzureServices: allowAzureServices
    allowedClientIps: allowedClientIps
  }
}

output resourceGroupName string = rg.name
output resourceGroupLocation string = rg.location
output postgresFqdn string = resources.outputs.postgresFqdn
output adminUser string = resources.outputs.adminUser
output databaseName string = resources.outputs.databaseName
output psqlConnectionHint string = resources.outputs.psqlConnectionHint
output urlStyleConnExample string = resources.outputs.urlStyleConnExample
