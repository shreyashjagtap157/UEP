[CmdletBinding()]
param(
    [ValidateSet('Demo', 'Real', 'Both', 'Presentation')]
    [string]$Mode = 'Presentation',
    [ValidateSet('SQLite', 'PostgreSQL')]
    [string]$Database = 'SQLite',
    [ValidateSet('LocalNative', 'Keycloak')]
    [string]$IdentityProvider = 'LocalNative',
    [switch]$NoBrowser,
    [switch]$SkipAccountSeeding
)

$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'scripts\Start-UEP.ps1'
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw "UEP native launcher was not found: $launcher"
}

# This entry point is deliberately a thin compatibility wrapper. All process,
# database and identity-provider lifecycle behavior—including the opt-in local
# Keycloak service—belongs to the canonical launcher.
& $launcher -Mode $Mode -Database $Database -IdentityProvider $IdentityProvider -NoBrowser:$NoBrowser -SkipAccountSeeding:$SkipAccountSeeding
