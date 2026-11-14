param([switch]$NoInstaller, [string]$OutputDir)
$ErrorActionPreference = 'Stop'
$electraDesktop = Split-Path $PSScriptRoot -Parent
$electraRoot = Split-Path $electraDesktop -Parent
$runtimeRoot = Join-Path $electraDesktop 'resources'
if (!$env:JAVA_HOME -or !(Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
  $env:JAVA_HOME = Join-Path $runtimeRoot 'jre'
}
if (!(Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { throw 'Configure JAVA_HOME com um JDK 21.' }
Push-Location (Join-Path $electraRoot 'frontend')
try {
  npm run build
  if ($LASTEXITCODE -ne 0) { throw 'Build do frontend falhou.' }
} finally { Pop-Location }
$staticRoot = [IO.Path]::GetFullPath((Join-Path $electraRoot 'backend\src\main\resources\static'))
if (!$staticRoot.StartsWith([IO.Path]::GetFullPath($electraRoot) + '\')) { throw 'Pasta de estaticos invalida.' }
if (Test-Path -LiteralPath $staticRoot) { Remove-Item -LiteralPath $staticRoot -Recurse -Force }
New-Item -ItemType Directory -Path $staticRoot -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $electraRoot 'frontend\dist') | Copy-Item -Destination $staticRoot -Recurse
$mavenExe = Join-Path $electraRoot 'backend\mvnw.cmd'
Push-Location (Join-Path $electraRoot 'backend')
try {
  & $mavenExe -B -ntp package '-DskipTests'
  if ($LASTEXITCODE -ne 0) { throw 'Build do backend falhou.' }
} finally { Pop-Location }
Copy-Item -LiteralPath (Join-Path $electraRoot 'backend\target\managerqa-1.0.0.jar') -Destination (Join-Path $runtimeRoot 'app.jar') -Force
# O seed e a configuracao sao independentes do banco de desenvolvimento.
# Nunca copiar F:\manager\.runtime\pgdata ou gerar segredos por cima de um seed existente.
if (!$NoInstaller) {
  foreach ($resource in @('pgdata-seed\PG_VERSION','app-config.json','jre\bin\java.exe','postgres\bin\pg_ctl.exe')) {
    if (!(Test-Path -LiteralPath (Join-Path $runtimeRoot $resource))) { throw "Recurso ausente: $resource" }
  }
  if (Test-Path -LiteralPath (Join-Path $runtimeRoot 'pgdata-seed\postmaster.pid')) { throw 'Pare o banco seed antes de empacotar.' }
  Push-Location $electraDesktop
  try {
    if ($OutputDir) {
      New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
      npx electron-builder --win nsis --config.directories.output=$OutputDir
    } else { npm run dist }
    if ($LASTEXITCODE -ne 0) { throw 'Instalador falhou.' }
  } finally { Pop-Location }
}
