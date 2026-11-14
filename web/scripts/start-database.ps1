$ErrorActionPreference = 'Stop'
$managerRoot = Split-Path $PSScriptRoot -Parent
$pgCtl = Join-Path $managerRoot '.runtime\postgres\bin\pg_ctl.exe'
$pgData = Join-Path $managerRoot '.runtime\pgdata'
if (!(Test-Path -LiteralPath $pgCtl) -or !(Test-Path -LiteralPath $pgData)) { throw 'Banco portátil não encontrado. Configure seu PostgreSQL no .env.' }
& $pgCtl -D $pgData status
if ($LASTEXITCODE -eq 0) { exit 0 }
& $pgCtl -D $pgData -l (Join-Path $managerRoot '.runtime\postgres.log') -o '-h 127.0.0.1 -p 5432' -w start
exit $LASTEXITCODE
