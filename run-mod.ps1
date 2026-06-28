param(
    [ValidateSet('build','runs','client','all')]
    [string]$Mode = 'all'
)

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory |
    Where-Object { $_.Name -like 'jdk-17*' } |
    Sort-Object Name -Descending |
    Select-Object -First 1

if (-not $jdk) {
    throw 'Geen JDK 17 gevonden onder C:\Program Files\Eclipse Adoptium.'
}

$env:JAVA_HOME = $jdk.FullName
$env:Path = "$($env:JAVA_HOME)\bin;$env:Path"

Write-Host "JAVA_HOME=$env:JAVA_HOME"
java -version

switch ($Mode) {
    'build' {
        .\gradlew.bat build
    }
    'runs' {
        .\gradlew.bat genVSCodeRuns
    }
    'client' {
        .\gradlew.bat runClient
    }
    'all' {
        .\gradlew.bat build
        .\gradlew.bat genVSCodeRuns
    }
}