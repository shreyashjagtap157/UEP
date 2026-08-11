$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $Root

$Version = (Get-Content VERSION -Raw).Trim()
if (-not (Select-String -Path apps/platform-server/pom.xml -SimpleMatch "<version>$Version</version>" -Quiet)) { throw "POM version mismatch" }
if (-not (Select-String -Path docs/api/openapi.yaml -SimpleMatch "version: $Version" -Quiet)) { throw "OpenAPI version mismatch" }
if (-not (Select-String -Path apps/platform-server/src/main/resources/application.yaml -SimpleMatch "version: $Version" -Quiet)) { throw "application version mismatch" }

$Diff = git diff --check
if ($LASTEXITCODE -ne 0) { throw $Diff }
Write-Host "repository policy checks passed for $Version"
