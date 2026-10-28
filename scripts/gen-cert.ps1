# Generates the self-signed TLS keystore used by the server on https://localhost:9443
# and exports its certificate so clients can trust it.
param(
    [string]$Password = $(if ($env:SALES_KEYSTORE_PASSWORD) { $env:SALES_KEYSTORE_PASSWORD } else { 'changeit' })
)

$ErrorActionPreference = 'Stop'
$dir = Join-Path $PSScriptRoot '..\config\tls'
New-Item -ItemType Directory -Force $dir | Out-Null
$keystore = Join-Path $dir 'sales-keystore.p12'
$cert = Join-Path $dir 'sales.crt'

Remove-Item $keystore, $cert -ErrorAction SilentlyContinue

keytool -genkeypair -alias sales -keyalg RSA -keysize 2048 -validity 825 `
    -storetype PKCS12 -keystore $keystore -storepass $Password `
    -dname 'CN=localhost, OU=Dev, O=Sales Online, C=US' `
    -ext 'SAN=dns:localhost,ip:127.0.0.1'
if ($LASTEXITCODE -ne 0) { throw 'keytool -genkeypair failed' }

keytool -exportcert -rfc -alias sales -keystore $keystore -storepass $Password -file $cert
if ($LASTEXITCODE -ne 0) { throw 'keytool -exportcert failed' }

Write-Host "Keystore: $keystore"
Write-Host "Certificate: $cert"
