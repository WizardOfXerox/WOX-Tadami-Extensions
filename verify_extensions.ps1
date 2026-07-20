# verify_extensions.ps1
# Automating layout/endpoint liveness and selector validation for custom extensions.

$ErrorActionPreference = "Stop"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Starting Automated Extension Verification Suite" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

$results = [System.Collections.Generic.List[PSObject]]::new()

function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Url,
        [hashtable]$Headers,
        [string]$Method = "GET",
        [string]$Body = $null,
        [string]$SelectorPattern = $null
    )

    Write-Host "Testing $Name..." -NoNewline
    $result = [PSCustomObject]@{
        Name     = $Name
        Status   = "FAILED"
        Details  = "Connection Error"
    }

    try {
        $params = @{
            Uri            = $Url
            Method         = $Method
            Headers        = $Headers
            UserAgent      = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            TimeoutSec     = 15
            UseBasicParsing = $true
        }
        if ($Body) {
            $params["Body"] = $Body
            $params["ContentType"] = "application/json"
        }

        $response = Invoke-WebRequest @params
        
        if ($response.StatusCode -ne 200) {
            $result.Details = "HTTP Status Code: $($response.StatusCode)"
            Write-Host " [FAIL]" -ForegroundColor Red
        } else {
            if ($SelectorPattern) {
                if ($response.Content -match $SelectorPattern) {
                    $result.Status = "SUCCESS"
                    $result.Details = "OK (Passed layout/data matches)"
                    Write-Host " [OK]" -ForegroundColor Green
                } else {
                    $result.Details = "Layout Match Failed: Expected pattern '$SelectorPattern' not found in page content."
                    Write-Host " [FAIL]" -ForegroundColor Red
                }
            } else {
                # Simple check for JSON parsing
                try {
                    $json = $response.Content | ConvertFrom-Json
                    if ($json) {
                        $result.Status = "SUCCESS"
                        $result.Details = "OK (JSON Parsed)"
                        Write-Host " [OK]" -ForegroundColor Green
                    } else {
                        $result.Details = "Response is empty or invalid JSON."
                        Write-Host " [FAIL]" -ForegroundColor Red
                    }
                } catch {
                    $result.Details = "Failed to parse JSON response: $_"
                    Write-Host " [FAIL]" -ForegroundColor Red
                }
            }
        }
    } catch {
        $result.Details = $_.Exception.Message
        Write-Host " [FAIL] ($_)" -ForegroundColor Red
    }

    $results.Add($result)
}

# 1. Pornhub Check
$phHeaders = @{
    "Cookie"  = "age_verified=1; accessAgeDisclaimerPH=1; accessAgeDisclaimerUK=1; accessPH=1"
    "Referer" = "https://www.pornhub.com/"
    "Origin"  = "https://www.pornhub.com"
}
Test-Endpoint -Name "Pornhub" -Url "https://www.pornhub.com/video?o=mv" -Headers $phHeaders -SelectorPattern "(pcVideoListItem|videoBox)"

# 2. XNXX Check
$xnxxHeaders = @{
    "Referer" = "https://www.xnxx.com/"
    "Origin"  = "https://www.xnxx.com"
}
Test-Endpoint -Name "XNXX" -Url "https://www.xnxx.com" -Headers $xnxxHeaders -SelectorPattern "thumb-block"

# 3. Hstream Check
$hsHeaders = @{}
Test-Endpoint -Name "Hstream" -Url "https://hstream.moe/search?order=view-count&page=1" -Headers $hsHeaders -SelectorPattern "/hentai/"

# 4. Loklok Check
$loklokHeaders = @{
    "lang"            = "en"
    "versioncode"     = "33"
    "clienttype"      = "android_tem3"
    "deviceid"        = "60A3305FDAAC489AAF4C7DD33B1483B4"
    "X-Forwarded-For" = "120.28.0.1"
    "Accept"          = "application/json"
}
Test-Endpoint -Name "Loklok" -Url "https://ga-mobile-api.loklok.tv/cms/app/homePage/getHome?page=0" -Headers $loklokHeaders

Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Summary Report" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

$results | Format-Table -AutoSize
