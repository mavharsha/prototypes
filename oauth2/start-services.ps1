# PowerShell script to start OAuth2 services

Write-Host "Starting OAuth2 Services..." -ForegroundColor Green
Write-Host ""
Write-Host "Services:" -ForegroundColor Cyan
Write-Host "  - OAuth Server: http://localhost:4000" -ForegroundColor White
Write-Host "  - API Server: http://localhost:5000" -ForegroundColor White
Write-Host "  - Test Client: http://localhost:3001" -ForegroundColor White
Write-Host ""

# Check if Docker is installed
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "Error: Docker is not installed" -ForegroundColor Red
    exit 1
}

# Check if Docker Compose is installed
if (-not (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    Write-Host "Error: Docker Compose is not installed" -ForegroundColor Red
    exit 1
}

# Start services
Write-Host "Building and starting services..." -ForegroundColor Yellow
docker-compose up --build

# Cleanup
Write-Host "Stopping services..." -ForegroundColor Yellow
docker-compose down

