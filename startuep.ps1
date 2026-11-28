[CmdletBinding()]
param(
    [ValidateSet('Demo', 'Real', 'Both', 'Presentation')]
    [string]$Mode = 'Presentation',
    [ValidateSet('SQLite', 'PostgreSQL')]
    [string]$Database = 'SQLite',
    [switch]$NoBrowser,
    [switch]$SkipAccountSeeding
)

$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'scripts\Start-UEP.ps1'
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw "UEP native launcher was not found: $launcher"
}

# This entry point is deliberately a thin compatibility wrapper. All process,
# database and local-authentication lifecycle behavior belongs to the canonical
# launcher so retired Keycloak startup code cannot run on this path.
& $launcher -Mode $Mode -Database $Database -NoBrowser:$NoBrowser -SkipAccountSeeding:$SkipAccountSeeding
