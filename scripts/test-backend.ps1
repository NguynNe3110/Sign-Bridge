[CmdletBinding()]
param(
    [string]$BaseUrl = 'https://tartness-humbling-backboned.ngrok-free.dev',
    [ValidateSet('All', 'Health', 'Expand', 'Summarize', 'Sign')]
    [string]$Endpoint = 'All',
    [ValidateSet('CAFE', 'PHARMACY', 'PUBLIC', 'CUSTOM')]
    [string]$Context = 'PHARMACY',
    [string[]]$Chips = @('Đau đầu', 'Không buồn ngủ', 'Uống sau ăn'),
    [string]$PreviousIncoming,
    [string]$PreviousOutgoing,
    [string]$AudioPath,
    [string[]]$Frames = @(),
    [string[]]$PersonalChips = @(),
    [ValidateRange(1, 300)]
    [int]$TimeoutSeconds = 30,
    [string]$OutputDirectory,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $utf8
$curl = (Get-Command curl.exe -ErrorAction Stop).Source
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $PSScriptRoot '../build/backend-curl' }
$base = $null
if (-not [Uri]::TryCreate($BaseUrl, [UriKind]::Absolute, [ref]$base) -or
    $base.Scheme -notin @('http', 'https') -or $base.UserInfo -or $base.Query -or $base.Fragment -or
    $base.AbsolutePath -ne '/') {
    throw 'BaseUrl must be an http(s) origin without /api, credentials, query or fragment.'
}
$BaseUrl = $base.AbsoluteUri.TrimEnd('/')
$runDirectory = Join-Path $OutputDirectory ((Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6))
[IO.Directory]::CreateDirectory($runDirectory) | Out-Null
$runDirectory = (Resolve-Path -LiteralPath $runDirectory).Path
$script:failures = 0
$script:results = @()

function Write-Utf8([string]$Path, [string]$Value) {
    [IO.File]::WriteAllText($Path, $Value, $utf8)
}

function Resolve-Media([string]$Path, [string[]]$Extensions, [long]$MaxBytes) {
    $file = Get-Item -LiteralPath $Path -ErrorAction Stop
    if ($file.PSIsContainer -or $file.Extension.ToLowerInvariant() -notin $Extensions -or
        $file.Length -eq 0 -or $file.Length -gt $MaxBytes) {
        throw "Invalid media file: $Path. Allowed: $($Extensions -join ', '), 1..$MaxBytes bytes."
    }
    return $file
}

