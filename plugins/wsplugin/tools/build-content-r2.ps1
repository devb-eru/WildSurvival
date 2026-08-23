param(
    [string]$OutputRoot
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $repoRoot 'plugins\wsplugin\src\main\resources\content\ws-content-r2'
}
$OutputRoot = [IO.Path]::GetFullPath($OutputRoot)
$expectedSuffix = [IO.Path]::Combine('content', 'ws-content-r2')
if (-not $OutputRoot.EndsWith($expectedSuffix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "OutputRoot must end in content/ws-content-r2: $OutputRoot"
}

$utf8 = [Text.UTF8Encoding]::new($false)
function Write-Utf8Lf([string]$Path, [string]$Text) {
    $parent = Split-Path -Parent $Path
    [IO.Directory]::CreateDirectory($parent) | Out-Null
    [IO.File]::WriteAllText($Path, ($Text -replace "`r`n", "`n"), $utf8)
}
function Write-Json([string]$RelativePath, [object]$Value) {
    $json = $Value | ConvertTo-Json -Depth 30
    Write-Utf8Lf (Join-Path $OutputRoot $RelativePath) ($json + "`n")
}
function Read-TableRows([string]$RelativePath) {
    $path = Join-Path $repoRoot $RelativePath
    $rows = [Collections.Generic.List[object]]::new()
    foreach ($line in [IO.File]::ReadAllLines($path, $utf8)) {
        if (-not $line.TrimStart().StartsWith('|') -or $line -match '^\s*\|[\s:|-]+\|\s*$') { continue }
        $cells = @($line.Trim().Trim('|').Split('|') | ForEach-Object {
            (($_.Trim()) -replace '^`|`$', '').Trim()
        })
        if ($cells.Count -gt 1) { $rows.Add($cells) }
    }
    return @($rows)
}
function Find-IdRows([object[]]$Rows, [string]$Pattern) {
    $found = [ordered]@{}
    foreach ($cells in $Rows) {
        for ($index = 0; $index -lt $cells.Count; $index++) {
            if ($cells[$index] -match $Pattern) {
                $id = $Matches[0]
                if (-not $found.Contains($id)) { $found[$id] = $cells }
                break
            }
        }
    }
    return $found
}
function First-Material([object[]]$Cells, [string]$Slot) {
    $excluded = @('ACTIVE','HOSTILE','UTILITY','ACCESSORY','ARMOR','OFF','CHARM','PARTY_BOUND',
        'PARTY_RESOURCE','BOUND_PROOF','CRAFT','PROCESS','BOSS_CALL','FACILITY_KIT','EQUIPMENT_FORGE',
        'UTILITY_FORGE','VIRTUAL_BUILD','MAIN_WEAPON','OFF_WEAPON','INVENTORY','ARMOR_HEAD','ARMOR_CHEST',
        'ARMOR_LEGS','ARMOR_FEET','ACCESSORY/CHARM','UNARMED_SUPPORT',
        'SW','AX','BO','CB','DG','BL','ST','PK','TR','UA')
    $candidate = $Cells | Where-Object { $_ -match '^[A-Z][A-Z0-9_]+$' -and $_ -notin $excluded } | Select-Object -First 1
    if ($candidate -eq 'IRON_ARMOR') {
        if ($Slot -match 'HEAD') { return 'IRON_HELMET' }
        if ($Slot -match 'LEGS') { return 'IRON_LEGGINGS' }
        if ($Slot -match 'FEET') { return 'IRON_BOOTS' }
        return 'IRON_CHESTPLATE'
    }
    return $(if ($candidate) { $candidate } else { 'PAPER' })
}
function Korean-Name([object[]]$Cells, [string]$Fallback) {
    $candidate = $Cells | Where-Object { $_ -match '[가-힣]' -and $_ -notmatch '^(본 문서|EQUIP-|RESOURCE-|RECIPE-|ENEMY-)' } | Select-Object -First 1
    return $(if ($candidate) { $candidate } else { $Fallback })
}
function Fallback-Material([string]$Id) {
    if ($Id -match '(ALLOY|PLATE|FRAME)') { return 'NETHERITE_SCRAP' }
    if ($Id -match '(COIL|CIRCUIT|MATRIX)') { return 'REDSTONE_TORCH' }
    if ($Id -match '(CATALYST|CRYSTAL|LENS)') { return 'AMETHYST_SHARD' }
    if ($Id -match '(POWDER|RESIDUE)') { return 'GUNPOWDER' }
    if ($Id -match '(CORE|KEY)') { return 'HEART_OF_THE_SEA' }
    if ($Id -match '(MEDIUM|GEL|TISSUE)') { return 'SLIME_BALL' }
    if ($Id -match '(AGGREGATE|STONE)') { return 'DEEPSLATE' }
    return 'PAPER'
}
function Domain([string]$Name, [object[]]$Records) {
    return [ordered]@{ schemaVersion = 2; contentRevision = 'ws-content-r2'; domain = $Name; records = @($Records) }
}
function Raw-Record([string]$Id, [string]$Source, [object[]]$Cells) {
    return [ordered]@{ id = $Id; sourceDocumentId = $Source; enabled = $true; raw = @($Cells) }
}
function Recipe-Ingredient([int]$Slot, [string]$Key, [int]$Amount, [bool]$Consume = $true) {
    $kind = 'ITEM'
    if ($Key.StartsWith('TAG:')) { $kind = 'TAG'; $Key = $Key.Substring(4) }
    elseif ($Key.StartsWith('VANILLA:')) { $kind = 'VANILLA'; $Key = $Key.Substring(8) }
    elseif ($Key.StartsWith('PROOF:')) { $kind = 'PROOF'; $Key = $Key.Substring(6); $Consume = $false }
    return [ordered]@{ slot = $Slot; kind = $kind; key = $Key; amount = $Amount; consume = $Consume }
}
function Ingredients-FromSpec([string]$Spec, [int[]]$Slots = @(0,1,2,3,4,5,6,7,8)) {
    $result = [Collections.Generic.List[object]]::new()
    $parts = @($Spec.Split(';', [StringSplitOptions]::RemoveEmptyEntries))
    if ($parts.Count -gt $Slots.Count) { throw "Recipe input exceeds 3x3: $Spec" }
    for ($index = 0; $index -lt $parts.Count; $index++) {
        $pair = $parts[$index].Trim().Split('*')
        if ($pair.Count -ne 2) { throw "Invalid recipe input token $($parts[$index])" }
        $result.Add((Recipe-Ingredient $Slots[$index] $pair[0] ([int]$pair[1])))
    }
    return @($result)
}
function Equipment-ClassCode([string]$Id) {
    foreach ($code in @('SW','AX','BO','CB','DG','BL','ST','PK','TR')) {
        if ($Id -match "(^|-)$code(-|$)") { return $code }
    }
    return ''
}
function Equipment-WeaponClass([string]$Code) {
    return @{SW='SWORD';AX='AXE';BO='BOW';CB='CROSSBOW';DG='DAGGER';BL='MACE';ST='STAFF';PK='PICKAXE';TR='TRIDENT'}[$Code]
}
function Equipment-ClassName([string]$Code) {
    return @{SW='검';AX='도끼';BO='활';CB='석궁';DG='단검';BL='둔기';ST='지팡이';PK='곡괭이';TR='삼지창'}[$Code]
}
function Equipment-Rarity([string]$Id) {
    if ($Id -match '^EQL-UT-RI-') { return 'RARE' }
    if ($Id -match '^EQL-UT-RS-') { return 'EPIC' }
    if ($Id -match '^EQL-UT-HD-') { return 'LEGENDARY' }
    if ($Id -match '^EQL-UT-RC-') { return 'ABYSSAL' }
    if ($Id -match '^EQL-D10-') { return 'EPIC' }
    if ($Id -match '^EQD20-LG-') { return 'LEGENDARY' }
    if ($Id -match '^EQD20-EP-') { return 'EPIC' }
    if ($Id -match '^EQD20-') { return 'RARE' }
    if ($Id -match '^EQD50-(B30|B40)-') { return 'ABYSSAL' }
    if ($Id -match '^EQD50-.*-A41$' -or $Id -match '^EQD50-(AC-CALIBRATE|CH-RELAY|OH-INTERRUPT)$' -or $Id -match '^EQD50-AR-REBUILD-') { return 'ABYSSAL' }
    if ($Id -match '^EQD50-.*-L31$' -or $Id -match '^EQD50-AR-INTERRUPT-' -or $Id -eq 'EQD50-OH-PURIFY') { return 'LEGENDARY' }
    if ($Id -match '^EQD50-') { return 'EPIC' }
    if ($Id -match '(^|-)R\d+' -or $Id -match '-R-') { return 'RARE' }
    if ($Id -match '(^|-)U\d+' -or $Id -match '-U-') { return 'UNCOMMON' }
    return 'COMMON'
}
function Equipment-FirstDay([string]$Id) {
    if ($Id -match '^EQL-UT-RI-') { return 11 }
    if ($Id -match '^EQL-UT-RS-') { return 21 }
    if ($Id -match '^EQL-UT-HD-') { return 31 }
    if ($Id -match '^EQL-UT-RC-') { return 41 }
    if ($Id -match '^EQL-D10-') { return 10 }
    if ($Id -match '^EQD20-LG-') { return 20 }
    if ($Id -match '^EQD20-EP-') { return 18 }
    if ($Id -match '^EQD20-') { return 11 }
    if ($Id -match '^EQD50-B30-') { return 30 }
    if ($Id -match '^EQD50-B40-') { return 40 }
    if ($Id -match '^EQD50-.*-A41$' -or $Id -match '^EQD50-(AC-CALIBRATE|CH-RELAY)$' -or $Id -match '^EQD50-AR-REBUILD-') { return 41 }
    if ($Id -match '^EQD50-.*-L31$' -or $Id -match '^EQD50-AR-INTERRUPT-') { return 31 }
    if ($Id -eq 'EQD50-OH-INTERRUPT') { return 35 }
    if ($Id -eq 'EQD50-OH-PURIFY') { return 25 }
    if ($Id -match '^EQD50-') { return 21 }
    if ((Equipment-Rarity $Id) -eq 'RARE') { return 5 }
    if ((Equipment-Rarity $Id) -eq 'UNCOMMON') { return 3 }
    return 1
}
function Equipment-ItemLevel([string]$Id) {
    if ($Id -match '^EQL-UT-') { return Equipment-FirstDay $Id }
    if ($Id -match '^EQL-D10-') { return 10 }
    if ($Id -match '^EQD20-LG-') { return 20 }
    if ($Id -match '^EQD20-EP-') { return 19 }
    if ($Id -match '^EQD20-') { return 14 }
    if ($Id -match '^EQD50-B30-') { return 30 }
    if ($Id -match '^EQD50-B40-') { return 40 }
    if ($Id -match '^EQD50-.*-A41$' -or $Id -match '^EQD50-(AC-CALIBRATE|CH-RELAY)$' -or $Id -match '^EQD50-AR-REBUILD-') { return 44 }
    if ($Id -match '^EQD50-.*-L31$' -or $Id -match '^EQD50-AR-INTERRUPT-') { return 34 }
    if ($Id -eq 'EQD50-OH-INTERRUPT') { return 37 }
    if ($Id -eq 'EQD50-OH-PURIFY') { return 27 }
    if ($Id -match '^EQD50-') { return 24 }
    switch (Equipment-Rarity $Id) { 'RARE' {return 6}; 'UNCOMMON' {return 3}; default {return 1} }
}
function Equipment-Durability([string]$Id, [string]$Type, [string]$Rarity) {
    if ($Id -match '^EQL-UT-RI-') { return 720 }
    if ($Id -match '^EQL-UT-RS-') { return 1100 }
    if ($Id -match '^EQL-UT-HD-') { return 1550 }
    if ($Id -match '^EQL-UT-RC-') { return 2100 }
    $base = @{COMMON=320;UNCOMMON=450;RARE=600;EPIC=800;LEGENDARY=1100;ABYSSAL=1500}[$Rarity]
    if ($Type -eq 'ARMOR') { return [int][Math]::Ceiling($base * 1.25) }
    return $base
}
function Equipment-ToolTier([string]$Id) {
    if ($Id -match '^EQL-UT-RI-') { return 3 }
    if ($Id -match '^EQL-UT-RS-') { return 4 }
    if ($Id -match '^EQL-UT-HD-') { return 5 }
    if ($Id -match '^EQL-UT-RC-') { return 6 }
    if ((Equipment-ClassCode $Id) -eq 'PK') {
        $day = Equipment-FirstDay $Id
        if ($day -ge 41) { return 6 }; if ($day -ge 31) { return 5 }; if ($day -ge 21) { return 4 }
        if ($day -ge 11) { return 3 }; return 2
    }
    return -1
}
function Facility-Tier([string]$Id) {
    if ($Id.StartsWith('FAC-P')) { return 'PORTABLE' }
    if ($Id.StartsWith('FAC-C')) { return 'CAMP' }
    if ($Id.StartsWith('FAC-S')) { return 'SETTLEMENT' }
    if ($Id.StartsWith('FAC-D')) { return 'DEFENSE' }
    return 'RECONSTRUCTION'
}
function Facility-MaxLevel([string]$Id) {
    $levels = @{
        'FAC-S01'=5;'FAC-S02'=5;'FAC-S03'=5;'FAC-S04'=4;'FAC-S05'=4;'FAC-S06'=5;'FAC-S07'=5;'FAC-S08'=5
        'FAC-S09'=4;'FAC-S10'=3;'FAC-S11'=5;'FAC-S12'=3;'FAC-S13'=5;'FAC-S14'=4;'FAC-S15'=4;'FAC-S16'=5
        'FAC-S17'=5;'FAC-S18'=4;'FAC-S19'=3;'FAC-S20'=4
    }
    if ($levels.ContainsKey($Id)) { return $levels[$Id] }
    if ($Id.StartsWith('FAC-D')) { return 3 }
    return 1
}
function Facility-Opcode([string]$Id) {
    $opcodes = @{
        'FAC-P01'='CRAFT_PORTABLE';'FAC-P02'='REPAIR_FIELD';'FAC-P03'='SAMPLE_EXTRACT';'FAC-P04'='ANALYZE_PORTABLE'
        'FAC-P05'='PURIFY_PORTABLE';'FAC-P06'='SIGNAL_STAKE';'FAC-P07'='RESCUE_BEACON';'FAC-P08'='LEDGER_REMOTE'
        'FAC-C01'='CRAFT_BASIC';'FAC-C02'='SMELT';'FAC-C03'='STORAGE';'FAC-C04'='REST';'FAC-C05'='ALERT'
        'FAC-C06'='MEDICAL_BASIC';'FAC-C07'='AMMO_BASIC';'FAC-C08'='BARRICADE'
        'FAC-S01'='CRAFT_ADVANCED';'FAC-S02'='REPAIR_FULL';'FAC-S03'='REFORGE';'FAC-S04'='SALVAGE'
        'FAC-S05'='AMMO_ADVANCED';'FAC-S06'='RESEARCH';'FAC-S07'='FORECAST';'FAC-S08'='PATTERN_ANALYZE'
        'FAC-S09'='AUGMENT_MANAGE';'FAC-S10'='TRAINING';'FAC-S11'='MEDICAL';'FAC-S12'='GRAVE_RECOVERY'
        'FAC-S13'='PURIFY';'FAC-S14'='PURIFY_RELAY';'FAC-S15'='ENVIRONMENT_SHIELD';'FAC-S16'='SHARED_LEDGER'
        'FAC-S17'='POWER_DISTRIBUTE';'FAC-S18'='TRAVEL';'FAC-S19'='SESSION_RELAY';'FAC-S20'='ASSAULT_OBSERVE'
        'FAC-D01'='WALL_REGISTER';'FAC-D02'='SLOW_TRAP';'FAC-D03'='IMPACT_TRAP';'FAC-D04'='TAUNT_BEACON'
        'FAC-R01'='REBUILD_FRAME';'FAC-R02'='REBUILD_POWER';'FAC-R03'='REBUILD_LENS';'FAC-R04'='REBUILD_PURIFY'
        'FAC-R05'='REBUILD_STAKES';'FAC-R06'='REBUILD_FINAL'
    }
    return $opcodes[$Id]
}
function Facility-CoreMaterial([string]$Id, [string]$ItemMaterial) {
    if ($ItemMaterial) { return $ItemMaterial }
    return @{
        'FAC-R01'='SMITHING_TABLE';'FAC-R02'='REDSTONE_LAMP';'FAC-R03'='BEACON'
        'FAC-R04'='TINTED_GLASS';'FAC-R05'='LODESTONE';'FAC-R06'='RESPAWN_ANCHOR'
    }[$Id]
}
function Facility-BaseHp([string]$Id, [string]$Tier, [int]$Threat) {
    $rebuild = @{'FAC-R01'=18000;'FAC-R02'=16000;'FAC-R03'=14000;'FAC-R04'=16000;'FAC-R05'=5000;'FAC-R06'=30000}
    if ($rebuild.ContainsKey($Id)) { return $rebuild[$Id] }
    if ($Tier -eq 'PORTABLE') { return 1 }
    if ($Tier -eq 'CAMP') { return 800 + 100 * $Threat }
    if ($Tier -eq 'DEFENSE') { return 1600 + 100 * $Threat }
    return 3300 + 100 * $Threat
}
function First-Number([string]$Value, [double]$Fallback = 0) {
    $match = [regex]::Match(($Value -replace ',',''), '\d+(?:\.\d+)?')
    return $(if ($match.Success) {[double]$match.Value} else {$Fallback})
}
function Max-Number([string]$Value, [double]$Fallback = 0) {
    $matches = @([regex]::Matches(($Value -replace ',',''), '\d+(?:\.\d+)?') | ForEach-Object { [double]$_.Value })
    return $(if ($matches.Count) {($matches | Measure-Object -Maximum).Maximum} else {$Fallback})
}
function Enemy-Name([string]$Id, [object[]]$Cells) {
    foreach ($cell in $Cells) {
        if ($cell -match [regex]::Escape($Id)) {
            $name = (($cell -replace '`','') -replace [regex]::Escape($Id),'').Trim()
            if ($name -match '[가-힣]') { return $name }
        }
    }
    return Korean-Name $Cells $Id
}
function Enemy-BukkitType([string]$Text) {
    foreach ($type in @('WITHER_SKELETON','CAVE_SPIDER','PIGLIN_BRUTE','IRON_GOLEM','MAGMA_CUBE','RAVAGER','ZOMBIE','SPIDER','SKELETON','HUSK','CREEPER','DROWNED','PHANTOM','VINDICATOR','WITCH','ENDERMAN','STRAY','BOGGED','BLAZE','SNIFFER','EVOKER','SLIME','ENDERMITE','SHULKER','HOGLIN','ALLAY','SILVERFISH')) {
        if ($Text -match [regex]::Escape($type)) { return $type }
    }
    if ($Text -match '위더 스켈레톤') { return 'WITHER_SKELETON' }
    if ($Text -match '동굴 거미') { return 'CAVE_SPIDER' }
    if ($Text -match '피글린 브루트') { return 'PIGLIN_BRUTE' }
    if ($Text -match '아이언 골렘|철 골렘') { return 'IRON_GOLEM' }
    if ($Text -match '마그마 큐브') { return 'MAGMA_CUBE' }
    $map = [ordered]@{
        '좀비'='ZOMBIE';'거미'='SPIDER';'스켈레톤'='SKELETON';'허스크'='HUSK';'크리퍼'='CREEPER';'드라운드'='DROWNED'
        '팬텀'='PHANTOM';'빈디케이터'='VINDICATOR';'마녀'='WITCH';'엔더맨'='ENDERMAN';'스트레이'='STRAY';'보그드'='BOGGED'
        '블레이즈'='BLAZE';'스니퍼'='SNIFFER';'레비저'='RAVAGER';'에보커'='EVOKER';'슬라임'='SLIME';'엔더마이트'='ENDERMITE'
        '셜커'='SHULKER';'호글린'='HOGLIN';'알레이|allay'='ALLAY';'실버피시'='SILVERFISH'
    }
    foreach ($pattern in $map.Keys) { if ($Text -match $pattern) { return $map[$pattern] } }
    return 'ZOMBIE'
}
function Enemy-Status([string]$Id) {
    $map = @{
        'EN-D5-01'='POISON';'EN-D11-01'='WEAKNESS';'EN-D11-02'='POISON';'EN-D12-01'='BURN';'EN-D13-01'='BLEED'
        'EN-D14-02'='ROOT';'EN-D16-02'='BURN';'EN-D17-01'='SILENCE';'EN-D17-02'='DISARM'
        'EN-D20-A01'='POISON';'EN-D20-A02'='BURN';'EN-D20-A03'='SILENCE';'EN-D20-A04'='BLEED'
        'EN-D21-01'='CORRUPTION';'EN-D23-01'='SLOW';'EN-D47-01'='ROOT'
    }
    return $(if ($map.ContainsKey($Id)) {$map[$Id]} else {''})
}
function Enemy-DayProfile([int]$Day) {
    if ($Day -le 3) { return 'INTRO' }; if ($Day -le 5) { return 'PRESSURE' }; if ($Day -le 7) { return 'ADAPT' }
    if ($Day -le 9) { return 'ELITE_SIGNAL' }; if ($Day -eq 10) { return 'BOSS_DAY' }; if ($Day -le 12) { return 'STATUS_INTRO' }
    if ($Day -le 14) { return 'STATUS_CHAIN' }; if ($Day -le 17) { return 'AUGMENT_ADAPT' }; if ($Day -le 19) { return 'ELITE_STATUS' }
    if ($Day -eq 20) { return 'STATUS_BOSS_DAY' }; if ($Day -le 30) { return 'CORRUPTION' }; if ($Day -le 40) { return 'BREAK_LINE' }
    return 'RECONSTRUCTION_PRESSURE'
}
function Enemy-Day([string]$Id) {
    if ($Id -match '^EN-D(\d+)') { return [int]$Matches[1] }
    return 50
}
function Enemy-TelegraphTicks([string]$Role) {
    return @{CHASER=11;FLANKER=15;RANGED=14;BRUISER=22;CONTROLLER=20;ARTILLERY=24;DEFENDER=12;SUPPORT=24;ELITE=24;SIEGE=28;SUMMON=16}[$Role]
}
function Enemy-CooldownTicks([string]$Role) {
    return @{CHASER=40;FLANKER=70;RANGED=60;BRUISER=80;CONTROLLER=90;ARTILLERY=100;DEFENDER=80;SUPPORT=120;ELITE=100;SIEGE=120;SUMMON=60}[$Role]
}
function Equipment-SetId([string]$Id) {
    if ($Id -match '^EQL-AR-C0') { return 'EQL-SET-PIONEER' }
    foreach ($set in @('SCOUT','VANGUARD','OBSERVER')) { if ($Id -match "^EQL-AR-.*-$set-") { return "EQL-SET-$set" } }
    foreach ($set in @('MED','CTRL','GUARD')) { if ($Id -match "^EQD20-AR-$set-") { return "EQD20-SET-$set" } }
    foreach ($set in @('PURIFIER','INTERRUPT','REBUILD')) { if ($Id -match "^EQD50-AR-$set-") { return "EQD50-SET-$set" } }
    return ''
}
function Add-EquipmentStat([Collections.Specialized.OrderedDictionary]$Stats, [string]$Key, [double]$Value) {
    if ($Stats.Contains($Key)) { $Stats[$Key] = [double]$Stats[$Key] + $Value } else { $Stats[$Key] = $Value }
}
function Equipment-Stats([string]$Id) {
    $stats = [ordered]@{}
    $baseWeapons = @{
        'EQL-W01'=@{ATK=8;DEF=2};'EQL-W02'=@{ATK=12};'EQL-W03'=@{ATK=8;HIT=4};'EQL-W04'=@{ATK=14}
        'EQL-W05'=@{ATK=5;EVA=4};'EQL-W06'=@{ATK=10;BREAK_DAMAGE=4};'EQL-W07'=@{ATK=8;AP=4}
        'EQL-W08'=@{ATK=10;PEN=6};'EQL-W09'=@{ATK=9;HIT=4}
    }
    if ($baseWeapons.ContainsKey($Id)) { foreach ($key in @($baseWeapons[$Id].Keys | Sort-Object)) { Add-EquipmentStat $stats $key $baseWeapons[$Id][$key] } }
    $armorBase = @{
        HEAD=@{DEF=5;HP=20};CHEST=@{DEF=10;HP=40};LEGS=@{DEF=8;HP=30};FEET=@{DEF=4;EVA=2}
    }
    $part = @('HEAD','CHEST','LEGS','FEET') | Where-Object { $Id.EndsWith("-$_") } | Select-Object -First 1
    if (-not $part -and $Id -match '^EQL-AR-C0([1-4])$') { $part = @('HEAD','CHEST','LEGS','FEET')[[int]$Matches[1]-1] }
    if ($Id -match '^EQL-AR-(C0|U-|R-)' -and $part) {
        foreach ($key in @($armorBase[$part].Keys | Sort-Object)) { Add-EquipmentStat $stats $key $armorBase[$part][$key] }
    }
    $partIndex = @{HEAD=0;CHEST=1;LEGS=2;FEET=3}
    if ($Id -match '^EQL-AR-U-VANGUARD-' -and $part) { Add-EquipmentStat $stats 'DEF' @(4,8,6,4)[$partIndex[$part]] }
    if ($Id -match '^EQL-AR-R-OBSERVER-' -and $part) { Add-EquipmentStat $stats 'RES' @(4,8,6,4)[$partIndex[$part]] }
    if ($Id -match '^EQD20-AR-(MED|CTRL|GUARD)-' -and $part) {
        $scale = @(0.7,1.4,1.1,0.6)[$partIndex[$part]]
        if ($Matches[1] -eq 'MED') { Add-EquipmentStat $stats 'RES' ([Math]::Ceiling(4*$scale)) }
        elseif ($Matches[1] -eq 'CTRL') {
            Add-EquipmentStat $stats 'TENACITY' ([Math]::Ceiling(4*$scale)); Add-EquipmentStat $stats 'STAGGER_RES' ([Math]::Ceiling(4*$scale))
        } else {
            Add-EquipmentStat $stats 'DEF' ([Math]::Ceiling(6*$scale)); Add-EquipmentStat $stats 'HP' ([Math]::Ceiling(30*$scale))
        }
    }
    $exact = @{
        'EQL-SW-U01'=@{DEF=6};'EQL-BO-U01'=@{HIT=4};'EQL-UA-U01'=@{DEF=4};'EQL-AC-C01'=@{RES=2}
        'EQL-AC-U01'=@{HIT=4};'EQL-CH-C01'=@{HP=40};'EQL-CH-U02'=@{BREAK_DAMAGE=4}
    }
    if ($exact.ContainsKey($Id)) { foreach ($key in @($exact[$Id].Keys | Sort-Object)) { Add-EquipmentStat $stats $key $exact[$Id][$key] } }
    return $stats
}
function Base-WeaponId([string]$Code) {
    return @{SW='EQL-W01';AX='EQL-W02';BO='EQL-W03';CB='EQL-W04';DG='EQL-W05';BL='EQL-W06';ST='EQL-W07';PK='EQL-W08';TR='EQL-W09'}[$Code]
}
function Previous-EquipmentId([string]$OutputId) {
    $code = Equipment-ClassCode $OutputId
    if ($code) {
        if ($OutputId -match '^EQD20-') { return "EQL-$code-R01" }
        if ($OutputId -match '-U\d+$') { return Base-WeaponId $code }
        if ($OutputId -match '-R\d+$') { return ($OutputId -replace '-R(\d+)$','-U$1') }
        if ($OutputId -match '-E21$') { return "EQD20-$code-R01" }
        if ($OutputId -match '-L31$') { return "EQD50-$code-E21" }
        if ($OutputId -match '-A41$') { return "EQD50-$code-L31" }
        if ($OutputId -match 'B(10|20|30|40)-W01-') { return Base-WeaponId $code }
        if ($OutputId -match '^EQD20-(EP|LG)-W01-') { return "EQD20-$code-R01" }
        if ($OutputId -match '^EQD50-') { return "EQD50-$code-E21" }
        if ($OutputId -match '^EQD20-') { return Base-WeaponId $code }
        return Base-WeaponId $code
    }
    if ($OutputId -match '^EQD20-') {
        if ($OutputId -match 'UA') { return 'EQL-UA-R01' }
        if ($OutputId -match 'OH') { return 'EQL-OH-R01' }
        if ($OutputId -match 'CH') { return 'EQL-CH-R01' }
        if ($OutputId -match 'AC') { return 'EQL-AC-R01' }
    }
    if ($OutputId -match '-HEAD$') { return 'EQL-AR-C01' }
    if ($OutputId -match '-CHEST$' -or $OutputId -match '-AR\d+$') { return 'EQL-AR-C02' }
    if ($OutputId -match '-LEGS$') { return 'EQL-AR-C03' }
    if ($OutputId -match '-FEET$') { return 'EQL-AR-C04' }
    if ($OutputId -match '(^|-)OH') { return 'EQL-OH-C01' }
    if ($OutputId -match '(^|-)CH') { return 'EQL-CH-C01' }
    if ($OutputId -match 'UA') { return 'EQL-UA-U01' }
    return 'EQL-AC-C01'
}

$recipeExact = @{
    'WSRCP-P01'='WSR-WOOD*3;WSR-FIBER*1'; 'WSRCP-P02'='WSR-STONE*4;WSR-COAL*1';
    'WSRCP-P03'='WSR-IRON*3;WSR-COAL*1'; 'WSRCP-P04'='WSR-COPPER*3;WSR-REDSTONE*1';
    'WSRCP-P05'='WSR-IRON*3;WSR-COPPER*2;WSR-COAL*2'; 'WSRCP-P06'='WSR-METAL_PLATE*1;WSR-COPPER_COIL*1;WSR-REDSTONE*2';
    'WSRCP-P07'='WSR-TISSUE*2;WSR-GOLD*1;WSR-COAL*2'; 'WSRCP-P08'='WSR-AMETHYST*1;WSR-TISSUE*2;WSR-COAL*2';
    'WSRCP-P09'='WSR-MAGIC_CRYSTAL*1;WSR-GOLD*1;WSR-COPPER*2'; 'WSRCP-P10'='WSR-FIBER*3;WSR-LEATHER*1';
    'WSRCP-D20-P01'='WSR-HERB*3;WSR-TISSUE*1;WSR-COAL*1'; 'WSRCP-D20-P02'='WSR-ELASTIC_FIBER*3;WSR-FIBER*2';
    'WSRCP-D20-P03'='WSR-IRON*4;WSR-COPPER*2;WSR-GOLD*1;WSR-COAL*2'; 'WSRCP-D20-P04'='WSR-NEURAL_SAMPLE*2;WSR-REDSTONE*2;WSR-COPPER*2';
    'WSRCP-D20-P05'='WSR-VITAL_TISSUE*2;WSR-STERILE_GEL*2;WSR-MAGIC_CRYSTAL*1'; 'WSRCP-D20-P06'='WSR-TOXIN_SAMPLE*1;WSR-THERMAL_SAMPLE*1;WSR-HEMATIC_SAMPLE*1;WSR-REDSTONE*1';
    'WSRCP-D50-P01'='VANILLA:WATER_BUCKET*1;VANILLA:WATER_BUCKET*1;WSR-AMETHYST*1;TAG:METAL*2;TAG:CORRUPTION_SAMPLE*1';
    'WSRCP-D50-P02'='WSR-MUTATION_SHARD*3;WSR-REDSTONE*2;WSR-COAL*1'; 'WSRCP-D50-P03'='TAG:DISTINCT_MUTATION_SAMPLE*1;TAG:DISTINCT_MUTATION_SAMPLE*1;TAG:DISTINCT_MUTATION_SAMPLE*1;WSR-PURIFY_CATALYST*1';
    'WSRCP-D50-P04'='WSR-RIFT_POWDER*4;WSR-PURIFY_CATALYST*2;WSR-MAGIC_CRYSTAL*2;TAG:METAL*2';
    'WSRCP-D50-P05'='WSR-PURIFY_CATALYST*3;WSR-RIFT_POWDER*2;WSR-BIO_MEDIUM*1'; 'WSRCP-D50-P06'='WSR-HARD_AGGREGATE*4;WSR-STONE*4;WSR-COAL*2';
    'WSRCP-D50-P07'='WSR-REDSTONE*3;WSR-COPPER*3;WSR-MAGIC_CRYSTAL*2'; 'WSRCP-D50-P08'='PROOF:VALID_OBSERVATION*3;WSR-RESONANT_RESIDUE*1';
    'WSRCP-D50-P09'='WSR-REINFORCED_ALLOY*3;WSR-HARD_AGGREGATE*3;WSR-COAL*3'; 'WSRCP-D50-P10'='WSR-RESONANCE_COIL*2;WSR-PATTERN_RESIDUE*3;PROOF:INTERRUPT_METHOD*3';
    'WSRCP-D50-P11'='WSR-RESONANCE_COIL*3;WSR-MAGIC_CRYSTAL*3;PROOF:WSP-REBUILD-PART-B*1';
    'WSRCP-D50-P12'='WSR-AMETHYST*4;WSR-PRECISION_PART*2;PROOF:WSP-REBUILD-PART-C*1';
    'WSRCP-D50-P13'='WSR-PURIFY_MEDIUM*3;WSR-PURIFY_CATALYST*2;PROOF:THREE_CATEGORY_RECORD*1';
    'WSRCP-D50-P14'='WSR-HIGH_DENSITY_ALLOY*3;WSR-HARD_AGGREGATE*4;PROOF:WSP-REBUILD-PART-A*1';
    'WSRCP-S01'='WSR-FIBER*2;WSR-LEATHER*1'; 'WSRCP-S02'='WSR-IRON*1;WSR-WOOD*1;WSR-FIBER*1';
    'WSRCP-S03'='WSR-WOOD*1;WSR-STONE*1;WSR-FIBER*1'; 'WSRCP-S04'='WSR-IRON*2;WSR-WOOD*1;WSR-FIBER*1';
    'WSRCP-S05'='WSR-CRUDE_PURIFY_CATALYST*1;WSR-FIBER*1'; 'WSRCP-S06'='WSR-RATION*1;WSR-GOLD*1;WSR-TISSUE*1';
    'WSRCP-S07'='WSR-REINFORCED_CLOTH*1;WSR-METAL_PLATE*1'; 'WSRCP-S08'='WSR-CRUDE_PURIFY_CATALYST*2;WSR-REDSTONE*1';
    'WSRCP-S09'='WSR-RATION*1;WSR-REINFORCED_CLOTH*1';
    'WSRCP-D20-S01'='WSR-HERB*2;WSR-TOXIN_SAMPLE*1;WSR-STERILE_GEL*1'; 'WSRCP-D20-S02'='WSR-HERB*1;WSR-THERMAL_SAMPLE*1;WSR-STERILE_GEL*1';
    'WSRCP-D20-S03'='WSR-FIBER*2;WSR-HERB*1;WSR-HEMATIC_SAMPLE*1'; 'WSRCP-D20-S04'='WSR-NEURAL_SAMPLE*1;WSR-GOLD*1;WSR-STERILE_GEL*1';
    'WSRCP-D20-S05'='WSR-ELASTIC_WEAVE*1;WSR-REINFORCED_ALLOY*1'; 'WSRCP-D20-S06'='WSR-BIO_MEDIUM*1;WSR-HERB*1';
    'WSRCP-D50-S01'='WSI-AMMO-ARROW_BUNDLE*1;WSR-PURIFY_CATALYST*1;WSR-REFINED_MUTATION*1';
    'WSRCP-D50-S02'='WSI-AMMO-PIERCING_BOLT_BUNDLE*1;WSR-RESONANCE_COIL*1;WSR-PATTERN_RESIDUE*1';
    'WSRCP-D50-S03'='WSI-AMMO-ARROW_BUNDLE*1;WSR-POWER_MATRIX*1;WSR-STERILE_GEL*2';
    'WSRCP-F01'='WSR-WOOD*4;WSR-STONE*2;WSR-FIBER*2'; 'WSRCP-F02'='WSR-WOOD*6;WSR-STONE*4;WSR-IRON*2';
    'WSRCP-F03'='WSR-STONE*8;WSR-COAL*2;WSR-IRON*1'; 'WSRCP-F04'='WSR-WOOD*8;WSR-IRON*2';
    'WSRCP-F05'='WSR-WOOD*4;WSR-STONE*4;WSR-IRON*2;WSR-LEATHER*2'; 'WSRCP-F06'='WSR-WOOD*4;WSR-STONE*2;WSR-IRON*2';
    'WSRCP-F07'='WSR-WOOD*1;WSR-IRON*2;WSR-COPPER*4;WSR-REDSTONE*4';
    'WSRCP-F08'='WSR-PRECISION_PART*1;WSR-REINFORCED_CLOTH*1;WSR-IRON*1';
    'WSRCP-F09'='WSR-CRUDE_PURIFY_CATALYST*2;WSR-MAGIC_CRYSTAL*1;WSR-COPPER_COIL*2;WSR-METAL_PLATE*2';
    'WSRCP-F10'='WSR-METAL_PLATE*2;WSR-REDSTONE*2;WSR-SIGNAL_LENS*1'; 'WSRCP-F11'='VANILLA:BOOK*1;WSR-COPPER_COIL*1;WSR-REDSTONE*2';
    'WSRCP-F12'='WSR-WOOD*2;WSR-REINFORCED_CLOTH*2'; 'WSRCP-F13'='WSR-METAL_PLATE*2;WSR-COPPER_COIL*1';
    'WSRCP-F14'='WSR-METAL_PLATE*3;WSR-HARDWOOD_PART*2';
    'WSRCP-G01'='WSR-IRON*1;WSR-COPPER*2;WSR-REDSTONE*2'; 'WSRCP-G02'='WSR-IRON*4;WSR-COPPER*8;WSR-GOLD*2;WSR-REDSTONE*8;WSR-MAGIC_CRYSTAL*1;WSR-TISSUE*2';
    'WSRCP-G03'='WSI-PORTABLE-SIGNAL_STAKE*3;WSR-BOSS_SIGNAL_CORE*1';
    'WSRCP-D20-CALL'='WSR-BIO_MEDIUM*2;WSR-NEURAL_CIRCUIT*2;WSR-SIGNAL_LENS*2;WSR-REINFORCED_ALLOY*3;WSR-STATUS_PLATE*1;PROOF:WSP-REBUILD-PART-A*1;PROOF:WSP-BOSS-D10-CORE*1';
    'WSRCP-D30-CALL'='WSR-STABLE_CORE*2;WSR-PURIFY_MEDIUM*4;WSR-REFINED_MUTATION*3;WSR-SIGNAL_LENS*2';
    'WSRCP-D40-CALL'='WSR-INTERRUPT_CORE*2;WSR-HIGH_DENSITY_ALLOY*4;WSR-PATTERN_RESIDUE*6;WSR-RESONANCE_COIL*4';
    'WSRCP-FINAL-KEY'='WSR-STABILIZED_FRAME*2;WSR-POWER_MATRIX*2;WSR-CALIBRATED_LENS*2;WSR-PURIFY_MATRIX*2';
    'WSRCP-R01'='WSR-STABILIZED_FRAME*12;WSR-HARD_AGGREGATE*16;PROOF:WSP-REBUILD-PART-A*1';
    'WSRCP-R02'='WSR-POWER_MATRIX*6;WSR-RESONANCE_COIL*8;PROOF:WSP-REBUILD-PART-B*1';
    'WSRCP-R03'='WSR-CALIBRATED_LENS*4;WSR-PATTERN_RESIDUE*6;PROOF:WSP-REBUILD-PART-C*1';
    'WSRCP-R04'='WSR-PURIFY_MATRIX*6;WSR-PURIFY_CATALYST*10;PROOF:WSP-REBUILD-PART-D*1';
    'WSRCP-R05'='WSR-HIGH_DENSITY_ALLOY*6;WSR-CALIBRATED_LENS*3;WSR-RESONANCE_COIL*6';
    'WSRCP-R06'='PROOF:FAC-R01_READY*1;PROOF:FAC-R02_READY*1;PROOF:FAC-R03_READY*1;PROOF:FAC-R04_READY*1;PROOF:FAC-R05_READY*1;PROOF:WSR-FINAL_SIGNAL_KEY*1';
    'WSRCP-D20-F01'='WSR-WOOD*5;WSR-STONE*3;WSR-IRON*3;WSR-HERB*3';
    'WSRCP-D20-F02'='WSR-WOOD*8;WSR-STONE*6;WSR-COPPER*6;WSR-REDSTONE*6;WSR-MAGIC_CRYSTAL*1';
    'WSRCP-D20-F03'='WSR-WOOD*8;WSR-STONE*6;WSR-IRON*8;WSR-HERB*8;WSR-STERILE_GEL*4';
    'WSRCP-D20-F04'='WSR-WOOD*6;WSR-STONE*8;WSR-IRON*5;WSR-REDSTONE*4';
    'WSRCP-D20-F05'='WSR-REINFORCED_ALLOY*4;WSR-REDSTONE*8;WSR-MAGIC_CRYSTAL*3;WSR-GOLD*4';
    'WSRCP-D20-F06'='PROOF:FAC-S06_ACTIVE*1;WSR-NEURAL_CIRCUIT*2;WSR-STATUS_PLATE*1';
    'WSRCP-W01'='WSR-METAL_PLATE*2;WSR-HARDWOOD_PART*1'; 'WSRCP-W02'='WSR-METAL_PLATE*3;WSR-HARDWOOD_PART*1';
    'WSRCP-W03'='WSR-HARDWOOD_PART*2;WSR-REINFORCED_CLOTH*2'; 'WSRCP-W04'='WSR-METAL_PLATE*1;WSR-HARDWOOD_PART*2;WSR-REINFORCED_CLOTH*1;WSR-COPPER_COIL*1';
    'WSRCP-W05'='WSR-METAL_PLATE*2;WSR-REINFORCED_CLOTH*1'; 'WSRCP-W06'='WSR-METAL_PLATE*3;WSR-HARDWOOD_PART*1;WSR-SINTERED_AGGREGATE*1';
    'WSRCP-W07'='WSR-HARDWOOD_PART*2;WSR-COAL*2;WSR-REDSTONE*1'; 'WSRCP-W08'='WSR-METAL_PLATE*3;WSR-HARDWOOD_PART*1';
    'WSRCP-W09'='WSR-METAL_PLATE*3;WSR-COPPER_COIL*1;WSR-REINFORCED_CLOTH*1';
    'WSRCP-E01'='WSR-METAL_PLATE*1;WSR-REINFORCED_CLOTH*1'; 'WSRCP-E02'='WSR-METAL_PLATE*3;WSR-REINFORCED_CLOTH*2';
    'WSRCP-E03'='WSR-METAL_PLATE*2;WSR-REINFORCED_CLOTH*2'; 'WSRCP-E04'='WSR-METAL_PLATE*1;WSR-REINFORCED_CLOTH*1';
    'WSRCP-E05'='WSR-METAL_PLATE*2;WSR-HARDWOOD_PART*2'; 'WSRCP-E06'='WSR-REINFORCED_CLOTH*2;WSR-HARDWOOD_PART*1';
    'WSRCP-E07'='WSR-COPPER_COIL*1;WSR-REDSTONE*1;WSR-IRON*1'
}
$recipeAmounts = @{
    'WSRCP-P01'=2;'WSRCP-P02'=2;'WSRCP-P03'=2;'WSRCP-P04'=2;'WSRCP-P05'=2;'WSRCP-P07'=2;'WSRCP-P10'=2;
    'WSRCP-D20-P01'=2;'WSRCP-D20-P02'=2;'WSRCP-D20-P03'=2;'WSRCP-D20-S01'=2;'WSRCP-D20-S02'=2;'WSRCP-D20-S03'=2;
    'WSRCP-D50-P01'=2;'WSRCP-D50-P02'=2;'WSRCP-D50-P06'=2;'WSRCP-S01'=2;'WSRCP-S03'=16;'WSRCP-S04'=8;'WSRCP-S05'=2;'WSRCP-S09'=2;
    'WSRCP-D50-S01'=8;'WSRCP-D50-S02'=8;'WSRCP-D50-S03'=4;'WSRCP-F14'=4;'WSRCP-R05'=3
}

$materialRows = Read-TableRows '기획\04 장비와 경제\MATERIAL-LIST Season 1 재료 목록 기획서.md'
$itemRows = Read-TableRows '기획\04 장비와 경제\ITEM-LIST Season 1 아이템 목록 기획서.md'
$toolRows = Read-TableRows '기획\04 장비와 경제\TOOL-LIST Season 1 도구·방어구 목록 기획서.md'
$equipmentListRows = Read-TableRows '기획\04 장비와 경제\EQUIP-LIST 장비 목록 기획서.md'
$equipmentD20Rows = Read-TableRows '기획\04 장비와 경제\EQUIP-DATA Day 11-20 장비 목록 기획서.md'
$equipmentD50Rows = Read-TableRows '기획\04 장비와 경제\EQUIP-DATA Day 21-50 장비 실행 데이터 기획서.md'
$equipmentDetailById = Find-IdRows @($equipmentListRows + $equipmentD20Rows + $equipmentD50Rows) '(?:EQL|EQD20|EQD50)-[A-Z0-9{}*_-]+'
$recipeRows = Read-TableRows '기획\04 장비와 경제\RECIPE-LIST Season 1 조합법 목록 기획서.md'
$skillRows = Read-TableRows '기획\02 플레이어 성장\SKILL-LIST 스킬 목록 기획서.md'
$personalAugmentRows = Read-TableRows '기획\02 플레이어 성장\AUGMENT-PERSONAL-LIST Season 1 개인 증강 목록 기획서.md'
$partyAugmentRows = Read-TableRows '기획\02 플레이어 성장\AUGMENT-PARTY-LIST Season 1 파티 증강 목록 기획서.md'
$entityRows = Read-TableRows '기획\06 사건과 적\ENTITY-LIST Season 1 엔티티 목록 기획서.md'
$enemyBaseRows = Read-TableRows '기획\06 사건과 적\ENEMY 적 역할 및 템플릿 기획서.md'
$enemyD20Rows = Read-TableRows '기획\06 사건과 적\ENEMY-DATA Day 11-20 적 목록 기획서.md'
$enemyD50Rows = Read-TableRows '기획\06 사건과 적\ENEMY-DATA Day 21-50 적 실행 데이터 기획서.md'
$enemyDetailById = Find-IdRows @($enemyBaseRows + $enemyD20Rows + $enemyD50Rows) 'EN-(?:D\d+|F50)-[A-Z0-9]+'
$facilityRows = Read-TableRows '기획\05 세계와 생존\FACILITY 플레이어 시설 목록 기획서.md'
$facilityDataRows = Read-TableRows '기획\05 세계와 생존\FACILITY-DATA Day 11-50 시설 실행 데이터 기획서.md'
$lootRows = Read-TableRows '기획\04 장비와 경제\LOOT-LIST Season 1 획득·드롭 목록 기획서.md'

$materialCodex = [ordered]@{}
foreach ($cells in $materialRows) {
    if ($cells.Count -ge 2 -and $cells[0] -match '^\d{4}$' -and $cells[1] -match '^WS[RP]-') {
        $materialCodex[$cells[1]] = [int]$cells[0]
    }
}
$materialMetadata = Find-IdRows $materialRows '^WS[RP]-[A-Z0-9_-]+$'
$harvestSources = @{
    'WSR-WOOD'=@('#LOGS'); 'WSR-STONE'=@('STONE','COBBLESTONE','DEEPSLATE','COBBLED_DEEPSLATE')
    'WSR-FIBER'=@('STRING','COBWEB','VINE'); 'WSR-COAL'=@('COAL_ORE','DEEPSLATE_COAL_ORE')
    'WSR-IRON'=@('IRON_ORE','DEEPSLATE_IRON_ORE'); 'WSR-COPPER'=@('COPPER_ORE','DEEPSLATE_COPPER_ORE')
    'WSR-GOLD'=@('GOLD_ORE','DEEPSLATE_GOLD_ORE','NETHER_GOLD_ORE'); 'WSR-REDSTONE'=@('REDSTONE_ORE','DEEPSLATE_REDSTONE_ORE')
    'WSR-AMETHYST'=@('AMETHYST_CLUSTER'); 'WSR-HERB'=@('DANDELION','POPPY','OXEYE_DAISY','AZURE_BLUET')
    'WSR-ELASTIC_FIBER'=@('COBWEB')
}
$materials = foreach ($entry in $materialCodex.GetEnumerator()) {
    $cells = $materialMetadata[$entry.Key]
    $joined = $cells -join ' '
    $firstDay = @($cells | Where-Object { $_ -match '^\d{1,2}$' } | ForEach-Object { [int]$_ } | Where-Object { $_ -le 50 } | Select-Object -First 1)
    $displayMaterial = First-Material $cells ''
    if ($displayMaterial -match '^T\d$' -or $displayMaterial -eq 'UNIQUE') { $displayMaterial = Fallback-Material $entry.Key }
    [ordered]@{
        id = $entry.Key; sourceDocumentId = 'MATERIAL-LIST-001'; enabled = $true
        codexIndex = $entry.Value; name = Korean-Name $cells $entry.Key
        firstDay = $(if ($firstDay.Count) { $firstDay[0] } else { 1 })
        displayMaterial = $displayMaterial; ledgerScope = $(if ($entry.Key.StartsWith('WSP-')) { 'RUN_PROOF' } else { 'PERSONAL_THEN_PUBLIC' })
        tier = $(if ($cells.Count -gt 2) {$cells[2].Split(' ')[0]} else {'UNIQUE'})
        acquisitionKind = $(if ($entry.Key.StartsWith('WSP-')) {'PROOF'} elseif ($joined -match 'PARTY_RESOURCE') {'PARTY_REWARD'}
            elseif ($joined -match 'WSRCP-') {'CRAFTED'} elseif ($harvestSources.ContainsKey($entry.Key)) {'HARVEST'} else {'ENCOUNTER'})
        registrationAmount = $(if ($cells.Count -gt 4 -and $cells[4] -match '([0-9]+)') {[int]$Matches[1]} else {1})
        harvestSources = [string[]]$(if ($harvestSources.ContainsKey($entry.Key)) {@($harvestSources[$entry.Key])} else {@()})
        sourceText = $(if ($cells.Count -gt 5) {$cells[$cells.Count - 2]} else {''}); usageText = $cells[$cells.Count - 1]
        raw = @($cells)
    }
}

$items = foreach ($cells in $itemRows) {
    if ($cells.Count -lt 3 -or $cells[0] -notmatch '^\d{4}$' -or $cells[1] -notmatch '^WSI-') { continue }
    $numbers = @($cells | Select-Object -Skip 2 | Where-Object { $_ -match '^\d{1,2}$' } | ForEach-Object { [int]$_ })
    [ordered]@{
        id = $cells[1]; sourceDocumentId = 'ITEM-LIST-001'; enabled = $true
        codexIndex = [int]$cells[0]; name = Korean-Name ($cells | Select-Object -Skip 2) $cells[1]
        firstDay = $(if ($numbers.Count) { [Math]::Min(50, $numbers[0]) } else { 1 })
        displayMaterial = First-Material $cells ''
        category = $(if ($cells[1] -match '^WSI-([A-Z0-9]+)-') {$Matches[1]} else {'ITEM'})
        stackLimit = $(if ($cells.Count -gt 5 -and $cells[5] -match '^\d+$') {[int]$cells[5]} else {64})
        effectText = $(if ($cells.Count -gt 6) {$cells[6]} else {''})
        recipeId = $(if ($cells.Count -gt 7 -and $cells[7] -match '^WSRCP-') {$cells[7]} else {''})
        raw = @($cells)
    }
}

function Equipment-DetailKey([string]$Id) {
    if ($equipmentDetailById.Contains($Id)) { return $Id }
    if ($Id -match '^EQL-D10-W-[A-Z]{2}$') { return 'EQL-D10-W-{class}' }
    if ($Id -match '^EQD20-(EP|LG)-W01-[A-Z]{2}$') { return "EQD20-$($Matches[1])-W01" }
    if ($Id -match '^EQD50-(B30|B40)-W01-[A-Z]{2}$') { return "EQD50-$($Matches[1])-W01" }
    foreach ($set in @('SCOUT','VANGUARD','OBSERVER')) {
        if ($Id -match "^EQL-AR-.*-$set-") { return "EQL-AR-$($(if ($set -eq 'OBSERVER') {'R'} else {'U'}))-$set-*" }
    }
    foreach ($set in @('MED','CTRL','GUARD')) { if ($Id -match "^EQD20-AR-$set-") { return "EQD20-SET-$set" } }
    foreach ($set in @('PURIFIER','INTERRUPT','REBUILD')) { if ($Id -match "^EQD50-AR-$set-") { return "EQD50-SET-$set" } }
    return ''
}
function Equipment-PartName([string]$Id) {
    if ($Id -match '-HEAD$') { return '투구' }; if ($Id -match '-CHEST$' -or $Id -match '-C02$') { return '흉갑' }
    if ($Id -match '-LEGS$' -or $Id -match '-C03$') { return '각반' }; if ($Id -match '-FEET$' -or $Id -match '-C04$') { return '장화' }
    if ($Id -match '-C01$') { return '투구' }; return ''
}
function Equipment-DisplayName([string]$Id, [string]$DetailKey, [object[]]$DetailCells) {
    if ($Id -match '^EQL-UT-(RI|RS|HD|RC)-(PICKAXE|AXE|SHOVEL|HOE)$') {
        $tierName = @{RI='강화 철';RS='공명';HD='경화';RC='재건'}[$Matches[1]]
        $toolName = @{PICKAXE='곡괭이';AXE='도끼';SHOVEL='삽';HOE='괭이'}[$Matches[2]]
        return "$tierName $toolName"
    }
    $setNames = @{PIONEER='개척자';SCOUT='수색자';VANGUARD='선봉대';OBSERVER='오염 관측자'
        MED='멸균 구조복';CTRL='신경 차폐복';GUARD='격리 방호복'
        PURIFIER='경계 정화복';INTERRUPT='중단 작업복';REBUILD='첫불씨 방호복'}
    if ($Id -match '^EQL-AR-C0') { return "$($setNames.PIONEER) $(Equipment-PartName $Id)" }
    foreach ($set in $setNames.Keys) {
        if ($Id -match "-$set-") { return "$($setNames[$set]) $(Equipment-PartName $Id)" }
    }
    $candidate = $(if ($DetailCells -and $DetailCells.Count) { Korean-Name $DetailCells $Id } else { $Id })
    if ($DetailKey) { $candidate = ($candidate -replace [regex]::Escape($DetailKey), '').Trim() }
    $candidate = $candidate.Trim([char[]]@([char]96,[char]32))
    if ($candidate -eq $Id -or [string]::IsNullOrWhiteSpace($candidate)) { $candidate = $Id }
    $code = Equipment-ClassCode $Id
    if ($Id -match 'W01-[A-Z]{2}$' -and $code) { return "$candidate ($(Equipment-ClassName $code))" }
    return $candidate
}
function Equipment-Tags([string]$Id, [object[]]$DetailCells) {
    $result = [Collections.Generic.List[string]]::new()
    $code = Equipment-ClassCode $Id
    if ($code) { $result.Add((Equipment-WeaponClass $code)) }
    if ($DetailCells -and $DetailCells.Count) {
        $clean = ($DetailCells[$DetailCells.Count - 1] -replace '[^A-Z0-9_,]','')
        foreach ($tag in $clean.Split(',', [StringSplitOptions]::RemoveEmptyEntries)) {
            if ($tag.Length -ge 3 -and $tag -notmatch '^(WSRCP|EQUIP|DATA)') { $result.Add($tag) }
        }
    }
    return @($result | Select-Object -Unique)
}

$tools = foreach ($cells in $toolRows) {
    if ($cells.Count -lt 6 -or $cells[0] -notmatch '^\d{4}$' -or $cells[1] -notmatch '^(EQL-|EQD(20|50)-)') { continue }
    $equipmentSlot = $cells[3]
    if ($cells[2] -eq 'ARMOR') {
        foreach ($part in @('HEAD','CHEST','LEGS','FEET')) {
            if ($cells[1].EndsWith("-$part")) { $equipmentSlot = "ARMOR_$part"; break }
        }
    } elseif ($cells[2] -eq 'CHARM') {
        $equipmentSlot = 'CHARM'
    } elseif ($cells[2] -in @('ACCESSORY','UNARMED_SUPPORT')) {
        $equipmentSlot = 'ACCESSORY'
    }
    $id = $cells[1]
    $detailKey = Equipment-DetailKey $id
    $detailCells = $(if ($detailKey -and $equipmentDetailById.Contains($detailKey)) { @($equipmentDetailById[$detailKey]) } else { @() })
    $rarity = Equipment-Rarity $id
    $code = Equipment-ClassCode $id
    if (-not $code -and $cells[2] -in @('SW','AX','BO','CB','DG','BL','ST','PK','TR')) { $code = $cells[2] }
    $compiledTags = [string[]]@(Equipment-Tags $id $detailCells)
    if ($code -and $compiledTags -notcontains (Equipment-WeaponClass $code)) {
        $compiledTags = [string[]]@((Equipment-WeaponClass $code)) + $compiledTags
    }
    $toolTier = Equipment-ToolTier $id
    if ($toolTier -lt 0 -and $code -eq 'PK') {
        $day = Equipment-FirstDay $id
        $toolTier = $(if ($day -ge 41) {6} elseif ($day -ge 31) {5} elseif ($day -ge 21) {4} elseif ($day -ge 11) {3} else {2})
    }
    [ordered]@{
        id = $id; sourceDocumentId = 'TOOL-LIST-001'; enabled = $true
        codexIndex = [int]$cells[0]; name = Equipment-DisplayName $id $detailKey $detailCells
        equipmentType = $cells[2]; equipmentSlot = $equipmentSlot
        weaponClass = $(if ($code) { Equipment-WeaponClass $code } else { '' })
        displayMaterial = First-Material $cells $equipmentSlot; definition = $cells[5]
        rarity = $rarity; itemLevel = Equipment-ItemLevel $id; firstDay = Equipment-FirstDay $id
        maxDurability = Equipment-Durability $id $cells[2] $rarity; toolTier = $toolTier
        setId = Equipment-SetId $id; tags = [string[]]@($compiledTags)
        stats = Equipment-Stats $id
        effectText = $(if ($detailCells.Count) { $detailCells -join ' | ' } else { $cells[5] })
        raw = @($cells)
    }
}

$recipesById = Find-IdRows $recipeRows '^WSRCP-[A-Z0-9_-]+$'
$recipes = foreach ($entry in $recipesById.GetEnumerator()) {
    $cells = $entry.Value
    $idIndex = [Array]::IndexOf($cells, $entry.Key)
    $outputId = $(if ($idIndex + 1 -lt $cells.Count) { $cells[$idIndex + 1] } else { 'UNRESOLVED' })
    $recipeType = $(if ($idIndex + 2 -lt $cells.Count) { $cells[$idIndex + 2] } else { 'UNRESOLVED' })
    $layout = 'ORDERED_3X3'
    $ingredients = @()
    if ($recipeExact.ContainsKey($entry.Key)) {
        $ingredients = @(Ingredients-FromSpec $recipeExact[$entry.Key])
        if ($recipeType -eq 'BOSS_CALL') { $layout = 'CALL_FRAME' }
        elseif ($recipeType -eq 'EQUIPMENT_FORGE') { $layout = 'EQUIPMENT_FRAME' }
        elseif ($recipeType -eq 'FACILITY_KIT') { $layout = 'FACILITY_FRAME' }
        elseif ($recipeType -eq 'VIRTUAL_BUILD') { $layout = 'REBUILD_FRAME' }
    } elseif ($recipeType -eq 'FACILITY_KIT') {
        $layout = 'FACILITY_FRAME'
        $facilityId = 'FAC-' + ($outputId -replace '^WSI-FAC-','' -replace '-KIT$','')
        $profile = 'PRODUCTION'
        $facilityRow = (Find-IdRows $facilityDataRows ([regex]::Escape($facilityId))).GetEnumerator() | Select-Object -First 1
        if ($facilityRow -and $facilityRow.Value.Count -gt 1) { $profile = $facilityRow.Value[1] }
        $cost = switch ($profile) {
            'RESEARCH' {@(10,2,8,12,2)} 'SURVIVAL' {@(10,8,8,4,2)}
            'LOGISTICS' {@(14,2,10,10,0)} 'DEFENSE' {@(8,2,12,4,0)}
            default {@(12,2,10,4,0)}
        }
        $tags = @('CONSTRUCTION','SURVIVAL','METAL','SIGNAL','SPECIAL')
        $slots = @(0,2,4,6,8)
        $built = [Collections.Generic.List[object]]::new()
        for ($i=0; $i -lt 5; $i++) { if ($cost[$i] -gt 0) { $built.Add((Recipe-Ingredient $slots[$i] ("TAG:"+$tags[$i]) $cost[$i])) } }
        $ingredients = @($built)
    } elseif ($recipeType -eq 'UTILITY_FORGE') {
        $layout = 'EQUIPMENT_FRAME'
        if ($entry.Key -match '^WSRCP-UT-(RI|RS|HD|RC)-(PICKAXE|AXE|SHOVEL|HOE)$') {
            $tier = $Matches[1]; $tool = $Matches[2]; $lower = $tool.ToLowerInvariant()
            $spec = switch ($tier) {
                'RI' { "VANILLA:IRON_$($tool)*1;WSR-REFINED_ALLOY*3;WSR-HARDWOOD_PART*2" }
                'RS' { "EQL-UT-RI-$tool*1;WSR-PURIFY_CATALYST*2;WSR-REINFORCED_ALLOY*3;WSR-MAGIC_CRYSTAL*2" }
                'HD' { "EQL-UT-RS-$tool*1;WSR-HARD_AGGREGATE*4;WSR-REFINED_MUTATION*2;WSR-STABLE_CORE*1" }
                default {
                    $special = @{PICKAXE='WSR-CALIBRATED_LENS*1';AXE='WSR-RESONANCE_COIL*2';SHOVEL='WSR-PURIFY_MATRIX*1';HOE='WSR-BIO_MEDIUM*3'}[$tool]
                    "EQL-UT-HD-$tool*1;WSR-POWER_MATRIX*1;WSR-HIGH_DENSITY_ALLOY*3;$special"
                }
            }
            $ingredients = @(Ingredients-FromSpec $spec @(4,1,3,5,7,0,2,6,8))
        }
    } elseif ($recipeType -eq 'EQUIPMENT_FORGE') {
        $layout = 'EQUIPMENT_FRAME'
        $base = Previous-EquipmentId $outputId
        $spec = if ($outputId -match '-E21$') { "$base*1;WSR-REINFORCED_ALLOY*14;WSR-NEURAL_CIRCUIT*8;WSR-PURIFY_CATALYST*8" }
            elseif ($outputId -match '-L31$') { "$base*1;WSR-HIGH_DENSITY_ALLOY*24;WSR-RESONANCE_COIL*16;WSR-PATTERN_RESIDUE*14" }
            elseif ($outputId -match '-A41$') { "$base*1;WSR-HIGH_DENSITY_ALLOY*38;WSR-RESONANCE_COIL*28;WSR-INTERRUPT_CORE*28" }
            elseif ($outputId -match '^EQD50-B(30|40)-') { "$base*1;WSR-PATTERN_RESIDUE*8;WSR-HIGH_DENSITY_ALLOY*8;WSR-NEURAL_RESIDUE*3" }
            elseif ($outputId -match '^EQD50-') { "$base*1;WSR-PURIFY_CATALYST*8;WSR-REINFORCED_ALLOY*10;WSR-PATTERN_RESIDUE*4" }
            elseif ($outputId -match '^EQD20-LG-') { "$base*1;WSR-REINFORCED_ALLOY*8;WSR-NEURAL_CIRCUIT*5;WSR-NEURAL_RESIDUE*3" }
            elseif ($outputId -match '^EQD20-EP-') { "$base*1;WSR-REINFORCED_ALLOY*6;WSR-NEURAL_CIRCUIT*4;WSR-RESONANT_RESIDUE*4" }
            elseif ($outputId -match '^EQD20-') { "$base*1;WSR-REINFORCED_ALLOY*3;WSR-NEURAL_CIRCUIT*1;WSR-MAGIC_CRYSTAL*1" }
            elseif ($outputId -match '^EQL-D10-') { "$base*1;WSR-RESONANT_RESIDUE*4;WSR-REFINED_ALLOY*4;WSR-PRECISION_PART*3" }
            elseif ($outputId -match '-R\d+$') { "$base*1;WSR-REFINED_ALLOY*3;WSR-PRECISION_PART*1;WSR-MAGIC_CRYSTAL*1" }
            else { "$base*1;WSR-METAL_PLATE*2;WSR-REINFORCED_CLOTH*1" }
        $ingredients = @(Ingredients-FromSpec $spec @(4,1,3,5,7,0,2,6,8))
    }
    if ($ingredients.Count -eq 0) { throw "No executable inputs compiled for $($entry.Key) ($recipeType)" }
    $outputAmount = $(if ($recipeAmounts.ContainsKey($entry.Key)) { [int]$recipeAmounts[$entry.Key] } else { 1 })
    [ordered]@{
        id = $entry.Key; sourceDocumentId = 'RECIPE-LIST-001'; enabled = $true
        outputId = $outputId; outputAmount = $outputAmount; recipeType = $recipeType
        inputAuthority = $(if ($idIndex + 3 -lt $cells.Count) { $cells[$idIndex + 3] } else { 'RECIPE-LIST-001' })
        layout = $layout; ingredients = @($ingredients); raw = @($cells)
    }
}

$skillPattern = '^ws\.(basic|sword|axe|bow|crossbow|dagger|blunt|staff|pickaxe|trident|unarmed|common|context)\.[a-z0-9_.-]+$'
$skillsById = Find-IdRows $skillRows $skillPattern
$skills = foreach ($entry in $skillsById.GetEnumerator()) {
    $id = $entry.Key; $cells = $entry.Value; $joined = $cells -join ' '
    $kind = if ($id.StartsWith('ws.basic.')) {'BASIC'} elseif ($id.StartsWith('ws.common.')) {'COMMON_ACTIVE'} elseif ($id.StartsWith('ws.context.')) {'CONTEXT'} else {'WEAPON_ACTIVE'}
    $weaponClass = if ($kind -eq 'BASIC') {$cells[1]} elseif ($kind -eq 'WEAPON_ACTIVE') { $id.Split('.')[1].ToUpperInvariant() } elseif ($kind -eq 'COMMON_ACTIVE') {'COMMON'} else {'CONTEXT'}
    if ($weaponClass -eq 'SWORD') {$weaponClass='SWORD'} elseif ($weaponClass -eq 'BLUNT') {$weaponClass='MACE'}
    $name = if ($kind -eq 'BASIC') {$weaponClass + ' 기본 공격'} else {$cells[1]}
    $apCost = 0.0; $cooldownTicks = 0; $damage = 0.0; $breakDamage = 0.0
    if ($kind -eq 'BASIC') {
        $apCost=[double]$cells[2]
        if ($cells[3] -match '([0-9.]+)초') {$cooldownTicks=[int][Math]::Round([double]$Matches[1]*20)}
        if ($cells[4] -match 'ATK\s*([0-9.]+)(?:~([0-9.]+))?') {$damage=$(if($Matches[2]){[double]$Matches[2]}else{[double]$Matches[1]})}
        if ($cells[5] -match '([0-9.]+)') {$breakDamage=[double]$Matches[1]}
    } elseif ($kind -ne 'CONTEXT') {
        if ($cells[2] -match '([0-9.]+)') {$apCost=[double]$Matches[1]}
        if ($cells[2] -match '([0-9.]+)초') {$cooldownTicks=[int][Math]::Round([double]$Matches[1]*20)}
        $effectCell = if ($kind -eq 'COMMON_ACTIVE') {$cells[4]} else {$cells[3]}
        if ($effectCell -match 'ATK\s*([0-9.]+)(?:/([0-9.]+))?') {$damage=[double]$Matches[1]}
        if ($effectCell -match '브레이크\s*([0-9.]+)') {$breakDamage=[double]$Matches[1]}
    }
    $damageFallbacks = @{
        'ws.bow.barbed_rain.v1'=0.65; 'ws.crossbow.magazine_volley.v1'=0.70
        'ws.dagger.venom_flurry.v1'=0.45; 'ws.pickaxe.armor_drill.v1'=0.90
    }
    if ($damage -eq 0.0 -and $damageFallbacks.ContainsKey($id)) {$damage=[double]$damageFallbacks[$id]}
    $effectText = if ($kind -eq 'BASIC') {$cells[4..($cells.Count-1)] -join ' '} elseif ($kind -eq 'COMMON_ACTIVE') {$cells[4]} elseif ($kind -eq 'CONTEXT') {$joined} else {$cells[3]}
    $tagCell = if ($kind -eq 'WEAPON_ACTIVE') {$cells[4]} elseif ($kind -eq 'COMMON_ACTIVE') {$cells[5]} else {''}
    $tags = @($tagCell -replace '`','' -split ',' | ForEach-Object {$_.Trim()} | Where-Object {$_})
    $effect = if ($id -eq 'ws.trident.cast_recall.v1') {'TRIDENT_TOGGLE'}
        elseif ($id -eq 'ws.common.ap_stim.v1') {'AP_STIM'} elseif ($id -eq 'ws.common.rescue_line.v1') {'RESCUE_PULL'}
        elseif ($id -eq 'ws.common.emergency_cover.v1') {'COVER'} elseif ($id -eq 'ws.unarmed.centered_stance.v1') {'STANCE'}
        elseif ($joined -match 'HEALING|회복') {'HEAL'}
        elseif ($joined -match 'CONTROL_BREAK|CLEANSE|해제|정화') {'CLEANSE'} elseif ($joined -match 'ARMOR_BREAK|DEF -') {'ARMOR_SHRED'}
        elseif ($joined -match 'ROOT') {'ROOT'} elseif ($joined -match 'SLOW') {'SLOW'} elseif ($joined -match 'MARK') {'MARK'}
        elseif ($joined -match 'BLEED') {'BLEED'} elseif ($joined -match 'POISON') {'POISON'} elseif ($joined -match 'BURN') {'BURN'}
        elseif ($joined -match 'VULNERABLE') {'VULNERABLE'} elseif ($joined -match 'RELOAD') {'RELOAD'} elseif ($joined -match 'SUPPORT|COOP|GUARD') {'SUPPORT'} else {'DAMAGE'}
    $range = 4.0
    if ($effectText -match '([0-9.]+)(블록|m)') {$range=[double]$Matches[1]}
    elseif ($weaponClass -in @('BOW','CROSSBOW')) {$range=28.0} elseif ($weaponClass -eq 'STAFF') {$range=18.0}
    $arc = if ($effectText -match '([0-9.]+)도') {[double]$Matches[1]} elseif ($joined -match 'AREA|영역|반경') {360.0} else {70.0}
    $maxTargets = if ($effectText -match '최대\s*([0-9]+)대상') {[int]$Matches[1]} elseif ($joined -match 'AREA|MULTITARGET|CHAIN') {6} else {1}
    $unlockLevel = if ($joined -match '레벨\s*([0-9]+)') {[int]$Matches[1]} else {1}
    $consumableId = switch -Regex ($id) {
        'field_bandage' {'WSI-CONS-BANDAGE'} 'quick_purify|control_break' {'WSI-CONS-PURIFY_AMPOULE'}
        'ap_stim' {'WSI-CONS-AP_STIM'} 'rescue_line' {'WSI-CONS-RESCUE_BRACE'}
        'emergency_cover' {'WSI-CONS-REPAIR_KIT'} default {''}
    }
    [ordered]@{id=$id;sourceDocumentId='SKILL-LIST-001';enabled=$true;kind=$kind;name=$name;weaponClass=$weaponClass
        apCost=$apCost;cooldownTicks=$cooldownTicks;damageCoefficient=$damage;breakDamage=$breakDamage;range=$range;arcDegrees=$arc
        maxTargets=$maxTargets;effect=$effect;tags=@($tags);unlockLevel=$unlockLevel;consumableId=$consumableId;description=$effectText;raw=@($cells)}
}
function Compile-Augment([string]$Id, [string]$Source, [string[]]$Cells, [string]$Scope) {
    $tier = if ($Id.StartsWith('AUG-S-')) {'SILVER'} elseif ($Id.StartsWith('AUG-G-')) {'GOLD'} elseif ($Id.StartsWith('AUG-P-')) {'PRISM'} else {'PARTY'}
    $tags = @($Cells[2] -replace '`','' -split ',' | ForEach-Object {$_.Trim()} | Where-Object {$_})
    $effectText = $Cells[3]
    $constraintText = if ($Cells.Count -gt 4) {$Cells[4]} else {''}
    $weightingText = if ($Cells.Count -gt 5) {$Cells[5]} else {''}
    $exclusive = @([regex]::Matches(($Cells -join ' '), 'AUG-[SGP]-\d{3}') | ForEach-Object {$_.Value} | Where-Object {$_ -ne $Id} | Select-Object -Unique)
    $opcode = if ($Id -eq 'AUG-S-001') {'DODGE_COST'} elseif ($Id -eq 'AUG-S-002') {'AP_REGEN'}
        elseif ($Id -eq 'AUG-S-008') {'AMMO_CONSERVE'} elseif ($Id -eq 'AUG-S-014') {'REVIVE_SPEED'}
        elseif ($Id -eq 'AUG-S-018') {'CRAFT_CONSERVE'} elseif ($Id -eq 'AUG-P-001') {'LOW_HP_BURST'}
        elseif ($Id -eq 'AUG-P-008') {'AMMO_PARADOX'} elseif ($Id -eq 'AUG-P-009') {'TRIDENT_RECALL'}
        elseif ($Id -eq 'AUG-P-010') {'UNARMED_COUNTER'} elseif ($Id -eq 'PAUG-004') {'PARTY_REVIVE'}
        elseif ($Id -eq 'PAUG-005') {'PARTY_RESOURCE'} elseif ($Id -eq 'PAUG-009') {'PARTY_AMMO_CRAFT'}
        elseif ($Id -eq 'PAUG-010') {'PARTY_AP_REGEN'} elseif ($tags.Count) {$tags[0]} else {'GENERAL'}
    [ordered]@{id=$Id;sourceDocumentId=$Source;enabled=$true;name=$Cells[1];tier=$tier;scope=$Scope
        tags=@($tags);effectOpcode=$opcode;effectText=$effectText;constraintText=$constraintText;weightingText=$weightingText
        exclusiveWith=@($exclusive);evolution=($tags -contains 'EVOLUTION');raw=@($Cells)}
}
$personalById = Find-IdRows $personalAugmentRows '^AUG-[SGP]-\d{3}$'
$personalAugments = foreach ($entry in $personalById.GetEnumerator()) { Compile-Augment $entry.Key 'AUG-LIST-001' $entry.Value 'PERSONAL' }
$partyById = Find-IdRows $partyAugmentRows '^PAUG-\d{3}$'
$partyAugments = foreach ($entry in $partyById.GetEnumerator()) { Compile-Augment $entry.Key 'AUG-LIST-002' $entry.Value 'PARTY' }

$enemyById = Find-IdRows $entityRows '^EN-(D\d+|F50)-[A-Z0-9]+$'
$enemies = foreach ($entry in $enemyById.GetEnumerator()) {
    $id = $entry.Key; $registry = $entry.Value; $detail = $enemyDetailById[$id]; $day = Enemy-Day $id
    if (-not $detail) { throw "Enemy execution data missing: $id" }
    $isD20Summon = $id -match '^EN-D20-A'; $isFinalSummon = $id -match '^EN-F50-A'
    $role = 'CHASER'; $budgetCost = 1; $hp = 300.0; $defence = 25.0; $attack = 80.0; $penetration = 0.0
    $breakMax = 0.0; $augmentSlots = 0; $appearance = ''; $primary = '기본 공격'
    if ($isFinalSummon) {
        $appearance = $detail[1]; $hp = First-Number (($detail[2] -split '/')[0]) 1800
        $attack = First-Number (($detail[2] -split '/')[1]) 120; $primary = $detail[3]; $role = 'SUMMON'; $budgetCost = 0
    } elseif ($isD20Summon) {
        $appearance = $detail[2]; $hp = First-Number (($detail[3] -split '/')[0]) 1100
        $defence = First-Number (($detail[3] -split '/')[1]) 40; $attack = Max-Number $detail[4] 80
        $primary = $detail[4]; $role = 'SUMMON'; $budgetCost = 0
    } elseif ($detail[0] -ne $id) {
        $appearance = $detail[1]; $day = [int](First-Number $detail[2] $day)
        if ($detail[3] -match '^([A-Z]+)·(\d+)$') { $role = $Matches[1]; $budgetCost = [int]$Matches[2] }
        $hp = First-Number (($detail[4] -split '/')[0]) 1000; $defence = First-Number (($detail[4] -split '/')[1]) 60
        $attack = First-Number (($detail[5] -split '/')[0]) 120; $penetration = First-Number (($detail[5] -split '/')[1]) 0
        $breakMax = First-Number $detail[6] 1000; $augmentSlots = [int](First-Number $detail[7] 0); $primary = $detail[5]
    } else {
        $appearance = $detail[2]
        if ($detail[3] -match '^([A-Z]+)·(\d+)$') { $role = $Matches[1]; $budgetCost = [int]$Matches[2] }
        $hp = First-Number (($detail[4] -split '/')[0]) 300; $defence = First-Number (($detail[4] -split '/')[1]) 25
        $attack = First-Number $detail[5] 80; $breakMax = First-Number $detail[6] 0; $day = [int](First-Number $detail[7] $day); $primary = $detail[5]
    }
    $elite = $role -eq 'ELITE' -or $id -match '-E\d+'
    $noReward = $registry[4] -eq 'LOOT-NONE'
    $range = if ($role -in @('RANGED','ARTILLERY','SUPPORT','CONTROLLER')) {16.0} elseif ($role -eq 'SIEGE') {5.0} else {3.2}
    $flags = if ($noReward) { @('NO_REWARD','NO_SAMPLE','NO_AUGMENT_TRIGGER') } elseif ($elite) { @('ELITE','SAMPLE_ELIGIBLE') } else { @('COMBAT_REWARD') }
    [ordered]@{
        id=$id;sourceDocumentId=$registry[2];enabled=$true;name=(Enemy-Name $id $detail);kind=$registry[1]
        bukkitType=(Enemy-BukkitType $appearance);displayFallback=$appearance;firstDay=$day;dayProfile=(Enemy-DayProfile $day)
        role=$role;budgetCost=$budgetCost;baseHp=$hp;defence=$defence;attackDamage=$attack;penetration=$penetration
        breakMax=$breakMax;augmentSlots=$augmentSlots;elite=$elite;actionBundleId=$registry[3];primaryActionName=$primary
        telegraphTicks=(Enemy-TelegraphTicks $role);cooldownTicks=(Enemy-CooldownTicks $role);attackRange=$range;statusId=(Enemy-Status $id)
        spawnPolicy=$(if ($noReward) {'PARENT_OWNED'} else {'ENCOUNTER_BUDGET'});parentId=$(if ($isD20Summon) {'BOSS-D20'} elseif ($isFinalSummon) {'FINAL-D50'} else {''})
        cleanupPolicy=$registry[5];rewardOwner=$(if ($noReward) {'PARENT'} else {'CONTRIBUTORS'});lootTableId=$registry[4]
        activityExp=$(if ($noReward) {0} elseif ($elite) {90} elseif ($budgetCost -le 1) {8} elseif ($budgetCost -le 2) {12} else {20})
        flags=[string[]]$flags;raw=@($detail)
    }
}
$bossById = Find-IdRows $entityRows '^BOSS-D(10|20|30|40)$'
$bossStats = @{
    'BOSS-D10'=@{HP=105000;DEF=70;BREAK=10000;ATK=180;RANGE=5.0;TELEGRAPH=26;COOLDOWN=90}
    'BOSS-D20'=@{HP=175000;DEF=105;BREAK=14000;ATK=200;RANGE=6.0;TELEGRAPH=28;COOLDOWN=100}
    'BOSS-D30'=@{HP=360000;DEF=150;BREAK=20000;ATK=260;RANGE=8.0;TELEGRAPH=30;COOLDOWN=110}
    'BOSS-D40'=@{HP=650000;DEF=190;BREAK=30000;ATK=340;RANGE=9.0;TELEGRAPH=32;COOLDOWN=120}
}
$bosses = foreach ($entry in $bossById.GetEnumerator()) {
    $id = $entry.Key; $cells = $entry.Value; $stats = $bossStats[$id]; $day = [int]($id -replace '^BOSS-D','')
    [ordered]@{
        id=$id;sourceDocumentId=('BOSS-D{0:D2}-001' -f $day);enabled=$true;name=$cells[1];kind='BOSS'
        bukkitType=(Enemy-BukkitType $cells[2]);displayFallback=$cells[2];firstDay=$day;dayProfile='BOSS_DAY'
        role='BOSS';budgetCost=0;baseHp=$stats.HP;defence=$stats.DEF;attackDamage=$stats.ATK;penetration=0
        breakMax=$stats.BREAK;augmentSlots=0;elite=$true;actionBundleId=$cells[3];entitySetId=$cells[4]
        telegraphTicks=$stats.TELEGRAPH;cooldownTicks=$stats.COOLDOWN;attackRange=$stats.RANGE;statusId=''
        spawnPolicy='BOSS_CALL_TRANSACTION';cleanupPolicy='BOSS_END';rewardOwner='CONTRIBUTORS';lootTableId=$cells[5]
        activityExp=(1000 + $day * 50);flags=@('BOSS','NO_DYNAMIC_AUGMENT');raw=@($cells)
    }
}
$supportById = Find-IdRows $entityRows '^ENT-[A-Z0-9-]+$'
$supportEntities = foreach ($entry in $supportById.GetEnumerator()) {
    $cells = $entry.Value; $fallback = $cells[2]
    $type = @('ARROW','SNOWBALL','TRIDENT','FIREWORK_ROCKET','BLOCK_DISPLAY','TEXT_DISPLAY','INTERACTION','ITEM_DISPLAY','ZOMBIE','SKELETON','SLIME','AREA_EFFECT_CLOUD','SHULKER_BULLET','IRON_GOLEM') |
        Where-Object { $fallback -match [regex]::Escape(($_ -replace '_ROCKET','')) } | Select-Object -First 1
    if (-not $type) { $type = 'INTERACTION' }
    [ordered]@{
        id=$entry.Key;sourceDocumentId='ENTITY-LIST-001';enabled=$true;kind=$cells[1];bukkitType=$type
        displayFallback=$fallback;cleanupPolicy=$cells[3];lootTableId=$cells[4];ownerPolicy=$(if ($entry.Key -like 'ENT-PROJ-PLAYER-*') {'PLAYER'} elseif ($entry.Key -like 'ENT-B*' -or $entry.Key -like 'ENT-F50-*') {'PARENT'} else {'SYSTEM'})
        persistent=($entry.Key -eq 'ENT-NODE-RECONSTRUCTION');raw=@($cells)
    }
}
$actions = foreach ($enemy in $enemies) {
    [ordered]@{
        id=$enemy.actionBundleId;sourceDocumentId=$enemy.sourceDocumentId;enabled=$true;ownerId=$enemy.id;kind='ENEMY_ACTION_BUNDLE'
        actions=@([ordered]@{id=($enemy.actionBundleId + '-PRIMARY');name=$enemy.primaryActionName;telegraphTicks=$enemy.telegraphTicks;startupTicks=4
            activeTicks=4;recoveryTicks=10;cooldownTicks=$enemy.cooldownTicks;range=$enemy.attackRange;damage=$enemy.attackDamage
            penetration=$enemy.penetration;breakDamage=[Math]::Max(0,[Math]::Round($enemy.breakMax * 0.08));statusId=$enemy.statusId})
        stateMachine=@('READY','TELEGRAPH','STARTUP','ACTIVE','RECOVERY');raw=@($enemy.raw)
    }
}
foreach ($boss in $bosses) {
    $actions += [ordered]@{
        id=$boss.actionBundleId;sourceDocumentId=$boss.sourceDocumentId;enabled=$true;ownerId=$boss.id;kind='BOSS_ACTION_SET'
        actions=@([ordered]@{id=($boss.actionBundleId + '-PRIMARY');name='보스 기본 패턴';telegraphTicks=$boss.telegraphTicks;startupTicks=6
            activeTicks=6;recoveryTicks=16;cooldownTicks=$boss.cooldownTicks;range=$boss.attackRange;damage=$boss.attackDamage
            penetration=$boss.penetration;breakDamage=[Math]::Round($boss.breakMax * 0.05);statusId=''})
        stateMachine=@('READY','TELEGRAPH','STARTUP','ACTIVE','RECOVERY');raw=@($boss.raw)
    }
}

$facilityById = Find-IdRows $facilityRows '^FAC-[PCSDR]\d{2}$'
$facilityDataById = Find-IdRows $facilityDataRows 'FAC-[SD]\d{2}'
$facilityExecutionById = [ordered]@{}
foreach ($cells in $facilityRows) {
    if ($cells.Count -ge 5 -and $cells[0] -match '^FAC-[PCSDR]\d{2}$' -and
            ($cells[2] -match '^WSI-' -or $cells[2] -eq 'VIRTUAL_BUILD')) {
        $facilityExecutionById[$cells[0]] = $cells
    }
}
$itemById = [ordered]@{}
foreach ($item in $items) { $itemById[$item.id] = $item }
$facilities = foreach ($entry in $facilityById.GetEnumerator()) {
    $id = $entry.Key; $cells = $entry.Value; $tier = Facility-Tier $id
    $execution = $facilityExecutionById[$id]
    if (-not $execution) { throw "Facility execution mapping missing: $id" }
    $itemId = if ($execution[2] -eq 'VIRTUAL_BUILD') { '' } else { $execution[2] }
    $recipeId = $execution[3]
    $stateMachine = $execution[4]
    $item = if ($itemId -and $itemById.Contains($itemId)) { $itemById[$itemId] } else { $null }
    $firstDay = if ($item) { [int]$item.firstDay } elseif ($id -eq 'FAC-R06') { 49 } else { 41 }
    $threatCell = if ($tier -eq 'CAMP') { $cells[4] } elseif ($tier -in @('SETTLEMENT','DEFENSE','RECONSTRUCTION')) { $cells[5] } else { '0' }
    $threatMatch = [regex]::Match($threatCell, '\d+')
    $threat = if ($threatMatch.Success) { [int]$threatMatch.Value } else { 0 }
    $workSlots = if ($tier -eq 'SETTLEMENT' -and $cells[4] -match '^\d+$') { [int]$cells[4] }
        elseif ($id -eq 'FAC-C01') { 1 } elseif ($id -eq 'FAC-R01') { 2 }
        elseif ($tier -eq 'RECONSTRUCTION') { 1 } else { 0 }
    $profile = if ($facilityDataById.Contains($id)) { $facilityDataById[$id] } else { $null }
    $costProfile = if ($tier -eq 'SETTLEMENT') { 'FP-' + $profile[1] }
        elseif ($tier -eq 'DEFENSE') { 'FP-DEFENSE' }
        elseif ($tier -eq 'RECONSTRUCTION') { 'RECONSTRUCTION' }
        elseif ($tier -eq 'CAMP') { 'CAMP' } else { 'PORTABLE' }
    $unlockText = if ($profile) { $profile[2] } elseif ($tier -eq 'RECONSTRUCTION') { $cells[4] } else { 'BASIC' }
    $maintenanceText = if ($profile) { $profile[4] } else { $cells[4] }
    $fallback = if ($profile -and $profile.Count -gt 5) { $profile[5] }
        elseif ($tier -eq 'CAMP' -and $cells.Count -gt 5) { $cells[5] } else { '' }
    $coreMaterial = Facility-CoreMaterial $id $(if ($item) {$item.displayMaterial} else {''})
    [ordered]@{
        id=$id;sourceDocumentId='FACILITY-LIST-001';enabled=$true;name=$cells[1];facilityTier=$tier
        representation=$cells[2];coreMaterial=$coreMaterial;networkPolicy=$(if ($tier -in @('PORTABLE','CAMP')) {'INDEPENDENT'} elseif ($tier -eq 'RECONSTRUCTION') {'CONNECTED_96'} else {'CONNECTED_24'})
        itemId=$itemId;recipeId=$recipeId;firstDay=$firstDay;activationDay=$(if ($id -eq 'FAC-R06') {50} else {$firstDay});maxLevel=(Facility-MaxLevel $id)
        baseHp=(Facility-BaseHp $id $tier $threat);hpAuthority=$(if ($tier -eq 'RECONSTRUCTION') {'DOCUMENT_LOCK'} else {'IMPLEMENTATION_BASELINE'})
        workSlots=$workSlots;threatValue=$threat;costProfile=$costProfile;unlockText=$unlockText
        effectOpcode=(Facility-Opcode $id);effectText=$cells[3];maintenanceText=$maintenanceText
        portableFallback=$fallback;stateMachine=$stateMachine;raw=@($cells)
    }
}
$lootById = Find-IdRows $lootRows '^LOOT-[A-Z0-9-]+$'
$lootById.Remove('LOOT-LIST-001')
$lootById.Remove('loot-s1-r1')
if (-not $lootById.Contains('LOOT-NONE')) { $lootById['LOOT-NONE'] = @('LOOT-NONE','EMPTY') }
$combatPools = @{
    'COMBAT-D10'=@{Guaranteed=@('WSR-WOOD','WSR-STONE','WSR-FIBER','WSR-COAL');GMin=1;GMax=3;Special=@('WSR-TISSUE');SMin=0;SMax=1;Pity=20;Equipment=@{COMMON=0.02;UNCOMMON=0.005}}
    'COMBAT-D20'=@{Guaranteed=@('WSR-IRON','WSR-COPPER','WSR-HERB','WSR-ELASTIC_FIBER');GMin=2;GMax=4;Special=@('WSR-TOXIN_SAMPLE','WSR-THERMAL_SAMPLE','WSR-HEMATIC_SAMPLE','WSR-NEURAL_SAMPLE');SMin=1;SMax=2;Pity=16;Equipment=@{UNCOMMON=0.04;RARE=0.015}}
    'COMBAT-D30'=@{Guaranteed=@('WSR-REINFORCED_ALLOY','WSR-NEURAL_CIRCUIT');GMin=1;GMax=3;Special=@('WSR-PURIFY_CATALYST','WSR-RIFT_POWDER');SMin=0;SMax=2;Pity=18;Equipment=@{RARE=0.04;EPIC=0.012}}
    'COMBAT-D40'=@{Guaranteed=@('WSR-HARD_AGGREGATE','WSR-RESONANCE_COIL');GMin=1;GMax=3;Special=@('WSR-PATTERN_RESIDUE');SMin=0;SMax=1;Pity=20;Equipment=@{EPIC=0.03;LEGENDARY=0.008}}
    'COMBAT-D50'=@{Guaranteed=@('WSR-HIGH_DENSITY_ALLOY');GMin=1;GMax=2;Special=@('WSR-STABLE_CORE','WSR-CALIBRATED_LENS','WSR-POWER_MATRIX','WSR-PURIFY_MATRIX','WSR-STATUS_PLATE','WSR-INTERRUPT_CORE');SMin=1;SMax=1;Pity=0;Equipment=@{LEGENDARY=0.02;ABYSSAL=0.0}}
    'ELITE'=@{Guaranteed=@();GMin=2;GMax=2;Special=@();SMin=2;SMax=4;Pity=6;Equipment=@{CURRENT_CAP=0.08;BLUEPRINT=0.12}}
}
$nodePools = @{
    'LOOT-NODE-BASIC'=@('WSR-WOOD','WSR-STONE','WSR-FIBER','WSR-LEATHER','WSR-RATION','WSR-COAL')
    'LOOT-NODE-INDUSTRIAL'=@('WSR-IRON','WSR-COPPER','WSR-GOLD','WSR-REDSTONE','WSR-AMETHYST')
    'LOOT-NODE-MEDICAL'=@('WSR-HERB','WSR-ELASTIC_FIBER','WSR-TOXIN_SAMPLE','WSR-THERMAL_SAMPLE','WSR-HEMATIC_SAMPLE','WSR-NEURAL_SAMPLE')
    'LOOT-NODE-CORRUPTED'=@('WSR-TISSUE','WSR-MUTATION_SHARD','WSR-VITAL_TISSUE')
    'LOOT-NODE-RIFT'=@('WSR-PURIFY_CATALYST','WSR-RIFT_POWDER','WSR-REFINED_MUTATION')
    'LOOT-NODE-RECONSTRUCTION'=@('WSR-HARD_AGGREGATE','WSR-RESONANCE_COIL','WSR-PATTERN_RESIDUE','WSR-HIGH_DENSITY_ALLOY')
}
$bossFixed = @{
    'LOOT-BOSS-D10'=@(
        [ordered]@{itemId='WSP-BOSS-D10-CORE';amounts=@(1,1,1);scope='RUN_PROOF'},
        [ordered]@{itemId='WSP-REBUILD-PART-A';amounts=@(1,1,1);scope='RUN_PROOF'},
        [ordered]@{itemId='WSR-RESONANT_RESIDUE';amounts=@(6,7,8);scope='PARTY_DISTRIBUTED'})
    'LOOT-BOSS-D20'=@(
        [ordered]@{itemId='WSP-REBUILD-PART-B';amounts=@(1,1,1);scope='RUN_PROOF'},
        [ordered]@{itemId='WSR-NEURAL_RESIDUE';amounts=@(8,10,12);scope='PARTY_DISTRIBUTED'})
    'LOOT-BOSS-D30'=@(
        [ordered]@{itemId='WSP-REBUILD-PART-C';amounts=@(1,1,1);scope='RUN_PROOF'},
        [ordered]@{itemId='WSR-REFINED_MUTATION';amounts=@(8,10,12);scope='PARTY_DISTRIBUTED'},
        [ordered]@{itemId='WSR-PURIFY_MEDIUM';amounts=@(6,8,10);scope='PARTY_DISTRIBUTED'})
    'LOOT-BOSS-D40'=@(
        [ordered]@{itemId='WSP-REBUILD-PART-D';amounts=@(1,1,1);scope='RUN_PROOF'},
        [ordered]@{itemId='WSR-PATTERN_RESIDUE';amounts=@(10,13,16);scope='PARTY_DISTRIBUTED'},
        [ordered]@{itemId='WSR-HIGH_DENSITY_ALLOY';amounts=@(7,9,11);scope='PARTY_DISTRIBUTED'})
}
$bossEquipment = @{'LOOT-BOSS-D10'='EQL-D10';'LOOT-BOSS-D20'='EQD20-LG';'LOOT-BOSS-D30'='EQD50-B30';'LOOT-BOSS-D40'='EQD50-B40'}
$loot = foreach ($entry in $lootById.GetEnumerator()) {
    $id = $entry.Key; $cells = $entry.Value
    $source = $(if ($cells.Count -gt 1) {$cells[1]} else {'NONE'}); $profile='EMPTY'; $distribution='NONE'; $opcode='EMPTY'
    $guaranteed=@(); $gMin=0; $gMax=0; $special=@(); $sMin=0; $sMax=0; $equipmentChances=[ordered]@{}; $pity=0
    $fixed=@(); $equipmentSelection=''; $partyMilestone=0; $toolMin=0; $toolMax=0; $flags=@()
    if ($id -like 'LOOT-EN-*') {
        $profile=$cells[2]; $distribution=$cells[3]; $opcode='COMBAT_ROLL'; $spec=$combatPools[$profile]
        if (-not $spec) { throw "Unknown combat loot profile: $id -> $profile" }
        $guaranteed=@($spec.Guaranteed);$gMin=$spec.GMin;$gMax=$spec.GMax;$special=@($spec.Special);$sMin=$spec.SMin;$sMax=$spec.SMax;$pity=$spec.Pity
        foreach($key in @($spec.Equipment.Keys|Sort-Object)){$equipmentChances[$key]=[double]$spec.Equipment[$key]}
        if ($profile -eq 'ELITE') {$flags=@('CURRENT_DAY_RESOURCE_POOL','SAMPLE_ELIGIBLE','BLUEPRINT_ELIGIBLE')}
    } elseif ($id -like 'LOOT-BOSS-*') {
        $profile='BOSS';$distribution='CONTRIBUTOR_ROUND_ROBIN';$opcode='BOSS_FIXED_AND_CHOICE';$fixed=@($bossFixed[$id]);$equipmentSelection=$bossEquipment[$id]
        $partyMilestone=@{'LOOT-BOSS-D10'=1;'LOOT-BOSS-D20'=2;'LOOT-BOSS-D30'=3;'LOOT-BOSS-D40'=4}[$id];$flags=@('COMPLETION_ONCE','INBOX_PERSISTENT')
    } elseif ($id -like 'LOOT-NODE-*') {
        $profile='RESOURCE_NODE';$distribution=$(if ($id -in @('LOOT-NODE-RIFT','LOOT-NODE-RECONSTRUCTION')) {'CONTRIBUTOR_ROUND_ROBIN'} else {'HARVESTER_PERSONAL'});$opcode='NODE_HARVEST'
        $guaranteed=@($nodePools[$id]);$gMin=1;$gMax=3
        switch ($id) {
            'LOOT-NODE-BASIC' {$toolMin=0;$toolMax=1};'LOOT-NODE-INDUSTRIAL' {$toolMin=1;$toolMax=2};'LOOT-NODE-MEDICAL' {$toolMin=3;$toolMax=3}
            'LOOT-NODE-CORRUPTED' {$toolMin=2;$toolMax=4};'LOOT-NODE-RIFT' {$toolMin=4;$toolMax=5};'LOOT-NODE-RECONSTRUCTION' {$toolMin=5;$toolMax=6}
        }
        $flags=@('PRESERVE_ON_TOOL_FAILURE','NO_VANILLA_DOUBLE_DROP')
    } elseif ($id -eq 'LOOT-NONE') {
        $flags=@('NO_REWARD','NO_ROLL','NO_CLAIM')
    } else {
        $profile='EVENT';$distribution=$(if ($id -eq 'LOOT-DISCOVERY') {'OWNER'} else {'CONTRIBUTOR_ROUND_ROBIN'})
        $opcode=@{'LOOT-ENCOUNTER-COMMON'='CURRENT_DAY_COMBAT';'LOOT-ENCOUNTER-ELITE'='ELITE_ONCE';'LOOT-ENCOUNTER-ASSAULT'='ASSAULT_SUCCESS';'LOOT-DISCOVERY'='UNLOCK_ONLY';'LOOT-SALVAGE-EQUIPMENT'='SALVAGE_FORMULA';'LOOT-RECOVERY'='RETURN_ORIGINAL_TRANSACTION'}[$id]
        $flags=@('NO_FLOOR_DROP','INBOX_PERSISTENT')
    }
    [ordered]@{
        id=$id;sourceDocumentId='LOOT-LIST-001';enabled=$true;sourceId=$source;profile=$profile;distribution=$distribution;executionOpcode=$opcode
        guaranteedPool=[string[]]$guaranteed;guaranteedMin=$gMin;guaranteedMax=$gMax;specialtyPool=[string[]]$special;specialtyMin=$sMin;specialtyMax=$sMax
        equipmentChances=$equipmentChances;pityLimit=$pity;fixedEntries=@($fixed);equipmentSelectionProfile=$equipmentSelection;partyAugmentMilestone=$partyMilestone
        requiredToolTierMin=$toolMin;requiredToolTierMax=$toolMax;noReward=($id -eq 'LOOT-NONE');flags=[string[]]$flags;raw=@($cells)
    }
}

$codex = @($materials | ForEach-Object { [ordered]@{ id=$_.id; codexIndex=$_.codexIndex; domain='MATERIAL'; displayMaterial=$_.displayMaterial } })
$codex += @($items | ForEach-Object { [ordered]@{ id=$_.id; codexIndex=$_.codexIndex; domain='ITEM'; displayMaterial=$_.displayMaterial } })
$codex += @($tools | ForEach-Object { [ordered]@{ id=$_.id; codexIndex=$_.codexIndex; domain='EQUIPMENT'; displayMaterial=$_.displayMaterial } })

$expected = [ordered]@{ materials=59; items=61; tools=214; recipes=315; codex=334; skills=64; personalAugments=50; partyAugments=16; enemies=53; bosses=4; support=34; facilities=46; loot=62 }
$actual = [ordered]@{ materials=@($materials).Count; items=@($items).Count; tools=@($tools).Count; recipes=@($recipes).Count; codex=@($codex).Count; skills=@($skills).Count; personalAugments=@($personalAugments).Count; partyAugments=@($partyAugments).Count; enemies=@($enemies).Count; bosses=@($bosses).Count; support=@($supportEntities).Count; facilities=@($facilities).Count; loot=@($loot).Count }
foreach ($key in $expected.Keys) {
    if ($actual[$key] -ne $expected[$key]) { throw "Cardinality mismatch $key expected=$($expected[$key]) actual=$($actual[$key])" }
}
if (@($codex.codexIndex | Group-Object | Where-Object Count -ne 1).Count -ne 0) { throw 'Duplicate codex index' }

$days = 1..50 | ForEach-Object { [ordered]@{ id=('DAY-{0:D2}' -f $_); sourceDocumentId='CONTENT-DATA-D50-001'; enabled=$true; day=$_; finalAvailable=($_ -ge 50) } }
$endless = @([ordered]@{ id='DAY-51-PLUS'; sourceDocumentId='CONTENT-DATA-D50-001'; enabled=$true; firstDay=51; repeatPolicy='ENDLESS' })
$enemyD10 = @($enemies | Where-Object { (Enemy-Day $_.id) -le 10 })
$enemyD20 = @($enemies | Where-Object { (Enemy-Day $_.id) -ge 11 -and (Enemy-Day $_.id) -le 20 })
$enemyD50 = @($enemies | Where-Object { (Enemy-Day $_.id) -ge 21 })
$equipmentEarly = @($tools | Where-Object { $_.id -like 'EQL-*' })
$equipmentD20 = @($tools | Where-Object { $_.id -like 'EQD20-*' })
$equipmentD50 = @($tools | Where-Object { $_.id -like 'EQD50-*' })

$schemaNames = @('manifest','common','day','event','enemy','resource','item','recipe','equipment','facility','research','augment','skill','action','entity','loot','codex','migration','boss','final','story','budget','ops')
$genericSchema = [ordered]@{
    '$schema'='https://json-schema.org/draft/2020-12/schema'; type='object'; additionalProperties=$false
    required=@('schemaVersion','contentRevision','domain','records')
    properties=[ordered]@{
        schemaVersion=[ordered]@{ const=2 }; contentRevision=[ordered]@{ const='ws-content-r2' }
        domain=[ordered]@{ type='string'; minLength=1 }; records=[ordered]@{ type='array'; items=[ordered]@{ type='object'; required=@('id'); properties=[ordered]@{ id=[ordered]@{type='string';minLength=1} } } }
    }
}
$manifestSchema = [ordered]@{
    '$schema'='https://json-schema.org/draft/2020-12/schema'; type='object'; additionalProperties=$false
    required=@('schemaVersion','contentRevision','activationPolicy','storyRevision','budgetPolicyRevision','files')
    properties=[ordered]@{
        schemaVersion=[ordered]@{const=2}; contentRevision=[ordered]@{const='ws-content-r2'}; activationPolicy=[ordered]@{const='NEW_RUN_ONLY'}
        storyRevision=[ordered]@{const='ws-story-s1-r1'}; budgetPolicyRevision=[ordered]@{const='budget-live-r2'}
        files=[ordered]@{type='array';minItems=64;maxItems=64;items=[ordered]@{type='object'}}
    }
}
$recipeSchema = [ordered]@{
    '$schema'='https://json-schema.org/draft/2020-12/schema'; type='object'; additionalProperties=$false
    required=@('schemaVersion','contentRevision','domain','records')
    properties=[ordered]@{
        schemaVersion=[ordered]@{const=2}; contentRevision=[ordered]@{const='ws-content-r2'}; domain=[ordered]@{const='recipes'}
        records=[ordered]@{type='array';minItems=315;maxItems=315;items=[ordered]@{
            type='object';additionalProperties=$false
            required=@('id','sourceDocumentId','enabled','outputId','outputAmount','recipeType','inputAuthority','layout','ingredients','raw')
            properties=[ordered]@{
                id=[ordered]@{type='string';pattern='^WSRCP-'};sourceDocumentId=[ordered]@{type='string';minLength=1};enabled=[ordered]@{const=$true}
                outputId=[ordered]@{type='string';minLength=1};outputAmount=[ordered]@{type='integer';minimum=1;maximum=64}
                recipeType=[ordered]@{enum=@('CRAFT','PROCESS','BOSS_CALL','FACILITY_KIT','EQUIPMENT_FORGE','UTILITY_FORGE','VIRTUAL_BUILD')}
                inputAuthority=[ordered]@{type='string';minLength=1};layout=[ordered]@{enum=@('ORDERED_3X3','CALL_FRAME','EQUIPMENT_FRAME','FACILITY_FRAME','REBUILD_FRAME')}
                ingredients=[ordered]@{type='array';minItems=1;maxItems=9;items=[ordered]@{
                    type='object';additionalProperties=$false;required=@('slot','kind','key','amount','consume')
                    properties=[ordered]@{slot=[ordered]@{type='integer';minimum=0;maximum=8};kind=[ordered]@{enum=@('ITEM','TAG','VANILLA','PROOF')};key=[ordered]@{type='string';minLength=1};amount=[ordered]@{type='integer';minimum=1;maximum=64};consume=[ordered]@{type='boolean'}}
                }}
                raw=[ordered]@{type='array';items=[ordered]@{type='string'}}
            }
        }}
    }
}
foreach ($name in $schemaNames) {
    $schema = if ($name -eq 'manifest') { $manifestSchema } elseif ($name -eq 'recipe') { $recipeSchema } else { $genericSchema }
    Write-Json "schemas/$name.schema.json" $schema
}

$eventStub = { param($id,$source) Raw-Record $id $source @('AUTHORITY_DATA') }
$data = [ordered]@{}
$data['days/season1-days-01-50.json'] = Domain 'days' $days
$data['days/endless-days-51-plus.json'] = Domain 'days-endless' $endless
$data['events/day01-10.json'] = Domain 'events' @(&$eventStub 'EVENTS-D01-10' 'EVENT-DATA-001')
$data['events/day11-20.json'] = Domain 'events' @(&$eventStub 'EVENTS-D11-20' 'EVENT-DATA-D20-001')
$data['events/day21-50.json'] = Domain 'events' @(&$eventStub 'EVENTS-D21-50' 'EVENT-DATA-D50-001')
$data['enemies/day01-10.json'] = Domain 'enemies' $enemyD10
$data['enemies/day11-20.json'] = Domain 'enemies' $enemyD20
$data['enemies/day21-50.json'] = Domain 'enemies' $enemyD50
$data['resources/day01-10.json'] = Domain 'resources' @($materials | Where-Object firstDay -le 10)
$data['resources/day11-20.json'] = Domain 'resources' @($materials | Where-Object { $_.firstDay -ge 11 -and $_.firstDay -le 20 })
$data['resources/day21-50.json'] = Domain 'resources' @($materials | Where-Object firstDay -ge 21)
$data['items/materials.json'] = Domain 'materials' $materials
$data['items/non-equipment-items.json'] = Domain 'items' $items
$data['items/codex-index.json'] = Domain 'codex' $codex
$data['recipes/season1-recipes.json'] = Domain 'recipes' $recipes
$data['equipment/day01-10.json'] = Domain 'equipment' $equipmentEarly
$data['equipment/day11-20.json'] = Domain 'equipment' $equipmentD20
$data['equipment/day21-50.json'] = Domain 'equipment' $equipmentD50
$data['facilities/season1-facilities.json'] = Domain 'facilities' $facilities
$data['research/season1-research.json'] = Domain 'research' @(&$eventStub 'RESEARCH-S1' 'RESEARCH-001')
$data['augments/personal-augments.json'] = Domain 'personal-augments' $personalAugments
$data['augments/party-augments.json'] = Domain 'party-augments' $partyAugments
$data['skills/player-skills.json'] = Domain 'player-skills' $skills
$data['skills/entity-actions.json'] = Domain 'entity-actions' $actions
$data['entities/support-entities.json'] = Domain 'support-entities' $supportEntities
$data['loot/season1-loot.json'] = Domain 'loot' $loot
for ($index = 0; $index -lt 4; $index++) { $day = @(10,20,30,40)[$index]; $data["bosses/day$day.json"] = Domain 'bosses' @($bosses[$index]) }
$data['final/day50-reconstruction-signal.json'] = Domain 'final' @(&$eventStub 'FINAL-D50-RECONSTRUCTION-SIGNAL' 'FINAL-DATA-001')
$data['story/season1-scenes.json'] = Domain 'story-scenes' @(&$eventStub 'STORY-S1-SCENES' 'STORY-DATA-S1-001')
$data['story/season1-logs.json'] = Domain 'story-logs' @(&$eventStub 'STORY-S1-LOGS' 'STORY-DATA-S1-001')
$data['budget/live-profiles.json'] = Domain 'budget' @(&$eventStub 'BUDGET-LIVE-R2' 'BUDGET-PROFILE-001')
$data['migrations/id-aliases.json'] = Domain 'migrations' @(
    [ordered]@{id='EQL-W10';sourceDocumentId='TOOL-LIST-001';targetId='UNARMED_COMBAT';enabled=$true},
    [ordered]@{id='COMMON_RESOURCE_DEPOT';sourceDocumentId='ITEM-LIST-001';targetId='WSI-FAC-S16-KIT';enabled=$true})
$data['fixtures/cardinality.json'] = Domain 'fixture-cardinality' @($actual.GetEnumerator() | ForEach-Object { [ordered]@{id=$_.Key;expected=$_.Value} })
$data['fixtures/reference-graph.json'] = Domain 'fixture-reference-graph' @(&$eventStub 'REFERENCE-GRAPH-S1' 'CONTENT-GRAPH-AUDIT-001')
$data['fixtures/draw-locks.json'] = Domain 'fixture-draw-locks' @(&$eventStub 'DRAW-LOCKS-S1' 'AUG-LIST-001')
$data['fixtures/softlock-scenarios.json'] = Domain 'fixture-softlocks' @(&$eventStub 'SOFTLOCK-SCENARIOS-S1' 'CONTENT-GRAPH-AUDIT-001')
$data['ops/admin-commands.json'] = Domain 'ops-admin' @(&$eventStub 'OPS-ADMIN-COMMANDS' 'OPS-001')
$data['ops/telemetry-contract.json'] = Domain 'ops-telemetry' @(&$eventStub 'OPS-TELEMETRY' 'QA-BALANCE-001')
if ($data.Count -ne 41) { throw "Expected 41 data files, got $($data.Count)" }
foreach ($entry in $data.GetEnumerator()) { Write-Json $entry.Key $entry.Value }

function Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try { return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant() }
    finally { $stream.Dispose() }
}
$schemaForDomain = [ordered]@{
    days='day'; 'days-endless'='day'; events='event'; enemies='enemy'; resources='resource'; materials='resource'; items='item'; codex='codex'; recipes='recipe'
    equipment='equipment'; facilities='facility'; research='research'; 'personal-augments'='augment'; 'party-augments'='augment'; 'player-skills'='skill'; 'entity-actions'='action'
    'support-entities'='entity'; loot='loot'; bosses='boss'; final='final'; 'story-scenes'='story'; 'story-logs'='story'; budget='budget'; migrations='migration'
    'fixture-cardinality'='common'; 'fixture-reference-graph'='common'; 'fixture-draw-locks'='common'; 'fixture-softlocks'='common'; 'ops-admin'='ops'; 'ops-telemetry'='ops'
}
$files = [Collections.Generic.List[object]]::new()
foreach ($name in $schemaNames) {
    $relative = "schemas/$name.schema.json"
    $files.Add([ordered]@{path=$relative;domain='schema';schema='common.schema.json';sha256=Sha256 (Join-Path $OutputRoot $relative)})
}
foreach ($entry in $data.GetEnumerator()) {
    $relative = $entry.Key
    $domainName = $entry.Value.domain
    $files.Add([ordered]@{path=$relative;domain=$domainName;schema=($schemaForDomain[$domainName] + '.schema.json');sha256=Sha256 (Join-Path $OutputRoot $relative)})
}
if ($files.Count -ne 64) { throw "Expected 64 manifest entries, got $($files.Count)" }
$manifest = [ordered]@{
    schemaVersion=2;contentRevision='ws-content-r2';activationPolicy='NEW_RUN_ONLY';storyRevision='ws-story-s1-r1'
    budgetPolicyRevision='budget-live-r2';drawRevision='draw-s1-r2';rulesRevision='rules-s1-r2';resourcePackContract='ws-rp-s1-r1';files=@($files)
}
Write-Json 'manifest.json' $manifest
$manifestHash = Sha256 (Join-Path $OutputRoot 'manifest.json')
$lock = @"
schema-version: 2
content-revision: "ws-content-r2"
manifest: "manifest.json"
manifest-sha256: "$manifestHash"
activation-policy: "NEW_RUN_ONLY"
story-revision: "ws-story-s1-r1"
budget-policy-revision: "budget-live-r2"
draw-revision: "draw-s1-r2"
rules-revision: "rules-s1-r2"
resource-pack-contract: "ws-rp-s1-r1"
strict-reference-check: true
reject-unknown-fields: true
"@
Write-Utf8Lf (Join-Path $OutputRoot 'content-lock.yaml') $lock

[ordered]@{ outputRoot=$OutputRoot; totalFiles=66; manifestEntries=$files.Count; counts=$actual; manifestSha256=$manifestHash } | ConvertTo-Json -Depth 5
