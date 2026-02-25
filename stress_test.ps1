# ============================================
# FlashSale 秒杀系统 - 并发压测脚本
# 模拟多用户并发抢购场景
# ============================================
# 使用方法:
#   .\stress_test.ps1 -Users 50 -GoodsId 3
# ============================================

param(
    [int]$Users = 20,          # 并发用户数
    [int]$GoodsId = 3,         # 秒杀商品ID (推荐用库存200的AirPods)
    [string]$BaseUrl = "http://localhost:8080/api"
)

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " FlashSale Stress Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Concurrent Users : $Users"
Write-Host " Target Goods ID  : $GoodsId"
Write-Host " API Base URL     : $BaseUrl"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Register test users and get tokens
Write-Host "[1/4] Registering test users..." -ForegroundColor Yellow
$tokens = @()
for ($i = 1; $i -le $Users; $i++) {
    $phone = "139" + $i.ToString().PadLeft(8, '0')
    $username = "stress_user_$i"

    # Register
    $regBody = @{ username = $username; phone = $phone; password = "e10adc3949ba59abbe56e057f20f883e" } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "$BaseUrl/user/register" -Method Post -Body $regBody -ContentType 'application/json' -ErrorAction SilentlyContinue | Out-Null
    }
    catch { }

    # Login
    $loginBody = @{ phone = $phone; password = "e10adc3949ba59abbe56e057f20f883e" } | ConvertTo-Json
    try {
        $loginRes = Invoke-RestMethod -Uri "$BaseUrl/user/login" -Method Post -Body $loginBody -ContentType 'application/json'
        if ($loginRes.code -eq 200) {
            $tokens += $loginRes.data.token
        }
    }
    catch {
        Write-Host "  User $i login failed" -ForegroundColor Red
    }

    if ($i % 10 -eq 0) {
        Write-Host "  Registered $i/$Users users..." -ForegroundColor Gray
    }
}

Write-Host "  Total tokens acquired: $($tokens.Count)" -ForegroundColor Green
Write-Host ""

# Step 2: Check initial stock (Use Admin Account)
Write-Host "[2/4] Checking initial stock..." -ForegroundColor Yellow
try {
    $adminLogin = Invoke-RestMethod -Uri "$BaseUrl/user/login" -Method Post -Body '{"phone":"13800138000","password":"e10adc3949ba59abbe56e057f20f883e"}' -ContentType 'application/json' -ErrorAction Stop
    $dashToken = "Bearer " + $adminLogin.data.token
}
catch {
    Write-Host "  Admin login failed. Make sure admin user 13800138000 exists." -ForegroundColor Red
    exit
}

$dash = Invoke-RestMethod -Uri "$BaseUrl/admin/dashboard" -Headers @{Authorization = $dashToken }
$targetGoods = $dash.data.stockDetails | Where-Object { $_.seckillGoodsId -eq $GoodsId }
$initialStock = $targetGoods.dbStock
Write-Host "  Goods: $($targetGoods.goodsName)"
Write-Host "  Initial DB Stock : $($targetGoods.dbStock)"
Write-Host "  Initial Redis Stock: $($targetGoods.redisStock)"
Write-Host ""

# Step 3: Launch concurrent seckill requests
Write-Host "[3/4] Launching $($tokens.Count) concurrent seckill requests..." -ForegroundColor Yellow
$startTime = Get-Date

