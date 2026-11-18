// Song Book Library: one environment (dev or prod) in one resource group.
// See docs/AZURE_DEPLOYMENT.md and docs/RUNBOOK.md for the order of operations.
//
// Topology (decision D8 = custom domains): the SPA is served from https://<frontendHostname> by
// Static Web Apps and calls the API at https://<apiHostname> on App Service. Both hostnames share a
// registrable domain, so the SameSite=Strict refresh cookie is sent ("same site", cross-origin with CORS).

targetScope = 'resourceGroup'

@allowed(['dev', 'prod'])
param environmentName string

param location string = resourceGroup().location

@description('Static Web Apps is offered in a few regions only; content is served from a global edge network either way.')
@allowed(['centralus', 'eastus2', 'westus2', 'westeurope', 'eastasia'])
param staticSiteLocation string = 'eastasia'

@description('Short lowercase name used in resource names, e.g. "songbook".')
@minLength(3)
@maxLength(12)
param appName string = 'songbook'

@description('Custom domain of the SPA, e.g. songbook.example.org. Empty = Azure default hostname (refresh cookie will NOT work; test only).')
param frontendHostname string = ''

@description('Custom domain of the API, e.g. api.songbook.example.org. Must share the registrable domain with frontendHostname.')
param apiHostname string = ''

@description('Bind the custom domains and create managed certificates. Set true only after the DNS records in docs/RUNBOOK.md exist.')
param bindCustomDomains bool = false

@description('App Service plan SKU. Staging slots need Standard or Premium.')
param appServiceSku string = environmentName == 'prod' ? 'P1v3' : 'B1'

@description('PostgreSQL flexible server SKU name and tier.')
param postgresSkuName string = environmentName == 'prod' ? 'Standard_D2ds_v5' : 'Standard_B1ms'
@allowed(['Burstable', 'GeneralPurpose', 'MemoryOptimized'])
param postgresSkuTier string = environmentName == 'prod' ? 'GeneralPurpose' : 'Burstable'

@minValue(7)
@maxValue(35)
param postgresBackupRetentionDays int = environmentName == 'prod' ? 35 : 7

param postgresGeoRedundantBackup bool = environmentName == 'prod'

param postgresAdminLogin string = 'songbookadmin'

@secure()
@description('PostgreSQL admin password. Stored in Key Vault as "db-password"; never in app settings.')
param postgresAdminPassword string

@description('Email for operational alerts.')
param alertEmail string

@description('Business timezone for date rules (decision D5), e.g. Asia/Kolkata.')
param businessTimezone string = 'UTC'

@description('Default membership length in days; 0 = no end date (decision D1).')
param membershipDays int = 365

@description('SMS provider for one-time codes: msg91 (real SMS) or log (codes written to the app log; dev only).')
@allowed(['msg91', 'log'])
param smsProvider string = environmentName == 'prod' ? 'msg91' : 'log'

@description('MSG91 DLT template ids (each template contains the OTP variable). Verify is required when smsProvider is msg91; reset and change default to it.')
param msg91TemplateVerify string = ''
param msg91TemplateReset string = ''
param msg91TemplateChange string = ''

@description('Name of the OTP variable in the MSG91 template text (##otp## -> otp).')
param msg91OtpVariable string = 'otp'

@description('Maximum SMS codes sent in any 24 hours across all users (cost guard).')
param smsDailyLimit int = 500

@description('Blob soft-delete / container soft-delete retention in days.')
param blobSoftDeleteDays int = 14

@description('Key Vault purge protection cannot be turned off once enabled.')
param keyVaultPurgeProtection bool = environmentName == 'prod'

var suffix = '${appName}-${environmentName}'
var compact = toLower(replace('${appName}${environmentName}', '-', ''))
var tags = { app: appName, environment: environmentName }
var useStagingSlot = startsWith(appServiceSku, 'S') || startsWith(appServiceSku, 'P')

// ------------------------------------------------------------------ monitoring

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: 'log-${suffix}'
  location: location
  tags: tags
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: environmentName == 'prod' ? 90 : 30
  }
}

resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: 'appi-${suffix}'
  location: location
  tags: tags
  kind: 'web'
  properties: {
    Application_Type: 'web'
    WorkspaceResourceId: logs.id
    DisableLocalAuth: false
  }
}

// ------------------------------------------------------------------ secrets

resource vault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: take('kv-${compact}-${uniqueString(resourceGroup().id)}', 24)
  location: location
  tags: tags
  properties: {
    tenantId: subscription().tenantId
    sku: { family: 'A', name: 'standard' }
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 90
    enablePurgeProtection: keyVaultPurgeProtection ? true : null
    publicNetworkAccess: 'Enabled'
  }
}

// jwt-secret, ticket-secret and msg91-auth-key are set by an operator (RUNBOOK §3):
// Bicep has no cryptographic random generator and must not hold the SMS provider key.
resource dbPasswordSecret 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: vault
  name: 'db-password'
  properties: { value: postgresAdminPassword }
}

// ------------------------------------------------------------------ database

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: 'pg-${suffix}-${take(uniqueString(resourceGroup().id), 6)}'
  location: location
  tags: tags
  sku: { name: postgresSkuName, tier: postgresSkuTier }
  properties: {
    version: '16'
    administratorLogin: postgresAdminLogin
    administratorLoginPassword: postgresAdminPassword
    storage: { storageSizeGB: 32, autoGrow: 'Enabled' }
    backup: {
      backupRetentionDays: postgresBackupRetentionDays
      geoRedundantBackup: postgresGeoRedundantBackup ? 'Enabled' : 'Disabled'
    }
    highAvailability: { mode: 'Disabled' }
    authConfig: { passwordAuth: 'Enabled', activeDirectoryAuth: 'Disabled' }
    network: { publicNetworkAccess: 'Enabled' }
  }
}

resource database 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgres
  name: 'songbook'
  properties: { charset: 'UTF8', collation: 'en_US.utf8' }
}

// Allows Azure services (incl. App Service) to connect. TLS is enforced by the server default
// (require_secure_transport=on). For prod, prefer VNet integration + private access (RUNBOOK §9).
resource postgresAzureAccess 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: postgres
  name: 'AllowAzureServices'
  properties: { startIpAddress: '0.0.0.0', endIpAddress: '0.0.0.0' }
}

// ------------------------------------------------------------------ file storage

resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: take('st${compact}${uniqueString(resourceGroup().id)}', 24)
  location: location
  tags: tags
  kind: 'StorageV2'
  sku: { name: environmentName == 'prod' ? 'Standard_ZRS' : 'Standard_LRS' }
  properties: {
    accessTier: 'Hot'
    allowBlobPublicAccess: false // PDFs and covers are only streamed through the API
    allowSharedKeyAccess: false // Entra ID (Managed Identity) only; no account keys / SAS
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
    defaultToOAuthAuthentication: true
    publicNetworkAccess: 'Enabled'
  }
}

resource blobService 'Microsoft.Storage/storageAccounts/blobServices@2023-05-01' = {
  parent: storage
  name: 'default'
  properties: {
    isVersioningEnabled: true
    deleteRetentionPolicy: { enabled: true, days: blobSoftDeleteDays }
    containerDeleteRetentionPolicy: { enabled: true, days: blobSoftDeleteDays }
  }
}

resource coversContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-05-01' = {
  parent: blobService
  name: 'book-covers'
  properties: { publicAccess: 'None' }
}

resource pdfsContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-05-01' = {
  parent: blobService
  name: 'book-pdfs'
  properties: { publicAccess: 'None' }
}

// ------------------------------------------------------------------ frontend (SPA)

resource staticSite 'Microsoft.Web/staticSites@2024-04-01' = {
  name: 'swa-${suffix}'
  location: staticSiteLocation
  tags: tags
  sku: { name: 'Standard', tier: 'Standard' }
  properties: {
    // Deployed by the GitHub workflow with a short-lived deployment token, not linked to a repo here.
    stagingEnvironmentPolicy: 'Disabled'
    allowConfigFileUpdates: true
  }
}

