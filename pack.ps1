$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = Join-Path $here 'src'
$out = Join-Path $here 'submission.zip'

# 控制台输出用 UTF-8，避免中文乱码
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if (-not (Test-Path $src)) {
    Write-Host "[错误] 没有找到 src 文件夹: $src" -ForegroundColor Red
    exit 1
}
if (Test-Path $out) { Remove-Item $out }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($out, 'Create')
$count = 0
Get-ChildItem $src -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($src.Length + 1).Replace('\', '/')
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $rel) | Out-Null
    $count++
}
$zip.Dispose()

Write-Host ("[OK] 打包完成 ({0} 个文件): {1}" -f $count, $out) -ForegroundColor Green
Write-Host "顶层: Compiler.java, config.json, error/, frontend/, midend/, utils/"
