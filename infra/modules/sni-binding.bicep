// Second step of a custom-domain binding: the hostname is first bound without TLS (so the managed
// certificate can be issued), then re-bound here with SNI and the certificate thumbprint.
// A module is needed because one template cannot declare the same resource twice.

param siteName string
param hostname string
param thumbprint string

resource site 'Microsoft.Web/sites@2024-04-01' existing = {
  name: siteName
}

resource binding 'Microsoft.Web/sites/hostNameBindings@2024-04-01' = {
  parent: site
  name: hostname
  properties: {
    siteName: siteName
    hostNameType: 'Verified'
    sslState: 'SniEnabled'
    thumbprint: thumbprint
  }
}
