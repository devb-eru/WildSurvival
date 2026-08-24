param(
    [string]$OutputRoot
)

$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'build-content-r2.ps1') -OutputRoot $OutputRoot -ContentRevision 'ws-content-r2.1'
