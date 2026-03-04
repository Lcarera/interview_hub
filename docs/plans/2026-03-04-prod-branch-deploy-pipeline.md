# Prod Branch & Deploy Pipeline Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Set up a `prod` branch that is protected (requires PR to merge), and update the deploy workflow to trigger on pushes to `prod` instead of `main`.

**Architecture:** `main` remains the development branch. `prod` is a long-lived deploy branch. Merging `main → prod` via PR triggers the GHA deploy pipeline. Branch protection is enforced via GitHub rulesets (available on free plans for public repos).

**Tech Stack:** GitHub Actions, GitHub Rulesets API (`gh api`)

---

### Task 1: Create the `prod` branch

**Step 1: Create `prod` from current `main`**

Run:
```bash
cd /c/Users/lcare/OneDrive/Documentos/Programacion/interview_hub
git checkout main
git pull origin main
git checkout -b prod
git push -u origin prod
git checkout main
```

Expected: `prod` branch exists on remote, identical to `main`.

---

### Task 2: Update deploy workflow trigger

**Files:**
- Modify: `.github/workflows/deploy.yml`

**Step 1: Change the trigger branch from `main` to `prod`**

In `.github/workflows/deploy.yml`, change line 4:

```yaml
# Before
on:
  push:
    branches: [main]

# After
on:
  push:
    branches: [prod]
```

**Step 2: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: trigger deploy workflow on prod branch instead of main"
```

---

### Task 3: Add branch protection ruleset for `prod`

**Step 1: Create a ruleset requiring PRs to merge into `prod`**

Run:
```bash
gh api repos/Lcarera/interview_hub/rulesets \
  --method POST \
  --input - <<'EOF'
{
  "name": "Protect prod branch",
  "target": "branch",
  "enforcement": "active",
  "conditions": {
    "ref_name": {
      "include": ["refs/heads/prod"],
      "exclude": []
    }
  },
  "rules": [
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": false,
        "automatic_copilot_review_enabled": false
      }
    },
    {
      "type": "deletion"
    }
  ]
}
EOF
```

This creates a ruleset that:
- Requires a PR to merge into `prod` (no direct pushes)
- Sets required approvals to 0 (since you're a solo dev, you can merge your own PRs)
- Prevents accidental deletion of the `prod` branch

Expected: JSON response with the created ruleset (contains `"id"` and `"name": "Protect prod branch"`).

**Step 2: Verify the ruleset is active**

Run:
```bash
gh api repos/Lcarera/interview_hub/rulesets --jq '.[].name'
```

Expected output includes: `Protect prod branch`

---

### Task 4: Push workflow change to `main` and test the flow

**Step 1: Push the workflow change to `main`**

```bash
git push origin main
```

Expected: Push succeeds. No deploy triggers (workflow now only triggers on `prod`).

**Step 2: Verify direct push to `prod` is blocked**

```bash
git checkout prod
echo "# test" >> README.md
git add README.md
git commit -m "test: verify protection"
git push origin prod
```

Expected: Push is **rejected** by the ruleset.

**Step 3: Clean up the test commit**

```bash
git reset --hard HEAD~1
git checkout main
```

**Step 4: Test the PR flow**

```bash
gh pr create --base prod --head main --title "Deploy: sync main to prod" --body "Initial deployment via prod branch"
```

Expected: PR is created. After merging, the deploy workflow should trigger.

---

### Summary of final state

| Branch | Purpose | Protection |
|--------|---------|------------|
| `main` | Development, feature PRs merge here | None (default) |
| `prod` | Deployment trigger | Requires PR, no direct push, no deletion |

**Deploy flow:** `feature-branch → PR → main → PR → prod → deploy workflow runs`
