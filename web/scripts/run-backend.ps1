$ErrorActionPreference = 'Stop'
$managerRoot = Split-Path $PSScriptRoot -Parent
$configFile = Join-Path $managerRoot '.env'
if (!(Test-Path -LiteralPath $configFile)) { throw 'Copie .env.example para .env e configure o banco e as chaves.' }
foreach ($line in Get-Content -LiteralPath $configFile) {
  if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
}
$java21 = 'C:\Program Files\Java\jdk-21'
if (Test-Path -LiteralPath $java21) { $env:JAVA_HOME = $java21 }
$javaExe = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$managerJar = Join-Path $managerRoot 'backend\target\managerqa-1.0.0.jar'
if (!(Test-Path -LiteralPath $managerJar)) { throw 'Execute Maven clean package dentro de backend antes de iniciar.' }
Set-Location (Join-Path $managerRoot 'backend')
& $javaExe '-Dspring.devtools.restart.enabled=false' -jar $managerJar
exit $LASTEXITCODE
