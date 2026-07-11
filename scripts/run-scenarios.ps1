# Runs every scenario in test-data/calculation-scenarios.json against a running
# service instance and compares the response with the expected values.
#
# Usage:  powershell -File scripts\run-scenarios.ps1 [-BaseUrl http://localhost:8080]

param(
    [string]$BaseUrl = "http://localhost:8080"
)

$scenariosPath = Join-Path $PSScriptRoot "..\test-data\calculation-scenarios.json"
$scenarios = Get-Content $scenariosPath -Raw | ConvertFrom-Json

$passed = 0
$failed = 0

foreach ($s in $scenarios) {
    $body = $s.request | ConvertTo-Json -Depth 5
    try {
        $resp = Invoke-RestMethod -Uri "$BaseUrl/tax/calculate" -Method Post -Body $body -ContentType "application/json"
    } catch {
        Write-Host ("FAIL {0}: HTTP error - {1}" -f $s.id, $_.Exception.Message) -ForegroundColor Red
        $failed++
        continue
    }

    $errors = @()
    if ([math]::Abs($resp.taxRate - $s.expected.taxRate) -gt 0.001) {
        $errors += "taxRate expected $($s.expected.taxRate) got $($resp.taxRate)"
    }
    if ([math]::Abs($resp.taxAmount - $s.expected.taxAmount) -gt 0.011) {
        $errors += "taxAmount expected $($s.expected.taxAmount) got $($resp.taxAmount)"
    }
    if ([math]::Abs($resp.totalAmount - $s.expected.totalAmount) -gt 0.011) {
        $errors += "totalAmount expected $($s.expected.totalAmount) got $($resp.totalAmount)"
    }
    if ($resp.taxJurisdiction -ne $s.expected.taxJurisdiction) {
        $errors += "taxJurisdiction expected $($s.expected.taxJurisdiction) got $($resp.taxJurisdiction)"
    }
    if ($null -ne $s.expected.reportingTaxAmount) {
        if ($null -eq $resp.reporting) {
            $errors += "expected reporting block, got none"
        } elseif ([math]::Abs($resp.reporting.taxAmount - $s.expected.reportingTaxAmount) -gt 0.011) {
            $errors += "reporting.taxAmount expected $($s.expected.reportingTaxAmount) got $($resp.reporting.taxAmount)"
        }
    }

    if ($errors.Count -eq 0) {
        Write-Host ("PASS {0}: {1}" -f $s.id, $s.description) -ForegroundColor Green
        $passed++
    } else {
        Write-Host ("FAIL {0}: {1} -> {2}" -f $s.id, $s.description, ($errors -join "; ")) -ForegroundColor Red
        $failed++
    }
}

Write-Host ""
Write-Host ("Result: {0}/{1} scenarios passed" -f $passed, ($passed + $failed)) -ForegroundColor Cyan
if ($failed -gt 0) { exit 1 }
