[CmdletBinding()]
param(
    [ValidateSet('Demo', 'Real', 'Both', 'Presentation')]
    [string]$Mode = 'Presentation',
    [ValidateSet('SQLite', 'PostgreSQL')]
    [string]$Database = 'SQLite',
    [switch]$NoBrowser,
    [switch]$SkipAccountSeeding,
    [switch]$WithKeycloak
)

$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'scripts\Start-UEP.ps1'
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw "UEP native launcher was not found: $launcher"
}

# Start Keycloak on port 8081 if requested or in paired/Real modes
$kcJob = $null
$kcBat = Join-Path $PSScriptRoot '.uep-local\tools\keycloak-26.7.5\bin\kc.bat'
if ($WithKeycloak -or $Mode -in @('Both', 'Presentation', 'Real')) {
    if (Test-Path -LiteralPath $kcBat -PathType Leaf) {
        $kcListening = $false
        try {
            $client = New-Object System.Net.Sockets.TcpClient
            $async = $client.BeginConnect('127.0.0.1', 8081, $null, $null)
            $wait = $async.AsyncWaitHandle.WaitOne(400, $false)
            if ($wait) {
                $client.EndConnect($async)
                $kcListening = $true
            }
            $client.Close()
        } catch {}

        if (-not $kcListening) {
            Write-Host 'Starting Keycloak 26.7.5 on port 8081...' -ForegroundColor Cyan
            $realmSource = Join-Path $PSScriptRoot 'infra\keycloak\education-realm.json'
            $importDir = Join-Path $PSScriptRoot '.uep-local\tools\keycloak-26.7.5\data\import'
            if (Test-Path -LiteralPath $realmSource -PathType Leaf) {
                if (-not (Test-Path $importDir)) { New-Item -ItemType Directory -Path $importDir -Force | Out-Null }
                Copy-Item -LiteralPath $realmSource -Destination (Join-Path $importDir 'education-realm.json') -Force
            }
            $kcJob = Start-Process -FilePath $kcBat -ArgumentList 'start-dev', '--http-port', '8081', '--import-realm' -WorkingDirectory (Join-Path $PSScriptRoot '.uep-local\tools\keycloak-26.7.5') -PassThru -WindowStyle Minimized
            Write-Host "Keycloak process started (PID: $($kcJob.Id))." -ForegroundColor Green
        } else {
            Write-Host 'Keycloak is already listening on port 8081.' -ForegroundColor Green
        }
    }
}

try {
    & $launcher -Mode $Mode -Database $Database -NoBrowser:$NoBrowser -SkipAccountSeeding:$SkipAccountSeeding
} finally {
    if ($null -ne $kcJob -and -not $kcJob.HasExited) {
        try { Stop-Process -Id $kcJob.Id -Force } catch {}
    }
}
