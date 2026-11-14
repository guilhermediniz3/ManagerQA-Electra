param([switch]$TestsOnly)
$ErrorActionPreference = 'Stop'
$managerRoot = Split-Path $PSScriptRoot -Parent
$java21 = 'C:\Program Files\Java\jdk-21'
if (Test-Path -LiteralPath $java21) { $env:JAVA_HOME = $java21 }
$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($mavenCommand) {
    $managerMaven = $mavenCommand.Source
} else {
    $mavenCache = Join-Path $env:USERPROFILE '.m2\wrapper\dists\apache-maven-3.9.9'
    $managerMaven = Get-ChildItem -LiteralPath $mavenCache -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (!$managerMaven) { throw 'Instale Maven 3.9+ e Java 21 ou execute o Maven Wrapper na pasta backend.' }
Push-Location (Join-Path $managerRoot 'backend')
try {
    if ($TestsOnly) { & $managerMaven -B -ntp clean test }
    else { & $managerMaven -B -ntp clean package }
    $managerExit = $LASTEXITCODE
} finally { Pop-Location }
exit $managerExit