$jobs = @()
foreach ($token in $tokens) {
    $jobs += Start-Job -ScriptBlock {
        param($BaseUrl, $Token, $GoodsId)
        $headers = @{ Authorization = "Bearer $Token" }
        $result = @{
            captcha = "unknown"
            path    = "unknown"
            seckill = "unknown"
            error   = ""
        }

        try {
            # Get captcha (we'll skip verification by directly trying path)
            # In real test, captcha would be solved. Here test the flow's error handling
            $captchaRes = Invoke-RestMethod -Uri "$BaseUrl/captcha/seckill/$GoodsId" -Headers $headers
            $result.captcha = "ok"

            # Try to get path with a random answer (will fail captcha check)
            # This tests the rate limiting and error handling
            try {
                $pathRes = Invoke-RestMethod -Uri "$BaseUrl/seckill/path/${GoodsId}?captcha=7" -Headers $headers -ErrorAction Stop
                $result.path = "ok"
                $path = $pathRes.data.path

                # Execute seckill
                $seckillRes = Invoke-RestMethod -Uri "$BaseUrl/seckill/$path/do/$GoodsId" -Method Post -Headers $headers -ErrorAction Stop
                $result.seckill = "ok"
            }
            catch {
                $errBody = $_.ErrorDetails.Message
                if ($errBody -match '"code":\s*(\d+)') {
                    $result.seckill = "error_$($Matches[1])"
                }
                else {
                    $result.seckill = "error"
                }
                $result.error = $_.Exception.Message
            }
        }
        catch {
            $result.error = $_.Exception.Message
        }

        return $result
    } -ArgumentList $BaseUrl, $token, $GoodsId
}

# Wait for all jobs
Write-Host "  Waiting for all requests to complete..." -ForegroundColor Gray
$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job

$endTime = Get-Date
$duration = ($endTime - $startTime).TotalSeconds

Write-Host "  Completed in $([math]::Round($duration, 2)) seconds" -ForegroundColor Green
Write-Host ""

# Step 4: Analyze results
Write-Host "[4/4] Results Analysis" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

$captchaOk = ($results | Where-Object { $_.captcha -eq "ok" }).Count
$seckillOk = ($results | Where-Object { $_.seckill -eq "ok" }).Count
$captchaErr = ($results | Where-Object { $_.seckill -like "error_3007" }).Count
$stockEmpty = ($results | Where-Object { $_.seckill -like "error_3004" }).Count
$repeatErr = ($results | Where-Object { $_.seckill -like "error_3003" }).Count
$limitErr = ($results | Where-Object { $_.seckill -like "error_3005" }).Count
$otherErr = ($results | Where-Object { $_.seckill -like "error*" -and $_.seckill -notlike "error_300*" }).Count

Write-Host "  Total Requests    : $($tokens.Count)"
Write-Host "  Duration          : $([math]::Round($duration, 2))s"
Write-Host "  QPS               : $([math]::Round($tokens.Count / $duration, 1))"
Write-Host "  --------------------------------"
Write-Host "  Captcha OK        : $captchaOk" -ForegroundColor Green
Write-Host "  Seckill Success   : $seckillOk" -ForegroundColor Green
Write-Host "  Captcha Error     : $captchaErr" -ForegroundColor Yellow
Write-Host "  Stock Empty       : $stockEmpty" -ForegroundColor Yellow
Write-Host "  Repeat Order      : $repeatErr" -ForegroundColor Yellow
Write-Host "  Rate Limited      : $limitErr" -ForegroundColor Red
Write-Host "  Other Errors      : $otherErr" -ForegroundColor Red

# Check final stock
Start-Sleep -Seconds 3
$dashFinal = Invoke-RestMethod -Uri "$BaseUrl/admin/dashboard" -Headers @{Authorization = $dashToken }
$finalGoods = $dashFinal.data.stockDetails | Where-Object { $_.seckillGoodsId -eq $GoodsId }
Write-Host "  --------------------------------"
Write-Host "  Final DB Stock    : $($finalGoods.dbStock) (was $initialStock)"
Write-Host "  Final Redis Stock : $($finalGoods.redisStock)"
Write-Host "  Stock Consumed    : $($initialStock - $finalGoods.dbStock)"
Write-Host "  DB/Redis Match    : $(if($finalGoods.dbStock -eq $finalGoods.redisStock){'YES'}else{'NO - MISMATCH!'})" -ForegroundColor $(if ($finalGoods.dbStock -eq $finalGoods.redisStock) { 'Green' }else { 'Red' })

# Order stats
Write-Host "  --------------------------------"
Write-Host "  Total Orders      : $($dashFinal.data.orders.total)"
Write-Host "  Unpaid Orders     : $($dashFinal.data.orders.unpaid)"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
