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
$kcProc = $null
$kcBat = Join-Path $PSScriptRoot '.uep-local\tools\keycloak-26.7.5\bin\kc.bat'
$kcDir = Join-Path $PSScriptRoot '.uep-local\tools\keycloak-26.7.5'
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
            $importDir = Join-Path $kcDir 'data\import'
            if (Test-Path -LiteralPath $realmSource -PathType Leaf) {
                if (-not (Test-Path $importDir)) { New-Item -ItemType Directory -Path $importDir -Force | Out-Null }
                Copy-Item -LiteralPath $realmSource -Destination (Join-Path $importDir 'education-realm.json') -Force
            }
            $env:KEYCLOAK_ADMIN = 'admin'
            $env:KEYCLOAK_ADMIN_PASSWORD = 'admin'
            $psi = New-Object System.Diagnostics.ProcessStartInfo
            $psi.FileName = 'cmd.exe'
            $psi.Arguments = '/c ""' + $kcBat + '"" start-dev --http-port=8081 --import-realm'
            $psi.WorkingDirectory = $kcDir
            $psi.UseShellExecute = $true
            $psi.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Minimized
            $kcProc = [System.Diagnostics.Process]::Start($psi)
            Write-Host "Keycloak process started (PID: $($kcProc.Id))." -ForegroundColor Green
        } else {
            Write-Host 'Keycloak is already listening on port 8081.' -ForegroundColor Green
        }
    }
}

try {
    & $launcher -Mode $Mode -Database $Database -NoBrowser:$NoBrowser -SkipAccountSeeding:$SkipAccountSeeding
} finally {
    if ($null -ne $kcProc -and -not $kcProc.HasExited) {
        try { Stop-Process -Id $kcProc.Id -Force } catch {}
    }
}