resource staticSiteDomain 'Microsoft.Web/staticSites/customDomains@2024-04-01' = if (bindCustomDomains && !empty(frontendHostname)) {
  parent: staticSite
  name: empty(frontendHostname) ? 'unused.invalid' : frontendHostname
  properties: { validationMethod: 'cname-delegation' }
}

var frontendOrigin = empty(frontendHostname) ? 'https://${staticSite.properties.defaultHostname}' : 'https://${frontendHostname}'

// ------------------------------------------------------------------ API (App Service)

resource plan 'Microsoft.Web/serverfarms@2024-04-01' = {
  name: 'asp-${suffix}'
  location: location
  tags: tags
  kind: 'linux'
  sku: { name: appServiceSku }
  properties: { reserved: true }
}

// Mask access tickets in Application Insights request URLs (docs/SECURITY.md §5).
var appInsightsAgentConfig = string({
  preview: {
    processors: [
      {
        type: 'attribute'
        actions: [
          { key: 'url.full', action: 'mask', pattern: '(?<p>ticket=)[^&]*', replace: '\${p}***' }
          { key: 'url.query', action: 'mask', pattern: '(?<p>ticket=)[^&]*', replace: '\${p}***' }
          { key: 'http.url', action: 'mask', pattern: '(?<p>ticket=)[^&]*', replace: '\${p}***' }
          { key: 'http.target', action: 'mask', pattern: '(?<p>ticket=)[^&]*', replace: '\${p}***' }
        ]
      }
    ]
  }
})

func kvRef(vaultName string, secretName string) string => '@Microsoft.KeyVault(VaultName=${vaultName};SecretName=${secretName})'

// The MSG91 key is only referenced when MSG91 is used, so the log provider runs without that secret.
var smsSettings = smsProvider == 'msg91'
  ? [
      { name: 'SONGBOOK_SMS_PROVIDER', value: 'msg91' }
      { name: 'SONGBOOK_SMS_ALLOW_LOG', value: 'false' }
      { name: 'MSG91_AUTH_KEY', value: kvRef(vault.name, 'msg91-auth-key') }
      { name: 'MSG91_TEMPLATE_VERIFY', value: msg91TemplateVerify }
      { name: 'MSG91_TEMPLATE_RESET', value: msg91TemplateReset }
      { name: 'MSG91_TEMPLATE_CHANGE', value: msg91TemplateChange }
      { name: 'MSG91_OTP_VARIABLE', value: msg91OtpVariable }
    ]
  : [
      { name: 'SONGBOOK_SMS_PROVIDER', value: 'log' }
      { name: 'SONGBOOK_SMS_ALLOW_LOG', value: 'true' }
    ]

var appSettings = concat([
  { name: 'SPRING_PROFILES_ACTIVE', value: 'prod' }
  { name: 'SERVER_PORT', value: '8080' }
  { name: 'WEBSITES_PORT', value: '8080' }
  { name: 'DB_URL', value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/${database.name}?sslmode=require' }
  { name: 'DB_USERNAME', value: postgresAdminLogin }
  { name: 'DB_PASSWORD', value: kvRef(vault.name, 'db-password') }
  { name: 'SONGBOOK_JWT_SECRET', value: kvRef(vault.name, 'jwt-secret') }
  { name: 'SONGBOOK_TICKET_SECRET', value: kvRef(vault.name, 'ticket-secret') }
  { name: 'SONGBOOK_FRONTEND_URL', value: frontendOrigin }
  { name: 'SONGBOOK_CORS_ORIGINS', value: frontendOrigin }
  { name: 'SONGBOOK_TIMEZONE', value: businessTimezone }
  { name: 'SONGBOOK_MEMBERSHIP_DAYS', value: string(membershipDays) }
  { name: 'SONGBOOK_STORAGE_PROVIDER', value: 'azure' }
  { name: 'AZURE_STORAGE_ACCOUNT_ENDPOINT', value: storage.properties.primaryEndpoints.blob }
  { name: 'SONGBOOK_SMS_DAILY_LIMIT', value: string(smsDailyLimit) }
  { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', value: appInsights.properties.ConnectionString }
  { name: 'ApplicationInsightsAgent_EXTENSION_VERSION', value: '~3' }
  { name: 'APPLICATIONINSIGHTS_CONFIGURATION_CONTENT', value: appInsightsAgentConfig }
], smsSettings)

var siteConfig = {
  linuxFxVersion: 'JAVA|21-java21'
  alwaysOn: true
  http20Enabled: true
  ftpsState: 'Disabled'
  minTlsVersion: '1.2'
  scmMinTlsVersion: '1.2'
  healthCheckPath: '/actuator/health/readiness'
  appSettings: appSettings
}

resource api 'Microsoft.Web/sites@2024-04-01' = {
  name: 'app-${suffix}-${take(uniqueString(resourceGroup().id), 6)}'
  location: location
  tags: tags
  kind: 'app,linux'
  identity: { type: 'SystemAssigned' }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    clientAffinityEnabled: false
    keyVaultReferenceIdentity: 'SystemAssigned'
    siteConfig: siteConfig
  }
}

resource staging 'Microsoft.Web/sites/slots@2024-04-01' = if (useStagingSlot) {
  parent: api
  name: 'staging'
  location: location
  tags: tags
  kind: 'app,linux'
  identity: { type: 'SystemAssigned' }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    clientAffinityEnabled: false
    keyVaultReferenceIdentity: 'SystemAssigned'
    siteConfig: siteConfig
  }
}

