using '../main.bicep'

// Production. Secrets come from environment variables at deploy time, never from this file.
param environmentName = 'prod'
param frontendHostname = 'songbook.example.org' // TODO(D8): real domain
param apiHostname = 'api.songbook.example.org' // TODO(D8): same registrable domain as frontendHostname
param bindCustomDomains = false // set true after the DNS records exist (RUNBOOK §2)
param alertEmail = 'ops@example.org' // TODO: real address
param smsProvider = 'msg91'
param msg91TemplateOtp = 'TODO' // TODO: MSG91 template id of the DLT-approved OTP template (contains ##otp##)
param msg91TemplateReset = '' // optional: own template for password reset
param msg91TemplateChange = '' // optional: own template for mobile change
param businessTimezone = 'UTC' // TODO(D5)
param membershipDays = 365 // TODO(D1)
param postgresAdminPassword = readEnvironmentVariable('PG_ADMIN_PASSWORD')