# Curl's multipart parser needs quotes around paths containing spaces or semicolons.
function Form-Path([string]$Path) { return '"' + $Path.Replace('\', '/') + '"' }

function Invoke-Backend([string]$Name, [string]$Method, [string[]]$BodyArguments, [string]$RequestDescription) {
    $directory = Join-Path $runDirectory $Name
    [IO.Directory]::CreateDirectory($directory) | Out-Null
    $url = "$BaseUrl/api/$Name"
    $responseFile = Join-Path $directory 'response.json'
    $headerFile = Join-Path $directory 'response.headers.txt'
    $errorFile = Join-Path $directory 'curl.stderr.txt'
    $arguments = @('--silent', '--show-error', '--connect-timeout', '10', '--max-time', "$TimeoutSeconds",
        '--request', $Method, '--header', 'Accept: application/json',
        '--dump-header', $headerFile, '--output', $responseFile,
        '--write-out', "HTTP_STATUS=%{http_code}`nTIME_TOTAL=%{time_total}`n") + $BodyArguments + @($url)

    $command = '& curl.exe ' + (($arguments | ForEach-Object { "'" + $_.Replace("'", "''") + "'" }) -join (" " + [char]96 + "`r`n  "))
    # Reproducible PowerShell command, including all headers, body and file paths.
    [IO.File]::WriteAllText((Join-Path $directory 'request.curl.ps1'), $command + "`r`n", (New-Object System.Text.UTF8Encoding($true)))
    Write-Utf8 (Join-Path $directory 'request.txt') ("$Method $url`nAccept: application/json`n$RequestDescription")
    Write-Host "`n===== REQUEST: $Name =====" -ForegroundColor Cyan
    Write-Host "$Method $url"
    Write-Host $RequestDescription
    Write-Host "`n$command"
    if ($DryRun) {
        Write-Host 'DRY RUN: request prepared, no network call.'
        return
    }

    $previousErrorPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 otherwise turns curl's stderr into a terminating error.
        $ErrorActionPreference = 'Continue'
        $statistics = (& $curl @arguments 2> $errorFile) -join "`n"
        $curlExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousErrorPreference }
    Write-Utf8 (Join-Path $directory 'statistics.txt') $statistics
    $status = if ($statistics -match 'HTTP_STATUS=(\d+)') { [int]$Matches[1] } else { 0 }
    $raw = if (Test-Path -LiteralPath $responseFile) { [IO.File]::ReadAllText($responseFile, $utf8) } else { '' }
    Write-Host "`n===== RESPONSE: $Name (curl=$curlExit, HTTP=$status) =====" -ForegroundColor Yellow
    Write-Host $statistics
    Write-Host $raw
    if ((Test-Path -LiteralPath $errorFile) -and (Get-Item -LiteralPath $errorFile).Length -gt 0) {
        Write-Host ([IO.File]::ReadAllText($errorFile, $utf8))
    }
    $json = $null
    try { $json = $raw | ConvertFrom-Json -ErrorAction Stop } catch { }
    $valid = $false
    if ($null -ne $json) {
        switch ($Name) {
            'health' { $valid = $json.status -eq 'UP' }
            'expand' { $valid = $json.spoken_sentence -is [string] -and -not [string]::IsNullOrWhiteSpace($json.spoken_sentence) }
            'summarize' { $valid = $json.transcript -is [string] -and $json.summary -is [string] -and
                -not [string]::IsNullOrWhiteSpace($json.transcript) -and -not [string]::IsNullOrWhiteSpace($json.summary) -and
                @($json.quickReplies).Count -eq 3 }
            'sign' { $valid = @($json.glosses).Count -gt 0 -and $json.confidence -is [ValueType] -and
                $json.confidence -ge 0 -and $json.confidence -le 1 -and $json.spoken_sentence -is [string] }
        }
    }
    $passed = $curlExit -eq 0 -and $status -ge 200 -and $status -lt 300 -and $valid
    if (-not $passed) { $script:failures++ }
    $script:results += [PSCustomObject]@{ Endpoint = $Name; HttpStatus = $status; CurlExit = $curlExit; BasicResponseValid = $valid; Passed = $passed }
    Write-Host $(if ($passed) { 'PASS: HTTP and basic response fields.' } else { 'FAIL: transport, HTTP status or basic response fields.' })
}

Write-Host "Artifacts: $runDirectory"
Write-Host 'PASS checks transport and basic response fields only; it does not prove real AI is enabled.'
if ($Endpoint -in @('All', 'Health')) { Invoke-Backend 'health' 'GET' @() '(no request body)' }
if ($Endpoint -in @('All', 'Expand')) {
    if ($Chips.Count -lt 1 -or $Chips.Count -gt 5 -or @($Chips | Where-Object { [string]::IsNullOrWhiteSpace($_) -or $_.Length -gt 50 }).Count -gt 0) {
        throw 'Provide 1..5 nonblank chips, at most 50 characters each (Android UI uses at most 3).'
    }
    $body = [ordered]@{ context = $Context; chips = @($Chips); previousIncoming = $(if ($PreviousIncoming) { $PreviousIncoming } else { $null }) } | ConvertTo-Json -Depth 5
    $bodyFile = Join-Path $runDirectory 'expand.request.json'
    Write-Utf8 $bodyFile $body
    Invoke-Backend 'expand' 'POST' @('--header', 'Content-Type: application/json; charset=utf-8', '--data-binary', "@$bodyFile") "Content-Type: application/json; charset=utf-8`n`n$body"
}
if ($Endpoint -eq 'Summarize' -and -not $AudioPath) { throw 'Summarize requires -AudioPath recording.m4a.' }
if ($Endpoint -in @('All', 'Summarize') -and $AudioPath) {
    $audio = Resolve-Media $AudioPath @('.m4a') (2 * 1024 * 1024)
    $fields = @('--form-string', "context=$Context", '--form', ('audio=@' + (Form-Path $audio.FullName) + ';filename=reply.m4a;type=audio/mp4'))
    $description = "Content-Type: multipart/form-data (boundary generated by curl)`ncontext=$Context`naudio=@$($audio.FullName); filename=reply.m4a; type=audio/mp4; bytes=$($audio.Length)"
    if ($PreviousOutgoing) {
        $textFile = Join-Path $runDirectory 'previousOutgoing.txt'
        Write-Utf8 $textFile $PreviousOutgoing
        $fields += @('--form', ('previousOutgoing=<' + (Form-Path $textFile)))
        $description += "`npreviousOutgoing=$PreviousOutgoing"
    }
    Invoke-Backend 'summarize' 'POST' $fields $description
} elseif ($Endpoint -eq 'All') { Write-Host 'SKIP summarize: pass -AudioPath with a real M4A recording (up to 60 seconds, 2 MB).' }
if ($Endpoint -eq 'Sign' -and $Frames.Count -eq 0) { throw 'Sign requires -Frames with 4, 8, 12 or 16 JPEG paths in capture order.' }
if ($Endpoint -in @('All', 'Sign') -and $Frames.Count -gt 0) {
    if ($Frames.Count -notin @(4, 8, 12, 16)) { throw 'Provide 4, 8, 12 or 16 JPEG frames in capture order.' }
    if ($PersonalChips.Count -gt 20) { throw 'At most 20 personal chips are allowed.' }
    $personalFile = Join-Path $runDirectory 'personalChips.json'
    Write-Utf8 $personalFile (ConvertTo-Json -InputObject @($PersonalChips) -Compress)
    $fields = @('--form-string', "context=$Context", '--form', ('personalChips=<' + (Form-Path $personalFile)))
    $description = "Content-Type: multipart/form-data (boundary generated by curl)`ncontext=$Context`npersonalChips=$([IO.File]::ReadAllText($personalFile, $utf8))"
    for ($i = 0; $i -lt $Frames.Count; $i++) {
        $frame = Resolve-Media $Frames[$i] @('.jpg', '.jpeg') (250 * 1024)
        $fields += @('--form', ('frames=@' + (Form-Path $frame.FullName) + ";filename=frame_$i.jpg;type=image/jpeg"))
        $description += "`nframes[$i]=@$($frame.FullName); filename=frame_$i.jpg; type=image/jpeg; bytes=$($frame.Length)"
    }
    if ($PreviousIncoming) {
        $textFile = Join-Path $runDirectory 'previousIncoming.txt'
        Write-Utf8 $textFile $PreviousIncoming
        $fields += @('--form', ('previousIncoming=<' + (Form-Path $textFile)))
        $description += "`npreviousIncoming=$PreviousIncoming"
    }
    Invoke-Backend 'sign' 'POST' $fields $description
} elseif ($Endpoint -eq 'All') { Write-Host 'SKIP sign: pass -Frames with real JPEG frames, each at most 640px and 250 KB.' }

if (-not $DryRun) {
    Write-Utf8 (Join-Path $runDirectory 'results.json') (ConvertTo-Json -InputObject @($script:results) -Depth 5)
    $script:results | Format-Table -AutoSize | Out-Host
}
Write-Host "Saved requests and responses: $runDirectory"
if ($script:failures -gt 0) { exit 1 }
