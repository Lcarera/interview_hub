# Supabase Migration Deploy Step Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Automatically run new Supabase SQL migrations before backend deployment when PRs are merged to `prod`.

**Architecture:** Add a `migrations` path filter to the existing `dorny/paths-filter` change detection, then add a migration step in the `deploy` job that uses `psql` to execute new migration files against the Supabase database before Pulumi deploys the backend. This ensures `hibernate.ddl-auto: validate` never fails due to schema drift.

**Tech Stack:** GitHub Actions, dorny/paths-filter, psql (PostgreSQL client), git diff

---

### Task 1: Add `migrations` filter to change detection

**Files:**
- Modify: `.github/workflows/deploy.yml:17-55` (the `changes` job)

**Step 1: Add the `migrations` output and filter**

In the `changes` job, add a `migrations` output and a new filter entry:

```yaml
# In the outputs section (lines 23-26), add:
    outputs:
      backend: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.backend }}
      frontend: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.frontend }}
      infra: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.infra }}
      migrations: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.migrations }}

# In the filters block (lines 43-55), add after the infra filter:
            migrations:
              - 'supabase/migrations/**'
```

**Step 2: Update the `deploy` job `if` condition**

The `deploy` job must also run when only migrations changed (line 59):

```yaml
    if: needs.changes.outputs.backend == 'true' || needs.changes.outputs.frontend == 'true' || needs.changes.outputs.infra == 'true' || needs.changes.outputs.migrations == 'true'
```

**Step 3: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: add migrations change detection to deploy pipeline"
```

---

### Task 2: Add the migration execution step

**Files:**
- Modify: `.github/workflows/deploy.yml:66-` (the `deploy` job steps)

**Step 1: Add the migration step before Pulumi**

Insert this step after "Configure Docker for Artifact Registry" (after line 78) and before "Set up Java 25" (line 80):

```yaml
      - name: Run Supabase migrations
        if: needs.changes.outputs.migrations == 'true'
        env:
          SUPABASE_DB_URL: ${{ secrets.SUPABASE_DB_URL }}
        run: |
          # Find migration files added in this push
          NEW_FILES=$(git diff --name-only --diff-filter=A ${{ github.event.before }} ${{ github.sha }} -- 'supabase/migrations/*.sql' | sort)
          if [ -z "$NEW_FILES" ]; then
            echo "No new migration files detected, skipping."
            exit 0
          fi
          echo "New migration files to apply:"
          echo "$NEW_FILES"
          # Apply each migration in order
          for file in $NEW_FILES; do
            echo "Applying migration: $file"
            psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f "$file"
          done
          echo "All migrations applied successfully."
```

Key design decisions:
- **`--diff-filter=A`**: Only picks up *added* files (not modified or deleted) — prevents re-running existing migrations
- **`| sort`**: Ensures migrations run in filename order (001, 002, etc.)
- **`-v ON_ERROR_STOP=1`**: Makes `psql` exit with non-zero on the first SQL error, which fails the workflow and blocks deployment
- **`if: needs.changes.outputs.migrations == 'true'`**: Skips entirely when no migration files changed
- **Placement before Java/Gradle/Pulumi**: Schema is ready before backend builds or deploys

**Step 2: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: add Supabase migration step to deploy pipeline"
```

---

### Task 3: Handle edge case — initial push to prod (before SHA)

**Files:**
- Modify: `.github/workflows/deploy.yml` (the migration step)

**Step 1: Add fallback for zero SHA**

When the `prod` branch is first created, `github.event.before` is all zeros. The `changes` job already handles this (lines 32-38). We need the same handling in the migration step. Update the `run` block:

```yaml
      - name: Run Supabase migrations
        if: needs.changes.outputs.migrations == 'true'
        env:
          SUPABASE_DB_URL: ${{ secrets.SUPABASE_DB_URL }}
        run: |
          BEFORE=${{ github.event.before }}
          if [ "$BEFORE" = "0000000000000000000000000000000000000000" ]; then
            BEFORE=$(git rev-parse HEAD~1)
          fi
          NEW_FILES=$(git diff --name-only --diff-filter=A "$BEFORE" ${{ github.sha }} -- 'supabase/migrations/*.sql' | sort)
          if [ -z "$NEW_FILES" ]; then
            echo "No new migration files detected, skipping."
            exit 0
          fi
          echo "New migration files to apply:"
          echo "$NEW_FILES"
          for file in $NEW_FILES; do
            echo "Applying migration: $file"
            psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f "$file"
          done
          echo "All migrations applied successfully."
```

**Step 2: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: handle zero-SHA edge case in migration step"
```

---

### Task 4: Add `SUPABASE_DB_URL` secret documentation

**Files:**
- Modify: `CLAUDE.md` (Environment Variables section)

**Step 1: Document the new CI-only secret**

Add to the Environment Variables section, after the existing vars:

```markdown
Required for CI/CD (GitHub Actions secrets):
- `SUPABASE_DB_URL` - PostgreSQL connection string for running migrations in the deploy pipeline (format: `postgresql://user:pass@host:port/dbname`)
```

**Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: document SUPABASE_DB_URL CI secret"
```

---

### Task 5: Verify the complete workflow file

**Step 1: Review the final `.github/workflows/deploy.yml`**

Read the full file and verify:
- [ ] `migrations` output is declared in the `changes` job
- [ ] `migrations` filter matches `supabase/migrations/**`
- [ ] `deploy` job `if` includes `migrations == 'true'`
- [ ] Migration step runs before Pulumi deploy
- [ ] Migration step is conditional on `needs.changes.outputs.migrations == 'true'`
- [ ] Zero-SHA edge case is handled
- [ ] `SUPABASE_DB_URL` is referenced from secrets

**Step 2: Validate YAML syntax**

```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/deploy.yml'))"
```
Expected: No errors.

**Step 3: Final commit (if any fixups needed)**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: finalize migration deploy step"
```

---

## Post-Implementation: Required Manual Step

After merging, the repo owner must add the `SUPABASE_DB_URL` secret to the GitHub repository:
1. Go to repo Settings → Secrets and variables → Actions
2. Add `SUPABASE_DB_URL` with the Supabase PostgreSQL connection string

---

## Final Workflow Shape

```
changes job
  ├── backend: true/false
  ├── frontend: true/false
  ├── infra: true/false
  └── migrations: true/false

deploy job (if any of the above is true)
  ├── Checkout
  ├── GCP Auth
  ├── Cloud SDK
  ├── Docker Registry
  ├── ★ Run Supabase migrations (if migrations == true)  ← NEW
  ├── Java setup (if backend)
  ├── Build backend (if backend)
  ├── Build frontend (if frontend)
  ├── Python + Pulumi setup
  └── Pulumi deploy
```
