@description('Location for the server.')
param location string

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

// ---- Derived names/values ----
var pgServerName = toLower('${namePrefix}-pg-${uniqueString(resourceGroup().id)}')

// LRSQL params
param appName string = 'lrsql-app'

param lrsqlAdminUser string = 'admin'

@secure()
@description('(required) LRSQL Admin Password')
param lrsqlAdminPassword string

// -------------------- Variables --------------------

var vnetName       = '${namePrefix}-vnet'
var appSubnetName  = 'appsvc-subnet'
var pgSubnetName   = 'pg-subnet'
var pgDnsZoneName  = 'privatelink.postgres.database.azure.com'

// -------------------- Networking --------------------

resource vnet 'Microsoft.Network/virtualNetworks@2024-03-01' = {
  name: vnetName
  location: location
  properties: {
    addressSpace: {
      addressPrefixes: [
        '10.20.0.0/16'
      ]
    }
    subnets: [
      // App Service Integration subnet (dedicated)
      {
        name: appSubnetName
        properties: {
          addressPrefix: '10.20.1.0/24'
          delegations: [
            {
              name: 'appsvcDelegation'
              properties: {
                serviceName: 'Microsoft.Web/serverFarms'
              }
            }
          ]
        }
      }
      // PG Flexible Server delegated subnet
      {
        name: pgSubnetName
        properties: {
          addressPrefix: '10.20.2.0/24'
          delegations: [
            {
              name: 'pgDelegation'
              properties: {
                serviceName: 'Microsoft.DBforPostgreSQL/flexibleServers'
              }
            }
          ]
          privateEndpointNetworkPolicies: 'Disabled'
          privateLinkServiceNetworkPolicies: 'Disabled'
        }
      }
    ]
  }
}

var appSubnetId = '${vnet.id}/subnets/${appSubnetName}'
var pgSubnetId  = '${vnet.id}/subnets/${pgSubnetName}'

// Private DNS zone for Azure Database for PostgreSQL Flexible Server
resource pgDns 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: pgDnsZoneName
  location: 'global'
}

resource pgDnsVnetLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = {
  name: 'vnet-link'
  location: 'global'
  parent: pgDns
  properties: {
    virtualNetwork: {
      id: vnet.id
    }
    registrationEnabled: false
  }
}

// -------- Postgres ----------

// Use a stable non-preview API where possible
resource pgServer 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: pgServerName
  location: location
  sku: {
    name: pgSkuName              // e.g., B_Standard_B1ms
    tier: 'Burstable'
  }
  properties: {
    version: pgVersion
    administratorLogin: pgAdminUser
    administratorLoginPassword: pgAdminPassword
    authConfig: {passwordAuth: 'Enabled'
                 activeDirectoryAuth: 'Disabled'}
    storage: {
      storageSizeGB: pgStorageSizeGB
      autoGrow: 'Enabled'
      type: pgStorageType        // Premium_LRS (SSD)
      tier: pgStorageTier        // P4 (≈120 IOPS)
      // iops/throughput are not required for Premium_LRS; omit for cheapest
    }
    network: {
      delegatedSubnetResourceId: pgSubnetId
      privateDnsZoneArmResourceId: pgDns.id
    }
    highAvailability: {
      mode: 'Disabled'
    }
    // Intentionally omitting "backup" block for simplest defaults (PITR defaults apply)
    createMode: 'Default'

    
  }
}

// Optional initial database (handy for app bootstrap)
@description('Initial database name to create.')
param pgDatabaseName string = 'appdb'

resource pgDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  name: '${pgServer.name}/${pgDatabaseName}'
  properties: {}
}

resource plan 'Microsoft.Web/serverfarms@2024-11-01' = {
  name: '${appName}-plan'
  location: location
  kind: 'linux'
  sku: {
    name: 'B1'
    capacity: 1
  }
  properties: {
    reserved: true
  }
}

resource app 'Microsoft.Web/sites@2024-11-01' = {
  name: appName
  location: location
  kind: 'app,linux,container'
  dependsOn: [
    plan
  ]
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    siteConfig: {
      linuxFxVersion: 'DOCKER|yetanalytics/lrsql:latest'
      appCommandLine: '/lrsql/bin/run_postgres.sh'

      // App settings: required platform + optional health + your env
      appSettings: [
        // azure specific stuff
        { name: 'WEBSITES_PORT', value: '8080' }
        { name: 'WEBSITE_HEALTHCHECK_PATH', value: '/health' }
        { name: 'WEBSITE_DNS_SERVER', value: '168.63.129.16' }
        { name: 'WEBSITE_DNS_ALT_SERVER', value: '8.8.8.8'  }
        { name: 'WEBSITE_VNET_ROUTE_ALL', value: '1' }
        
        // lrsql settings
        { name: 'LRSQL_DB_HOST', value: '${pgServer.name}.postgres.database.azure.com' }
        { name: 'LRSQL_DB_USER', value: pgAdminUser }
        { name: 'LRSQL_DB_PASSWORD', value: pgAdminPassword }
        { name: 'LRSQL_DB_NAME', value: pgDatabaseName }

        // Credentials
        { name: 'LRSQL_ADMIN_USER_DEFAULT', value: lrsqlAdminUser }
        { name: 'LRSQL_ADMIN_PASS_DEFAULT', value: lrsqlAdminPassword }
      ]
    }
  }
}

// ---------- App Service VNet Integration (Swift) ----------

resource appVnetIntegration 'Microsoft.Web/sites/networkConfig@2024-11-01' = {
  name: 'virtualNetwork'
  parent: app
  properties: {
    // must be a subnet delegated to Microsoft.Web/serverFarms
    subnetResourceId: appSubnetId
    // optional, leave out; platform sets this
    // swiftSupported: true
  }
}

// ---- Postgres outputs ----
var pgHost = '${pgServer.name}.postgres.database.azure.com'
var pgPort = '5432'
var pgUserFull = '${pgAdminUser}@${pgServer.name}'

output postgresFqdn string = pgHost
output adminUser string = pgUserFull
output databaseName string = pgDatabaseName
output psqlConnectionHint string = 'psql "host=${pgHost} port=${pgPort} dbname=${pgDatabaseName} user=${pgUserFull} sslmode=require"'
output urlStyleConnExample string = 'postgres://${pgUserFull}:<PASSWORD>@${pgHost}:${pgPort}/${pgDatabaseName}?sslmode=require'

// ---- LRSQL app outputs ----

output appUrl string =  'https://${app.name}.azurewebsites.net'
//output appUrl        string = 'https://${appDefaultHost}'
