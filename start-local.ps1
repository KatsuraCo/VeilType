$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$python = Get-Command python -ErrorAction SilentlyContinue
if ($python) {
    Start-Process "http://localhost:4173"
    python -m http.server 4173
    exit 0
}

Start-Process (Join-Path $root "index.html")
