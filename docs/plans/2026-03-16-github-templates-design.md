# GitHub PR and Issue Templates Design

## Context

Interview Hub has no GitHub issue or PR templates. Existing Claude skills (`create-github-issue-feature-from-specification`, `create-github-pull-request-from-specification`, etc.) expect templates at `.github/ISSUE_TEMPLATE/` and `.github/pull_request_template.md`. This design adds lean, skill-compatible templates for a solo project.

## Files

```
.github/
  ISSUE_TEMPLATE/
    config.yml
    feature_request.yml
    bug_report.yml
    chore_request.yml
  pull_request_template.md
```

## Issue Templates (YAML Forms)

### feature_request.yml

- Title prefix: `[FEATURE] `
- Labels: `enhancement`
- Fields:
  - Problem Statement (textarea, required)
  - Proposed Solution (textarea, required)
  - Priority (dropdown: low/medium/high, required)
  - Additional Context (textarea, optional)

### bug_report.yml

- Title prefix: `[BUG] `
- Labels: `bug`
- Fields:
  - Description (textarea, required)
  - Steps to Reproduce (textarea, required)
  - Expected vs Actual (textarea, required)
  - Environment (dropdown: backend/frontend/both, required)
  - Priority (dropdown: low/medium/high/critical, required)
  - Additional Context (textarea, optional)

### chore_request.yml

- Title prefix: `[CHORE] `
- Labels: `chore`
- Fields:
  - Task Description (textarea, required)
  - Motivation (textarea, required)
  - Scope (dropdown: backend/frontend/infra/docs, required)
  - Priority (dropdown: low/medium/high, required)
  - Additional Context (textarea, optional)

### config.yml

Disables blank issues to force template usage.

## PR Template (Markdown)

Sections:
- Summary (bullet points)
- Related Issue (`Closes #...`)
- Test Plan (how it was tested)

## Decisions

- **YAML forms over Markdown** for issues: structured input, cleaner experience
- **Lean fields**: solo project, no CoC/contributor guidelines needed
- **Skill-compatible**: field names and structure match what existing Claude skills expect
- **Blank issues disabled**: forces consistent structure
