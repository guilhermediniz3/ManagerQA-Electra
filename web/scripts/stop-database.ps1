$ErrorActionPreference = 'Stop'
$managerRoot = Split-Path $PSScriptRoot -Parent
& (Join-Path $managerRoot '.runtime\postgres\bin\pg_ctl.exe') -D (Join-Path $managerRoot '.runtime\pgdata') -m fast -w stop
exit $LASTEXITCODE
