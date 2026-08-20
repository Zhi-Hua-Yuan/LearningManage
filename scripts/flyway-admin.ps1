param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('info', 'validate', 'baseline', 'migrate')]
    [string]$Action
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$mavenWrapper = Join-Path $projectRoot 'mvnw.cmd'

foreach ($name in 'DB_HOST', 'DB_PORT', 'DB_NAME', 'FLYWAY_DB_USERNAME', 'FLYWAY_DB_PASSWORD') {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name is required; credentials must be supplied through the protected environment"
    }
}

& $mavenWrapper '-q' '-DskipTests' 'spring-boot:run' `
    '-Dspring-boot.run.main-class=com.spt.learningmanage.flyway.FlywayAdmin' `
    "-Dspring-boot.run.arguments=$Action"

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
