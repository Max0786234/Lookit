$userJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')

$env:JAVA_HOME = $userJavaHome
$env:Path = "$userPath;$machinePath"

Write-Host "Persisted JAVA_HOME=$userJavaHome"
Write-Host "Java executable:"
Get-Command java -ErrorAction Stop

java -version
javac -version

$localMaven = Join-Path (Split-Path -Parent $PSScriptRoot) 'tools\apache-maven-3.9.16\bin\mvn.cmd'
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    mvn -version
} elseif (Test-Path $localMaven) {
    & $localMaven -version
} else {
    throw 'Maven was not found on PATH or in tools/apache-maven-3.9.16.'
}
