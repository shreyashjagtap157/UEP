[CmdletBinding()]
param(
    [ValidateSet('Demo', 'Real', 'Both', 'Presentation')]
    [string]$Mode = 'Both',
    [ValidateSet('SQLite', 'PostgreSQL')]
    [string]$Database = 'SQLite',
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
$PostgresClientMajor = $null
$Psql = $null
$PgIsReady = $null
$MavenCmd = $null
$KeycloakHome = $null
$LocalAuthToken = $null
$LocalAuthSessionId = $null
$LocalAuthExpiresAt = $null
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
        @{ Name='SQLite local profile'; Path=(Join-Path $ApiDir 'src\main\resources\application-local-sqlite.yaml') },
        @{ Name='Local native authentication profile'; Path=(Join-Path $ApiDir 'src\main\resources\application-local-native.yaml') },
        @{ Name='Local API documentation configuration'; Path=(Join-Path $ApiDir 'src\main\java\com\universalplatform\LocalApiDocumentationConfiguration.java') },
        @{ Name='Local OIDC decoder guard'; Path=(Join-Path $ApiDir 'src\main\java\com\universalplatform\security\LocalNativeJwtDecoderConfiguration.java') }

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
        if ($Database -eq 'PostgreSQL') {
            Find-PostgresTools
            $pgService = Get-PostgresService
            Write-Status 'PostgreSQL tools' $(if ($script:Psql) { 'PRESENT' } else { 'MISSING — installer will be offered' }) $(if ($script:Psql) { 'Green' } else { 'Yellow' })
            Write-Status 'PostgreSQL service' $(if ($pgService) { $pgService.Status.ToString() } else { 'MISSING — install PostgreSQL service' }) $(if ($pgService -and $pgService.Status -eq 'Running') { 'Green' } else { 'Yellow' })
        } else {
            Write-Status 'SQLite' 'Built into the local qualification profile; no database server required' Green
        }
        Write-Status 'JDK 21+' $(if ($java) { 'PRESENT' } else { 'MISSING — installer will be offered' }) $(if ($java) { 'Green' } else { 'Yellow' })
        Write-Status 'Maven 3.9+' $(if ($maven -or (Test-Path (Join-Path $ToolDir 'apache-maven-3.10.0\bin\mvn.cmd'))) { 'PRESENT' } else { 'MISSING — download will be offered' }) $(if ($maven -or (Test-Path (Join-Path $ToolDir 'apache-maven-3.10.0\bin\mvn.cmd'))) { 'Green' } else { 'Yellow' })
        Write-Status 'Identity' 'Local launcher-issued API session; Keycloak is not started' Green
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
function Protect-File([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $acl = New-Object System.Security.AccessControl.FileSecurity
    $acl.SetAccessRuleProtection($true, $false)
    $acl.SetOwner((New-Object System.Security.Principal.NTAccount($identity)))
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule(
        $identity,
        [System.Security.AccessControl.FileSystemRights]::FullControl,
        [System.Security.AccessControl.AccessControlType]::Allow
    )
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
    if (Test-Path -LiteralPath $StateFile -PathType Leaf) { Protect-File $StateFile }
    $State | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $StateFile -Encoding UTF8
    Protect-File $StateFile
}
function Test-Command([string]$Name) { return [bool](Get-Command $Name -ErrorAction SilentlyContinue) }
function Refresh-Path {
    $env:Path = @([Environment]::GetEnvironmentVariable('Path', 'Machine'), [Environment]::GetEnvironmentVariable('Path', 'User')) -join ';'
}
function Invoke-WingetInstall([string]$PackageId, [string]$DisplayName, [switch]$Interactive) {
    if (-not (Test-Command 'winget.exe')) { throw "$DisplayName is missing and winget.exe is not available. Install Microsoft App Installer from https://aka.ms/getwinget, or install the requirement manually using docs/operations/LOCAL_NATIVE_WINDOWS.md, then rerun." }
    Write-Host "Installing $DisplayName from the official winget source..." -ForegroundColor Yellow
    $wingetArguments = @('install', '--id', $PackageId, '--exact', '--source', 'winget', '--accept-source-agreements', '--accept-package-agreements')
    if ($Interactive) { $wingetArguments += '--interactive' } else { $wingetArguments += @('--silent', '--disable-interactivity') }
    & winget.exe @wingetArguments
    if ($LASTEXITCODE -ne 0) { throw "winget failed to install $DisplayName (exit code $LASTEXITCODE)." }
    Refresh-Path
}
function Find-Java {
    $candidates = New-Object System.Collections.Generic.List[string]
    $cmd = Get-Command 'java.exe' -ErrorAction SilentlyContinue
    if ($cmd) { $candidates.Add($cmd.Source) }
    foreach ($root in @('C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft', 'C:\Program Files\Java')) {
        if (Test-Path $root) {
            Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | ForEach-Object {
                $candidate = Join-Path $_.FullName 'bin\java.exe'
                if (Test-Path $candidate) { $candidates.Add($candidate) }
            }
        }
    }
    $compatibleCandidates = New-Object System.Collections.Generic.List[object]
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        $binDirectory = Split-Path $candidate -Parent
        $javac = Join-Path $binDirectory 'javac.exe'
        if (-not (Test-Path $javac)) { continue }
        $javaText = (& $candidate -version 2>&1 | Out-String)
        $javacText = (& $javac -version 2>&1 | Out-String)
        $javaMatch = [regex]::Match($javaText, 'version "?(?<major>\d+)')
        if (-not $javaMatch.Success) { $javaMatch = [regex]::Match($javaText, 'openjdk (?<major>\d+)') }
        $javacMatch = [regex]::Match($javacText, 'javac (?<major>\d+)')
        if (-not $javaMatch.Success -or -not $javacMatch.Success) { continue }
        $javaMajor = [int]$javaMatch.Groups['major'].Value
        $javacMajor = [int]$javacMatch.Groups['major'].Value
        if ($javaMajor -lt 21 -or $javaMajor -ne $javacMajor) { continue }
        $compatibleCandidates.Add([pscustomobject]@{ Path = $candidate; Major = $javaMajor })
    }
    # Prefer JDK 21 for the project's Java 21 baseline; newer full JDKs are a fallback.
    $selected = $compatibleCandidates | Sort-Object @{ Expression = { if ($_.Major -eq 21) { 0 } else { 1 } } }, @{ Expression = { $_.Major } } | Select-Object -First 1
    if ($selected) { return $selected.Path }
    return $null
}
function Ensure-Java {
    $java = Find-Java
    if (-not $java) {
        Invoke-WingetInstall 'EclipseAdoptium.Temurin.21.JDK' 'Eclipse Temurin JDK 21'
        Refresh-Path
        $java = Find-Java
    }
    if (-not $java) { throw 'A full JDK 21 or later (java.exe and javac.exe) is required. Install Eclipse Temurin JDK 21 from https://adoptium.net/temurin/releases/?version=21, then reopen PowerShell and rerun this launcher.' }
    $javaRuntimeText = (& $java -version 2>&1 | Out-String)
    $javaCompiler = Join-Path (Split-Path $java -Parent) 'javac.exe'
    $javaCompilerText = (& $javaCompiler -version 2>&1 | Out-String)
    Write-Status 'Java runtime' (($javaRuntimeText -split "`r?`n" | Select-Object -First 1).Trim()) Gray
    Write-Status 'Java compiler' (($javaCompilerText -split "`r?`n" | Select-Object -First 1).Trim()) Gray
    $script:JavaHome = Split-Path (Split-Path $java -Parent) -Parent
    $javac = Join-Path (Split-Path $java -Parent) 'javac.exe'
    if (-not (Test-Path $javac)) { throw 'The selected Java installation is missing javac.exe; install a full JDK, not a JRE.' }
    $env:JAVA_HOME = $script:JavaHome
    $env:Path = (Join-Path $script:JavaHome 'bin') + ';' + $env:Path
    Write-Status 'Java' 'OK — full JDK 21+ (java and javac)' Green
}
function Ensure-Node {
    $node = Get-Command 'node.exe' -ErrorAction SilentlyContinue
    if (-not $node -and (Test-Path 'C:\Program Files\nodejs\node.exe')) {
        $env:Path = 'C:\Program Files\nodejs;' + $env:Path
        $node = Get-Command 'node.exe' -ErrorAction SilentlyContinue
    }
    $version = if ($node) { (& $node.Source --version).Trim().TrimStart('v') } else { '' }
    $nodeIsCurrent = $false
    if ($version) { try { $nodeIsCurrent = ([version]$version -ge [version]'22.22.0') } catch { $nodeIsCurrent = $false } }
    if (-not $nodeIsCurrent) {
        if ($node) {
            if (-not (Test-Command 'winget.exe')) { throw "Node.js $version is too old. Install Node.js 22.22.0 or later from https://nodejs.org/en/download, then reopen PowerShell." }
            Write-Host 'Upgrading Node.js to a supported LTS release...' -ForegroundColor Yellow
            & winget.exe upgrade --id 'OpenJS.NodeJS.LTS' --exact --source winget --accept-source-agreements --accept-package-agreements --silent --disable-interactivity
            Refresh-Path
            $node = Get-Command 'node.exe' -ErrorAction SilentlyContinue
            if ($node) { $version = (& $node.Source --version).Trim().TrimStart('v') } else { $version = '' }
            $stillOld = $true
            if ($version) { try { $stillOld = ([version]$version -lt [version]'22.22.0') } catch { $stillOld = $true } }
            if ($stillOld) {
                Write-Host 'WinGet upgrade did not provide Node.js 22.22.0+; reinstalling the supported LTS package...' -ForegroundColor Yellow
                & winget.exe install --id 'OpenJS.NodeJS.LTS' --exact --source winget --accept-source-agreements --accept-package-agreements --silent --disable-interactivity --force
                if ($LASTEXITCODE -ne 0) { throw 'WinGet could not upgrade Node.js. Install Node.js 22.22.0 or later from https://nodejs.org/en/download and rerun.' }
                Refresh-Path
            }
        } else {
            Invoke-WingetInstall 'OpenJS.NodeJS.LTS' 'Node.js LTS'
            Refresh-Path
        }
        $node = Get-Command 'node.exe' -ErrorAction SilentlyContinue
        if (-not $node -and (Test-Path 'C:\Program Files\nodejs\node.exe')) {
            $env:Path = 'C:\Program Files\nodejs;' + $env:Path
            $node = Get-Command 'node.exe' -ErrorAction SilentlyContinue
        }
        if (-not $node) { throw 'Node.js 22.22.0 or later could not be found after installation. Install it from https://nodejs.org/en/download and reopen PowerShell.' }
        $version = (& $node.Source --version).Trim().TrimStart('v')
    }
    try { if ([version]$version -lt [version]'22.22.0') { throw 'old' } } catch { throw "Node.js 22.22.0 or later is required. Detected $version. Update Node.js from https://nodejs.org/en/download and rerun." }
    $npm = Get-Command 'npm.cmd' -ErrorAction SilentlyContinue
    if ($npm) { $script:NpmCmd = $npm.Source }
    else {
        $npmCandidate = Join-Path (Split-Path $node.Source -Parent) 'npm.cmd'
        if (Test-Path $npmCandidate) { $script:NpmCmd = $npmCandidate }
        else { throw 'npm.cmd was not found beside Node.js. Repair the Node.js LTS installation from https://nodejs.org/en/download.' }
    }
    Write-Status 'Node.js' ("OK — " + $version) Green
}
function Ensure-Maven {
    $localMaven = Join-Path $ToolDir 'apache-maven-3.10.0\bin\mvn.cmd'
    $globalMaven = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
    $useGlobal = $false
    if ($globalMaven) {
        $globalLine = (& $globalMaven.Source -version 2>&1 | Select-Object -First 1).ToString()
        $globalMatch = [regex]::Match($globalLine, 'Apache Maven (?<version>\d+\.\d+\.\d+)')
        if ($globalMatch.Success -and [version]$globalMatch.Groups['version'].Value -ge [version]'3.9.0') { $useGlobal = $true }
        else { Write-Host ("System Maven is too old or invalid; using the verified local Maven distribution instead. Detected: " + $globalLine) -ForegroundColor Yellow }
    }
    if (Test-Path $localMaven) { $script:MavenCmd = $localMaven }
    elseif ($useGlobal) { $script:MavenCmd = $globalMaven.Source }
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
    if (-not (Test-Path $script:MavenCmd)) { throw 'Maven 3.9+ could not be located. Install Maven 3.9+ from https://maven.apache.org/download.cgi or rerun to download the verified local distribution.' }
    $mavenLine = (& $script:MavenCmd -version 2>&1 | Select-Object -First 1).ToString()
    $mavenMatch = [regex]::Match($mavenLine, 'Apache Maven (?<version>\d+\.\d+\.\d+)')
    if (-not $mavenMatch.Success -or [version]$mavenMatch.Groups['version'].Value -lt [version]'3.9.0') {
        throw "Maven 3.9 or later is required. Detected: $mavenLine. Install Maven 3.9+ from https://maven.apache.org/download.cgi."
    }
    Write-Status 'Maven' ("OK — " + $mavenLine) Green
}
function Find-PostgresTools {
    $candidates = @()
    $root = 'C:\Program Files\PostgreSQL'
    if (Test-Path $root) {
        foreach ($directory in Get-ChildItem $root -Directory -ErrorAction SilentlyContinue) {
            $versionMatch = [regex]::Match($directory.Name, '^(?<major>\d+)(?:\.\d+)?$')
            if (-not $versionMatch.Success) { continue }
            $major = [int]$versionMatch.Groups['major'].Value
            if ($major -notin @(17, 18)) { continue }
            $psqlPath = Join-Path $directory.FullName 'bin\psql.exe'
            $readyPath = Join-Path $directory.FullName 'bin\pg_isready.exe'
            if ((Test-Path $psqlPath) -and (Test-Path $readyPath)) {
                $candidates += [pscustomobject]@{ Major = $major; Psql = $psqlPath; PgIsReady = $readyPath }
            }
        }
    }

    $pathCommand = Get-Command 'psql.exe' -ErrorAction SilentlyContinue
    if ($pathCommand) {
        $versionText = (& $pathCommand.Source --version 2>&1 | Out-String).Trim()
        $pathVersionMatch = [regex]::Match($versionText, 'PostgreSQL\) (?<major>\d+)')
        if ($pathVersionMatch.Success) {
            $major = [int]$pathVersionMatch.Groups['major'].Value
            $readyPath = Join-Path (Split-Path $pathCommand.Source -Parent) 'pg_isready.exe'
            if ($major -in @(17, 18) -and (Test-Path $readyPath) -and
                -not ($candidates | Where-Object { $_.Psql -eq $pathCommand.Source })) {
                $candidates += [pscustomobject]@{
                    Major = $major
                    Psql = $pathCommand.Source
                    PgIsReady = $readyPath
                }
            }
        }
    }

    $selected = $candidates | Sort-Object -Property Major -Descending | Select-Object -First 1
    if ($selected) {
        $script:PostgresClientMajor = [int]$selected.Major
        $script:Psql = $selected.Psql
        $script:PgIsReady = $selected.PgIsReady
        $env:Path = (Split-Path $selected.Psql -Parent) + ';' + $env:Path
    } else {
        $script:PostgresClientMajor = $null
        $script:Psql = $null
        $script:PgIsReady = $null
    }
}
function Get-PostgresService {
    $services = @(
        Get-Service -ErrorAction SilentlyContinue | ForEach-Object {
            $match = [regex]::Match($_.Name, '^postgresql-x64-(?<major>\d+)$')
            if ($match.Success) {
                $major = [int]$match.Groups['major'].Value
                if ($major -in @(17, 18)) {
                    [pscustomobject]@{ Major = $major; Service = $_ }
                }
            }
        } | Sort-Object -Property Major -Descending
    )
    $running = $services | Where-Object { $_.Service.Status -eq 'Running' } | Select-Object -First 1
    if ($running) { return $running.Service }
    if ($script:PostgresClientMajor) {
        $matching = $services | Where-Object { $_.Major -eq $script:PostgresClientMajor } | Select-Object -First 1
        if ($matching) { return $matching.Service }
    }
    $selected = $services | Select-Object -First 1
    if ($selected) { return $selected.Service }
    return $null
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
    $service = Get-PostgresService
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
    $keycloakInstallDir = Join-Path $ToolDir 'keycloak-26.7.5'
    $kcBat = Join-Path $keycloakInstallDir 'bin\kc.bat'
    if (-not (Test-Path $kcBat)) {
        Protect-Directory $ToolDir
        $zip = Join-Path $ToolDir 'keycloak-26.7.5.zip'
        $url = 'https://github.com/keycloak/keycloak/releases/download/26.7.5/keycloak-26.7.5.zip'
        $expected = '30ef87fb7101c43d29688d1ae80c0588ff4a151b3330e9f664bf9692d398658a'
        Write-Host 'Downloading Keycloak 26.7.5...' -ForegroundColor Yellow
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
        $actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $expected) { Remove-Item $zip -Force; throw 'Keycloak SHA-256 verification failed.' }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $ToolDir)
    }
    if (-not (Test-Path $kcBat)) { throw 'Keycloak 26.7.5 could not be extracted.' }
    $realmSource = Join-Path $RepoRoot 'infra\keycloak\education-realm.json'
    if (-not (Test-Path $realmSource)) { throw 'The tracked education Keycloak realm file is missing.' }
    $importDir = Join-Path $keycloakInstallDir 'data\import'
    New-Item -ItemType Directory -Path $importDir -Force | Out-Null
    Copy-Item -LiteralPath $realmSource -Destination (Join-Path $importDir 'education-realm.json') -Force
    $script:KeycloakHome = $keycloakInstallDir
    Write-Status 'Keycloak' 'OK — 26.7.5, realm import prepared' Green
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
        if (-not ($State.PSObject.Properties.Name -contains 'DatabaseProvider')) {
            $State | Add-Member -NotePropertyName DatabaseProvider -NotePropertyValue 'PostgreSQL' -Force
        }
        if (-not ($State.PSObject.Properties.Name -contains 'SQLiteDatabasePath')) {
            $State | Add-Member -NotePropertyName SQLiteDatabasePath -NotePropertyValue '' -Force
        }
        if (-not ($State.PSObject.Properties.Name -contains 'TenantId')) {
            $State | Add-Member -NotePropertyName TenantId -NotePropertyValue '' -Force
        }
        if (-not ($State.PSObject.Properties.Name -contains 'LocalAdminUserId')) {
            $State | Add-Member -NotePropertyName LocalAdminUserId -NotePropertyValue '' -Force
        }
        $previousProvider = [string]$State.DatabaseProvider
        $sqlitePath = Join-Path $LocalDir 'uep-local.sqlite'
        $sqliteMissingOrEmpty = -not (Test-Path -LiteralPath $sqlitePath -PathType Leaf)
        if (-not $sqliteMissingOrEmpty) {
            $sqliteMissingOrEmpty = (Get-Item -LiteralPath $sqlitePath).Length -eq 0
        }
        $providerChanged = -not [string]::Equals($previousProvider, $Database, [System.StringComparison]::OrdinalIgnoreCase)
        $sqliteIdentityMustBeRecreated = ($Database -eq 'SQLite' -and $sqliteMissingOrEmpty)
        if ($providerChanged -or $sqliteIdentityMustBeRecreated) {
            $reason = if ($providerChanged) { "database provider changed from $previousProvider to $Database" } else { 'the local SQLite database file is missing or empty' }
            Write-Status 'Local state' ("Reinitializing database-specific tenant identity because " + $reason + '; preserving local credentials and database files') Yellow
            $State.TenantId = ''
            $State.LocalAdminUserId = ''
            $State.TestAccounts = @()
        }
        $State.DatabaseProvider = $Database
        if ($Database -eq 'SQLite') { $State.SQLiteDatabasePath = $sqlitePath }
        Save-State
        return
    }
    $script:State = [pscustomobject]@{
        DatabaseProvider = $Database
        SQLiteDatabasePath = $(if ($Database -eq 'SQLite') { Join-Path $LocalDir 'uep-local.sqlite' } else { '' })
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
function Initialize-LocalSqliteDatabase {
    $script:State.DatabaseProvider = 'SQLite'
    $script:State.SQLiteDatabasePath = Join-Path $LocalDir 'uep-local.sqlite'
    Protect-Directory $LocalDir
    if (Test-Path -LiteralPath $State.SQLiteDatabasePath -PathType Leaf) {
        Protect-File $State.SQLiteDatabasePath
    }
    Save-State
    Write-Status 'Database' ('SQLite file — ' + $State.SQLiteDatabasePath) Green
    Write-Host 'Hibernate creates the local schema from JPA entities. PostgreSQL Flyway migrations are not run.' -ForegroundColor Yellow
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
        Protect-File $rolesFile
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
            & $script:MavenCmd -B -f (Join-Path $ApiDir 'pom.xml') compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java
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
    Protect-File $path
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
        $attributes = @{}
        if (-not [string]::IsNullOrWhiteSpace($TenantId)) { $attributes.tenant_id = @($TenantId) }
        $representation = @{ username = $Username; email = $Email; emailVerified = $true; enabled = $true; firstName = $FirstName; lastName = $LastName; attributes = $attributes }
        Invoke-KeycloakAdmin 'Post' '/admin/realms/education/users' $AdminToken $representation | Out-Null
        $user = Get-LocalUser $Username $AdminToken
        if (-not $user) { throw "Keycloak did not create the local test user '$Username'." }
    } else {
        $attributesProperty = $user.PSObject.Properties['attributes']
        if ($null -eq $attributesProperty -or $null -eq $attributesProperty.Value) {
            $user | Add-Member -NotePropertyName attributes -NotePropertyValue ([pscustomobject]@{}) -Force
        }
        if (-not [string]::IsNullOrWhiteSpace($TenantId)) {
            $tenantAttributeProperty = $user.attributes.PSObject.Properties['tenant_id']
            if ($null -eq $tenantAttributeProperty) {
                $user.attributes | Add-Member -NotePropertyName tenant_id -NotePropertyValue @() -Force
            }
            $user.attributes.tenant_id = @($TenantId)
        }
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
function Assert-TenantTokenContext([string]$Token, [string]$ExpectedTenantId) {
    $segments = $Token.Split('.')
    if ($segments.Count -ne 3) { throw 'Keycloak returned a token that is not a compact JWT.' }
    $payload = $segments[1].Replace('-', '+').Replace('_', '/')
    switch ($payload.Length % 4) {
        2 { $payload += '==' }
        3 { $payload += '=' }
        1 { throw 'Keycloak returned a JWT with an invalid payload encoding.' }
    }
    $claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
    $tenantClaim = $claims.PSObject.Properties['tenant_id']
    if ($null -eq $tenantClaim -or $tenantClaim.Value -isnot [string]) {
        $claimType = if ($null -eq $tenantClaim) { 'missing' } else { $tenantClaim.Value.GetType().FullName }
        $claimNames = ($claims.PSObject.Properties.Name | Sort-Object) -join ', '
        throw "Local bootstrap token tenant_id must be a scalar JSON string; received $claimType. Claims returned: $claimNames. Check the uep-local-bootstrap user attribute and client protocol mapper."
    }
    if ($tenantClaim.Value -ne $ExpectedTenantId) {
        throw "Local bootstrap token tenant_id '$($tenantClaim.Value)' does not match the expected tenant '$ExpectedTenantId'. Check the uep-local-bootstrap Keycloak user attribute."
    }
}
function Invoke-Api([string]$Method, [string]$Path, [string]$Token, $Body = $null) {
    $params = @{ Method = $Method; Uri = ('http://127.0.0.1:8080' + $Path); Headers = @{ Authorization = "Bearer $Token"; Accept = 'application/json' }; TimeoutSec = 30 }
    if ($null -ne $Body) { $params.ContentType = 'application/json'; $params.Body = ConvertTo-Json -InputObject $Body -Depth 20 }
    try {
        return Invoke-RestMethod @params
    } catch {
        $statusCode = $null
        $responseBody = $null
        if ($null -ne $_.Exception.Response) {
            try { $statusCode = [int]$_.Exception.Response.StatusCode } catch {}
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($null -ne $stream) {
                    $reader = [System.IO.StreamReader]::new($stream)
                    try { $responseBody = $reader.ReadToEnd() } finally { $reader.Dispose() }
                }
            } catch {}
        }
        $detail = if ($responseBody) { $responseBody } else { $_.Exception.Message }
        throw "UEP API $Method $Path failed (HTTP $statusCode): $detail"
    }
}
function Ensure-RealIdentity {
    Write-Section 'Keycloak local realm and owner bootstrap'
    $adminToken = Get-KeycloakAdminToken
    $tenantIdMapper = @{
        name = 'tenant-id'
        protocol = 'openid-connect'
        protocolMapper = 'oidc-usermodel-attribute-mapper'
        consentRequired = $false
        config = @{
            'user.attribute' = 'tenant_id'
            'claim.name' = 'tenant_id'
            'jsonType.label' = 'String'
            'multivalued' = 'false'
            'aggregate.attrs' = 'false'
            'id.token.claim' = 'true'
            'access.token.claim' = 'true'
            'userinfo.token.claim' = 'true'
        }
    }
    $clients = Invoke-KeycloakAdmin 'Get' ("/admin/realms/education/clients?clientId=" + [uri]::EscapeDataString($State.LocalBootstrapClient)) $adminToken
    $bootstrapClient = $clients | Select-Object -First 1
    if (-not $bootstrapClient) {
        $client = @{
            clientId = $State.LocalBootstrapClient
            name = 'Local-only UEP bootstrap helper'
            enabled = $true
            publicClient = $true
            standardFlowEnabled = $false
            implicitFlowEnabled = $false
            directAccessGrantsEnabled = $true
            serviceAccountsEnabled = $false
            protocol = 'openid-connect'
            protocolMappers = @($tenantIdMapper)
        }
        Invoke-KeycloakAdmin 'Post' '/admin/realms/education/clients' $adminToken $client | Out-Null
        $clients = Invoke-KeycloakAdmin 'Get' ("/admin/realms/education/clients?clientId=" + [uri]::EscapeDataString($State.LocalBootstrapClient)) $adminToken
        $bootstrapClient = $clients | Select-Object -First 1
    }
    if (-not $bootstrapClient -or -not $bootstrapClient.id) {
        throw 'Keycloak did not return the local bootstrap client identifier.'
    }
    $bootstrapMappers = Invoke-KeycloakAdmin 'Get' ("/admin/realms/education/clients/" + $bootstrapClient.id + '/protocol-mappers/models') $adminToken
    $existingTenantMapper = $bootstrapMappers | Where-Object { $_.name -eq 'tenant-id' } | Select-Object -First 1
    if (-not $existingTenantMapper) {
        Invoke-KeycloakAdmin 'Post' ("/admin/realms/education/clients/" + $bootstrapClient.id + '/protocol-mappers/models') $adminToken $tenantIdMapper | Out-Null
    } elseif ($existingTenantMapper.config.'user.attribute' -ne 'tenant_id' -or
              $existingTenantMapper.config.'claim.name' -ne 'tenant_id' -or
              $existingTenantMapper.config.'access.token.claim' -ne 'true' -or
              $existingTenantMapper.config.'multivalued' -ne 'false') {
        $tenantIdMapper.id = $existingTenantMapper.id
        Invoke-KeycloakAdmin 'Put' ("/admin/realms/education/clients/" + $bootstrapClient.id + '/protocol-mappers/models/' + $existingTenantMapper.id) $adminToken $tenantIdMapper | Out-Null
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
        $tenantIdProperty = if ($null -ne $tenant) { $tenant.PSObject.Properties['id'] } else { $null }
        if ($null -eq $tenantIdProperty -or [string]::IsNullOrWhiteSpace([string]$tenantIdProperty.Value)) {
            throw 'Tenant bootstrap API response did not include the required id field. Verify the tenant response DTO and backend logs.'
        }
        $State.TenantId = [string]$tenantIdProperty.Value
        # Mark the tenant record as a local development environment, not a hosted Fly deployment.
        Invoke-Api 'Patch' ("/api/v1/tenants/" + $State.TenantId + '/deployment?platform=local&status=active') $localAdminToken | Out-Null
        Save-State
    }
    $localAdmin = Ensure-KeycloakUser $State.LocalAdminUsername 'admin@local.uep' 'UEP' 'Local Administrator' $State.LocalAdminPassword $State.TenantId $adminToken
    $State.LocalAdminUserId = $localAdmin.id
    Save-State
    $verifiedLocalAdmin = Get-LocalUser $State.LocalAdminUsername $adminToken
    $tenantAttributeProperty = if ($null -ne $verifiedLocalAdmin -and $null -ne $verifiedLocalAdmin.attributes) { $verifiedLocalAdmin.attributes.PSObject.Properties['tenant_id'] } else { $null }
    $tenantAttributeValues = if ($null -ne $tenantAttributeProperty) { @($tenantAttributeProperty.Value | ForEach-Object { [string]$_ }) } else { @() }
    if (-not ($tenantAttributeValues -contains $State.TenantId)) {
        throw 'Keycloak did not persist the local owner tenant_id user attribute. The local owner was not issued an API bootstrap token.'
    }
    $verifiedBootstrapMappers = Invoke-KeycloakAdmin 'Get' ("/admin/realms/education/clients/" + $bootstrapClient.id + '/protocol-mappers/models') $adminToken
    $verifiedTenantMapper = $verifiedBootstrapMappers | Where-Object { $_.name -eq 'tenant-id' } | Select-Object -First 1
    if (-not $verifiedTenantMapper -or $verifiedTenantMapper.config.'user.attribute' -ne 'tenant_id' -or
        $verifiedTenantMapper.config.'claim.name' -ne 'tenant_id' -or $verifiedTenantMapper.config.'access.token.claim' -ne 'true' -or
        $verifiedTenantMapper.config.'multivalued' -ne 'false') {
        throw 'The local bootstrap client is missing the required scalar tenant_id access-token mapper.'
    }
    $localAdminToken = Get-UserToken $State.LocalAdminUsername $State.LocalAdminPassword
    Assert-TenantTokenContext $localAdminToken $State.TenantId
    $current = $null
    try { $current = Invoke-Api 'Get' '/api/v1/me' $localAdminToken } catch {}
    if (-not $current) {
        Invoke-Api 'Post' '/api/v1/identity/bootstrap-owner' $localAdminToken @{ subject = $localAdmin.id; email = 'admin@local.uep'; displayName = 'UEP Local Administrator'; roleIds = @() } | Out-Null
        $localAdminToken = Get-UserToken $State.LocalAdminUsername $State.LocalAdminPassword
        $current = Invoke-Api 'Get' '/api/v1/me' $localAdminToken
    }
    Write-Status 'Identity' ("OK — Keycloak OIDC, tenant " + $State.TenantId + ', owner membership active') Green
    if ($SkipAccountSeeding) {
        Write-Status 'Test users' 'SKIPPED — requested by -SkipAccountSeeding' Yellow
    } elseif ($Database -eq 'SQLite') {
        if ($Mode -in @('Both', 'Presentation')) {
            Write-Status 'Test users' 'SKIPPED — SQLite qualification provisions only the Real UEP OIDC owner; the separate Offline Demo has its own seeded browser personas' Yellow
        } else {
            Write-Status 'Test users' 'SKIPPED — SQLite qualification provisions only the Real UEP OIDC owner; additional backend personas require separate SQLite validation' Yellow
        }
    } else {
        Ensure-LocalTestAccounts $localAdminToken $adminToken
    }
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
function Test-LocalApiProxy {
    $headers = @{ 'X-UEP-Local-Auth' = $script:LocalAuthToken }
    try {
        $identity = Invoke-RestMethod -Method Get -Uri 'http://127.0.0.1:5173/api/v1/me' -Headers $headers -TimeoutSec 15
    } catch {
        throw "Real Vite API proxy failed its authenticated /api/v1/me check. Verify the local-native profile, Backend log and Vite /api proxy. $($_.Exception.Message)"
    }
    if ([string]$identity.oidcSubject -ne 'uep-local-demo-owner' -or
        [string]$identity.membershipStatus -ne 'ACTIVE' -or
        -not (@($identity.roles) -contains 'Organization Owner') -or
        [string]::IsNullOrWhiteSpace([string]$identity.tenantId)) {
        throw 'The authenticated Vite proxy response did not contain the persisted active local owner identity, tenant and Organization Owner role.'
    }
    if ([string]$State.TenantId -ne [string]$identity.tenantId) {
        $State.TenantId = [string]$identity.tenantId
        Save-State
    }
    Write-Status 'Vite API proxy' 'PASS — authenticated /api/v1/me returned the persisted owner, tenant and role' Green
}
function Write-RuntimeCommandFiles([switch]$RunReal, [switch]$RunDemo, [int]$DemoPort = 5174) {
    if ($RunReal) {
        if ($Database -eq 'SQLite') {
            $sqliteJdbcPath = $State.SQLiteDatabasePath.Replace('\', '/')
            $backendLines = @(
                '@echo off',
                'set SPRING_PROFILES_ACTIVE=local-sqlite,local-native',
                ('set UEP_SQLITE_JDBC_URL=jdbc:sqlite:' + $sqliteJdbcPath),
                'set PLATFORM_DEV_MODE=true',
                'set PLATFORM_LOCAL_AUTH_ENABLED=true',
                ('set UEP_LOCAL_TENANT_ID=' + $State.TenantId),
                ('set UEP_LOCAL_AUTH_TOKEN=' + $script:LocalAuthToken),
                ('set UEP_LOCAL_AUTH_SESSION_ID=' + $script:LocalAuthSessionId),
                ('set UEP_LOCAL_AUTH_EXPIRES_AT=' + $script:LocalAuthExpiresAt),
                'set PLATFORM_LIVEKIT_ENABLED=false',
                'set PLATFORM_CLAMAV_ENABLED=false',
                'set PLATFORM_BACKGROUND_WORKERS_ENABLED=false',
                'set PLATFORM_UPLOAD_STAGING_ENABLED=false',
                'set DATABASE_POOL_SIZE=1',
                'set PORT=8080',
                ('cd /d "' + $ApiDir + '"'),
                ('call "' + $MavenCmd + '" -B -Dmaven.compiler.release=21 clean compile'),
                'if errorlevel 1 exit /b %errorlevel%',
                ('powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $RepoRoot 'scripts\verify-java-class-version.ps1') + '" -ClassRoot "' + (Join-Path $ApiDir 'target\classes') + '" -ExpectedMajor 65 -RequiredClass "com\universalplatform\grading\package-info.class"'),
                'if errorlevel 1 exit /b %errorlevel%',
                ('call "' + $MavenCmd + '" -B -Dmaven.compiler.release=21 spring-boot:run -Dspring-boot.run.arguments=--platform.background-workers.enabled=false')
            )
        } else {
            $backendLines = @(
                '@echo off',
                ('set SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/' + $State.DatabaseName + '?sslmode=disable'),
                ('set SPRING_DATASOURCE_USERNAME=' + $State.RuntimeUser),
                ('set SPRING_DATASOURCE_PASSWORD=' + $State.RuntimePassword),
                'set SPRING_FLYWAY_ENABLED=false',
                'set SPRING_PROFILES_ACTIVE=local-native',
                'set PLATFORM_DEV_MODE=true',
                'set PLATFORM_LOCAL_AUTH_ENABLED=true',
                ('set UEP_LOCAL_TENANT_ID=' + $State.TenantId),
                ('set UEP_LOCAL_AUTH_TOKEN=' + $script:LocalAuthToken),
                ('set UEP_LOCAL_AUTH_SESSION_ID=' + $script:LocalAuthSessionId),
                ('set UEP_LOCAL_AUTH_EXPIRES_AT=' + $script:LocalAuthExpiresAt),
                'set PLATFORM_LIVEKIT_ENABLED=false',
                'set PLATFORM_CLAMAV_ENABLED=false',
                'set PLATFORM_BACKGROUND_WORKERS_ENABLED=true',
                'set PLATFORM_UPLOAD_STAGING_ENABLED=true',
                'set DATABASE_POOL_SIZE=10',
                'set PORT=8080',
                ('cd /d "' + $ApiDir + '"'),
                ('call "' + $MavenCmd + '" -B -Dmaven.compiler.release=21 clean compile'),
                'if errorlevel 1 exit /b %errorlevel%',
                ('powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $RepoRoot 'scripts\verify-java-class-version.ps1') + '" -ClassRoot "' + (Join-Path $ApiDir 'target\classes') + '" -ExpectedMajor 65 -RequiredClass "com\universalplatform\grading\package-info.class"'),
                'if errorlevel 1 exit /b %errorlevel%',
                ('call "' + $MavenCmd + '" -B -Dmaven.compiler.release=21 spring-boot:run')
            )
        }
        # Pin the runtime and compiler to the exact JDK discovered by Ensure-Java.
        # Maven's forked Spring Boot process inherits this JAVA_HOME and PATH.
        $javaBin = Join-Path $script:JavaHome 'bin'
        $commandHeader = $backendLines[0]
        $backendLines = @(
            $commandHeader,
            ('set "JAVA_HOME=' + $script:JavaHome + '"'),
            ('set "PATH=' + $javaBin + ';%PATH%"'),
            'echo === Selected Java runtime ===',
            'java -version',
            'echo === Selected Java compiler ===',
            'javac -version',
            'echo === Selected Maven runtime ===',
            ('call "' + $MavenCmd + '" -version')
        ) + @($backendLines | Select-Object -Skip 1)
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
            'set VITE_LOCAL_API_MODE=true',
            ('set VITE_LOCAL_API_AUTH_TOKEN=' + $script:LocalAuthToken),
            'set VITE_BACKEND_URL=',
            ('set VITE_UEP_PRESENTATION_LABEL=REAL UEP - LOCAL ' + $Database.ToUpperInvariant()),
            'set VITE_KEYCLOAK_URL=',
            'set VITE_KEYCLOAK_REALM=',
            'set VITE_KEYCLOAK_CLIENT_ID=',
            ('cd /d "' + $WebDir + '"'),
            ('call "' + $NpmCmd + '" run dev -- --host 127.0.0.1 --port 5173 --strictPort')
        )
        $frontFile = Write-CommandFile 'run-real-frontend' $frontLines
        Start-ManagedProcess 'Real frontend' $frontFile $WebDir
        if (-not (Wait-ForHttp 'http://127.0.0.1:5173' 90 'Real frontend')) { throw 'Real frontend did not become ready. Inspect .uep-local\logs\Real frontend.log.' }
        if (-not (Wait-ForHttp 'http://127.0.0.1:5173/api/v1/platform/version' 90 'Vite API proxy')) { throw 'Real frontend is reachable, but /api/v1/platform/version did not pass through the Vite proxy as HTTP 2xx. Inspect Vite and Backend logs.' }
        if (-not (Wait-ForHttp 'http://127.0.0.1:8080/swagger-ui/index.html' 60 'Backend Swagger UI')) { throw 'The backend is healthy but its local Swagger UI did not become available at /swagger-ui/index.html. Inspect Backend.log.' }
        if (-not (Wait-ForHttp 'http://127.0.0.1:8080/v3/api-docs' 60 'Backend OpenAPI document')) { throw 'The backend Swagger UI is present but its OpenAPI document is unavailable at /v3/api-docs.' }
        Test-LocalApiProxy
    }
    if ($RunDemo) {
        $frontLines = @(
            '@echo off',
            'set VITE_LOCAL_MODE=true',
            'set VITE_BACKEND_URL=',
            'set VITE_UEP_PRESENTATION_LABEL=OFFLINE DEMO - SEEDED BROWSER DATA',
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
    if ($Mode -eq 'Presentation') {
        Write-Host 'PRESENTATION MODE — Real UEP and the offline seeded demo run as separate applications.' -ForegroundColor Cyan
        Write-Host 'Real UEP uses Spring Boot, the selected local database and a launcher-issued loopback session; Keycloak is not started.'
        Write-Host 'Offline Demo uses simulated personas and browser-local sample state; it does not call the API.'
    }
    Show-RequirementInventory
    Ensure-Node

    if ($Mode -ne 'Demo') {
        Ensure-Java
        Ensure-Maven
        if ($Database -eq 'PostgreSQL') { Ensure-Postgres }
        Test-PortAvailable 8080
        Test-PortAvailable 5173
        if ($Mode -in @('Both', 'Presentation')) { Test-PortAvailable 5174 }
        Initialize-LocalState
        if ($Database -eq 'SQLite') {
            Initialize-LocalSqliteDatabase
        } else {
            Initialize-LocalDatabase
        }
        $script:LocalAuthToken = New-RandomSecret -Bytes 48
        $script:LocalAuthSessionId = [guid]::NewGuid().ToString()
        $script:LocalAuthExpiresAt = [DateTime]::UtcNow.AddHours(12).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
        Write-Status 'Identity' 'Created a new 12-hour loopback-only local API session' Green
        Write-RuntimeCommandFiles -RunReal -RunDemo:($Mode -in @('Both', 'Presentation'))
    } else {
        Test-PortAvailable 5173
        Write-RuntimeCommandFiles -RunDemo -DemoPort 5173
    }

    if ($Mode -in @('Both', 'Presentation')) {
        Write-Host ''
        Write-Host 'REAL UEP — LOCAL INTEGRATED APPLICATION' -ForegroundColor Green
        Write-Host '  Frontend:  http://localhost:5173'
        Write-Host '  Backend:   http://localhost:8080'
        Write-Host '  Swagger:   http://localhost:8080/swagger-ui.html'
        Write-Host '  OpenAPI:   http://localhost:8080/v3/api-docs'
        Write-Host '  Identity:  launcher-managed local API session (Keycloak not started)'
        Write-Host ('  Database:  ' + $(if ($Database -eq 'SQLite') { 'SQLite / .uep-local/uep-local.sqlite' } else { 'PostgreSQL / uep_local_native / localhost:5432' }))
        Write-Host 'OFFLINE PRESENTATION DEMO (seeded browser data; no API/Keycloak/database)' -ForegroundColor Yellow
        Write-Host '  Frontend:  http://localhost:5174'
        Write-Host 'Real and Demo use separate app origins, sessions and data stores.'
    } elseif ($Mode -eq 'Real') {
        Write-Host ''
        Write-Host 'REAL UEP — LOCAL INTEGRATED APPLICATION' -ForegroundColor Green
        Write-Host '  Frontend:  http://localhost:5173'
        Write-Host '  Backend:   http://localhost:8080'
        Write-Host '  Swagger:   http://localhost:8080/swagger-ui.html'
        Write-Host '  OpenAPI:   http://localhost:8080/v3/api-docs'
        Write-Host '  Identity:  launcher-managed local API session (Keycloak not started)'
        Write-Host ('  Database:  ' + $(if ($Database -eq 'SQLite') { 'SQLite / .uep-local/uep-local.sqlite' } else { 'PostgreSQL / uep_local_native / localhost:5432' }))
    } else {
        Write-Host ''
        Write-Host 'DEMO MODE' -ForegroundColor Green
        Write-Host '  Frontend:  http://localhost:5173'
        Write-Host '  Backend:   not started (offline fixtures only)'
        Write-Host '  Database:  not started (offline fixtures only)'
    }

    if ($Mode -ne 'Demo') {
        Write-Host ''
        Write-Host 'LOCAL REAL-APP IDENTITY' -ForegroundColor Cyan
        Write-Host '  UEP Local Development Owner — persisted in the selected local database.'
        Write-Host '  No password or Keycloak login is required for this local loopback demonstration.'
    }
    if (-not $NoBrowser) {
        if ($Mode -in @('Both', 'Presentation')) { Start-Process 'http://localhost:5173'; Start-Process 'http://localhost:5174' }
        else { Start-Process 'http://localhost:5173' }
    }

    Write-Section 'Live service status'
    Write-Host 'Press Ctrl+C to stop application processes. PostgreSQL is stopped only when this script started its Windows service.'
    while ($true) {
        if ($Mode -ne 'Demo') {
            $db = 'DOWN'
            if ($Database -eq 'SQLite') {
                if ((Test-Path -LiteralPath $State.SQLiteDatabasePath -PathType Leaf) -and
                    (Get-Item -LiteralPath $State.SQLiteDatabasePath).Length -gt 0) { $db = 'UP' }
            } else {
                try {
                    $dbResult = Invoke-Psql $State.RuntimeUser $State.RuntimePassword $State.DatabaseName 'SELECT 1'
                    if ($dbResult.Trim() -eq '1') { $db = 'UP' }
                } catch {}
            }
            $api = 'DOWN'
            try { $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 3; $api = [string]$health.status } catch {}
            Write-Status 'Database' $db $(if ($db -eq 'UP') { 'Green' } else { 'Red' })
            Write-Status 'Identity' 'LOCAL DEV AUTH — loopback-only session' Green
            Write-Status 'Backend' $api $(if ($api -eq 'UP') { 'Green' } else { 'Red' })
        } else {
            Write-Status 'Database' 'NOT STARTED — offline Demo' Yellow
            Write-Status 'Backend' 'NOT STARTED — offline Demo' Yellow
        }
        if ($Mode -in @('Both', 'Presentation')) {
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
