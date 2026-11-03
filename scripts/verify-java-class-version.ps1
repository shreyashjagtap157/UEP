[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Container })]
    [string]$ClassRoot,

    [ValidateRange(45, 100)]
    [int]$ExpectedMajor = 65,

    [string]$RequiredClass = 'com\universalplatform\grading\package-info.class'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$resolvedRoot = (Resolve-Path -LiteralPath $ClassRoot).Path
$requiredPath = Join-Path $resolvedRoot $RequiredClass
if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
    throw "Expected compiled class was not found: $requiredPath. The backend compile step may not have completed."
}

function Get-ClassMajorVersion([string]$Path) {
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $header = New-Object byte[] 8
        $read = $stream.Read($header, 0, $header.Length)
        if ($read -ne 8) {
            throw "Class file is truncated: $Path"
        }
    } finally {
        $stream.Dispose()
    }

    if ($header[0] -ne 0xCA -or $header[1] -ne 0xFE -or $header[2] -ne 0xBA -or $header[3] -ne 0xBE) {
        throw "Invalid Java class-file magic header: $Path"
    }

    return ([int]$header[6] * 256) + [int]$header[7]
}

$requiredMajor = Get-ClassMajorVersion -Path $requiredPath
Write-Host ("Required class: {0} (class-file major {1})" -f $RequiredClass, $requiredMajor)
if ($requiredMajor -ne $ExpectedMajor) {
    throw "The grading package-info class has class-file major $requiredMajor; expected $ExpectedMajor for the configured Java release. Check JAVA_HOME and maven.compiler.release."
}

$classFiles = @(Get-ChildItem -LiteralPath $resolvedRoot -Filter '*.class' -File -Recurse)
if ($classFiles.Count -eq 0) {
    throw "No compiled Java classes were found under $resolvedRoot."
}

$versionCounts = @{}
$incompatible = New-Object System.Collections.Generic.List[string]
foreach ($classFile in $classFiles) {
    $major = Get-ClassMajorVersion -Path $classFile.FullName
    $key = [string]$major
    if (-not $versionCounts.ContainsKey($key)) {
        $versionCounts[$key] = 0
    }
    $versionCounts[$key] = [int]$versionCounts[$key] + 1

    if ($major -gt $ExpectedMajor) {
        $relative = $classFile.FullName.Substring($resolvedRoot.Length).TrimStart('\')
        $incompatible.Add("$relative (major $major)")
    }
}

foreach ($major in ($versionCounts.Keys | Sort-Object { [int]$_ })) {
    Write-Host ("Class-file major {0}: {1} class(es)" -f $major, $versionCounts[$major])
}

if ($incompatible.Count -gt 0) {
    $details = ($incompatible | Select-Object -First 20) -join [Environment]::NewLine
    throw ("Found {0} class(es) newer than Java {1} bytecode. First incompatible files:{2}{3}" -f
        $incompatible.Count, $ExpectedMajor, [Environment]::NewLine, $details)
}

Write-Host ("PASS: all {0} backend class files are compatible with Java release {1} (major {2})." -f
    $classFiles.Count, ($ExpectedMajor - 44), $ExpectedMajor) -ForegroundColor Green
