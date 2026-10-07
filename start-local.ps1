$ErrorActionPreference = 'Stop'

Push-Location $PSScriptRoot
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI was not found. Install and start Docker Desktop, then run this command again.'
    }

    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Engine is not running. Start Docker Desktop, wait for it to finish starting, then retry.'
    }

    $envFile = Join-Path $PSScriptRoot '.env'
    if (-not (Test-Path -LiteralPath $envFile)) {
        $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $appBytes = New-Object byte[] 36
            $rootBytes = New-Object byte[] 36
            $random.GetBytes($appBytes)
            $random.GetBytes($rootBytes)
        }
        finally {
            $random.Dispose()
        }

        $appPassword = [Convert]::ToBase64String($appBytes).TrimEnd('=').Replace('+', 'A').Replace('/', 'B')
        $rootPassword = [Convert]::ToBase64String($rootBytes).TrimEnd('=').Replace('+', 'C').Replace('/', 'D')
        $contents = @(
            'MYSQL_DATABASE=ai_recommendation_system'
            'DB_USERNAME=recommendation_app'
            "DB_PASSWORD=$appPassword"
            "MYSQL_ROOT_PASSWORD=$rootPassword"
            'MYSQL_HOST_PORT=3307'
            'APP_HOST_PORT=8080'
            'SESSION_COOKIE_SECURE=false'
        ) -join "`n"
        [System.IO.File]::WriteAllText($envFile, $contents + "`n", (New-Object System.Text.UTF8Encoding($false)))
        Write-Host 'Created a local .env file with randomly generated passwords.'
    }

    & docker compose up --build --wait
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose could not start the application. Review the output above.'
    }

    Write-Host 'Application is ready at http://localhost:8080/'
}
finally {
    Pop-Location
}
