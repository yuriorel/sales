# Fetches the public certificate of the running OAuth2 broker and imports it into
# config/tls/broker-truststore.p12, which the JWT decoder uses to call the broker's JWKS endpoint.
param(
    [string]$BrokerHost = 'localhost:8443',
    [string]$Password = $(if ($env:SALES_BROKER_TRUSTSTORE_PASSWORD) { $env:SALES_BROKER_TRUSTSTORE_PASSWORD } else { 'changeit' })
)

$ErrorActionPreference = 'Stop'
$dir = Join-Path $PSScriptRoot '..\config\tls'
New-Item -ItemType Directory -Force $dir | Out-Null
$truststore = Join-Path $dir 'broker-truststore.p12'
$cert = Join-Path $dir 'broker.crt'

Remove-Item $truststore, $cert -ErrorAction SilentlyContinue

$pem = keytool -printcert -sslserver $BrokerHost -rfc
if ($LASTEXITCODE -ne 0) { throw "Cannot read the certificate from $BrokerHost. Is the broker running?" }
$pem | Set-Content -Encoding ascii $cert

keytool -importcert -noprompt -alias oauth2-broker -file $cert `
    -storetype PKCS12 -keystore $truststore -storepass $Password
if ($LASTEXITCODE -ne 0) { throw 'keytool -importcert failed' }

Write-Host "Truststore: $truststore"
