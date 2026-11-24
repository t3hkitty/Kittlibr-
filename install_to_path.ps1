<#
.SYNOPSIS
    Installs Kittlibr to the User's PATH and creates a 'kittlibr' command.
.DESCRIPTION
    Adds the Kittlibr repository directory to the user's permanent environment PATH.
    After running this script once, you can type 'kittlibr' from any terminal, folder,
    or drive without navigating to the repository first.
#>

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "🐾 Installing Kittlibr to User PATH..." -ForegroundColor Cyan
Write-Host "Target Directory: $ScriptDir" -ForegroundColor Gray

# Get current User PATH from registry
$UserPath = [System.Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::User)
$Paths = $UserPath -split ';' | Where-Object { $_ -ne '' }

if ($Paths -contains $ScriptDir) {
    Write-Host "✅ Kittlibr is already in your User PATH!" -ForegroundColor Green
} else {
    $NewPath = ($Paths + $ScriptDir) -join ';'
    [System.Environment]::SetEnvironmentVariable("Path", $NewPath, [System.EnvironmentVariableTarget]::User)
    # Update current session PATH so it works immediately in the current window
    $env:Path = "$env:Path;$ScriptDir"
    Write-Host "✅ Successfully added '$ScriptDir' to User PATH!" -ForegroundColor Green
}

# Also ensure Python packages are installed
Write-Host "`n📦 Checking requirements..." -ForegroundColor Cyan
$ReqPath = Join-Path $ScriptDir "requirements.txt"
if (Test-Path $ReqPath) {
    pip install -r $ReqPath
}

Write-Host "`n🎉 Setup Complete!" -ForegroundColor Green
Write-Host "You can now run 'kittlibr' from ANY directory or PowerShell prompt." -ForegroundColor Yellow
