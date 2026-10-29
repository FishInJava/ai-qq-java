#Requires -Version 7.0
<#
.SYNOPSIS
    启发式扫描 Java 测试中的「AI 自欺欺人」与低精确率信号。

.DESCRIPTION
    按测试方法统计：边界值、异常路径、参数化、纯 mock 断言、仅 not-null 断言、
    占位/无断言、实现耦合、mock 重、Thread.sleep 等。
    结果仅用于缩小人工复核范围，不能直接当作最终结论。

.PARAMETER Root
    仓库根目录，默认当前目录。

.PARAMETER TestsPath
    显式指定一个或多个 src/test/java 目录。缺省时自动在 Root 下查找。

.PARAMETER Json
    输出 JSON（便于 Agent 解析），不输出人类可读文本。

.PARAMETER MaxExamples
    人类可读输出中最多列出的方法数，默认 200。
#>
[CmdletBinding()]
param(
    [string]$Root = (Get-Location).Path,
    [string[]]$TestsPath,
    [switch]$Json,
    [int]$MaxExamples = 200
)

$ErrorActionPreference = 'Stop'

function Get-MaskedJava {
    <# 将注释替换为空格，保持字符下标不变；识别字符串/字符/文本块，避免误伤。 #>
    param([string]$Text)

    $chars = $Text.ToCharArray()
    $state = 'normal'
    $i = 0
    while ($i -lt $chars.Length) {
        $c = $chars[$i]
        $n = if ($i + 1 -lt $chars.Length) { $chars[$i + 1] } else { [char]0 }

        switch ($state) {
            'normal' {
                if ($c -eq '/' -and $n -eq '/') { $state = 'line'; $chars[$i] = ' '; $chars[$i + 1] = ' '; $i += 2; continue }
                if ($c -eq '/' -and $n -eq '*') { $state = 'block'; $chars[$i] = ' '; $chars[$i + 1] = ' '; $i += 2; continue }
                if ($c -eq '"') {
                    if ($i + 2 -lt $chars.Length -and $chars[$i + 1] -eq '"' -and $chars[$i + 2] -eq '"') {
                        $state = 'textblock'; $i += 3; continue
                    }
                    $state = 'string'; $i++; continue
                }
                if ($c -eq "'") { $state = 'char'; $i++; continue }
                $i++
            }
            'line' {
                if ($c -eq "`n" -or $c -eq "`r") { $state = 'normal' }
                else { $chars[$i] = ' ' }
                $i++
            }
            'block' {
                if ($c -eq '*' -and $n -eq '/') {
                    $chars[$i] = ' '; $chars[$i + 1] = ' '; $state = 'normal'; $i += 2
                }
                else {
                    if ($c -ne "`n" -and $c -ne "`r") { $chars[$i] = ' ' }
                    $i++
                }
            }
            'string' {
                if ($c -eq '\') { $i += 2; continue }
                if ($c -eq '"') { $state = 'normal' }
                $i++
            }
            'char' {
                if ($c -eq '\') { $i += 2; continue }
                if ($c -eq "'") { $state = 'normal' }
                $i++
            }
            'textblock' {
                if ($c -eq '"' -and $i + 2 -lt $chars.Length -and $chars[$i + 1] -eq '"' -and $chars[$i + 2] -eq '"') {
                    $state = 'normal'; $i += 3; continue
                }
                $i++
            }
        }
    }
    return -join $chars
}

function Get-MatchingBrace {
    <# 返回与 OpenIndex 处 '{' 匹配的 '}' 下标；失败返回 -1。 #>
    param([string]$Text, [int]$OpenIndex)

    $depth = 0
    $state = 'normal'
    $i = $OpenIndex
    while ($i -lt $Text.Length) {
        $c = $Text[$i]
        $n = if ($i + 1 -lt $Text.Length) { $Text[$i + 1] } else { [char]0 }

        switch ($state) {
            'normal' {
                if ($c -eq '/' -and $n -eq '/') { $state = 'line'; $i += 2; continue }
                if ($c -eq '/' -and $n -eq '*') { $state = 'block'; $i += 2; continue }
                if ($c -eq '"') {
                    if ($i + 2 -lt $Text.Length -and $Text[$i + 1] -eq '"' -and $Text[$i + 2] -eq '"') { $state = 'textblock'; $i += 3; continue }
                    $state = 'string'; $i++; continue
                }
                if ($c -eq "'") { $state = 'char'; $i++; continue }
                if ($c -eq '{') { $depth++ }
                elseif ($c -eq '}') {
                    $depth--
                    if ($depth -eq 0) { return $i }
                }
                $i++
            }
            'line' {
                if ($c -eq "`n" -or $c -eq "`r") { $state = 'normal' }
                $i++
            }
            'block' {
                if ($c -eq '*' -and $n -eq '/') { $state = 'normal'; $i += 2; continue }
                $i++
            }
            'string' {
                if ($c -eq '\') { $i += 2; continue }
                if ($c -eq '"') { $state = 'normal' }
                $i++
            }
            'char' {
                if ($c -eq '\') { $i += 2; continue }
                if ($c -eq "'") { $state = 'normal' }
                $i++
            }
            'textblock' {
                if ($c -eq '"' -and $i + 2 -lt $Text.Length -and $Text[$i + 1] -eq '"' -and $Text[$i + 2] -eq '"') { $state = 'normal'; $i += 3; continue }
                $i++
            }
        }
    }
    return -1
}

$sigRegex = '(?s)\b([A-Za-z_$][\w$]*)\s*\([^()]*\)\s*(?:throws\s+[^{]+)?\{'
$annRegex = '(?m)^[ \t]*@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b'

$reValueAssert = @'
\bassert(?:Equals|NotEquals|Same|NotSame|ArrayEquals|IterableEquals|LinesMatch|Throws|DoesNotThrow|True|False|InstanceOf|All|Any|Timeout)\s*\(|\bassertThat\w*\s*\(|\bassertThatThrownBy\s*\(|\bassertThatExceptionOfType\s*\(|\bassertThatCode\s*\(|\bandExpect\w*\s*\(|\bisEqualTo\s*\(|\bhasSize\s*\(|\bcontainsExactly\w*\s*\(|\bfail\s*\(
'@
$reNotNull = @'
\bassertNotNull\s*\(|\bisNotNull\s*\(
'@
$reMockVerify = @'
\bverify\s*\(|\bverifyNoInteractions\s*\(|\bverifyNoMoreInteractions\s*\(|\binOrder\s*\(
'@
$reBoundary = @'
\bnull\b|""|''|"\s+"|\bemptyList\s*\(|\bemptyMap\s*\(|\bemptySet\s*\(|\bOptional\.empty\s*\(|\b(?:List|Set|Map)\.(?:<[^>]*>)?of\s*\(\s*\)|\bnew\s+\w+\s*\[\s*0\s*\]|\bBigDecimal\.ZERO\b|\bInteger\.(?:MAX|MIN)_VALUE\b|\bLong\.(?:MAX|MIN)_VALUE\b|\bShort\.(?:MAX|MIN)_VALUE\b|\bByte\.(?:MAX|MIN)_VALUE\b|\bDouble\.(?:MAX|MIN|NaN|POSITIVE_INFINITY|NEGATIVE_INFINITY)\b|\bFloat\.(?:MAX|MIN|NaN|POSITIVE_INFINITY|NEGATIVE_INFINITY)\b|\bLocalDate\.(?:MIN|MAX)\b|\bLocalDateTime\.(?:MIN|MAX)\b|\b-1\b|\b0[LlDdFf]?\b
'@
$reException = @'
\bassertThrows\s*\(|\bassertThatThrownBy\s*\(|\bassertThatExceptionOfType\s*\(|\bassertThatCode\s*\(|\.isCompletedExceptionally\s*\(|\bExecutionException\b|\bTimeoutException\b|@Test\s*\([^)]*expected\s*=
'@
$rePlaceholder = @'
assertTrue\s*\(\s*true\s*\)|assertFalse\s*\(\s*false\s*\)|assumeTrue\s*\(\s*false\s*\)|@Disabled\b|@Ignore\b
'@
$reMockHeavy = @'
@Mock\b|@MockitoBean\b|@MockBean\b|@SpyBean\b|@MockitoSpyBean\b|\bmock\s*\(|\bmockStatic\s*\(
'@
$reCoupling = @'
ReflectionTestUtils|getDeclaredField|getDeclaredMethod|setAccessible\s*\(|\bWhitebox\b|@VisibleForTesting
'@
$reSleep = @'
Thread\.sleep|TimeUnit\.\w+\.sleep|\bawaitTermination\s*\(
'@

# ---------- 定位测试根 ----------
if ($TestsPath) {
    $roots = foreach ($p in $TestsPath) {
        if (-not (Test-Path -LiteralPath $p)) { throw "测试路径不存在: $p" }
        Get-Item -LiteralPath $p
    }
}
else {
    $roots = Get-ChildItem -LiteralPath $Root -Recurse -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]src[\\/]test[\\/]java$' -and $_.FullName -notmatch '[\\/]target[\\/]' }
}

$files = @()
foreach ($r in $roots) {
    $files += Get-ChildItem -LiteralPath $r.FullName -Recurse -File -Filter '*.java' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' }
}
$files = $files | Sort-Object FullName -Unique

if ($files.Count -eq 0) {
    if ($Json) {
        [pscustomobject]@{
            root     = (Resolve-Path -LiteralPath $Root).Path
            testRoots = @($roots | ForEach-Object { $_.FullName })
            files    = 0
            methods  = 0
            summary  = $null
            results  = @()
        } | ConvertTo-Json -Depth 6
    }
    else {
        Write-Host "未找到 Java 测试文件（src/test/java/**/*.java）。测试基线为 0。"
    }
    exit 0
}

$allMethods = [System.Collections.Generic.List[object]]::new()

foreach ($file in $files) {
    $raw = [System.IO.File]::ReadAllText($file.FullName)
    $masked = Get-MaskedJava $raw
    $annMatches = [regex]::Matches($masked, $annRegex)

    if ($annMatches.Count -eq 0) { continue }

    $fileMethods = [System.Collections.Generic.List[object]]::new()
    $prevEnd = 0

    foreach ($ann in $annMatches) {
        $searchStart = $ann.Index + $ann.Length
        $sig = [regex]::Match($masked.Substring($searchStart), $sigRegex)
        if (-not $sig.Success) { continue }

        $sigIndex = $searchStart + $sig.Index
        $openBrace = $sigIndex + $sig.Length - 1
        $closeBrace = Get-MatchingBrace -Text $raw -OpenIndex $openBrace
        if ($closeBrace -lt 0) { continue }

        $methodName = $sig.Groups[1].Value
        $bodyRaw = $raw.Substring($openBrace, $closeBrace - $openBrace + 1)
        $bodyMasked = $masked.Substring($openBrace, $closeBrace - $openBrace + 1)

        # 所属类/接口/记录
        $before = $masked.Substring(0, [Math]::Max(0, $ann.Index))
        $typeMatches = [regex]::Matches($before, '\b(?:class|interface|record|enum)\s+([A-Za-z_$][\w$]*)')
        $typeName = if ($typeMatches.Count -gt 0) { $typeMatches[$typeMatches.Count - 1].Groups[1].Value } else { '<unknown>' }

        # 方法前的注解窗口：从「上一个成员结束」开始，避免把上一个测试的方法体
        # 误算进当前测试（例如 @Disabled / mock()）。
        $lastBrace = $masked.LastIndexOf('}', [Math]::Max(0, $sigIndex - 1))
        $windowStart = [Math]::Max($prevEnd, $lastBrace + 1)
        $annWindow = if ($sigIndex -gt $windowStart) { $masked.Substring($windowStart, $sigIndex - $windowStart) } else { '' }

        $valueAsserts = [regex]::Matches($bodyMasked, $reValueAssert).Count
        $notNullAsserts = [regex]::Matches($bodyMasked, $reNotNull).Count
        $mockVerify = [regex]::Matches($bodyMasked, $reMockVerify).Count
        $boundary = [regex]::IsMatch($bodyMasked, $reBoundary)
        $exceptionPath = [regex]::IsMatch($bodyMasked, $reException)
        $placeholder = [regex]::IsMatch($annWindow, $rePlaceholder) -or
                       [regex]::IsMatch($bodyRaw, $rePlaceholder) -or
                       [regex]::IsMatch($bodyRaw, '//\s*TODO|/\*\s*TODO') -or
                       (($valueAsserts + $notNullAsserts + $mockVerify) -eq 0)
        $mockHeavy = [regex]::IsMatch($bodyMasked, $reMockHeavy) -or [regex]::IsMatch($annWindow, $reMockHeavy)
        $coupling = [regex]::IsMatch($bodyMasked, $reCoupling)
        $sleep = [regex]::IsMatch($bodyMasked, $reSleep)
        $notNullOnly = ($notNullAsserts -gt 0 -and $valueAsserts -eq 0)
        $pureMock = ($mockVerify -gt 0 -and $valueAsserts -eq 0 -and $notNullAsserts -eq 0)
        $parameterized = $ann.Groups[1].Value -in @('ParameterizedTest', 'RepeatedTest', 'TestFactory', 'TestTemplate')

        $flags = [System.Collections.Generic.List[string]]::new()
        if ($boundary) { $flags.Add('boundary') }
        if ($exceptionPath) { $flags.Add('exception') }
        if ($parameterized) { $flags.Add('parameterized') }
        if ($pureMock) { $flags.Add('mock-only') }
        if ($notNullOnly) { $flags.Add('not-null-only') }
        if ($placeholder) { $flags.Add('placeholder') }
        if ($coupling) { $flags.Add('coupling') }
        if ($mockHeavy) { $flags.Add('mock-heavy') }
        if ($sleep) { $flags.Add('sleep') }
        if (-not $boundary -and -not $exceptionPath) { $flags.Add('happy-path-only') }

        $row = [pscustomobject]@{
            file             = $file.FullName
            relativeFile     = [System.IO.Path]::GetRelativePath((Resolve-Path -LiteralPath $Root).Path, $file.FullName)
            type             = $typeName
            method           = $methodName
            line             = ($raw.Substring(0, $sigIndex) -split "`n").Count
            annotation       = $ann.Groups[1].Value
            boundary         = $boundary
            exceptionPath    = $exceptionPath
            parameterized    = $parameterized
            pureMock         = $pureMock
            notNullOnly      = $notNullOnly
            placeholder      = $placeholder
            coupling         = $coupling
            mockHeavy        = $mockHeavy
            sleep            = $sleep
            valueAssertions  = $valueAsserts
            notNullAssertions = $notNullAsserts
            mockVerify       = $mockVerify
            flags            = @($flags)
        }
        $fileMethods.Add($row)
        $allMethods.Add($row)
        $prevEnd = $closeBrace + 1
    }
}

$total = $allMethods.Count
function Pct([int]$n) { if ($total -eq 0) { '0.0' } else { ([math]::Round(100.0 * $n / $total, 1)).ToString('0.0', [System.Globalization.CultureInfo]::InvariantCulture) } }

$summary = [pscustomobject]@{
    files            = $files.Count
    methods          = $total
    boundary         = @($allMethods | Where-Object boundary).Count
    exceptionPath    = @($allMethods | Where-Object exceptionPath).Count
    parameterized    = @($allMethods | Where-Object parameterized).Count
    happyPathOnly    = @($allMethods | Where-Object { -not $_.boundary -and -not $_.exceptionPath }).Count
    pureMock         = @($allMethods | Where-Object pureMock).Count
    notNullOnly      = @($allMethods | Where-Object notNullOnly).Count
    placeholder      = @($allMethods | Where-Object placeholder).Count
    coupling         = @($allMethods | Where-Object coupling).Count
    mockHeavy        = @($allMethods | Where-Object mockHeavy).Count
    sleep            = @($allMethods | Where-Object sleep).Count
}

if ($Json) {
    [pscustomobject]@{
        root      = (Resolve-Path -LiteralPath $Root).Path
        testRoots = @($roots | ForEach-Object { $_.FullName })
        files     = $files.Count
        methods   = $total
        summary   = $summary
        results   = @($allMethods)
    } | ConvertTo-Json -Depth 6
    exit 0
}

Write-Host ''
Write-Host '== Java 测试自欺欺人扫描（启发式，需人工复核）==' -ForegroundColor Cyan
Write-Host "根目录    : $((Resolve-Path -LiteralPath $Root).Path)"
Write-Host "测试根    : $($roots.Count) 个"
Write-Host "文件/方法 : $($files.Count) / $total"
Write-Host ''
Write-Host "边界值          : $($summary.boundary) ($(Pct $summary.boundary)%)"
Write-Host "异常路径        : $($summary.exceptionPath) ($(Pct $summary.exceptionPath)%)"
Write-Host "参数化          : $($summary.parameterized) ($(Pct $summary.parameterized)%)"
Write-Host "仅 happy path   : $($summary.happyPathOnly) ($(Pct $summary.happyPathOnly)%)"
Write-Host "纯 mock 断言    : $($summary.pureMock) ($(Pct $summary.pureMock)%)"
Write-Host "仅 not-null     : $($summary.notNullOnly) ($(Pct $summary.notNullOnly)%)"
Write-Host "占位/无断言     : $($summary.placeholder) ($(Pct $summary.placeholder)%)"
Write-Host "实现耦合        : $($summary.coupling) ($(Pct $summary.coupling)%)"
Write-Host "mock 重         : $($summary.mockHeavy) ($(Pct $summary.mockHeavy)%)"
Write-Host "sleep/等待      : $($summary.sleep) ($(Pct $summary.sleep)%)"
Write-Host ''

$shown = 0
foreach ($group in ($allMethods | Group-Object relativeFile)) {
    Write-Host "$($group.Name)  ($($group.Count) tests)" -ForegroundColor Yellow
    foreach ($m in $group.Group) {
        if ($shown -ge $MaxExamples) {
            Write-Host "  ... 仅显示前 $MaxExamples 条，使用 -Json 查看全部"
            break
        }
        $flagText = if ($m.flags.Count -gt 0) { ' [' + ($m.flags -join '] [') + ']' } else { '' }
        Write-Host "  - L$($m.line) $($m.type)#$($m.method)$flagText"
        $shown++
    }
    if ($shown -ge $MaxExamples) { break }
}

Write-Host ''
Write-Host '提示：happy-path-only / mock-only / placeholder 等标记只是线索，最终判定必须阅读实现与测试代码。' -ForegroundColor DarkGray
