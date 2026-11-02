[CmdletBinding()]
param(
    [ValidateSet('Demo', 'Real', 'Both')]
    [string]$Mode = 'Both',
    [switch]$NoBrowser,
    [switch]$SkipAccountSeeding
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$WebDir = Join-Path $RepoRoot 'apps\web'
$ApiDir = Join-Path $RepoRoot 'apps\platform-server'
$LocalDir = Join-Path $RepoRoot '.uep-local'
$LogDir = Join-Path $LocalDir 'logs'
$ToolDir = Join-Path $LocalDir 'tools'
$StateDir = Join-Path $env:LOCALAPPDATA 'UEP\local-native'
$StateFile = Join-Path $StateDir 'state.json'
$Processes = New-Object System.Collections.Generic.List[object]
$PostgresServiceStartedByScript = $false
$PostgresServiceName = $null
$Psql = $null
$PgIsReady = $null
$MavenCmd = $null
$KeycloakHome = $null
$State = $null

function Show-RequirementInventory {
    Write-Section 'Requirement inventory — before installation or startup'
    $requiredFiles = @(
        @{ Name='Frontend package'; Path=(Join-Path $WebDir 'package.json') },
        @{ Name='Frontend lockfile'; Path=(Join-Path $WebDir 'package-lock.json') },
        @{ Name='Frontend Vite entry'; Path=(Join-Path $WebDir 'vite.config.ts') },
        @{ Name='Backend Maven project'; Path=(Join-Path $ApiDir 'pom.xml') },
        @{ Name='Backend application'; Path=(Join-Path $ApiDir 'src\main\java\com\universalplatform\PlatformServerApplication.java') },
        @{ Name='Database migration runner'; Path=(Join-Path $ApiDir 'src\main\java\com\universalplatform\DatabaseMigrationRunner.java') },
        @{ Name='Database migrations'; Path=(Join-Path $ApiDir 'src\main\resources\db\migration') },
        @{ Name='Keycloak local realm'; Path=(Join-Path $RepoRoot 'infra\keycloak\education-realm.json') }
    )
    foreach ($item in $requiredFiles) {
        if (Test-Path -LiteralPath $item.Path) {
            Write-Status $item.Name 'PRESENT' Green
        } else {
            Write-Status $item.Name 'MISSING' Red
            throw "Required UEP project component is missing: $($item.Path)"
        }
    }

    $node = Get-Command 'node.exe' -ErrorAction SilentlyContinue
    $npm = Get-Command 'npm.cmd' -ErrorAction SilentlyContinue
    Write-Status 'Node.js' $(if ($node) { 'PRESENT' } else { 'MISSING — installer will be offered' }) $(if ($node) { 'Green' } else { 'Yellow' })
    Write-Status 'npm' $(if ($npm) { 'PRESENT' } else { 'MISSING — installer will be offered' }) $(if ($npm) { 'Green' } else { 'Yellow' })

    if ($Mode -ne 'Demo') {
        $java = Find-Java
        $maven = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
        Find-PostgresTools
        $pgService = Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^postgresql-x64-\d+' } | Sort-Object Name -Descending | Select-Object -First 1
        $keycloak = Join-Path $ToolDir 'keycloak-26.7.0\bin\kc.bat'
        Write-Status 'JDK 21+' $(if ($java) { 'PRESENT' } else { 'MISSING — installer will be offered' }) $(if ($java) { 'Green' } else { 'Yellow' })
        Write-Status 'Maven 3.9+' $(if ($maven -or (Test-Path (Join-Path $ToolDir 'apache-maven-3.10.0\bin\mvn.cmd'))) { 'PRESENT' } else { 'MISSING — download will be offered' }) $(if ($maven -or (Test-Path (Join-Path $ToolDir 'apache-maven-3.10.0\bin\mvn.cmd'))) { 'Green' } else { 'Yellow' })
        Write-Status 'PostgreSQL tools' $(if ($script:Psql) { 'PRESENT' } else { 'MISSING — installer will be offered' }) $(if ($script:Psql) { 'Green' } else { 'Yellow' })
        Write-Status 'PostgreSQL service' $(if ($pgService) { $pgService.Status.ToString() } else { 'MISSING — install PostgreSQL service' }) $(if ($pgService -and $pgService.Status -eq 'Running') { 'Green' } else { 'Yellow' })
        Write-Status 'Keycloak 26.7' $(if (Test-Path $keycloak) { 'PRESENT' } else { 'MISSING — verified download will be performed' }) $(if (Test-Path $keycloak) { 'Green' } else { 'Yellow' })
        Write-Status 'Local state' $(if (Test-Path $StateFile) { 'PRESENT — existing local credentials/database identity will be reused' } else { 'NOT INITIALIZED — first-run bootstrap required' }) $(if (Test-Path $StateFile) { 'Green' } else { 'Yellow' })
        Write-Status 'Frontend node_modules' $(if (Test-Path (Join-Path $WebDir 'node_modules')) { 'PRESENT — npm ci will still verify lockfile' } else { 'MISSING — npm ci will install it' }) $(if (Test-Path (Join-Path $WebDir 'node_modules')) { 'Green' } else { 'Yellow' })
    }
}

function Write-Section([string]$Message) {
    Write-Host ''
    Write-Host ('=' * 76) -ForegroundColor DarkCyan
    Write-Host (' ' + $Message) -ForegroundColor Cyan
    Write-Host ('=' * 76) -ForegroundColor DarkCyan
}
function Write-Status([string]$Name, [string]$Status, [ConsoleColor]$Color = [ConsoleColor]::Gray) {
    Write-Host ('[{0,-13}] {1}' -f $Name, $Status) -ForegroundColor $Color
}
function Protect-Directory([string]$Path) {
    if (-not (Test-Path $Path)) { New-Item -ItemType Directory -Path $Path -Force | Out-Null }
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $acl = New-Object System.Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    $acl.SetOwner((New-Object System.Security.Principal.NTAccount($identity)))
    $inheritance = [System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [System.Security.AccessControl.InheritanceFlags]::ObjectInherit
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule($identity, [System.Security.AccessControl.FileSystemRights]::FullControl, $inheritance, [System.Security.AccessControl.PropagationFlags]::None, [System.Security.AccessControl.AccessControlType]::Allow)
    $acl.AddAccessRule($rule)
    Set-Acl -Path $Path -AclObject $acl
}
function New-RandomSecret([int]$Bytes = 32) {
    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($buffer).TrimEnd('=').Replace('+', 'A').Replace('/', 'B')
}
function Save-State {
    Protect-Directory $StateDir
    $State | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $StateFile -Encoding UTF8
}
function Test-Command([string]$Name) { return [bool](Get-Command $Name -ErrorAction SilentlyContinue) }
function Refresh-Path {
    $env:Path = @([Environment]::GetEnvironmentVariable('Path', 'Machine'), [Environment]::GetEnvironmentVariable('Path', 'User')) -join ';'
}
function Invoke-WingetInstall([string]$PackageId, [string]$DisplayName, [switch]$Interactive) {
    if (-not (Test-Command 'winget.exe')) { throw "$DisplayName is missing and winget.exe is not available. Install Microsoft App Installer, then rerun." }
    Write-Host "Installing $DisplayName from the official winget source..." -ForegroundColor Yellow
    $args = @('install', '--id', $PackageId, '--exact', '--source', 'winget', '--accept-source-agreements', '--accept-package-agreements')
    if ($Interactive) { $args += '--interactive' } else { $args += @('--silent', '--disable-interactivity') }
    & winget.exe @args
    if ($LASTEXITCODE -ne 0) { throw "winget failed to install $DisplayName (exit code $LASTEXITCODE)." }
    Refresh-Path
}
function Find-Java {
    $cmd = Get-Command 'java.exe' -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($root in @('C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft', 'C:\Program Files\Java')) {
        if (Test-Path $root) {
            $found = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending |
                ForEach-Object { Join-Path $_.FullName 'bin\java.exe' } | Where-Object { Test-Path $_ } | Select-Object -First 1
            if ($found) { return $found }
        }
    }
    return $null
}
function Ensure-Java {
    $java = Find-Java
    if (-not $java) { Invoke-WingetInstall 'EclipseAdoptium.Temurin.21.JDK' 'Eclipse Temurin JDK 21'; $java = Find-Java }
    if (-not $java) { throw 'Java 21 or later was not found after installation.' }
    $text = (& $java -version 2>&1 | Out-String)
    $match = [regex]::Match($text, 'version "?(?<major>\d+)')
    if (-not $match.Success) { $match = [regex]::Match($text, 'openjdk (?<major>\d+)') }
    if (-not $match.Success -or [int]$match.Groups['major'].Value -lt 21) { throw "UEP requires JDK 21 or later. Detected: $text" }
    $script:JavaHome = Split-Path (Split-Path $java -Parent) -Parent
    $env:JAVA_HOME = $script:JavaHome
    $env:Path = (Join-Path $script:JavaHome 'bin') + ';' + $env:Path
    Write-Status 'Java' 'OK — JDK 21+' Green
}
function Ensure-Node {
    $node = Get-Command 'node.exe' -ErrorAction SilentlyContinue
    if (-not $node) {
        Invoke-WingetInstall 'OpenJS.NodeJS.LTS' 'Node.js LTS'
        $node = Get-Command 'node.exe' -ErrorAction SilentlyContinue
        if (-not $node -and (Test-Path 'C:\Program Files\nodejs\node.exe')) {
            $env:Path = 'C:\Program Files\nodejs;' + $env:Path
            $node = Get-Command 'node.exe' -ErrorAction SilentlyContinue
        }
    }
    if (-not $node) { throw 'Node.js 22.22.0 or later is required. Install Node.js LTS and reopen PowerShell.' }
    $version = (& $node.Source --version).Trim().TrimStart('v')
    if ([version]$version -lt [version]'22.22.0') { throw "Node.js 22.22.0 or later is required. Detected $version." }
    $npm = Get-Command 'npm.cmd' -ErrorAction SilentlyContinue
    if (-not $npm) { throw 'npm.cmd was not found beside Node.js. Repair the Node.js installation.' }
    $script:NpmCmd = $npm.Source
    Write-Status 'Node.js' ("OK — " + $version) Green
}
function Ensure-Maven {
    $localMaven = Join-Path $ToolDir 'apache-maven-3.10.0\bin\mvn.cmd'
    $globalMaven = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
    if (Test-Path $localMaven) { $script:MavenCmd = $localMaven }
    elseif ($globalMaven) { $script:MavenCmd = $globalMaven.Source }
    else {
        Protect-Directory $ToolDir
        $zip = Join-Path $ToolDir 'apache-maven-3.10.0-bin.zip'
        $url = 'https://dlcdn.apache.org/maven/maven-3/3.10.0/binaries/apache-maven-3.10.0-bin.zip'
        Write-Host 'Downloading Apache Maven 3.10.0...' -ForegroundColor Yellow
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
        $hashText = (Invoke-WebRequest -Uri ($url + '.sha512') -UseBasicParsing).Content
        $expected = [regex]::Match($hashText, '[A-Fa-f0-9]{128}').Value.ToLowerInvariant()
        $actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA512).Hash.ToLowerInvariant()
        if (-not $expected -or $actual -ne $expected) { Remove-Item $zip -Force; throw 'Apache Maven SHA-512 verification failed.' }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $ToolDir)
        $script:MavenCmd = $localMaven
    }
    if (-not (Test-Path $script:MavenCmd)) { throw 'Maven 3.9+ could not be located.' }
    $mavenLine = (& $script:MavenCmd -version 2>&1 | Select-Object -First 1).ToString()
    $mavenMatch = [regex]::Match($mavenLine, 'Apache Maven (?<version>\d+\.\d+\.\d+)')
    if (-not $mavenMatch.Success -or [version]$mavenMatch.Groups['version'].Value -lt [version]'3.9.0') {
        throw "Maven 3.9 or later is required. Detected: $mavenLine"
    }
    Write-Status 'Maven' ("OK — " + $mavenLine) Green
}
function Find-PostgresTools {
    $cmd = Get-Command 'psql.exe' -ErrorAction SilentlyContinue
    if ($cmd) {
        $script:Psql = $cmd.Source
        $script:PgIsReady = Join-Path (Split-Path $cmd.Source -Parent) 'pg_isready.exe'
        return
    }
    $root = 'C:\Program Files\PostgreSQL'
    if (Test-Path $root) {
        $found = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin\psql.exe' } | Where-Object { Test-Path $_ } | Select-Object -First 1
        if ($found) {
            $script:Psql = $found
            $script:PgIsReady = Join-Path (Split-Path $found -Parent) 'pg_isready.exe'
            $env:Path = (Split-Path $found -Parent) + ';' + $env:Path
        }
    }
}
function Ensure-Postgres {
    Find-PostgresTools
    if (-not $script:Psql) {
        Write-Host 'Installing PostgreSQL 18.4. The official EDB installer opens; set the postgres superuser password when prompted.' -ForegroundColor Yellow
        Invoke-WingetInstall 'PostgreSQL.PostgreSQL.18' 'PostgreSQL 18.4' -Interactive
        Find-PostgresTools
    }
    if (-not $script:Psql -or -not (Test-Path $script:PgIsReady)) {
        throw 'PostgreSQL client tools were not found. Install PostgreSQL 17 or 18 from https://www.postgresql.org/download/windows/ and rerun.'
    }
    $clientVersionLine = (& $script:Psql --version 2>&1 | Out-String).Trim()
    $clientVersionMatch = [regex]::Match($clientVersionLine, 'PostgreSQL\) (?<major>\d+)')
    if (-not $clientVersionMatch.Success -or [int]$clientVersionMatch.Groups['major'].Value -notin @(17, 18)) {
        throw "PostgreSQL client 17 or 18 is required. Detected: $clientVersionLine. Install PostgreSQL 18.4 side-by-side; do not upgrade/delete an existing database cluster automatically."
    }
    $service = Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^postgresql-x64-\d+' } | Sort-Object Name -Descending | Select-Object -First 1
    if ($service) {
        $script:PostgresServiceName = $service.Name
        if ($service.Status -ne 'Running') {
            try { Start-Service -Name $service.Name; $script:PostgresServiceStartedByScript = $true }
            catch { throw "PostgreSQL service '$($service.Name)' could not be started. Re-run PowerShell as Administrator. $($_.Exception.Message)" }
        }
    }
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        & $script:PgIsReady -h 127.0.0.1 -p 5432 -t 2 *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'PostgreSQL is not accepting connections on 127.0.0.1:5432.' }
    Write-Status 'PostgreSQL' 'OK — localhost:5432' Green
}
function Ensure-Keycloak {
    $home = Join-Path $ToolDir 'keycloak-26.7.0'
    $kcBat = Join-Path $home 'bin\kc.bat'
    if (-not (Test-Path $kcBat)) {
        Protect-Directory $ToolDir
        $zip = Join-Path $ToolDir 'keycloak-26.7.0.zip'
        $url = 'https://github.com/keycloak/keycloak/releases/download/26.7.0/keycloak-26.7.0.zip'
        $expected = 'e63bd0167199c0092b8a4d22cc137e6b7a70e0089070f6f7799b1be504b69a8a'
        Write-Host 'Downloading Keycloak 26.7.0...' -ForegroundColor Yellow
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
        $actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $expected) { Remove-Item $zip -Force; throw 'Keycloak SHA-256 verification failed.' }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $ToolDir)
    }
    if (-not (Test-Path $kcBat)) { throw 'Keycloak 26.7.0 could not be extracted.' }
    $realmSource = Join-Path $RepoRoot 'infra\keycloak\education-realm.json'
    if (-not (Test-Path $realmSource)) { throw 'The tracked education Keycloak realm file is missing.' }
    $importDir = Join-Path $home 'data\import'
    New-Item -ItemType Directory -Path $importDir -Force | Out-Null
    Copy-Item -LiteralPath $realmSource -Destination (Join-Path $importDir 'education-realm.json') -Force
    $script:KeycloakHome = $home
    Write-Status 'Keycloak' 'OK — 26.7.0, realm import prepared' Green
}
function Invoke-Psql([string]$User, [string]$Password, [string]$Database, [string]$Sql, [string]$File = '') {
    $previous = $env:PGPASSWORD
    $env:PGPASSWORD = $Password
    try {
        if ($File) { & $script:Psql -h 127.0.0.1 -p 5432 -U $User -d $Database -v ON_ERROR_STOP=1 -f $File }
        else { & $script:Psql -h 127.0.0.1 -p 5432 -U $User -d $Database -v ON_ERROR_STOP=1 -tA -c $Sql }
        if ($LASTEXITCODE -ne 0) { throw "PostgreSQL command failed for database '$Database' as user '$User'." }
    } finally { $env:PGPASSWORD = $previous }
}
function Initialize-LocalState {
    Protect-Directory $StateDir
    if (Test-Path $StateFile) {
        $script:State = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
        if (-not $State.TestAccounts) { $State | Add-Member -NotePropertyName TestAccounts -NotePropertyValue @() -Force }
        return
    }
    $script:State = [pscustomobject]@{
        DatabaseName = 'uep_local_native'
        MigrationUser = 'uep_local_migrator'
        RuntimeUser = 'uep_local_runtime'
        KeycloakUser = 'uep_local_keycloak'
        MigrationPassword = New-RandomSecret
        RuntimePassword = New-RandomSecret
        KeycloakPassword = New-RandomSecret
        KeycloakAdminUser = 'uep-local-bootstrap-admin'
        KeycloakAdminPassword = New-RandomSecret
        LocalAdminUsername = 'uep-local-admin'
        LocalAdminPassword = New-RandomSecret
        LocalBootstrapClient = 'uep-local-bootstrap'
        TenantId = ''
        LocalAdminUserId = ''
        TestAccounts = @()
    }
    Save-State
}
function Assert-PostgresServerVersion([string]$User, [string]$Password, [string]$Database = 'postgres') {
    $serverVersionNumber = Invoke-Psql $User $Password $Database 'SHOW server_version_num'
    $parsedNumber = 0
    if (-not [int]::TryParse($serverVersionNumber.Trim(), [ref]$parsedNumber)) {
        throw 'Could not determine the PostgreSQL server version; refusing to initialize the local database.'
    }
    $major = [int][Math]::Floor($parsedNumber / 10000)
    if ($major -notin @(17, 18)) {
        throw "UEP local qualification supports PostgreSQL 17 or 18. The connected local server reports major version $major. No migrations were run."
    }
    Write-Status 'PostgreSQL' ("Server version " + $major + " verified") Green
}
function Initialize-LocalDatabase {
    $migrationCheck = $false
    try {
        $result = Invoke-Psql $State.MigrationUser $State.MigrationPassword $State.DatabaseName 'SELECT current_database()'
        $migrationCheck = [bool]$result
    } catch { $migrationCheck = $false }
    if ($migrationCheck) {
        Assert-PostgresServerVersion $State.MigrationUser $State.MigrationPassword $State.DatabaseName
    }
    if (-not $migrationCheck) {
        Write-Host 'Enter the PostgreSQL superuser password set during installation. It is not stored.' -ForegroundColor Yellow
        $secure = Read-Host 'PostgreSQL password for user postgres' -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $adminPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
        Assert-PostgresServerVersion 'postgres' $adminPassword
        $rolesFile = Join-Path $LocalDir 'bootstrap-local-roles.sql'
        $roleSql = @'
DO $uep_local_roles$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '__MIGRATION_USER__') THEN
    EXECUTE format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD %L', '__MIGRATION_USER__', '__MIGRATION_PASSWORD__');
  ELSE
    EXECUTE format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD %L', '__MIGRATION_USER__', '__MIGRATION_PASSWORD__');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '__RUNTIME_USER__') THEN
    EXECUTE format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD %L', '__RUNTIME_USER__', '__RUNTIME_PASSWORD__');
  ELSE
    EXECUTE format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD %L', '__RUNTIME_USER__', '__RUNTIME_PASSWORD__');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '__KEYCLOAK_USER__') THEN
    EXECUTE format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD %L', '__KEYCLOAK_USER__', '__KEYCLOAK_PASSWORD__');
  ELSE
    EXECUTE format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD %L', '__KEYCLOAK_USER__', '__KEYCLOAK_PASSWORD__');
  END IF;
END
$uep_local_roles$;
'@
        $roleSql = $roleSql.Replace('__MIGRATION_USER__', $State.MigrationUser).Replace('__MIGRATION_PASSWORD__', $State.MigrationPassword)
        $roleSql = $roleSql.Replace('__RUNTIME_USER__', $State.RuntimeUser).Replace('__RUNTIME_PASSWORD__', $State.RuntimePassword)
        $roleSql = $roleSql.Replace('__KEYCLOAK_USER__', $State.KeycloakUser).Replace('__KEYCLOAK_PASSWORD__', $State.KeycloakPassword)
        $roleSql | Set-Content -LiteralPath $rolesFile -Encoding ASCII
        Protect-Directory $LocalDir
        Invoke-Psql 'postgres' $adminPassword 'postgres' '' $rolesFile
        $exists = Invoke-Psql 'postgres' $adminPassword 'postgres' "SELECT 1 FROM pg_database WHERE datname = '$($State.DatabaseName)'"
        if (-not $exists) { Invoke-Psql 'postgres' $adminPassword 'postgres' ("CREATE DATABASE " + $State.DatabaseName + " OWNER " + $State.MigrationUser) }
        Invoke-Psql 'postgres' $adminPassword $State.DatabaseName ("GRANT CONNECT ON DATABASE " + $State.DatabaseName + " TO " + $State.RuntimeUser + ", " + $State.KeycloakUser)
        $schemaOwner = Invoke-Psql 'postgres' $adminPassword $State.DatabaseName "SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = 'keycloak'"
        if (-not $schemaOwner) {
            Invoke-Psql 'postgres' $adminPassword $State.DatabaseName ("CREATE SCHEMA keycloak AUTHORIZATION " + $State.KeycloakUser)
        } elseif ($schemaOwner.Trim() -ne $State.KeycloakUser) {
            throw "The local keycloak schema already exists but is owned by '$schemaOwner'. No ownership changes were made."
        }
        Save-State
        $adminPassword = $null
        Remove-Item $rolesFile -Force -ErrorAction SilentlyContinue
    }
    $migrationUrl = "jdbc:postgresql://127.0.0.1:5432/$($State.DatabaseName)?sslmode=disable"
    $previous = @{
        UEP_DATABASE_MIGRATION_JDBC_URL = $env:UEP_DATABASE_MIGRATION_JDBC_URL
        UEP_DATABASE_MIGRATION_USERNAME = $env:UEP_DATABASE_MIGRATION_USERNAME
        UEP_DATABASE_MIGRATION_PASSWORD = $env:UEP_DATABASE_MIGRATION_PASSWORD
        UEP_DATABASE_RUNTIME_USERNAME = $env:UEP_DATABASE_RUNTIME_USERNAME
    }
    try {
        $env:UEP_DATABASE_MIGRATION_JDBC_URL = $migrationUrl
        $env:UEP_DATABASE_MIGRATION_USERNAME = $State.MigrationUser
        $env:UEP_DATABASE_MIGRATION_PASSWORD = $State.MigrationPassword
        $env:UEP_DATABASE_RUNTIME_USERNAME = $State.RuntimeUser
        Write-Host 'Applying tracked Flyway migrations to the dedicated local database...' -ForegroundColor Yellow
        Push-Location $RepoRoot
        try {
            & $script:MavenCmd -B -f (Join-Path $ApiDir 'pom.xml') org.codehaus.mojo:exec-maven-plugin:3.5.0:java
            if ($LASTEXITCODE -ne 0) { throw "Local database migration failed (Maven exit code $LASTEXITCODE)." }
        } finally { Pop-Location }
    } finally {
        foreach ($key in $previous.Keys) {
            if ($null -eq $previous[$key]) {
                Remove-Item -Path ("Env:" + $key) -ErrorAction SilentlyContinue
            } else {
                Set-Item -Path ("Env:" + $key) -Value $previous[$key]
            }
        }
    }
    $dbResult = Invoke-Psql $State.RuntimeUser $State.RuntimePassword $State.DatabaseName "SELECT current_database() || '|' || current_user || '|' || count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'"
    Write-Status 'Database' ("OK — " + $dbResult.Trim() + ' (database|runtime-role|public-table-count)') Green
}

