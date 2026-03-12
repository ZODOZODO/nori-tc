param(
    [Parameter(Mandatory = $true)]
    [string]$WorkingDirectory,

    [Parameter(Mandatory = $true)]
    [string]$StdoutLogPath,

    [Parameter(Mandatory = $true)]
    [string]$StderrLogPath,

    [Parameter(Mandatory = $true)]
    [string]$ArgumentFilePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ConvertTo-ProcessArgument {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Value
    )

    if ($Value.Length -eq 0) {
        return '""'
    }

    if ($Value -notmatch '[\s"]') {
        return $Value
    }

    $escapedValue = $Value -replace '(\\*)"', '$1$1\"'
    $escapedValue = $escapedValue -replace '(\\+)$', '$1$1'
    return '"' + $escapedValue + '"'
}

if (-not (Test-Path -LiteralPath $ArgumentFilePath)) {
    throw "Java argument file was not found: $ArgumentFilePath"
}

$javaArguments = Get-Content -LiteralPath $ArgumentFilePath
$utf8Encoding = New-Object System.Text.UTF8Encoding($false)
$script:ioLock = New-Object object
$script:stdoutWriter = New-Object System.IO.StreamWriter($StdoutLogPath, $true, $utf8Encoding)
$script:stderrWriter = New-Object System.IO.StreamWriter($StderrLogPath, $true, $utf8Encoding)
$process = New-Object System.Diagnostics.Process

try {
    $process.StartInfo.FileName = 'java'
    $process.StartInfo.WorkingDirectory = $WorkingDirectory
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $process.StartInfo.CreateNoWindow = $true
    $process.StartInfo.Arguments = (($javaArguments | ForEach-Object { ConvertTo-ProcessArgument -Value $_ }) -join ' ')

    $stdoutHandler = [System.Diagnostics.DataReceivedEventHandler]{
        param($sender, $eventArgs)

        if ($null -eq $eventArgs.Data) {
            return
        }

        [System.Threading.Monitor]::Enter($script:ioLock)
        try {
            [Console]::Out.WriteLine($eventArgs.Data)
            $script:stdoutWriter.WriteLine($eventArgs.Data)
            $script:stdoutWriter.Flush()
        } finally {
            [System.Threading.Monitor]::Exit($script:ioLock)
        }
    }

    $stderrHandler = [System.Diagnostics.DataReceivedEventHandler]{
        param($sender, $eventArgs)

        if ($null -eq $eventArgs.Data) {
            return
        }

        [System.Threading.Monitor]::Enter($script:ioLock)
        try {
            [Console]::Error.WriteLine($eventArgs.Data)
            $script:stderrWriter.WriteLine($eventArgs.Data)
            $script:stderrWriter.Flush()
        } finally {
            [System.Threading.Monitor]::Exit($script:ioLock)
        }
    }

    $process.add_OutputDataReceived($stdoutHandler)
    $process.add_ErrorDataReceived($stderrHandler)

    if (-not $process.Start()) {
        throw 'Failed to start java process.'
    }

    $process.BeginOutputReadLine()
    $process.BeginErrorReadLine()
    $process.WaitForExit()
    $process.WaitForExit()

    exit $process.ExitCode
} finally {
    if ($null -ne $process) {
        $process.Dispose()
    }

    if ($null -ne $script:stdoutWriter) {
        $script:stdoutWriter.Dispose()
    }

    if ($null -ne $script:stderrWriter) {
        $script:stderrWriter.Dispose()
    }
}
