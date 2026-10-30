#Requires -Version 7.0
<#
.SYNOPSIS
    聚合 Maven 多模块的 JaCoCo CSV，输出 Markdown 覆盖率表格。

.DESCRIPTION
    递归查找 <Root>/**/target/site/jacoco/jacoco.csv，按模块汇总
    INSTRUCTION / BRANCH / LINE / METHOD 覆盖率，并列出分支覆盖最差的类。
    需要先运行 JaCoCo report（见 SKILL.md Step 2）。

.PARAMETER Root
    仓库根目录或 server 目录，默认当前目录。

.PARAMETER Lowest
    列出分支缺失最多的前 N 个类，默认 10；设为 0 关闭。

.PARAMETER Exclude
    类名排除正则（例如 '^.*(Application|Config|Dto|DO)$'），默认不过滤。
#>
[CmdletBinding()]
param(
    [string]$Root = (Get-Location).Path,
    [int]$Lowest = 10,
    [string]$Exclude = ''
)

$ErrorActionPreference = 'Stop'
$invariant = [System.Globalization.CultureInfo]::InvariantCulture
$rootPath = (Resolve-Path -LiteralPath $Root).Path

$files = Get-ChildItem -LiteralPath $rootPath -Recurse -File -Filter 'jacoco.csv' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match '[\\/]target[\\/]site[\\/]jacoco[\\/]jacoco\.csv$' }

if (-not $files) {
    [Console]::Error.WriteLine("未找到 jacoco.csv。请先运行：")
    [Console]::Error.WriteLine("  mvn -f server/pom.xml org.jacoco:jacoco-maven-plugin:prepare-agent clean test org.jacoco:jacoco-maven-plugin:report")
    exit 1
}

$rows = [System.Collections.Generic.List[object]]::new()

foreach ($f in $files) {
    $relative = [System.IO.Path]::GetRelativePath($rootPath, $f.FullName)
    $module = ($relative -split '[\\/]target[\\/]')[0]
    if ([string]::IsNullOrWhiteSpace($module)) { $module = '.' }

    foreach ($r in (Import-Csv -LiteralPath $f.FullName)) {
        if ($Exclude -and $r.CLASS -match $Exclude) { continue }

        $rows.Add([pscustomobject]@{
            Module             = $module
            Package            = $r.PACKAGE
            Class              = $r.CLASS
            InstructionMissed  = [int]$r.INSTRUCTION_MISSED
            InstructionCovered = [int]$r.INSTRUCTION_COVERED
            BranchMissed       = [int]$r.BRANCH_MISSED
            BranchCovered      = [int]$r.BRANCH_COVERED
            LineMissed         = [int]$r.LINE_MISSED
            LineCovered        = [int]$r.LINE_COVERED
            MethodMissed       = [int]$r.METHOD_MISSED
            MethodCovered      = [int]$r.METHOD_COVERED
            ComplexityMissed   = [int]$r.COMPLEXITY_MISSED
            ComplexityCovered  = [int]$r.COMPLEXITY_COVERED
        })
    }
}

if ($rows.Count -eq 0) {
    [Console]::Error.WriteLine("jacoco.csv 中没有可统计的类（可能被 -Exclude 全部过滤）。")
    exit 1
}

function Get-Percent {
    param([double]$Covered, [double]$Missed)
    $total = $Covered + $Missed
    if ($total -le 0) { return $null }
    return [math]::Round(100.0 * $Covered / $total, 1)
}

function Format-Percent {
    param($Value, [double]$Warn = 80)
    if ($null -eq $Value) { return 'n/a' }
    $text = ([double]$Value).ToString('0.0', $invariant) + '%'
    if ($Value -lt 60) { return "$text 🔴" }
    if ($Value -lt $Warn) { return "$text 🟡" }
    return "$text ✅"
}

function New-Aggregate {
    param($Items, [string]$Name)
    $covered = [pscustomobject]@{
        InstructionCovered = 0; InstructionMissed = 0
        BranchCovered      = 0; BranchMissed      = 0
        LineCovered        = 0; LineMissed        = 0
        MethodCovered      = 0; MethodMissed      = 0
    }
    foreach ($item in $Items) {
        foreach ($p in $covered.PSObject.Properties.Name) {
            $covered.$p += $item.$p
        }
    }
    [pscustomobject]@{
        Module         = $Name
        Instruction    = Get-Percent $covered.InstructionCovered $covered.InstructionMissed
        Branch         = Get-Percent $covered.BranchCovered $covered.BranchMissed
        Line           = Get-Percent $covered.LineCovered $covered.LineMissed
        Method         = Get-Percent $covered.MethodCovered $covered.MethodMissed
        InstructionRaw = $covered
    }
}

$aggregates = @()
foreach ($group in ($rows | Group-Object Module | Sort-Object Name)) {
    $aggregates += New-Aggregate -Items $group.Group -Name $group.Name
}
$aggregates += New-Aggregate -Items $rows -Name 'TOTAL'

Write-Output ''
Write-Output '## JaCoCo 覆盖率'
Write-Output ''
Write-Output '| Module | Branch | Line | Method | Instruction |'
Write-Output '|---|---:|---:|---:|---:|'

foreach ($a in $aggregates) {
    $moduleLabel = if ($a.Module -eq 'TOTAL') { '**TOTAL**' } else { $a.Module }
    Write-Output ("| {0} | {1} | {2} | {3} | {4} |" -f `
        $moduleLabel,
        (Format-Percent $a.Branch),
        (Format-Percent $a.Line),
        (Format-Percent $a.Method),
        (Format-Percent $a.Instruction))
}

if ($Lowest -gt 0) {
    $worst = $rows |
        Where-Object { $_.BranchMissed -gt 0 } |
        Sort-Object BranchMissed -Descending |
        Select-Object -First $Lowest

    if ($worst) {
        Write-Output ''
        Write-Output "### 分支覆盖最差的前 $($worst.Count) 个类"
        Write-Output ''
        Write-Output '| Class | Branch | Missed | Module |'
        Write-Output '|---|---:|---:|---|'
        foreach ($w in $worst) {
            $pct = Get-Percent $w.BranchCovered $w.BranchMissed
            Write-Output ("| {0}.{1} | {2} | {3} | {4} |" -f `
                $w.Package, $w.Class, (Format-Percent $pct), $w.BranchMissed, $w.Module)
        }
    }
}

# 机器可读的摘要，便于 Agent 直接引用
Write-Output ''
Write-Output '<!-- JACOCO_JSON'
$aggregates | ForEach-Object {
    [pscustomobject]@{
        module      = $_.Module
        branch      = $_.Branch
        line        = $_.Line
        method      = $_.Method
        instruction = $_.Instruction
    } | ConvertTo-Json -Compress
} 
Write-Output 'JACOCO_JSON -->'