function Start-ManagedProcess([string]$Name, [string]$CommandFile, [string]$WorkingDirectory) {
    $stdout = Join-Path $LogDir ($Name + '.log')
    $stderr = Join-Path $LogDir ($Name + '.err.log')
    $proc = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/c', ('"' + $CommandFile + '"')) -WorkingDirectory $WorkingDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $entry = [pscustomobject]@{ Name = $Name; ProcessId = $proc.Id; Log = $stdout; ErrorLog = $stderr }
    $script:Processes.Add($entry)
    Write-Status $Name ("started (PID " + $proc.Id + ')') Yellow
    return $entry
}
function Write-CommandFile([string]$Name, [string[]]$Lines) {
    $path = Join-Path $LocalDir ($Name + '.cmd')
    $Lines | Set-Content -LiteralPath $path -Encoding ASCII
    return $path
}
function Wait-ForHttp([string]$Url, [int]$TimeoutSeconds = 120, [string]$Name = 'service') {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 4
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) { return $true }
        } catch {}
        Start-Sleep -Seconds 2
    }
    foreach ($entry in $script:Processes) {
        if ($entry.Name -eq $Name -and (Test-Path $entry.Log)) {
            Write-Host ("Last log lines for " + $Name + ":") -ForegroundColor Red
            Get-Content $entry.Log -Tail 80
            if (Test-Path $entry.ErrorLog) { Get-Content $entry.ErrorLog -Tail 40 }
        }
    }
    return $false
}
function Get-KeycloakAdminToken {
    $body = @{ grant_type = 'password'; client_id = 'admin-cli'; username = $State.KeycloakAdminUser; password = $State.KeycloakAdminPassword }
    $response = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8081/realms/master/protocol/openid-connect/token' -ContentType 'application/x-www-form-urlencoded' -Body $body
    return $response.access_token
}
function Invoke-KeycloakAdmin([string]$Method, [string]$Path, [string]$Token, $Body = $null) {
    $params = @{ Method = $Method; Uri = ('http://127.0.0.1:8081' + $Path); Headers = @{ Authorization = "Bearer $Token" }; TimeoutSec = 20 }
    if ($null -ne $Body) { $params.ContentType = 'application/json'; $params.Body = ConvertTo-Json -InputObject $Body -Depth 20 }
    return Invoke-RestMethod @params
}
function Get-LocalUser([string]$Username, [string]$AdminToken) {
    $items = Invoke-KeycloakAdmin 'Get' ("/admin/realms/education/users?username=$([uri]::EscapeDataString($Username))&exact=true") $AdminToken
    if ($items -and $items.Count -gt 0) { return $items[0] }
    return $null
}
function Ensure-KeycloakUser([string]$Username, [string]$Email, [string]$FirstName, [string]$LastName, [string]$Password, [string]$TenantId, [string]$AdminToken) {
    $user = Get-LocalUser $Username $AdminToken
    if (-not $user) {
        $representation = @{ username = $Username; email = $Email; emailVerified = $true; enabled = $true; firstName = $FirstName; lastName = $LastName; attributes = @{ tenant_id = @($TenantId) } }
        Invoke-KeycloakAdmin 'Post' '/admin/realms/education/users' $AdminToken $representation | Out-Null
        $user = Get-LocalUser $Username $AdminToken
        if (-not $user) { throw "Keycloak did not create the local test user '$Username'." }
    } else {
        if (-not $user.attributes) { $user | Add-Member -NotePropertyName attributes -NotePropertyValue @{} -Force }
        if (-not $user.attributes.PSObject.Properties.Match('tenant_id').Count) { $user.attributes | Add-Member -NotePropertyName tenant_id -NotePropertyValue @() -Force }
        $user.attributes.tenant_id = @($TenantId)
        $user.enabled = $true
        Invoke-KeycloakAdmin 'Put' ("/admin/realms/education/users/" + $user.id) $AdminToken $user | Out-Null
    }
    $passwordBody = @{ type = 'password'; value = $Password; temporary = $false }
    Invoke-KeycloakAdmin 'Put' ("/admin/realms/education/users/" + $user.id + '/reset-password') $AdminToken $passwordBody | Out-Null
    return $user
}
function Get-UserToken([string]$Username, [string]$Password) {
    $body = @{ grant_type = 'password'; client_id = $State.LocalBootstrapClient; username = $Username; password = $Password; scope = 'openid profile email' }
    $response = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8081/realms/education/protocol/openid-connect/token' -ContentType 'application/x-www-form-urlencoded' -Body $body
    return $response.access_token
}
function Invoke-Api([string]$Method, [string]$Path, [string]$Token, $Body = $null) {
    $params = @{ Method = $Method; Uri = ('http://127.0.0.1:8080' + $Path); Headers = @{ Authorization = "Bearer $Token"; Accept = 'application/json' }; TimeoutSec = 30 }
    if ($null -ne $Body) { $params.ContentType = 'application/json'; $params.Body = ConvertTo-Json -InputObject $Body -Depth 20 }
    return Invoke-RestMethod @params
}
function Ensure-RealIdentity {
    Write-Section 'Keycloak local realm and owner bootstrap'
    $adminToken = Get-KeycloakAdminToken
    $clients = Invoke-KeycloakAdmin 'Get' '/admin/realms/education/clients?clientId=uep-local-bootstrap' $adminToken
    if (-not $clients -or $clients.Count -eq 0) {
        $client = @{ clientId = $State.LocalBootstrapClient; name = 'Local-only UEP bootstrap helper'; enabled = $true; publicClient = $true; standardFlowEnabled = $false; implicitFlowEnabled = $false; directAccessGrantsEnabled = $true; serviceAccountsEnabled = $false; protocol = 'openid-connect' }
        Invoke-KeycloakAdmin 'Post' '/admin/realms/education/clients' $adminToken $client | Out-Null
    }
    $realmRole = Invoke-KeycloakAdmin 'Get' '/admin/realms/education/roles/platform_super_admin' $adminToken
    $localAdmin = Ensure-KeycloakUser $State.LocalAdminUsername 'admin@local.uep' 'UEP' 'Local Administrator' $State.LocalAdminPassword $State.TenantId $adminToken
    $State.LocalAdminUserId = $localAdmin.id
    $mappings = Invoke-KeycloakAdmin 'Get' ("/admin/realms/education/users/" + $localAdmin.id + '/role-mappings/realm') $adminToken
    if (-not ($mappings | Where-Object { $_.name -eq 'platform_super_admin' })) {
        Invoke-KeycloakAdmin 'Post' ("/admin/realms/education/users/" + $localAdmin.id + '/role-mappings/realm') $adminToken @($realmRole) | Out-Null
    }
    Save-State
    $localAdminToken = Get-UserToken $State.LocalAdminUsername $State.LocalAdminPassword
    if (-not $State.TenantId) {
        $tenant = Invoke-Api 'Post' '/api/v1/tenants' $localAdminToken @{ slug = 'uep-local'; displayName = 'UEP Local Test Institution'; subscriptionTier = 'free' }
        $State.TenantId = [string]$tenant.id
        # Mark the tenant record as a local development environment, not a hosted Fly deployment.
        Invoke-Api 'Patch' ("/api/v1/tenants/" + $State.TenantId + '/deployment?platform=local&status=active') $localAdminToken | Out-Null
        Save-State
    }
    $localAdmin = Ensure-KeycloakUser $State.LocalAdminUsername 'admin@local.uep' 'UEP' 'Local Administrator' $State.LocalAdminPassword $State.TenantId $adminToken
    $State.LocalAdminUserId = $localAdmin.id
    Save-State
    $localAdminToken = Get-UserToken $State.LocalAdminUsername $State.LocalAdminPassword
    $current = $null
    try { $current = Invoke-Api 'Get' '/api/v1/me' $localAdminToken } catch {}
    if (-not $current) {
        Invoke-Api 'Post' '/api/v1/identity/bootstrap-owner' $localAdminToken @{ subject = $localAdmin.id; email = 'admin@local.uep'; displayName = 'UEP Local Administrator'; roleIds = @() } | Out-Null
        $localAdminToken = Get-UserToken $State.LocalAdminUsername $State.LocalAdminPassword
        $current = Invoke-Api 'Get' '/api/v1/me' $localAdminToken
    }
    Write-Status 'Identity' ("OK — Keycloak OIDC, tenant " + $State.TenantId + ', owner membership active') Green
    if (-not $SkipAccountSeeding) { Ensure-LocalTestAccounts $localAdminToken $adminToken }
    Save-State
}
function Ensure-LocalTestAccounts([string]$OwnerToken, [string]$AdminToken) {
    $rolePage = Invoke-Api 'Get' '/api/v1/identity/roles?size=100' $OwnerToken
    $roles = @{}
    foreach ($role in $rolePage.items) { if ($role.systemKey) { $roles[[string]$role.systemKey] = $role } }
    $developerRole = $rolePage.items | Where-Object { $_.name -eq 'Product Developer (Local Test)' } | Select-Object -First 1
    if (-not $developerRole) {
        $developerRole = Invoke-Api 'Post' '/api/v1/identity/roles' $OwnerToken @{
            name = 'Product Developer (Local Test)'
            description = 'Read-only local testing persona for product and integration operations.'
            permissions = @('API_VIEW', 'WEBHOOKS_VIEW', 'OPERATIONS_VIEW', 'AUDIT_VIEW', 'INTEROPERABILITY_VIEW', 'FEDERATION_VIEW')
            expectedVersion = 0
        }
    }

    $branches = Invoke-Api 'Get' '/api/v1/organization/branches?size=100' $OwnerToken
    $testBranch = $branches.items | Where-Object { $_.code -eq 'NATIVE-LOCAL' } | Select-Object -First 1
    if (-not $testBranch) {
        $testBranch = Invoke-Api 'Post' '/api/v1/organization/branches' $OwnerToken @{
            code = 'NATIVE-LOCAL'
            displayName = 'UEP Native Local Test Branch'
            timezone = 'Asia/Kolkata'
        }
    }

    $existingMemberships = Invoke-Api 'Get' '/api/v1/identity/memberships?size=100' $OwnerToken
    $personas = @(
        @{ Key='student'; Username='uep-student'; Email='student@local.uep'; Name='Asha Sharma'; Role='STUDENT' },
        @{ Key='teacher'; Username='uep-teacher'; Email='teacher@local.uep'; Name='Rahul Deshmukh'; Role='TEACHER' },
        @{ Key='client'; Username='uep-client'; Email='client@local.uep'; Name='Priya Kulkarni'; Role='ORGANIZATION_ADMINISTRATOR' },
        @{ Key='developer'; Username='uep-developer'; Email='developer@local.uep'; Name='Dev Patel'; Role='CUSTOM' },
        @{ Key='admin'; Username='uep-branch-admin'; Email='branch-admin@local.uep'; Name='Morgan Admin'; Role='BRANCH_ADMINISTRATOR' },
        @{ Key='guardian'; Username='uep-guardian'; Email='guardian@local.uep'; Name='Nina Sharma'; Role='GUARDIAN' },
        @{ Key='exam-controller'; Username='uep-exam-controller'; Email='exam-controller@local.uep'; Name='Aarav Shah'; Role='EXAM_CONTROLLER' },
        @{ Key='finance-admin'; Username='uep-finance-admin'; Email='finance-admin@local.uep'; Name='Anaya Joshi'; Role='FINANCE_ADMINISTRATOR' },
        @{ Key='teaching-assistant'; Username='uep-teaching-assistant'; Email='teaching-assistant@local.uep'; Name='Ishaan Patil'; Role='TEACHING_ASSISTANT' },
        @{ Key='evaluator'; Username='uep-evaluator'; Email='evaluator@local.uep'; Name='Sara Khan'; Role='EVALUATOR' },
        @{ Key='mentor'; Username='uep-mentor'; Email='mentor@local.uep'; Name='Rohan Mehta'; Role='MENTOR' },
        @{ Key='support'; Username='uep-support'; Email='support@local.uep'; Name='Alex Support'; Role='SUPPORT_OPERATOR' },
        @{ Key='auditor'; Username='uep-auditor'; Email='auditor@local.uep'; Name='Jordan Audit'; Role='AUDITOR' }
    )
    foreach ($persona in $personas) {
        $account = $State.TestAccounts | Where-Object { $_.key -eq $persona.Key } | Select-Object -First 1
        if (-not $account) {
            $account = [pscustomobject]@{ key = $persona.Key; username = $persona.Username; email = $persona.Email; password = (New-RandomSecret) }
            $State.TestAccounts = @($State.TestAccounts) + @($account)
            Save-State
        }
        $nameParts = $persona.Name.Split(' ', 2)
        $user = Ensure-KeycloakUser $persona.Username $persona.Email $nameParts[0] $nameParts[-1] $account.password $State.TenantId $AdminToken
        $membership = $existingMemberships.items | Where-Object { $_.oidcSubject -eq $user.id } | Select-Object -First 1

        if ($persona.Role -eq 'CUSTOM') {
            $roleId = $developerRole.id
        } else {
            if (-not $roles.ContainsKey($persona.Role)) {
                throw "Required system role '$($persona.Role)' was not returned by the backend."
            }
            $roleId = $roles[$persona.Role].id
        }

        if (-not $membership) {
            $primaryBranchId = if ($persona.Role -eq 'BRANCH_ADMINISTRATOR') { $testBranch.id } else { $null }
            $initialRoleIds = if ($persona.Role -eq 'BRANCH_ADMINISTRATOR') { @() } else { @($roleId) }
            Invoke-Api 'Post' '/api/v1/identity/memberships' $OwnerToken @{
                subject = $user.id
                email = $persona.Email
                displayName = $persona.Name
                primaryBranchId = $primaryBranchId
                roleIds = $initialRoleIds
            } | Out-Null
            $existingMemberships = Invoke-Api 'Get' '/api/v1/identity/memberships?size=100' $OwnerToken
            $membership = $existingMemberships.items | Where-Object { $_.oidcSubject -eq $user.id } | Select-Object -First 1
        }

        if ($persona.Role -eq 'BRANCH_ADMINISTRATOR') {
            $membership = Invoke-Api 'Get' ("/api/v1/identity/memberships/" + $membership.membershipId) $OwnerToken
            if ($membership.primaryBranchId -ne $testBranch.id) {
                Invoke-Api 'Patch' ("/api/v1/identity/memberships/" + $membership.membershipId) $OwnerToken @{
                    status = $membership.membershipStatus
                    primaryBranchId = $testBranch.id
                    externalReference = $membership.externalReference
                    expectedVersion = $membership.version
                } | Out-Null
            }
            $assignments = Invoke-Api 'Get' ("/api/v1/identity/roles/memberships/" + $membership.membershipId + '?size=100') $OwnerToken
            $correct = $assignments.items | Where-Object {
                $_.roleId -eq $roleId -and $_.scopeKind -eq 'BRANCH' -and $_.scopeId -eq $testBranch.id
            } | Select-Object -First 1
            if (-not $correct) {
                $tenantScoped = $assignments.items | Where-Object {
                    $_.roleId -eq $roleId -and $_.scopeKind -eq 'TENANT'
                }
                foreach ($wrongScope in $tenantScoped) {
                    Invoke-Api 'Delete' ("/api/v1/identity/roles/assignments/" + $wrongScope.id) $OwnerToken | Out-Null
                }
                Invoke-Api 'Post' '/api/v1/identity/roles/assignments' $OwnerToken @{
                    membershipId = $membership.membershipId
                    roleId = $roleId
                    scopeKind = 'BRANCH'
                    scopeId = $testBranch.id
                } | Out-Null
            }
        }
    }
    Save-State
    Write-Status 'Test users' 'OK — 13 tenant-scoped OIDC users; Branch Administrator is branch-scoped' Green
}
function Write-CommandFile([string]$Name, [string[]]$Lines) {
    $path = Join-Path $LocalDir ($Name + '.cmd')
    $Lines | Set-Content -LiteralPath $path -Encoding ASCII
    return $path
}
function Start-ManagedProcess([string]$Name, [string]$CommandFile, [string]$WorkingDirectory) {
    $stdout = Join-Path $LogDir ($Name + '.log')
    $stderr = Join-Path $LogDir ($Name + '.err.log')
    $proc = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/c', ('"' + $CommandFile + '"')) -WorkingDirectory $WorkingDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $entry = [pscustomobject]@{ Name = $Name; ProcessId = $proc.Id; Log = $stdout; ErrorLog = $stderr }
    $script:Processes.Add($entry)
    Write-Status $Name ("started (PID " + $proc.Id + ')') Yellow
    return $entry
}
function Wait-ForHttp([string]$Url, [int]$TimeoutSeconds = 120, [string]$Name = 'service') {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 4
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) { return $true }
        } catch {}
        Start-Sleep -Seconds 2
    }
    foreach ($entry in $script:Processes) {
        if ($entry.Name -eq $Name -and (Test-Path $entry.Log)) {
            Write-Host ("Last log lines for " + $Name + ":") -ForegroundColor Red
            Get-Content $entry.Log -Tail 80
            if (Test-Path $entry.ErrorLog) { Get-Content $entry.ErrorLog -Tail 40 }
        }
    }
    return $false
}
function Test-PortAvailable([int]$Port) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        $process = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
        throw "Port $Port is already in use by '$($process.ProcessName)' (PID $($listener.OwningProcess)). Stop that process before launching UEP."
    }
}
function Stop-ManagedProcesses {
    $entries = @($script:Processes.ToArray())
    [array]::Reverse($entries)
    foreach ($entry in $entries) {
        try { & taskkill.exe /PID $entry.ProcessId /T /F *> $null } catch {}
    }
    if ($script:PostgresServiceStartedByScript -and $script:PostgresServiceName) {
        try {
            Stop-Service -Name $script:PostgresServiceName -ErrorAction Stop
            Write-Status 'PostgreSQL' 'Stopped (the launcher started this Windows service)' Yellow
        } catch { Write-Warning "PostgreSQL could not be stopped automatically: $($_.Exception.Message)" }
    }
    Write-Host 'UEP application processes stopped. Database files and local accounts were preserved.' -ForegroundColor Cyan
}
function Write-RuntimeCommandFiles([switch]$RunReal, [switch]$RunDemo, [int]$DemoPort = 5174) {
    if ($RunReal) {
        $kcBat = Join-Path $KeycloakHome 'bin\kc.bat'
        $kcLines = @(
            '@echo off',
            'set KC_DB=postgres',
            ('set KC_DB_URL=jdbc:postgresql://127.0.0.1:5432/' + $State.DatabaseName),
            'set KC_DB_SCHEMA=keycloak',
            ('set KC_DB_USERNAME=' + $State.KeycloakUser),
            ('set KC_DB_PASSWORD=' + $State.KeycloakPassword),
            ('set KC_BOOTSTRAP_ADMIN_USERNAME=' + $State.KeycloakAdminUser),
            ('set KC_BOOTSTRAP_ADMIN_PASSWORD=' + $State.KeycloakAdminPassword),
            'set KC_HEALTH_ENABLED=true',
            'set KC_HTTP_ENABLED=true',
            'set KC_HOSTNAME=http://localhost:8081',
            ('cd /d "' + $KeycloakHome + '"'),
            ('call "' + $kcBat + '" start-dev --http-port=8081 --import-realm')
        )
        $kcFile = Write-CommandFile 'run-keycloak' $kcLines
        Start-ManagedProcess 'Keycloak' $kcFile $KeycloakHome
        if (-not (Wait-ForHttp 'http://127.0.0.1:8081/realms/education/.well-known/openid-configuration' 180 'Keycloak')) { throw 'Keycloak did not become ready. Inspect .uep-local\logs\Keycloak.log.' }

        $backendLines = @(
            '@echo off',
            ('set SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/' + $State.DatabaseName + '?sslmode=disable'),
            ('set SPRING_DATASOURCE_USERNAME=' + $State.RuntimeUser),
            ('set SPRING_DATASOURCE_PASSWORD=' + $State.RuntimePassword),
            'set SPRING_FLYWAY_ENABLED=false',
            'set PLATFORM_DEV_MODE=true',
            'set OIDC_ISSUER_URI=http://localhost:8081/realms/education',
            'set OIDC_JWK_SET_URI=http://localhost:8081/realms/education/protocol/openid-connect/certs',
            'set PLATFORM_LIVEKIT_ENABLED=false',
            'set PLATFORM_CLAMAV_ENABLED=false',
            'set PLATFORM_BACKGROUND_WORKERS_ENABLED=true',
            'set PLATFORM_UPLOAD_STAGING_ENABLED=true',
            'set DATABASE_POOL_SIZE=10',
            'set PORT=8080',
            ('cd /d "' + $ApiDir + '"'),
            ('call "' + $MavenCmd + '" -B spring-boot:run')
        )
        $apiFile = Write-CommandFile 'run-api' $backendLines
        Start-ManagedProcess 'Backend' $apiFile $ApiDir
        if (-not (Wait-ForHttp 'http://127.0.0.1:8080/actuator/health' 180 'Backend')) { throw 'Backend did not become healthy. Inspect .uep-local\logs\Backend.log.' }
        Write-Status 'Backend' 'OK — actuator health returned HTTP 200' Green
    }

    Push-Location $WebDir
    try {
        Write-Host 'Installing locked frontend dependencies with npm ci --ignore-scripts...' -ForegroundColor Yellow
        & $NpmCmd ci --ignore-scripts
        if ($LASTEXITCODE -ne 0) { throw "npm ci failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }

    if ($RunReal) {
        $frontLines = @(
            '@echo off',
            'set VITE_LOCAL_MODE=false',
            'set VITE_BACKEND_URL=',
            'set VITE_KEYCLOAK_URL=http://localhost:8081',
            'set VITE_KEYCLOAK_REALM=education',
            'set VITE_KEYCLOAK_CLIENT_ID=platform-web',
            ('cd /d "' + $WebDir + '"'),
            ('call "' + $NpmCmd + '" run dev -- --host 127.0.0.1 --port 5173 --strictPort')
        )
        $frontFile = Write-CommandFile 'run-real-frontend' $frontLines
        Start-ManagedProcess 'Real frontend' $frontFile $WebDir
        if (-not (Wait-ForHttp 'http://127.0.0.1:5173' 90 'Real frontend')) { throw 'Real frontend did not become ready. Inspect .uep-local\logs\Real frontend.log.' }
    }
    if ($RunDemo) {
        $frontLines = @(
            '@echo off',
            'set VITE_LOCAL_MODE=true',
            'set VITE_BACKEND_URL=',
            ('cd /d "' + $WebDir + '"'),
            ('call "' + $NpmCmd + '" run dev -- --host 127.0.0.1 --port ' + $DemoPort + ' --strictPort')
        )
        $frontFile = Write-CommandFile 'run-demo-frontend' $frontLines
        Start-ManagedProcess 'Demo frontend' $frontFile $WebDir
        if (-not (Wait-ForHttp ("http://127.0.0.1:" + $DemoPort) 90 'Demo frontend')) { throw 'Demo frontend did not become ready. Inspect .uep-local\logs\Demo frontend.log.' }
    }
}

try {
    Protect-Directory $LocalDir
    Protect-Directory $LogDir
    Write-Section ('UEP Native Windows Launcher — ' + $Mode + ' mode')
    Write-Host 'No Docker, Podman, containers, cloud databases, or paid execution services are used.'
    Write-Host ('Repository: ' + $RepoRoot)
    Write-Host ('Mode: ' + $Mode)
    Show-RequirementInventory
    Ensure-Node

    if ($Mode -ne 'Demo') {
        Ensure-Java
        Ensure-Maven
        Ensure-Postgres
        Ensure-Keycloak
        Test-PortAvailable 8080
        Test-PortAvailable 8081
        Test-PortAvailable 5173
        if ($Mode -eq 'Both') { Test-PortAvailable 5174 }
        Initialize-LocalState
        Initialize-LocalDatabase
        Write-RuntimeCommandFiles -RunReal -RunDemo:($Mode -eq 'Both')
        Ensure-RealIdentity
    } else {
        Test-PortAvailable 5173
        Write-RuntimeCommandFiles -RunDemo -DemoPort 5173
    }

    if ($Mode -eq 'Both') {
        Write-Host ''
        Write-Host 'REAL PROJECT' -ForegroundColor Green
        Write-Host '  Frontend:  http://localhost:5173'
        Write-Host '  Backend:   http://localhost:8080'
        Write-Host '  Keycloak:  http://localhost:8081'
        Write-Host '  Database:  PostgreSQL / uep_local_native / localhost:5432'
        Write-Host 'DEMO (offline fixtures; no API/database requests)' -ForegroundColor Yellow
        Write-Host '  Frontend:  http://localhost:5174'
    } elseif ($Mode -eq 'Real') {
        Write-Host ''
        Write-Host 'REAL PROJECT' -ForegroundColor Green
        Write-Host '  Frontend:  http://localhost:5173'
        Write-Host '  Backend:   http://localhost:8080'
        Write-Host '  Keycloak:  http://localhost:8081'
        Write-Host '  Database:  PostgreSQL / uep_local_native / localhost:5432'
    } else {
        Write-Host ''
        Write-Host 'DEMO MODE' -ForegroundColor Green
        Write-Host '  Frontend:  http://localhost:5173'
        Write-Host '  Backend:   not started (offline fixtures only)'
        Write-Host '  Database:  not started (offline fixtures only)'
    }

    if ($Mode -ne 'Demo') {
        Write-Host ''
        Write-Host 'LOCAL TEST ACCOUNTS' -ForegroundColor Cyan
        Write-Host ('  Admin: ' + $State.LocalAdminUsername + ' (credentials are in the protected LocalAppData UEP folder)')
        if (-not $SkipAccountSeeding) {
            $accountLines = @('UEP local test accounts — localhost only', ('Admin: ' + $State.LocalAdminUsername + ' / ' + $State.LocalAdminPassword))
            foreach ($account in $State.TestAccounts) { $accountLines += ($account.username + ' / ' + $account.password + ' / ' + $account.email) }
            $accountsFile = Join-Path $StateDir 'accounts.txt'
            $accountLines | Set-Content -LiteralPath $accountsFile -Encoding UTF8
            Write-Host ('  Credentials file: ' + $accountsFile)
        }
    }
    if (-not $NoBrowser) {
        if ($Mode -eq 'Both') { Start-Process 'http://localhost:5173'; Start-Process 'http://localhost:5174' }
        else { Start-Process 'http://localhost:5173' }
    }

    Write-Section 'Live service status'
    Write-Host 'Press Ctrl+C to stop application processes. PostgreSQL is stopped only when this script started its Windows service.'
    while ($true) {
        if ($Mode -ne 'Demo') {
            $db = 'DOWN'
            try {
                $dbResult = Invoke-Psql $State.RuntimeUser $State.RuntimePassword $State.DatabaseName 'SELECT 1'
                if ($dbResult.Trim() -eq '1') { $db = 'UP' }
            } catch {}
            $kc = if ((Test-NetConnection 127.0.0.1 -Port 8081 -WarningAction SilentlyContinue).TcpTestSucceeded) { 'LISTENING' } else { 'DOWN' }
            $api = 'DOWN'
            try { $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 3; $api = [string]$health.status } catch {}
            Write-Status 'Database' $db $(if ($db -eq 'UP') { 'Green' } else { 'Red' })
            Write-Status 'Keycloak' $kc $(if ($kc -eq 'LISTENING') { 'Green' } else { 'Red' })
            Write-Status 'Backend' $api $(if ($api -eq 'UP') { 'Green' } else { 'Red' })
        } else {
            Write-Status 'Database' 'NOT STARTED — offline Demo' Yellow
            Write-Status 'Backend' 'NOT STARTED — offline Demo' Yellow
        }
        if ($Mode -eq 'Both') {
            $real = if ((Test-NetConnection 127.0.0.1 -Port 5173 -WarningAction SilentlyContinue).TcpTestSucceeded) { 'LISTENING' } else { 'DOWN' }
            $demo = if ((Test-NetConnection 127.0.0.1 -Port 5174 -WarningAction SilentlyContinue).TcpTestSucceeded) { 'LISTENING' } else { 'DOWN' }
            Write-Status 'Real UI' $real $(if ($real -eq 'LISTENING') { 'Green' } else { 'Red' })
            Write-Status 'Demo UI' $demo $(if ($demo -eq 'LISTENING') { 'Green' } else { 'Red' })
        } else {
            $front = if ((Test-NetConnection 127.0.0.1 -Port 5173 -WarningAction SilentlyContinue).TcpTestSucceeded) { 'LISTENING' } else { 'DOWN' }
            Write-Status 'Frontend' $front $(if ($front -eq 'LISTENING') { 'Green' } else { 'Red' })
        }
        Write-Host ('Updated: ' + (Get-Date -Format 'HH:mm:ss'))
        Start-Sleep -Seconds 8
    }
} catch {
    Write-Host ''
    Write-Host ('LAUNCH FAILED: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host 'Inspect .uep-local\logs. No database is dropped or reset by this script.' -ForegroundColor Yellow
    throw
} finally {
    Stop-ManagedProcesses
}
