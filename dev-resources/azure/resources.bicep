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

// ---- Simple public networking (dev) ----
@description('Enable public network access to the server (dev/simple).')
param publicNetworkAccess bool = true

@description('(Optional) allow Azure services to connect (0.0.0.0 firewall rule).')
param allowAzureServices bool = false

@description('(Optional) client IPv4s to permit; each entry creates a firewall rule (start=end).')
param allowedClientIps string = '[]'

// ---- Derived names/values ----
var pgServerName = toLower('${namePrefix}-pg-${uniqueString(resourceGroup().id)}')

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
    storage: {
      storageSizeGB: pgStorageSizeGB
      autoGrow: 'Enabled'
      type: pgStorageType        // Premium_LRS (SSD)
      tier: pgStorageTier        // P4 (≈120 IOPS)
      // iops/throughput are not required for Premium_LRS; omit for cheapest
    }
    network: {
      publicNetworkAccess: publicNetworkAccess ? 'Enabled' : 'Disabled'
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

// Optional firewall rules if using public access
resource firewallAzure 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = if (publicNetworkAccess && allowAzureServices) {
  name: '${pgServer.name}/AllowAllAzureIPs'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource firewallClients 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = [for (ip, i) in (publicNetworkAccess ? json(allowedClientIps) : []): {
  name: '${pgServer.name}/Client_${i}'
  properties: {
    startIpAddress: string(ip)
    endIpAddress: string(ip)
  }
}]

// ---- Helpful outputs ----
var pgHost = '${pgServer.name}.postgres.database.azure.com'
var pgPort = '5432'
var pgUserFull = '${pgAdminUser}@${pgServer.name}'

output postgresFqdn string = pgHost
output adminUser string = pgUserFull
output databaseName string = pgDatabaseName
output psqlConnectionHint string = 'psql "host=${pgHost} port=${pgPort} dbname=${pgDatabaseName} user=${pgUserFull} sslmode=require"'
output urlStyleConnExample string = 'postgres://${pgUserFull}:<PASSWORD>@${pgHost}:${pgPort}/${pgDatabaseName}?sslmode=require'
