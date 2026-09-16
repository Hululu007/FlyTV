$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$src = Join-Path $root "src"
$out = Join-Path $root "classes"
$libs = Join-Path (Split-Path $root) "libs"
$jarOut = Join-Path $root "jar-host.jar"

if (-not (Test-Path $src)) { Write-Output "缺少 src 目录"; exit 1 }
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out | Out-Null

# 查找 javac（PATH → 常见 JDK 安装位置）
$javac = "javac"
$found = Get-Command javac -ErrorAction SilentlyContinue
if ($found) { $javac = $found.Source }
else {
    foreach ($jdk in @("$env:USERPROFILE\.jdks\corretto-1.8.0_442", "$env:USERPROFILE\.jdks\corretto-17.0.14", "C:\Program Files\Amazon Corretto\jdk11.0.22_7")) {
        if (Test-Path (Join-Path $jdk "bin\javac.exe")) { $javac = Join-Path $jdk "bin\javac.exe"; break }
    }
}
Write-Output "javac: $javac"

$files = Get-ChildItem "$src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -source 8 -target 8 -nowarn -cp "$libs\*" -d $out $files
if ($LASTEXITCODE -ne 0) { Write-Output "编译失败"; exit 1 }

if (Test-Path $jarOut) { Remove-Item $jarOut -Force }
$jarExe = $javac -replace "javac.exe", "jar.exe"
& $jarExe cf $jarOut -C $out .
if ($LASTEXITCODE -ne 0) { Write-Output "打包失败"; exit 1 }
Write-Output "构建完成: $jarOut"
