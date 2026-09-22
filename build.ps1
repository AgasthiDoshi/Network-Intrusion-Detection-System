$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $javaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { '' }
    $javac = if ($javaBin) { Join-Path $javaBin 'javac.exe' } else { 'javac' }
    $jar = if ($javaBin) { Join-Path $javaBin 'jar.exe' } else { 'jar' }
    New-Item -ItemType Directory -Force 'build/classes' | Out-Null
    $sources = Get-ChildItem 'src/nids/*.java' | ForEach-Object { $_.FullName }
    & $javac --release 17 -encoding UTF-8 -d 'build/classes' $sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed. Install JDK 17 or newer.' }
    & $jar --create --file 'build/nids-demo.jar' --main-class nids.Main -C 'build/classes' .
    if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
    Write-Output 'Built build/nids-demo.jar'
} finally { Pop-Location }
