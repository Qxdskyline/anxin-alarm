param(
    [Parameter(Mandatory=$true)][string]$Jdk,
    [Parameter(Mandatory=$true)][string]$JsonJar
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force "$PSScriptRoot\work","$PSScriptRoot\work\testclasses" | Out-Null
$sources = @(Get-ChildItem "$PSScriptRoot\tests" -Recurse -Filter *.java | ForEach-Object FullName) + @('Alarm.java','AlarmPlan.java','AlarmSchedule.java','AlarmExecution.java','AlarmJson.java','AlarmRepository.java','AlarmScheduler.java','AlarmController.java','AlarmText.java','BoundedStreams.java','AudioFiles.java','PlaybackSessions.java','Schedule.java','HolidayCalendar.java','AlarmReceiver.java','AlarmWakeBridge.java','AlarmTrace.java','AlarmDelivery.java','AlarmProtectionPolicy.java' | ForEach-Object { "$PSScriptRoot\src\cn\local\oneshotalarm\$_" })
& "$Jdk\bin\javac.exe" -encoding UTF-8 --release 8 -classpath $JsonJar -d "$PSScriptRoot\work\testclasses" @sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
Push-Location $PSScriptRoot
try {
    & "$Jdk\bin\java.exe" -cp "$PSScriptRoot\work\testclasses;$JsonJar" cn.local.oneshotalarm.AlarmTests "$PSScriptRoot\2026年中国大陆调休日历.json"
    if ($LASTEXITCODE -ne 0) { throw 'Behavior tests failed' }
    & "$Jdk\bin\java.exe" -cp "$PSScriptRoot\work\testclasses;$JsonJar" cn.local.oneshotalarm.RefactorTests
    if ($LASTEXITCODE -ne 0) { throw 'Refactor tests failed' }
    & "$Jdk\bin\java.exe" -cp "$PSScriptRoot\work\testclasses;$JsonJar" cn.local.oneshotalarm.DeliveryTests
    if ($LASTEXITCODE -ne 0) { throw 'Delivery checks failed' }
    & "$Jdk\bin\java.exe" -cp "$PSScriptRoot\work\testclasses;$JsonJar" cn.local.oneshotalarm.DirectDeliveryTests
    if ($LASTEXITCODE -ne 0) { throw 'Direct delivery checks failed' }
    & "$Jdk\bin\java.exe" -cp "$PSScriptRoot\work\testclasses;$JsonJar" cn.local.oneshotalarm.RecoveryTests
    if ($LASTEXITCODE -ne 0) { throw 'Recovery checks failed' }
    & "$Jdk\bin\java.exe" -cp "$PSScriptRoot\work\testclasses;$JsonJar" cn.local.oneshotalarm.PlaylistTraceTests
    if ($LASTEXITCODE -ne 0) { throw 'Playlist and trace checks failed' }
} finally { Pop-Location }