// Deployments use Entra ID (OIDC from GitHub); disable legacy FTP/basic-auth publishing.
resource apiFtp 'Microsoft.Web/sites/basicPublishingCredentialsPolicies@2024-04-01' = {
  parent: api
  name: 'ftp'
  properties: { allow: false }
}

resource apiScm 'Microsoft.Web/sites/basicPublishingCredentialsPolicies@2024-04-01' = {
  parent: api
  name: 'scm'
  properties: { allow: false }
}

resource apiHostBinding 'Microsoft.Web/sites/hostNameBindings@2024-04-01' = if (bindCustomDomains && !empty(apiHostname)) {
  parent: api
  name: empty(apiHostname) ? 'unused.invalid' : apiHostname
  properties: { siteName: api.name, hostNameType: 'Verified', sslState: 'Disabled' }
}

resource apiCertificate 'Microsoft.Web/certificates@2024-04-01' = if (bindCustomDomains && !empty(apiHostname)) {
  name: 'cert-${suffix}-api'
  location: location
  tags: tags
  properties: { serverFarmId: plan.id, canonicalName: apiHostname }
  dependsOn: [apiHostBinding]
}

module apiSni 'modules/sni-binding.bicep' = if (bindCustomDomains && !empty(apiHostname)) {
  name: 'api-sni-binding'
  params: {
    siteName: api.name
    hostname: apiHostname
    thumbprint: (bindCustomDomains && !empty(apiHostname)) ? apiCertificate!.properties.thumbprint : ''
  }
}

// ------------------------------------------------------------------ role assignments (least privilege)

var roles = {
  blobDataContributor: 'ba92f5b4-2d11-453d-a403-e96b0029c9fe'
  keyVaultSecretsUser: '4633458b-17de-408a-b874-0445c86b69e6'
}

resource apiCoversRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: coversContainer
  name: guid(coversContainer.id, api.id, roles.blobDataContributor)
  properties: {
    principalId: api.identity.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.blobDataContributor)
  }
}

resource apiPdfsRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: pdfsContainer
  name: guid(pdfsContainer.id, api.id, roles.blobDataContributor)
  properties: {
    principalId: api.identity.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.blobDataContributor)
  }
}

resource apiVaultRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: vault
  name: guid(vault.id, api.id, roles.keyVaultSecretsUser)
  properties: {
    principalId: api.identity.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.keyVaultSecretsUser)
  }
}

resource stagingCoversRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (useStagingSlot) {
  scope: coversContainer
  name: guid(coversContainer.id, '${api.id}/slots/staging', roles.blobDataContributor)
  properties: {
    principalId: staging!.identity.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.blobDataContributor)
  }
}

