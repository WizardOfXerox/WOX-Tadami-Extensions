# copy_and_clean_apks.ps1
# Syncs compiled anime extensions to the organized legacy repo format and extracts icons.

$srcRoot = "H:\Ideas\Tadami-Extensions\aniyomi-extensions\src"
$distDir = "H:\Ideas\Tadami-Extensions\apks\anime"
$apkDir = Join-Path $distDir "apk"
$iconDir = Join-Path $distDir "icon"

# Ensure directories exist
if (-not (Test-Path $apkDir)) { New-Item -ItemType Directory -Path $apkDir -Force | Out-Null }
if (-not (Test-Path $iconDir)) { New-Item -ItemType Directory -Path $iconDir -Force | Out-Null }

# Load Zip assembly
[System.Reflection.Assembly]::LoadWithPartialName('System.IO.Compression.FileSystem') | Out-Null

# Get all compiled debug APKs
$compiledApks = Get-ChildItem -Path $srcRoot -Recurse -Filter "*-debug.apk" | Where-Object { $_.FullName -like "*\build\outputs\apk\debug\*" }

Write-Host "Syncing and cleaning APKs..."

# Load index to resolve package names for icons
$indexFile = Join-Path $distDir "index.min.json"
$entries = @()
if (Test-Path $indexFile) {
    $entries = ConvertFrom-Json (Get-Content $indexFile -Raw)
}

foreach ($apk in $compiledApks) {
    $name = $apk.Name
    $destName = $name -replace "-debug.apk", ".apk"
    
    $destSubPath = Join-Path $apkDir $destName
    $destRootPath = Join-Path $distDir $destName
    
    # Copy the file to both subfolder and root for 100% download URL compatibility
    Copy-Item -Path $apk.FullName -Destination $destSubPath -Force
    Copy-Item -Path $apk.FullName -Destination $destRootPath -Force
    Write-Host "Copied $name -> apk/$destName and $destName"
    
    # Extract icon
    $pkgName = $null
    if ($entries) {
        # Find match by APK name
        $match = $entries | Where-Object { $_.apk -eq $destName -or $_.apk -eq "apk/$destName" -or $_.apk -eq ($destName -replace "-debug.apk", ".apk") }
        if ($match) {
            $pkgName = $match.pkg
        }
    }
    
    # If package name is found, extract launcher icon
    if ($pkgName) {
        $iconPath = Join-Path $iconDir "$pkgName.png"
        try {
            $zip = [System.IO.Compression.ZipFile]::OpenRead($destSubPath)
            $candidates = $zip.Entries | Where-Object { 
                $_.FullName -match 'ic_launcher\.png$' -or 
                $_.FullName -match 'icon\.png$' 
            }
            
            $best = $candidates | Sort-Object -Property Length -Descending | Select-Object -First 1
            if ($best) {
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($best, $iconPath, $true)
                Write-Host "Extracted icon to icon/$pkgName.png"
            }
            $zip.Dispose()
        } catch {
            Write-Host "Failed to extract icon for ${destName}: $_" -ForegroundColor Yellow
        }
    }
    
    # Extract package suffix, e.g., "aniyomi-pt.animesdrive" from "aniyomi-pt.animesdrive-v14.8.apk"
    $baseName = $destName -replace "-v14\..*$", ""
    
    # Find all files matching the same extension prefix
    $existingSubFiles = Get-ChildItem -Path $apkDir -Filter "$baseName-v14.*"
    foreach ($file in $existingSubFiles) {
        if ($file.Name -ne $destName) {
            Remove-Item $file.FullName -Force
            Write-Host "Cleaned up obsolete file: $($file.Name)"
        }
    }
    
    $existingRootFiles = Get-ChildItem -Path $distDir -Filter "$baseName-v14.*"
    foreach ($file in $existingRootFiles) {
        if ($file.Name -ne $destName) {
            Remove-Item $file.FullName -Force
            Write-Host "Cleaned up obsolete root file: $($file.Name)"
        }
    }
}

Write-Host "Sync and cleanup completed successfully."
