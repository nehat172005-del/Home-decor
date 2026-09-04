$ErrorActionPreference = 'Stop'

$existing = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host 'Stopping the previous HomeDecorStore server...'
    $existing | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
}

$securePassword = Read-Host 'Enter your MySQL root password' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:DB_URL = 'jdbc:mysql://localhost:3306/home_decor_store?useSSL=false&serverTimezone=UTC'
    $env:DB_USER = 'root'
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)

    javac -cp '.;lib/*' HomeDecorStore.java DBConnection.java
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    java -cp '.;lib/*' HomeDecorStore
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
}
