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
        'UTILITY_FORGE','VIRTUAL_BUILD','MAIN_WEAPON','OFF_WEAPON','INVENTORY')
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
function Domain([string]$Name, [object[]]$Records) {
    return [ordered]@{ schemaVersion = 2; contentRevision = 'ws-content-r2'; domain = $Name; records = @($Records) }
}
function Raw-Record([string]$Id, [string]$Source, [object[]]$Cells) {
    return [ordered]@{ id = $Id; sourceDocumentId = $Source; enabled = $true; raw = @($Cells) }
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
    [ordered]@{
        id = $entry.Key; sourceDocumentId = 'MATERIAL-LIST-001'; enabled = $true
        codexIndex = $entry.Value; name = Korean-Name $cells $entry.Key
        firstDay = $(if ($firstDay.Count) { $firstDay[0] } else { 1 })
        displayMaterial = First-Material $cells ''; ledgerScope = $(if ($entry.Key.StartsWith('WSP-')) { 'RUN_PROOF' } else { 'PERSONAL_THEN_PUBLIC' })
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
    [ordered]@{
        id = $cells[1]; sourceDocumentId = 'TOOL-LIST-001'; enabled = $true
        codexIndex = [int]$cells[0]; name = $cells[1]; equipmentType = $cells[2]; equipmentSlot = $cells[3]
        displayMaterial = First-Material $cells $cells[3]; definition = $cells[5]; raw = @($cells)
    }
}

$recipesById = Find-IdRows $recipeRows '^WSRCP-[A-Z0-9_-]+$'
$recipes = foreach ($entry in $recipesById.GetEnumerator()) {
    $cells = $entry.Value
    $idIndex = [Array]::IndexOf($cells, $entry.Key)
    [ordered]@{
        id = $entry.Key; sourceDocumentId = 'RECIPE-LIST-001'; enabled = $true
        outputId = $(if ($idIndex + 1 -lt $cells.Count) { $cells[$idIndex + 1] } else { 'UNRESOLVED' })
        recipeType = $(if ($idIndex + 2 -lt $cells.Count) { $cells[$idIndex + 2] } else { 'UNRESOLVED' })
        inputAuthority = $(if ($idIndex + 3 -lt $cells.Count) { $cells[$idIndex + 3] } else { 'RECIPE-LIST-001' })
        layout = 'AUTHORITY_DEFINED_3X3'; raw = @($cells)
    }
}

$skillPattern = '^ws\.(basic|sword|axe|bow|crossbow|dagger|blunt|staff|pickaxe|trident|unarmed|common|context)\.[a-z0-9_.-]+$'
$skillsById = Find-IdRows $skillRows $skillPattern
$skills = foreach ($entry in $skillsById.GetEnumerator()) { Raw-Record $entry.Key 'SKILL-LIST-001' $entry.Value }
$personalById = Find-IdRows $personalAugmentRows '^AUG-[SGP]-\d{3}$'
$personalAugments = foreach ($entry in $personalById.GetEnumerator()) { Raw-Record $entry.Key 'AUG-LIST-001' $entry.Value }
$partyById = Find-IdRows $partyAugmentRows '^PAUG-\d{3}$'
$partyAugments = foreach ($entry in $partyById.GetEnumerator()) { Raw-Record $entry.Key 'AUG-LIST-002' $entry.Value }

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
foreach ($name in $schemaNames) { Write-Json "schemas/$name.schema.json" $(if ($name -eq 'manifest') { $manifestSchema } else { $genericSchema }) }

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
