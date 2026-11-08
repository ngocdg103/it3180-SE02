param([string]$WorktreePath)
$ErrorActionPreference = 'Stop'
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw | ConvertFrom-Json
$patch = Join-Path $PSScriptRoot '0001-household-br02-ui.patch'
$repoRoot = (git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Run this script inside a clone of it3180-SE02.' }
if (-not $WorktreePath) { $WorktreePath = Join-Path $repoRoot '.worktrees/dev3-br02-peer-review' }
$WorktreePath = [IO.Path]::GetFullPath($WorktreePath)
if (Test-Path -LiteralPath $WorktreePath) { throw 'Review target already exists; choose a NEW worktree path. Nothing overwritten.' }
if ((Get-FileHash -LiteralPath $patch -Algorithm SHA256).Hash -ne $manifest.patch_sha256) { throw 'Patch SHA256 mismatch.' }
foreach ($parent in @($manifest.parent_pr27, $manifest.parent_pr25)) {
    git cat-file -e "$parent`^{commit}"
    if ($LASTEXITCODE -ne 0) { throw "Dependency $parent missing. Run git fetch origin first." }
}
$treeOutput = @(git merge-tree --write-tree $manifest.parent_pr27 $manifest.parent_pr25)
if ($LASTEXITCODE -ne 0) { throw 'Dependency merge has conflicts; stop and report to Dev-2. Do not resolve.' }
$tree = $treeOutput[0].Trim()
if ($tree -ne $manifest.dependency_base_tree) { throw 'Dependency tree mismatch; stop and report.' }

# A synthetic local dependency commit, not a production commit. Fixed metadata makes its exact SHA reproducible.
$names = @('GIT_AUTHOR_NAME','GIT_AUTHOR_EMAIL','GIT_AUTHOR_DATE','GIT_COMMITTER_NAME','GIT_COMMITTER_EMAIL','GIT_COMMITTER_DATE')
$saved = @{}
foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:GIT_AUTHOR_NAME = 'BlueMoon Review Base'
    $env:GIT_AUTHOR_EMAIL = 'review-base@bluemoon.invalid'
    $env:GIT_COMMITTER_NAME = $env:GIT_AUTHOR_NAME
    $env:GIT_COMMITTER_EMAIL = $env:GIT_AUTHOR_EMAIL
    $env:GIT_AUTHOR_DATE = '2026-11-08T00:00:00+00:00'
    $env:GIT_COMMITTER_DATE = $env:GIT_AUTHOR_DATE
    $base = git commit-tree $tree -p $manifest.parent_pr27 -p $manifest.parent_pr25 -m 'Review dependency base for BR-02 UI (PR #27 + PR #25)'
    if ($LASTEXITCODE -ne 0) { throw 'Could not create local review base.' }
    $base = $base.Trim()
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}
if ($base -ne $manifest.dependency_base_commit) { throw 'Dependency commit SHA mismatch.' }
git worktree add --detach $WorktreePath $base
if ($LASTEXITCODE -ne 0) { throw 'Could not create fresh review worktree.' }
git -C $WorktreePath apply --check $patch
if ($LASTEXITCODE -ne 0) { throw 'Patch check failed; stop and report to Dev-2. Do not resolve.' }
git -C $WorktreePath apply --index $patch
if ($LASTEXITCODE -ne 0) { throw 'Patch application failed.' }
git -C $WorktreePath diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged whitespace check failed.' }
$staged = @(git -C $WorktreePath diff --cached --name-only)
if ($staged.Count -ne $manifest.files.Count) { throw 'Unexpected staged file count.' }
foreach ($file in $manifest.files) {
    $blob = git -C $WorktreePath rev-parse (':' + $file.path)
    if ($LASTEXITCODE -ne 0 -or $blob.Trim() -ne $file.target_blob_sha) { throw "Target blob mismatch: $($file.path)" }
}
git -C $WorktreePath diff --cached --stat
Write-Output "BASE=$base"
Write-Output "TREE=$tree"
Write-Output "WORKTREE=$WorktreePath"
Write-Output 'PASS: apply --check, staged diff check, and all 9 blobs match the latest reviewed BR-02 source.'
Write-Output 'Review only. No automatic commit, push, or main merge was performed.'
