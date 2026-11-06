param(
    [Parameter(Mandatory=$true)][string]$Jdk,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [Parameter(Mandatory=$true)][string]$Platform,
    [string]$BuildDirectory = "$PSScriptRoot\build",
    [string]$Apk = "$PSScriptRoot\安心闹钟.apk"
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"
function Run([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed: $LASTEXITCODE" }
}
New-Item -ItemType Directory -Force $BuildDirectory,"$BuildDirectory\gen","$BuildDirectory\classes","$BuildDirectory\dex","$PSScriptRoot\.signing" | Out-Null
Run "$BuildTools\aapt2.exe" @('compile','--dir',"$PSScriptRoot\res",'-o',"$BuildDirectory\resources.zip")
Run "$BuildTools\aapt2.exe" @('link','-o',"$BuildDirectory\unsigned.apk",'--manifest',"$PSScriptRoot\AndroidManifest.xml",'-I',"$Platform\android.jar",'--java',"$BuildDirectory\gen",'--auto-add-overlay',"$BuildDirectory\resources.zip")
$sources = @(Get-ChildItem "$PSScriptRoot\src","$BuildDirectory\gen" -Filter *.java -Recurse | ForEach-Object { $_.FullName })
Run "$Jdk\bin\javac.exe" (@('-encoding','UTF-8','--release','8','-classpath',"$Platform\android.jar",'-d',"$BuildDirectory\classes") + $sources)
Run "$Jdk\bin\jar.exe" @('cf',"$BuildDirectory\classes.jar",'-C',"$BuildDirectory\classes",'.')
Run "$BuildTools\d8.bat" @('--min-api','26','--lib',"$Platform\android.jar",'--output',"$BuildDirectory\dex","$BuildDirectory\classes.jar")
Run "$Jdk\bin\jar.exe" @('uf',"$BuildDirectory\unsigned.apk",'-C',"$BuildDirectory\dex",'classes.dex')
Run "$BuildTools\zipalign.exe" @('-f','-p','4',"$BuildDirectory\unsigned.apk","$BuildDirectory\aligned.apk")
$key = "$PSScriptRoot\.signing\release.p12"
if (!(Test-Path -LiteralPath $key)) {
    Run "$Jdk\bin\keytool.exe" @('-genkeypair','-keystore',$key,'-storetype','PKCS12','-storepass','oneshot-local-build','-keypass','oneshot-local-build','-alias','alarm','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=OneShot Alarm Local')
}
Run "$BuildTools\apksigner.bat" @('sign','--ks',$key,'--ks-pass','pass:oneshot-local-build','--key-pass','pass:oneshot-local-build','--out',$Apk,"$BuildDirectory\aligned.apk")
Run "$BuildTools\apksigner.bat" @('verify','--verbose',$Apk)
Run "$BuildTools\aapt2.exe" @('dump','badging',$Apk)
Get-FileHash -Algorithm SHA256 -LiteralPath $Apk
