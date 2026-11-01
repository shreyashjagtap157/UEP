# ==============================================================================
# Universal Education Platform (UEP) Development Startup Script
# ==============================================================================
# Starts both the Frontend (Vite) and Backend (Spring Boot) for the UEP project.
# Press Ctrl+C to stop both processes gracefully.
# ==============================================================================

Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host "  Universal Education Platform (UEP) Development Startup" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Cyan

Write-Host "`nStarting UEP development environment...`n" -ForegroundColor Gray

# Configuration
$backendDir = "C:\Users\siddh\Downloads\ABC\UEP\apps\platform-server"
$frontendDir = "C:\Users\siddh\Downloads\ABC\UEP\apps\web"

# Variables to track background jobs
$backendJob = $null
$frontendJob = $null

# Function to start backend
function Start-Backend {
    Write-Host "-> Starting Backend (Spring Boot in background)..." -ForegroundColor Yellow
    $cmd = "cd `$$backendDir && mvn spring-boot:run 2>&1 | Out-File -Encoding UTF8 backend.log"
    $backendJob = Start-Job -ScriptBlock { pushd $using:backendDir; mvn spring-boot:run 2>&1 | Out-File -Encoding UTF8 backend.log; popd } -ArgumentList $backendDir
    Start-Sleep -Seconds 5
    Write-Host "   Backend job started (PID: $($backendJob.ID))" -ForegroundColor Gray
}

# Function to start frontend
function Start-Frontend {
    Write-Host "-> Starting Frontend (Vite in background)..." -ForegroundColor Yellow
    $frontendJob = Start-Job -ScriptBlock { pushd $using:frontendDir; & npm run dev 2>&1 | Out-File -Encoding UTF8 frontend.log; popd } -ArgumentList $frontendDir
    Start-Sleep -Seconds 3
    Write-Host "   Frontend job started (PID: $($frontendJob.ID))" -ForegroundColor Gray
}

# Function to stop all processes
function Stop-All {
    Write-Host "`n-> Stopping all processes..." -ForegroundColor Red
    
    if ($backendJob) {
        Write-Host "   Stopping backend job..." -ForegroundColor Yellow
        Remove-Job -Job $backendJob -Force -ErrorAction SilentlyContinue
    }
    
    if ($frontendJob) {
        Write-Host "   Stopping frontend job..." -ForegroundColor Yellow
        Remove-Job -Job $frontendJob -Force -ErrorAction SilentlyContinue
    }
    
    # Wait for jobs to fully stop
    Start-Sleep -Seconds 2
    
    # Clean up log files
    Remove-Item "C:\Users\siddh\Downloads\ABC\UEP\apps\platform-server\backend.log" -ErrorAction SilentlyContinue
    Remove-Item "C:\Users\siddh\Downloads\ABC\UEP\apps\web\frontend.log" -ErrorAction SilentlyContinue
    
    Write-Host "   All processes stopped." -ForegroundColor Cyan
}

# Write instructions
Write-Host "Press Ctrl+C at any time to stop both servers.`n" -ForegroundColor Gray

# Start both services
Start-Backend
Start-Frontend

Write-Host "`nBoth servers are running:`n" -ForegroundColor Green
Write-Host "  Backend:  http://localhost:8080" -ForegroundColor Gray
Write-Host "  Frontend: http://localhost:5173`n" -ForegroundColor Gray
Write-Host "  Press Ctrl+C to stop both servers and exit..." -ForegroundColor Gray

# Keep the script running until Ctrl+C
try {
    # Loop until Ctrl+C is pressed
    while ($true) {
        Start-Sleep -Seconds 1 | Out-Null
    }
}
catch {
    # Ctrl+C was pressed or script was interrupted
    Write-Host "`n-> Interrupted. Shutting down..." -ForegroundColor Yellow
}

# Cleanup on exit - stop all jobs
Stop-All

Write-Host "Goodbye!" -ForegroundColor Cyan