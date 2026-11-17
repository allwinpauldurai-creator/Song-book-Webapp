using '../main.bicep'

// Production. Secrets come from environment variables at deploy time, never from this file.
param environmentName = 'prod'
param frontendHostname = 'songbook.example.org' // TODO(D8): real domain
param apiHostname = 'api.songbook.example.org' // TODO(D8): same registrable domain as frontendHostname
param bindCustomDomains = false // set true after the DNS records exist (RUNBOOK §2)
param alertEmail = 'ops@example.org' // TODO: real address
param mailFrom = 'DoNotReply@example.org' // TODO(D4): verified ACS sender
param businessTimezone = 'UTC' // TODO(D5)
param membershipDays = 365 // TODO(D1)
param postgresAdminPassword = readEnvironmentVariable('PG_ADMIN_PASSWORD')
