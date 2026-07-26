# Database Backup & Retention Script
# Usage: .\backup_db.ps1 -DatabaseUrl "postgres://user:password@host:port/dbname"

param (
    [string]$DatabaseUrl = $env:DATABASE_URL,
    [string]$BackupDir = "backups",
    [int]$RetentionDays = 7
)

# Enable UTF-8 Output
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   PostgreSQL Database Backup Script" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

if (-not $DatabaseUrl) {
    Write-Error "Database connection URL must be provided via -DatabaseUrl parameter or DATABASE_URL environment variable."
    Exit 1
}

# Ensure backup directory exists
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir | Out-Null
    Write-Host "Created backup directory: $BackupDir" -ForegroundColor Green
}

# Parse PostgreSQL URL
# Formats supported:
#   postgres://user:password@host:port/dbname
#   postgresql://user:password@host:port/dbname
$user = ""
$pass = ""
$hostName = ""
$port = "5432"
$dbName = ""

if ($DatabaseUrl -match "postgres(?:ql)?://([^:]+):([^@]+)@([^:/]+)(?::(\d+))?/([^?]+)") {
    $user = $Matches[1]
    $pass = $Matches[2]
    $hostName = $Matches[3]
    if ($Matches[4]) { $port = $Matches[4] }
    $dbName = $Matches[5]
} else {
    Write-Error "Unsupported Database URL format. Expected: postgres://user:password@host:port/dbname"
    Exit 1
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFile = Join-Path $BackupDir "backup_$($dbName)_$timestamp.dump"

Write-Host "Parsed connection parameters:"
Write-Host "  Host: $hostName"
Write-Host "  Port: $port"
Write-Host "  Database: $dbName"
Write-Host "  User: $user"

# Set password for pg_dump without interactive prompt using environment variable
$env:PGPASSWORD = $pass

# Build pg_dump arguments
# -F c: Custom compressed format (standard for postgres restore)
$pgDumpArgs = @(
    "--host=$hostName",
    "--port=$port",
    "--username=$user",
    "--format=c",
    "--file=$backupFile",
    $dbName
)

Write-Host "Starting pg_dump..." -ForegroundColor Yellow
try {
    # Check if pg_dump is available on the system PATH
    $pgDumpPath = Get-Command pg_dump -ErrorAction SilentlyContinue
    if (-not $pgDumpPath) {
        # Try default PostgreSQL installation path on Windows
        $pgPaths = @(
            "C:\Program Files\PostgreSQL\*\bin\pg_dump.exe",
            "C:\Program Files (x86)\PostgreSQL\*\bin\pg_dump.exe"
        )
        $found = Get-Item $pgPaths -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $pgDumpPath = $found.FullName
        } else {
            throw "pg_dump command not found on PATH or default PostgreSQL paths. Please install PostgreSQL client tools."
        }
    }

    & $pgDumpPath $pgDumpArgs
    
    if ($LASTEXITCODE -eq 0) {
        $fileSize = (Get-Item $backupFile).Length
        Write-Host "Backup completed successfully! Created file:" -ForegroundColor Green
        Write-Host "  $backupFile ($($fileSize / 1KB) KB)" -ForegroundColor Green
    } else {
        throw "pg_dump failed with exit code $LASTEXITCODE"
    }
} catch {
    Write-Error "Backup failed: $_"
    # Clean up incomplete file if any
    if (Test-Path $backupFile) { Remove-Item $backupFile }
    Exit 1
} finally {
    # Clear password environment variable
    $env:PGPASSWORD = $null
}

# Housekeeping: Retention cleanup
Write-Host "`nPerforming retention checks (cleaning backups older than $RetentionDays days)..." -ForegroundColor Yellow
$limitDate = (Get-Date).AddDays(-$RetentionDays)
$files = Get-ChildItem -Path $BackupDir -Filter "backup_$($dbName)_*.dump"

$deletedCount = 0
foreach ($file in $files) {
    if ($file.LastWriteTime -lt $limitDate) {
        Remove-Item $file.FullName -Force
        Write-Host "  Pruned old backup: $($file.Name)" -ForegroundColor Gray
        $deletedCount++
    }
}

if ($deletedCount -eq 0) {
    Write-Host "No old backups needed pruning." -ForegroundColor Gray
} else {
    Write-Host "Successfully pruned $deletedCount backup file(s)." -ForegroundColor Green
}

Write-Host "==========================================" -ForegroundColor Cyan
