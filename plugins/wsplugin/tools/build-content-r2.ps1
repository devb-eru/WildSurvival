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
        'ARMOR_LEGS','ARMOR_FEET','ACCESSORY/CHARM','UNARMED_SUPPORT')
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
$recipeRows = Read-TableRows '기획\04 장비와 경제\RECIPE-LIST Season 1 조합법 목록 기획서.md'
$skillRows = Read-TableRows '기획\02 플레이어 성장\SKILL-LIST 스킬 목록 기획서.md'
$personalAugmentRows = Read-TableRows '기획\02 플레이어 성장\AUGMENT-PERSONAL-LIST Season 1 개인 증강 목록 기획서.md'
$partyAugmentRows = Read-TableRows '기획\02 플레이어 성장\AUGMENT-PARTY-LIST Season 1 파티 증강 목록 기획서.md'
$entityRows = Read-TableRows '기획\06 사건과 적\ENTITY-LIST Season 1 엔티티 목록 기획서.md'
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
$materials = foreach ($entry in $materialCodex.GetEnumerator()) {
    $cells = $materialMetadata[$entry.Key]
    $firstDay = @($cells | Where-Object { $_ -match '^\d{1,2}$' } | ForEach-Object { [int]$_ } | Where-Object { $_ -le 50 } | Select-Object -First 1)
    $displayMaterial = First-Material $cells ''
    if ($displayMaterial -match '^T\d$' -or $displayMaterial -eq 'UNIQUE') { $displayMaterial = Fallback-Material $entry.Key }
    [ordered]@{
        id = $entry.Key; sourceDocumentId = 'MATERIAL-LIST-001'; enabled = $true
        codexIndex = $entry.Value; name = Korean-Name $cells $entry.Key
        firstDay = $(if ($firstDay.Count) { $firstDay[0] } else { 1 })
        displayMaterial = $displayMaterial; ledgerScope = $(if ($entry.Key.StartsWith('WSP-')) { 'RUN_PROOF' } else { 'PERSONAL_THEN_PUBLIC' })
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
        displayMaterial = First-Material $cells ''; raw = @($cells)
    }
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
    [ordered]@{
        id = $cells[1]; sourceDocumentId = 'TOOL-LIST-001'; enabled = $true
        codexIndex = [int]$cells[0]; name = $cells[1]; equipmentType = $cells[2]; equipmentSlot = $equipmentSlot
        displayMaterial = First-Material $cells $equipmentSlot; definition = $cells[5]; raw = @($cells)
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
$enemies = foreach ($entry in $enemyById.GetEnumerator()) { Raw-Record $entry.Key 'ENTITY-LIST-001' $entry.Value }
$bossById = Find-IdRows $entityRows '^BOSS-D(10|20|30|40)$'
$bosses = foreach ($entry in $bossById.GetEnumerator()) { Raw-Record $entry.Key 'ENTITY-LIST-001' $entry.Value }
$supportById = Find-IdRows $entityRows '^ENT-[A-Z0-9-]+$'
$supportEntities = foreach ($entry in $supportById.GetEnumerator()) { Raw-Record $entry.Key 'ENTITY-LIST-001' $entry.Value }
$actions = foreach ($enemy in $enemies) {
    $actionId = @($enemy.raw | Where-Object { $_ -match '^ACT-' } | Select-Object -First 1)
    Raw-Record $(if ($actionId.Count) { $actionId[0] } else { 'ACT-' + $enemy.id }) 'ENTITY-LIST-001' @($enemy.id)
}
foreach ($boss in $bosses) { $actions += Raw-Record ('ACTSET-' + $boss.id) 'ENTITY-LIST-001' @($boss.id) }

$facilityById = Find-IdRows $facilityRows '^FAC-[PCSDR]\d{2}$'
$facilities = foreach ($entry in $facilityById.GetEnumerator()) { Raw-Record $entry.Key 'FACILITY-LIST-001' $entry.Value }
$lootById = Find-IdRows $lootRows '^LOOT-[A-Z0-9-]+$'
$lootById.Remove('LOOT-LIST-001')
$lootById.Remove('loot-s1-r1')
if (-not $lootById.Contains('LOOT-NONE')) { $lootById['LOOT-NONE'] = @('LOOT-NONE','EMPTY') }
$loot = foreach ($entry in $lootById.GetEnumerator()) { Raw-Record $entry.Key 'LOOT-LIST-001' $entry.Value }

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
function Enemy-Day([string]$Id) {
    if ($Id -match '^EN-D(\d+)') { return [int]$Matches[1] }
    return 50
}
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
