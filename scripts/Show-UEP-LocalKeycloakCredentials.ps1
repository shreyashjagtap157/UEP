[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$stateFile = Join-Path $env:LOCALAPPDATA 'UEP\local-native\state.json'
if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) {
    throw 'UEP local state was not found. Start UEP once with -IdentityProvider Keycloak before requesting credentials.'
}
$state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
foreach ($property in @('KeycloakAdminUsername', 'KeycloakAdminPassword', 'KeycloakOwnerPassword')) {
    if (-not ($state.PSObject.Properties.Name -contains $property) -or
            [string]::IsNullOrWhiteSpace([string]$state.$property)) {
        throw 'Keycloak credentials are not initialized. Start UEP once with -IdentityProvider Keycloak.'
    }
}

Write-Host 'UEP LOCAL KEYCLOAK CREDENTIALS — LOCAL MACHINE ONLY' -ForegroundColor Cyan
Write-Host 'The passwords below are read from the ACL-protected LocalAppData state file.'
Write-Host ''
Write-Host 'Keycloak administrator console'
Write-Host '  URL:      http://localhost:8081/admin/'
Write-Host '  Realm:    master'
Write-Host ('  Username: ' + [string]$state.KeycloakAdminUsername)
Write-Host ('  Password: ' + [string]$state.KeycloakAdminPassword)
Write-Host ''
Write-Host 'UEP application owner'
Write-Host '  URL:      http://localhost:8081/realms/education/account/'
Write-Host '  Realm:    education'
Write-Host '  Username: uep-owner'
Write-Host '  Email:    owner@local.uep'
Write-Host ('  Password: ' + [string]$state.KeycloakOwnerPassword)
Write-Host ''
Write-Host 'These credentials are for the loopback-only development identity provider. Do not reuse them outside this workstation.' -ForegroundColor Yellow
