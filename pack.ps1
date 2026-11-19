$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$ErrorActionPreference = 'Stop'
$here = [IO.Path]::GetFullPath($here)
$src = Join-Path $here 'src'
$out = Join-Path $here 'submission.zip'
$backendOut = Join-Path $src 'backend.zip'
$temporary = Join-Path $here ('submission-' + [Guid]::NewGuid().ToString('N') + '.tmp')

# 控制台输出用 UTF-8，避免中文乱码
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if (-not (Test-Path $src)) {
    Write-Host "[错误] 没有找到 src 文件夹: $src" -ForegroundColor Red
    exit 1
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$files = @(Get-ChildItem -LiteralPath $src -Recurse -File | Where-Object {
    $_.Extension -eq '.java' -or $_.FullName -eq (Join-Path $src 'config.json')
} | Sort-Object FullName)
if (-not (Test-Path -LiteralPath (Join-Path $src 'Compiler.java'))) { throw 'Compiler.java is missing.' }
try {
    $zip = [System.IO.Compression.ZipFile]::Open($temporary, 'Create')
    try {
        foreach ($file in $files) {
            $rel = $file.FullName.Substring($src.Length + 1).Replace('\', '/')
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, $rel) | Out-Null
        }
    } finally { $zip.Dispose() }
    Copy-Item -LiteralPath $temporary -Destination $out -Force
    Copy-Item -LiteralPath $temporary -Destination $backendOut -Force
} finally {
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
}

Write-Host ("[OK] 打包完成 ({0} 个源码/配置文件): {1}" -f $files.Count, $out) -ForegroundColor Green
Write-Host ("同步提交包: {0}" -f $backendOut)
Write-Host ("SHA256: {0}" -f (Get-FileHash -LiteralPath $out -Algorithm SHA256).Hash)