resource stagingPdfsRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (useStagingSlot) {
  scope: pdfsContainer
  name: guid(pdfsContainer.id, '${api.id}/slots/staging', roles.blobDataContributor)
  properties: {
    principalId: staging!.identity.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.blobDataContributor)
  }
}

resource stagingVaultRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (useStagingSlot) {
  scope: vault
  name: guid(vault.id, '${api.id}/slots/staging', roles.keyVaultSecretsUser)
  properties: {
    principalId: staging!.identity.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roles.keyVaultSecretsUser)
  }
}

// ------------------------------------------------------------------ alerts

resource actionGroup 'Microsoft.Insights/actionGroups@2023-01-01' = {
  name: 'ag-${suffix}'
  location: 'global'
  tags: tags
  properties: {
    groupShortName: take('sb${environmentName}', 12)
    enabled: true
    emailReceivers: [{ name: 'ops', emailAddress: alertEmail, useCommonAlertSchema: true }]
  }
}

var metricAlerts = [
  {
    name: 'api-5xx'
    description: 'API returned more than 10 server errors in 5 minutes'
    scope: api.id
    metric: 'Http5xx'
    namespace: 'Microsoft.Web/sites'
    aggregation: 'Total'
    operator: 'GreaterThan'
    threshold: 10
    severity: 1
  }
  {
    name: 'api-slow'
    description: 'Average API response time above 1 s for 5 minutes'
    scope: api.id
    metric: 'HttpResponseTime'
    namespace: 'Microsoft.Web/sites'
    aggregation: 'Average'
    operator: 'GreaterThan'
    threshold: 1
    severity: 2
  }
  {
    name: 'api-unhealthy'
    description: 'App Service health check below 100 %'
    scope: api.id
    metric: 'HealthCheckStatus'
    namespace: 'Microsoft.Web/sites'
    aggregation: 'Average'
    operator: 'LessThan'
    threshold: 100
    severity: 1
  }
  {
    name: 'db-cpu'
    description: 'PostgreSQL CPU above 80 %'
    scope: postgres.id
    metric: 'cpu_percent'
    namespace: 'Microsoft.DBforPostgreSQL/flexibleServers'
    aggregation: 'Average'
    operator: 'GreaterThan'
    threshold: 80
    severity: 2
  }
  {
    name: 'db-storage'
    description: 'PostgreSQL storage above 85 %'
    scope: postgres.id
    metric: 'storage_percent'
    namespace: 'Microsoft.DBforPostgreSQL/flexibleServers'
    aggregation: 'Average'
    operator: 'GreaterThan'
    threshold: 85
    severity: 2
  }
]

resource alerts 'Microsoft.Insights/metricAlerts@2018-03-01' = [for a in metricAlerts: {
  name: 'alert-${suffix}-${a.name}'
  location: 'global'
  tags: tags
  properties: {
    description: a.description
    severity: a.severity
    enabled: true
    scopes: [a.scope]
    evaluationFrequency: 'PT1M'
    windowSize: 'PT5M'
    criteria: {
      'odata.type': 'Microsoft.Azure.Monitor.SingleResourceMultipleMetricCriteria'
      allOf: [
        {
          name: a.name
          criterionType: 'StaticThresholdCriterion'
          metricName: a.metric
          metricNamespace: a.namespace
          timeAggregation: a.aggregation
          operator: a.operator
          threshold: a.threshold
        }
      ]
    }
    actions: [{ actionGroupId: actionGroup.id }]
  }
}]

// ------------------------------------------------------------------ outputs (used by the deploy workflow)

output webAppName string = api.name
output stagingSlotEnabled bool = useStagingSlot
output apiDefaultHostname string = api.properties.defaultHostName
output apiOrigin string = empty(apiHostname) ? 'https://${api.properties.defaultHostName}' : 'https://${apiHostname}'
output staticSiteName string = staticSite.name
output frontendDefaultHostname string = staticSite.properties.defaultHostname
output frontendOrigin string = frontendOrigin
output keyVaultName string = vault.name
output postgresServerName string = postgres.name
output postgresFqdn string = postgres.properties.fullyQualifiedDomainName
output storageAccountName string = storage.name
output customDomainVerificationId string = api.properties.customDomainVerificationId
