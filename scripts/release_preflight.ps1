param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host "==> $Title"
}

function Invoke-GradleCheck {
    param(
        [string]$Name,
        [string[]]$Arguments
    )

    Write-Section $Name
    & .\gradlew.bat @Arguments
    if ($LASTEXITCODE -ne 0) {
        return $false
    }
    return $true
}

function Get-SuspiciousArtifacts {
    param([string]$Root)

    $excludePattern = '\\\.git\\|\\build\\|\\out\\|\\.gradle\\|\\.kotlin\\'
    Get-ChildItem -Path $Root -Recurse -File -Force |
        Where-Object {
            $_.FullName -notmatch $excludePattern -and (
                $_.FullName -match '\\android\\keystore\.properties$' -or
                $_.Extension -in '.jks', '.keystore', '.ovpn'
            )
        } |
        Sort-Object FullName
}

$failedChecks = New-Object System.Collections.Generic.List[string]

Push-Location (Join-Path $RepoRoot "android")
try {
    if (-not (Test-Path .\gradlew.bat)) {
        throw "gradlew.bat was not found under $PWD"
    }

    $checks = @(
        @{ Name = "Unit tests"; Args = @("testDebugUnitTest", "--console=plain") },
        @{ Name = "Lint"; Args = @("lintDebug", "--console=plain") },
        @{ Name = "Release assemble"; Args = @("assembleRelease", "--console=plain") }
    )

    foreach ($check in $checks) {
        if (-not (Invoke-GradleCheck -Name $check.Name -Arguments $check.Args)) {
            $failedChecks.Add($check.Name)
        }
    }
}
finally {
    Pop-Location
}

$suspiciousArtifacts = Get-SuspiciousArtifacts -Root $RepoRoot

if ($suspiciousArtifacts.Count -gt 0) {
    Write-Section "Suspicious artifacts"
    foreach ($artifact in $suspiciousArtifacts) {
        Write-Host (" - " + $artifact.FullName.Substring($RepoRoot.Length + 1))
    }
}

if ($failedChecks.Count -eq 0 -and $suspiciousArtifacts.Count -eq 0) {
    Write-Host ""
    Write-Host "Preflight passed."
    exit 0
}

Write-Section "Summary"
if ($failedChecks.Count -gt 0) {
    Write-Host ("Validation failures: " + ($failedChecks -join ", "))
}
if ($suspiciousArtifacts.Count -gt 0) {
    Write-Host ("Suspicious artifacts found: " + $suspiciousArtifacts.Count)
}

if ($failedChecks.Count -gt 0) {
    exit 1
}

exit 2
