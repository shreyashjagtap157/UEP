[CmdletBinding()]
param(
    [ValidateSet('Demo', 'Real', 'Both')]
    [string]$Mode = 'Both',
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

& $launcher -Mode $Mode -Database $Database -NoBrowser:$NoBrowser -SkipAccountSeeding:$SkipAccountSeeding
