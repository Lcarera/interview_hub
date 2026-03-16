# GitHub PR and Issue Templates Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add GitHub issue templates (feature, bug, chore) and a PR template to standardize project workflows and support existing Claude skills.

**Architecture:** Five static files in `.github/` — three YAML issue form templates, one config file, one Markdown PR template. No code changes, no tests needed.

**Tech Stack:** GitHub Issue Forms (YAML), Markdown

---

### Task 1: Create feature_request.yml

**Files:**
- Create: `.github/ISSUE_TEMPLATE/feature_request.yml`

**Step 1: Create the template file**

```yaml
name: Feature Request
description: Suggest a new feature or enhancement
title: "[FEATURE] "
labels: ["enhancement"]
body:
  - type: textarea
    id: problem
    attributes:
      label: Problem Statement
      description: What problem does this feature solve?
      placeholder: Describe the problem or need...
    validations:
      required: true
  - type: textarea
    id: solution
    attributes:
      label: Proposed Solution
      description: How should this be implemented?
      placeholder: Describe your proposed solution...
    validations:
      required: true
  - type: dropdown
    id: priority
    attributes:
      label: Priority
      options:
        - low
        - medium
        - high
    validations:
      required: true
  - type: textarea
    id: context
    attributes:
      label: Additional Context
      description: Any other context, screenshots, or references
```

**Step 2: Commit**

```bash
git add .github/ISSUE_TEMPLATE/feature_request.yml
git commit -m "chore: add feature request issue template"
```

---

### Task 2: Create bug_report.yml

**Files:**
- Create: `.github/ISSUE_TEMPLATE/bug_report.yml`

**Step 1: Create the template file**

```yaml
name: Bug Report
description: Report a bug or unexpected behavior
title: "[BUG] "
labels: ["bug"]
body:
  - type: textarea
    id: description
    attributes:
      label: Description
      description: What happened?
      placeholder: Describe the bug...
    validations:
      required: true
  - type: textarea
    id: reproduction
    attributes:
      label: Steps to Reproduce
      description: How can we reproduce this?
      placeholder: |
        1. Go to ...
        2. Click on ...
        3. See error ...
    validations:
      required: true
  - type: textarea
    id: expected
    attributes:
      label: Expected vs Actual Behavior
      description: What did you expect to happen, and what actually happened?
      placeholder: |
        Expected: ...
        Actual: ...
    validations:
      required: true
  - type: dropdown
    id: environment
    attributes:
      label: Environment
      options:
        - backend
        - frontend
        - both
    validations:
      required: true
  - type: dropdown
    id: priority
    attributes:
      label: Priority
      options:
        - low
        - medium
        - high
        - critical
    validations:
      required: true
  - type: textarea
    id: context
    attributes:
      label: Additional Context
      description: Logs, screenshots, or any other relevant info
```

**Step 2: Commit**

```bash
git add .github/ISSUE_TEMPLATE/bug_report.yml
git commit -m "chore: add bug report issue template"
```

---

### Task 3: Create chore_request.yml

**Files:**
- Create: `.github/ISSUE_TEMPLATE/chore_request.yml`

**Step 1: Create the template file**

```yaml
name: Chore / Tech Debt
description: Maintenance, refactoring, or operational task
title: "[CHORE] "
labels: ["chore"]
body:
  - type: textarea
    id: task
    attributes:
      label: Task Description
      description: What needs to be done?
      placeholder: Describe the task...
    validations:
      required: true
  - type: textarea
    id: motivation
    attributes:
      label: Motivation
      description: Why is this needed?
      placeholder: Explain the rationale...
    validations:
      required: true
  - type: dropdown
    id: scope
    attributes:
      label: Scope
      options:
        - backend
        - frontend
        - infra
        - docs
    validations:
      required: true
  - type: dropdown
    id: priority
    attributes:
      label: Priority
      options:
        - low
        - medium
        - high
    validations:
      required: true
  - type: textarea
    id: context
    attributes:
      label: Additional Context
      description: Any other context or references
```

**Step 2: Commit**

```bash
git add .github/ISSUE_TEMPLATE/chore_request.yml
git commit -m "chore: add chore request issue template"
```

---

### Task 4: Create config.yml and pull_request_template.md

**Files:**
- Create: `.github/ISSUE_TEMPLATE/config.yml`
- Create: `.github/pull_request_template.md`

**Step 1: Create config.yml**

```yaml
blank_issues_enabled: false
```

**Step 2: Create pull_request_template.md**

```markdown
## Summary
<!-- Bullet points describing what changed and why -->

-

## Related Issue
<!-- Link the issue this PR addresses -->

Closes #

## Test Plan
<!-- How was this tested? -->

-
```

**Step 3: Commit**

```bash
git add .github/ISSUE_TEMPLATE/config.yml .github/pull_request_template.md
git commit -m "chore: add issue config and PR template"
```

---
