# One-click Docker WSL2 vhdx compaction - returns freed space to C:.
# WSL2 never shrinks docker_data.vhdx on its own; this runs the full cycle:
#   fstrim (mark freed blocks) -> stop Docker -> diskpart compact -> restart + bring stack up.
# Self-elevates (diskpart needs admin). See memory: docker_vhdx_compaction.

# --- self-elevate ---
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) {
    Start-Process powershell -Verb RunAs -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$PSCommandPath`""
    exit
}

$vhdx = "$env:LOCALAPPDATA\Docker\wsl\disk\docker_data.vhdx"
$projectRoot = Split-Path $PSScriptRoot -Parent
$dockerExe = "C:\Program Files\Docker\Docker\Docker Desktop.exe"

function Get-VhdxGB { if (Test-Path $vhdx) { [math]::Round((Get-Item $vhdx).Length / 1GB, 1) } else { 0 } }
function Get-FreeGB { [math]::Round((Get-PSDrive C).Free / 1GB, 1) }

Write-Host "Before:  vhdx $(Get-VhdxGB) GB | C: free $(Get-FreeGB) GB" -ForegroundColor Cyan

# 1. TRIM freed blocks while the Docker VM is still running (best-effort).
Write-Host "fstrim inside the Docker VM..."
try { wsl -d docker-desktop -e sh -c "fstrim -av" 2>$null } catch {}

# 2. Stop Docker Desktop + WSL so the vhdx is unlocked.
Write-Host "Stopping Docker..."
Get-Process "*docker*" -ErrorAction SilentlyContinue |
    Where-Object { $_.ProcessName -match 'Docker Desktop|com.docker' } |
    Stop-Process -Force -ErrorAction SilentlyContinue
wsl --shutdown
Start-Sleep -Seconds 6

# 3. Compact via diskpart. Path MUST use plain double quotes (file="..."); a
#    backslash-escaped path fails silently. Script is piped on stdin (the /s flag
#    form is unnecessary and brittle).
Write-Host "Compacting vhdx (this is the slow part)..."
@"
select vdisk file="$vhdx"
attach vdisk readonly
compact vdisk
detach vdisk
exit
"@ | diskpart | Out-Null

Write-Host "After:   vhdx $(Get-VhdxGB) GB | C: free $(Get-FreeGB) GB" -ForegroundColor Green

# 4. Restart Docker and bring the stack back.
Write-Host "Restarting Docker..."
if (Test-Path $dockerExe) {
    Start-Process $dockerExe
    $up = $false
    for ($i = 0; $i -lt 60; $i++) { Start-Sleep 5; docker info *> $null; if ($?) { $up = $true; break } }
    if ($up) {
        Push-Location $projectRoot
        docker compose up -d | Out-Null
        Pop-Location
        Write-Host "Stack is back up." -ForegroundColor Green
    } else {
        Write-Host "Docker did not come up in time - start it manually, then 'docker compose up -d'." -ForegroundColor Yellow
    }
} else {
    Write-Host "Docker Desktop not found at $dockerExe - start it manually." -ForegroundColor Yellow
}

Write-Host ""
Read-Host "Done. Press Enter to close"
