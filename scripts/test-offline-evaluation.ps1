$ErrorActionPreference = 'Stop'
$project = 'recomai-eval-' + [guid]::NewGuid().ToString('N').Substring(0,12)
$local = Join-Path $PSScriptRoot "../backend/target/$project"
New-Item -ItemType Directory -Path $local | Out-Null
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $env:MYSQL_DATABASE = 'recomai_eval_synthetic'
    $env:DB_USERNAME = 'eval_fixture'
    $env:DB_PASSWORD = [guid]::NewGuid().ToString('N')
    $env:MYSQL_ROOT_PASSWORD = [guid]::NewGuid().ToString('N')
    $env:MYSQL_HOST_PORT = '0'
    $env:APP_HOST_PORT = '0'
    docker compose -p $project up -d --build --wait backend
    if ($LASTEXITCODE) { throw 'Isolated startup failed' }
    $sql = @'
INSERT INTO users(username,email,password_hash,display_name) VALUES ('fixture','fixture@example.invalid','disabled','Fixture');
INSERT INTO interactions(user_id,item_id,interaction_type) SELECT 1,MIN(item_id),'VIEW' FROM items WHERE item_id > 1;
SELECT MIN(item_id) FROM items WHERE is_active = TRUE;
'@
    $item = $sql | docker compose -p $project exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -u "$MYSQL_USER" "$MYSQL_DATABASE"'
    if ($LASTEXITCODE) { throw 'Synthetic fixture creation failed' }
    $cut = [DateTimeOffset]::FromUnixTimeSeconds([DateTimeOffset]::UtcNow.ToUnixTimeSeconds()+1)
    $stamp = 'yyyy-MM-ddTHH:mm:ssZ'
    $outcomes = Join-Path $local 'outcomes.tsv'
    $row = "1`t$item`t" + $cut.AddHours(1).ToString($stamp) + "`tSAVE"
    [IO.File]::WriteAllText($outcomes,$row)
    $jar = Join-Path $local 'application.jar'
    docker compose -p $project cp backend:/app/application.jar $jar
    if ($LASTEXITCODE) { throw 'Could not copy verified JAR' }
    $port = (docker compose -p $project port mysql 3306).Split(':')[-1]
    $env:EVAL_USER = $env:DB_USERNAME
    $env:EVAL_PASSWORD = $env:DB_PASSWORD
    $report = java '-Dloader.main=com.guvi.recommendationsystem.evaluation.OfflineEvaluation' -cp $jar org.springframework.boot.loader.launch.PropertiesLauncher SYNTHETIC $env:MYSQL_DATABASE $port $cut.ToString($stamp) $cut.AddDays(1).ToString($stamp) $outcomes
    if ($LASTEXITCODE -or -not ($report -match 'Eligible users 1$')) {
        throw 'Synthetic evaluation failed'
    }
    $report
} finally {
    docker compose -p $project down
    Pop-Location
}
