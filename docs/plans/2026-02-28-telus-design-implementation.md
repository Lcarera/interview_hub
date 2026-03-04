# TELUS Design Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Apply TELUS Digital brand colors and add visual depth across all frontend views, and fix the cramped interview form dialog.

**Architecture:** Pure visual changes only — modify Angular Material theme CSS variables, component templates, and SCSS files. No TypeScript logic changes except shell.ts inline styles. No backend changes.

**Tech Stack:** Angular 21, Angular Material 21.2, SCSS, Bun (for build/test)

---

## TELUS Color Reference

```
--telus-purple:            #4B286D   (primary brand, navbar, heroes, headings)
--telus-green:             #66CC00   (CTA buttons, approved/completed states)
--telus-green-accessible:  #2B8000   (green text on light bg)
--telus-dark:              #2A2C2E   (primary text)
--telus-muted:             #54595F   (label text, secondary)
--telus-surface:           #F4F4F7   (page background)
```

---

### Task 1: Global theme and CSS variables

**Files:**
- Modify: `frontend/src/styles.scss`

**Step 1: Replace the file contents**

```scss
@use '@angular/material' as mat;

html {
  height: 100%;
  @include mat.theme(
    (
      color: (
        primary: mat.$violet-palette,
        tertiary: mat.$green-palette,
      ),
      typography: Roboto,
      density: 0,
    )
  );

  // TELUS Digital brand tokens — use these in all components
  --telus-purple:           #4B286D;
  --telus-green:            #66CC00;
  --telus-green-accessible: #2B8000;
  --telus-dark:             #2A2C2E;
  --telus-muted:            #54595F;
  --telus-surface:          #F4F4F7;

  // Override Angular Material primary to exact TELUS purple
  --mat-sys-primary: #4B286D;
  --mat-sys-on-primary: #ffffff;
}

body {
  color-scheme: light;
  background-color: var(--telus-surface);
  color: var(--telus-dark);
  font: var(--mat-sys-body-medium);
  margin: 0;
  height: 100%;
}

// Global card elevation
mat-card {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08), 0 1px 2px rgba(0, 0, 0, 0.05) !important;
}

// Global status chip colors
mat-chip.status-scheduled {
  background-color: rgba(75, 40, 109, 0.12) !important;
  color: #4B286D !important;
}
mat-chip.status-completed, mat-chip.status-approved {
  background-color: rgba(43, 128, 0, 0.12) !important;
  color: #2B8000 !important;
}
mat-chip.status-cancelled, mat-chip.status-rejected {
  background-color: rgba(84, 89, 95, 0.12) !important;
  color: #54595F !important;
}
mat-chip.status-pending {
  background-color: rgba(180, 100, 0, 0.12) !important;
  color: #8a5000 !important;
}
```

**Step 2: Build to verify no errors**

```bash
cd frontend && bun run build 2>&1 | tail -20
```
Expected: build succeeds, no SCSS errors.

**Step 3: Commit**

```bash
git add frontend/src/styles.scss
git commit -m "feat: apply TELUS Digital theme and global CSS variables"
```

---

### Task 2: Shell / Navbar

**Files:**
- Modify: `frontend/src/app/features/shell/shell.ts`

**Step 1: Read the current file**

Read `frontend/src/app/features/shell/shell.ts` to see current inline template and styles.

**Step 2: Replace the inline styles object**

Find the `styles` array in the `@Component` decorator and replace it with:

```typescript
styles: [`
  mat-toolbar {
    position: sticky;
    top: 0;
    z-index: 100;
    background-color: var(--telus-purple) !important;
    color: white !important;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.18);
  }

  .brand {
    font-weight: 600;
    font-size: 1.1rem;
    margin-right: 1.5rem;
    letter-spacing: 0.01em;
  }

  .nav-links a {
    color: rgba(255, 255, 255, 0.85);
    font-weight: 400;
    transition: color 0.15s;
  }

  .nav-links a:hover {
    color: white;
  }

  .nav-links a.active {
    color: white;
    font-weight: 600;
    border-bottom: 2px solid var(--telus-green);
    border-radius: 0;
  }

  .spacer {
    flex: 1;
  }

  .shell-content {
    max-width: 1200px;
    margin: 2rem auto;
    padding: 0 1.5rem;
  }

  button[mat-button] {
    color: rgba(255, 255, 255, 0.9) !important;
  }

  button[mat-button]:hover {
    color: white !important;
  }
`]
```

**Step 3: Build to verify**

```bash
cd frontend && bun run build 2>&1 | tail -20
```
Expected: build succeeds.

**Step 4: Commit**

```bash
git add frontend/src/app/features/shell/shell.ts
git commit -m "feat: apply TELUS purple navbar with green active indicator"
```

---

### Task 3: Login page

**Files:**
- Modify: `frontend/src/app/features/auth/login/login.html`
- Modify: `frontend/src/app/features/auth/login/login.scss`
- Modify: `frontend/src/app/features/auth/login/login.ts` (add MatCardModule import)

**Step 1: Read the current login.ts imports**

Read `frontend/src/app/features/auth/login/login.ts` to see what is currently imported in the `imports` array.

**Step 2: Replace login.html**

```html
<div class="login-container">
  <div class="login-hero">
    <div class="hero-content">
      <mat-icon class="hero-icon">hub</mat-icon>
      <h1>Interview Hub</h1>
      <p class="hero-tagline">Streamline technical interviews and shadowing requests for your team.</p>
    </div>
  </div>

  <div class="login-panel">
    <mat-card class="login-card">
      <mat-card-header>
        <mat-card-title>Welcome</mat-card-title>
        <mat-card-subtitle>Sign in with your @gm2dev.com account</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <button mat-flat-button class="google-btn" (click)="login()">
          <mat-icon>login</mat-icon>
          Sign in with Google
        </button>
      </mat-card-content>
    </mat-card>
  </div>
</div>
```

**Step 3: Replace login.scss**

```scss
:host {
  display: flex;
  height: 100vh;
  width: 100%;
}

.login-container {
  display: flex;
  width: 100%;
  height: 100%;
}

.login-hero {
  flex: 1;
  background-color: var(--telus-purple);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 3rem;

  @media (max-width: 768px) {
    display: none;
  }
}

.hero-content {
  text-align: center;
  color: white;
  max-width: 360px;

  .hero-icon {
    font-size: 4rem;
    width: 4rem;
    height: 4rem;
    margin-bottom: 1.5rem;
    opacity: 0.9;
  }

  h1 {
    font-size: 2.75rem;
    font-weight: 300;
    margin: 0 0 1rem;
    letter-spacing: -0.01em;
  }

  .hero-tagline {
    font-size: 1rem;
    line-height: 1.65;
    opacity: 0.8;
    margin: 0;
  }
}

.login-panel {
  width: 440px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background: white;

  @media (max-width: 768px) {
    width: 100%;
  }
}

.login-card {
  width: 100%;
  padding: 1.5rem 1rem;
  box-shadow: none !important;
}

.google-btn {
  width: 100%;
  margin-top: 1.5rem;
  background-color: var(--telus-purple) !important;
  color: white !important;
  height: 48px;
  font-size: 1rem;
  font-weight: 500;
  letter-spacing: 0.02em;
}
```

**Step 4: Ensure login.ts imports MatCardModule, MatIconModule**

The component `imports` array must include: `MatCardModule`, `MatIconModule`, `MatButtonModule`.
Add any missing ones from `@angular/material/card` and `@angular/material/icon`.

**Step 5: Build to verify**

```bash
cd frontend && bun run build 2>&1 | tail -20
```
Expected: build succeeds.

**Step 6: Commit**

```bash
git add frontend/src/app/features/auth/login/
git commit -m "feat: redesign login page with TELUS purple hero split layout"
```

---

### Task 4: Dashboard

**Files:**
- Modify: `frontend/src/app/features/dashboard/dashboard.html`
- Modify: `frontend/src/app/features/dashboard/dashboard.scss`

**Step 1: Replace dashboard.html**

```html
<div class="page-hero">
  <div class="hero-inner">
    <h2>Dashboard</h2>
    <p class="hero-subtitle">Your upcoming interviews and pending requests at a glance.</p>
  </div>
</div>

@if (loading()) {
  <div class="spinner-container">
    <mat-spinner diameter="48" />
  </div>
} @else {
  <div class="dashboard-grid">
    <mat-card>
      <mat-card-header>
        <mat-icon matCardAvatar class="card-icon">event</mat-icon>
        <mat-card-title>Upcoming Interviews</mat-card-title>
        <mat-card-subtitle>Next 5 scheduled interviews</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        @if (interviews().length === 0) {
          <p class="empty-state">No upcoming interviews.</p>
        } @else {
          <mat-list>
            @for (iv of interviews(); track iv.id) {
              <a mat-list-item [routerLink]="['/interviews', iv.id]" class="interview-list-item">
                <span matListItemTitle>{{ iv.techStack }}</span>
                <span matListItemLine>{{ iv.startTime | date:'medium' }} — {{ iv.interviewer.email }}</span>
              </a>
            }
          </mat-list>
        }
      </mat-card-content>
      <mat-card-actions>
        <a mat-button routerLink="/interviews" class="view-all-btn">View All Interviews</a>
      </mat-card-actions>
    </mat-card>

    @if (pendingShadowingCount() > 0) {
      <mat-card class="pending-card">
        <mat-card-header>
          <mat-icon matCardAvatar class="card-icon pending-icon">notifications_active</mat-icon>
          <mat-card-title>Pending Shadowing Requests</mat-card-title>
          <mat-card-subtitle>Action required</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div class="pending-count-row">
            <span class="pending-count">{{ pendingShadowingCount() }}</span>
            <span class="pending-label">pending request{{ pendingShadowingCount() > 1 ? 's' : '' }} across your interviews</span>
          </div>
        </mat-card-content>
        <mat-card-actions>
          <a mat-button routerLink="/interviews" class="view-all-btn">Review Requests</a>
        </mat-card-actions>
      </mat-card>
    }
  </div>
}
```

**Step 2: Replace dashboard.scss**

```scss
.page-hero {
  background-color: var(--telus-purple);
  color: white;
  padding: 2rem 0;
  margin: -2rem -1.5rem 2rem;

  .hero-inner {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 1.5rem;
  }

  h2 {
    margin: 0 0 0.5rem;
    font-size: 1.75rem;
    font-weight: 400;
  }

  .hero-subtitle {
    margin: 0;
    opacity: 0.8;
    font-size: 0.95rem;
  }
}

.spinner-container {
  display: flex;
  justify-content: center;
  padding: 4rem 0;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
}

.card-icon {
  color: var(--telus-purple) !important;
  background-color: rgba(75, 40, 109, 0.1);
  border-radius: 50%;
}

.empty-state {
  color: var(--telus-muted);
  text-align: center;
  padding: 2rem 0;
  font-style: italic;
}

.interview-list-item {
  border-left: 3px solid var(--telus-purple);
  margin-bottom: 4px;
  border-radius: 0 4px 4px 0;
}

.view-all-btn {
  color: var(--telus-purple) !important;
  font-weight: 500;
}

.pending-card {
  .pending-icon {
    color: var(--telus-green-accessible) !important;
    background-color: rgba(43, 128, 0, 0.1);
  }
}

.pending-count-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 0;

  .pending-count {
    font-size: 2.5rem;
    font-weight: 700;
    color: var(--telus-purple);
    line-height: 1;
  }

  .pending-label {
    color: var(--telus-muted);
    font-size: 0.95rem;
  }
}
```

**Note:** The `.page-hero` negative margins (`-2rem -1.5rem`) cancel the shell content's padding so the hero stretches edge-to-edge. This works with `.shell-content { padding: 0 1.5rem; margin: 2rem auto; }`.

**Step 3: Build to verify**

```bash
cd frontend && bun run build 2>&1 | tail -20
```

**Step 4: Commit**

```bash
git add frontend/src/app/features/dashboard/
git commit -m "feat: add TELUS purple hero band and card depth to dashboard"
```

---

### Task 5: Interview List

**Files:**
- Modify: `frontend/src/app/features/interviews/interview-list/interview-list.html`
- Modify: `frontend/src/app/features/interviews/interview-list/interview-list.scss`

**Step 1: Replace interview-list.html**

Key changes: add page hero, color-coded status chips via CSS class binding, green New Interview button.

```html
@if (loading() && !page()) {
  <div class="spinner-container">
    <mat-spinner diameter="48" />
  </div>
} @else if (page(); as p) {
  <div class="page-hero">
    <div class="hero-inner">
      <h2>Interviews</h2>
      <p class="hero-subtitle">Manage and track all technical interviews.</p>
    </div>
    <button mat-flat-button class="create-btn" (click)="openCreateDialog()">
      <mat-icon>add</mat-icon>
      New Interview
    </button>
  </div>

  <mat-card class="table-card">
    <table mat-table [dataSource]="p.content" matSort (matSortChange)="onSortChange($event)" class="interview-table">
      <ng-container matColumnDef="techStack">
        <th mat-header-cell *matHeaderCellDef mat-sort-header="techStack">Tech Stack</th>
        <td mat-cell *matCellDef="let row">{{ row.techStack }}</td>
      </ng-container>

      <ng-container matColumnDef="candidateName">
        <th mat-header-cell *matHeaderCellDef>Candidate</th>
        <td mat-cell *matCellDef="let row">{{ row.candidateInfo?.['name'] || '—' }}</td>
      </ng-container>

      <ng-container matColumnDef="interviewer">
        <th mat-header-cell *matHeaderCellDef>Interviewer</th>
        <td mat-cell *matCellDef="let row">{{ row.interviewer.email }}</td>
      </ng-container>

      <ng-container matColumnDef="dateTime">
        <th mat-header-cell *matHeaderCellDef mat-sort-header="startTime">Date / Time</th>
        <td mat-cell *matCellDef="let row">{{ row.startTime | date:'medium' }}</td>
      </ng-container>

      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef mat-sort-header="status">Status</th>
        <td mat-cell *matCellDef="let row">
          <mat-chip-set>
            <mat-chip [class]="'status-' + row.status.toLowerCase()">{{ row.status }}</mat-chip>
          </mat-chip-set>
        </td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns;" (click)="onRowClick(row)" class="clickable-row"></tr>
    </table>

    <mat-paginator
      [length]="p.totalElements"
      [pageIndex]="p.number"
      [pageSize]="p.size"
      [pageSizeOptions]="[10, 20, 50]"
      (page)="onPageChange($event)"
      showFirstLastButtons
    />
  </mat-card>
}
```

**Step 2: Replace interview-list.scss**

```scss
.spinner-container {
  display: flex;
  justify-content: center;
  padding: 4rem 0;
}

.page-hero {
  background-color: var(--telus-purple);
  color: white;
  padding: 2rem 1.5rem;
  margin: -2rem -1.5rem 2rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;

  .hero-inner {
    h2 {
      margin: 0 0 0.25rem;
      font-size: 1.75rem;
      font-weight: 400;
    }

    .hero-subtitle {
      margin: 0;
      opacity: 0.8;
      font-size: 0.95rem;
    }
  }
}

.create-btn {
  background-color: var(--telus-green) !important;
  color: #1a1a1a !important;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.table-card {
  padding: 0;
  overflow: hidden;
}

.interview-table {
  width: 100%;
}

.clickable-row {
  cursor: pointer;

  &:hover {
    background-color: rgba(75, 40, 109, 0.04);
  }
}
```

**Step 3: Build to verify**

```bash
cd frontend && bun run build 2>&1 | tail -20
```

**Step 4: Commit**

```bash
git add frontend/src/app/features/interviews/interview-list/
git commit -m "feat: add TELUS hero header and status chip colors to interview list"
```

---

### Task 6: Interview Detail

**Files:**
- Modify: `frontend/src/app/features/interviews/interview-detail/interview-detail.html`
- Modify: `frontend/src/app/features/interviews/interview-detail/interview-detail.scss`

**Step 1: Replace interview-detail.html**

```html
@if (loading() && !interview()) {
  <div class="spinner-container">
    <mat-spinner diameter="48" />
  </div>
} @else if (interview(); as iv) {
  <mat-card class="detail-card">
    <div class="card-header-band">
      <div class="header-main">
        <h2 class="card-title">{{ iv.techStack }}</h2>
        <mat-chip-set>
          <mat-chip [class]="'status-' + iv.status.toLowerCase()">{{ iv.status }}</mat-chip>
        </mat-chip-set>
      </div>
    </div>

    <mat-card-content class="detail-content">
      <div class="detail-grid">
        <div class="detail-item">
          <span class="label">Interviewer</span>
          <span>{{ iv.interviewer.email }}</span>
        </div>
        @if (iv.candidateInfo['name']) {
          <div class="detail-item">
            <span class="label">Candidate</span>
            <span>{{ iv.candidateInfo['name'] }}</span>
          </div>
        }
        @if (iv.candidateInfo['email']) {
          <div class="detail-item">
            <span class="label">Candidate Email</span>
            <span>{{ iv.candidateInfo['email'] }}</span>
          </div>
        }
        <div class="detail-item">
          <span class="label">Start</span>
          <span>{{ iv.startTime | date:'medium' }}</span>
        </div>
        <div class="detail-item">
          <span class="label">End</span>
          <span>{{ iv.endTime | date:'medium' }}</span>
        </div>
      </div>
    </mat-card-content>

    <mat-card-actions class="card-actions">
      @if (iv.status === 'SCHEDULED') {
        @if (isOwner()) {
          <button mat-stroked-button class="edit-btn" (click)="openEditDialog()">
            <mat-icon>edit</mat-icon> Edit
          </button>
          <button mat-stroked-button color="warn" (click)="cancelInterview()">
            <mat-icon>cancel</mat-icon> Cancel Interview
          </button>
        }
        @if (!isOwner()) {
          <button mat-flat-button class="shadow-btn" (click)="requestShadow()">
            <mat-icon>visibility</mat-icon> Request to Shadow
          </button>
        }
      }
    </mat-card-actions>
  </mat-card>

  @if (iv.shadowingRequests && iv.shadowingRequests.length > 0) {
    <mat-card class="shadowing-card">
      <mat-card-header>
        <mat-icon matCardAvatar class="section-icon">group</mat-icon>
        <mat-card-title>Shadowing Requests</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <mat-list>
          @for (sr of iv.shadowingRequests; track sr.id) {
            <mat-list-item class="shadowing-item">
              <span matListItemTitle>{{ sr.shadower.email }}</span>
              <span matListItemLine>
                <mat-chip-set>
                  <mat-chip [class]="'status-' + sr.status.toLowerCase()">{{ sr.status }}</mat-chip>
                </mat-chip-set>
                @if (sr.reason) {
                  <span class="reason">Reason: {{ sr.reason }}</span>
                }
              </span>
              <span matListItemMeta>
                @if (sr.status === 'PENDING' && isOwner()) {
                  <button mat-icon-button class="approve-btn" (click)="approveShadowing(sr.id)" title="Approve">
                    <mat-icon>check_circle</mat-icon>
                  </button>
                  <button mat-icon-button color="warn" (click)="rejectShadowing(sr.id)" title="Reject">
                    <mat-icon>cancel</mat-icon>
                  </button>
                }
                @if (sr.status === 'PENDING' && sr.shadower.id === profileId()) {
                  <button mat-icon-button (click)="cancelShadowing(sr.id)" title="Cancel Request">
                    <mat-icon>delete</mat-icon>
                  </button>
                }
              </span>
            </mat-list-item>
            <mat-divider />
          }
        </mat-list>
      </mat-card-content>
    </mat-card>
  }
}
```

**Step 2: Replace interview-detail.scss**

```scss
.spinner-container {
  display: flex;
  justify-content: center;
  padding: 4rem 0;
}

.detail-card {
  margin-bottom: 1.5rem;
  overflow: hidden;
}

.card-header-band {
  background-color: var(--telus-purple);
  color: white;
  padding: 1.5rem 1.5rem 1.25rem;

  .header-main {
    display: flex;
    align-items: center;
    gap: 1rem;
    flex-wrap: wrap;
  }

  .card-title {
    margin: 0;
    font-size: 1.5rem;
    font-weight: 400;
    color: white;
  }

  mat-chip {
    background-color: rgba(255, 255, 255, 0.2) !important;
    color: white !important;
  }
}

.detail-content {
  padding: 1.5rem !important;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.25rem;
}

.detail-item {
  display: flex;
  flex-direction: column;

  .label {
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--telus-muted);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    margin-bottom: 0.3rem;
  }
}

.card-actions {
  padding: 0.75rem 1.5rem 1.25rem !important;
  gap: 0.75rem;
}

.edit-btn {
  color: var(--telus-purple) !important;
  border-color: var(--telus-purple) !important;
}

.shadow-btn {
  background-color: var(--telus-green) !important;
  color: #1a1a1a !important;
  font-weight: 600;
}

.shadowing-card {
  .section-icon {
    color: var(--telus-purple) !important;
    background-color: rgba(75, 40, 109, 0.1);
    border-radius: 50%;
  }
}

.shadowing-item {
  padding: 0.25rem 0;
}

.approve-btn {
  color: var(--telus-green-accessible) !important;
}

.reason {
  font-style: italic;
  color: var(--telus-muted);
  margin-left: 0.5rem;
  font-size: 0.85rem;
}
```

**Step 3: Build to verify**

```bash
cd frontend && bun run build 2>&1 | tail -20
```

**Step 4: Commit**

```bash
git add frontend/src/app/features/interviews/interview-detail/
git commit -m "feat: redesign interview detail with TELUS purple card header"
```

---

### Task 7: Interview Form Dialog (fix cramped layout)

**Files:**
- Modify: `frontend/src/app/features/interviews/interview-form-dialog/interview-form-dialog.html`
- Modify: `frontend/src/app/features/interviews/interview-form-dialog/interview-form-dialog.scss`

**Step 1: Replace interview-form-dialog.html**

Groups fields into 3 sections. Candidate Name + Email in a 2-col row. Start Time + Duration in a 2-col row.

```html
<h2 mat-dialog-title class="dialog-title">{{ isEdit ? 'Edit Interview' : 'New Interview' }}</h2>

<mat-dialog-content class="dialog-content">
  <form [formGroup]="form" (ngSubmit)="save()" id="interviewForm">

    <div class="form-section">
      <p class="section-label">Interview Details</p>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Tech Stack</mat-label>
        <input matInput formControlName="techStack" placeholder="e.g. Java, Spring Boot, PostgreSQL" required />
      </mat-form-field>
    </div>

    <div class="form-section">
      <p class="section-label">Candidate</p>
      <div class="field-row">
        <mat-form-field appearance="outline" class="half-width">
          <mat-label>Candidate Name</mat-label>
          <input matInput formControlName="candidateName" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="half-width">
          <mat-label>Candidate Email</mat-label>
          <input matInput formControlName="candidateEmail" type="email" />
        </mat-form-field>
      </div>
    </div>

    <div class="form-section">
      <p class="section-label">Schedule</p>
      <div class="field-row">
        <mat-form-field appearance="outline" class="half-width">
          <mat-label>Start Time</mat-label>
          <input matInput formControlName="startTime" type="datetime-local" required />
        </mat-form-field>

        <mat-form-field appearance="outline" class="half-width">
          <mat-label>Duration</mat-label>
          <mat-select formControlName="duration" required>
            @for (opt of durationOptions; track opt.value) {
              <mat-option [value]="opt.value">{{ opt.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>
    </div>

  </form>
</mat-dialog-content>

<mat-dialog-actions align="end" class="dialog-actions">
  <button mat-stroked-button mat-dialog-close>Cancel</button>
  <button mat-flat-button class="submit-btn" type="submit" form="interviewForm" [disabled]="form.invalid || submitting">
    {{ isEdit ? 'Update Interview' : 'Create Interview' }}
  </button>
</mat-dialog-actions>
```

**Step 2: Replace interview-form-dialog.scss**

```scss
.dialog-title {
  border-left: 4px solid var(--telus-purple);
  padding-left: 1rem !important;
  color: var(--telus-purple);
}

.dialog-content {
  padding: 8px 24px 16px !important;
  min-width: 560px;

  @media (max-width: 640px) {
    min-width: unset;
    width: 90vw;
  }
}

form {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-bottom: 0.5rem;

  & + .form-section {
    padding-top: 0.75rem;
    border-top: 1px solid rgba(0, 0, 0, 0.08);
  }
}

.section-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--telus-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin: 0 0 0.5rem;
}

.full-width {
  width: 100%;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;

  @media (max-width: 480px) {
    grid-template-columns: 1fr;
  }
}

.half-width {
  width: 100%;
}

.dialog-actions {
  padding: 8px 24px 16px !important;
  gap: 0.75rem;
}

.submit-btn {
  background-color: var(--telus-purple) !important;
  color: white !important;
  min-width: 140px;
}
```

**Step 3: Build to verify**

```bash
cd frontend && bun run build 2>&1 | tail -20
```

**Step 4: Run frontend tests**

```bash
cd frontend && bun run test --run 2>&1 | tail -30
```
Expected: all tests pass (no logic changes were made).

**Step 5: Commit**

```bash
git add frontend/src/app/features/interviews/interview-form-dialog/
git commit -m "feat: fix cramped dialog layout with grouped fields and TELUS styling"
```

---

### Task 8: Reject Dialog minor update

**Files:**
- Modify: `frontend/src/app/features/shadowing/reject-dialog/reject-dialog.ts` (inline styles + template)

**Step 1: Read the current file**

Read `frontend/src/app/features/shadowing/reject-dialog/reject-dialog.ts`.

**Step 2: Update inline styles**

Replace the `styles` array with:

```typescript
styles: [`
  .dialog-title {
    border-left: 4px solid #4B286D;
    padding-left: 1rem !important;
    color: #4B286D;
  }
  .dialog-content {
    padding: 8px 24px 16px !important;
    min-width: 420px;
  }
  .full-width {
    width: 100%;
  }
  .dialog-actions {
    padding: 8px 24px 16px !important;
    gap: 0.75rem;
  }
`]
```

**Step 3: Update the `h2 mat-dialog-title` in the template**

Add `class="dialog-title"` to the `h2` tag:
```html
<h2 mat-dialog-title class="dialog-title">Reject Shadowing Request</h2>
```

Also add `class="dialog-content"` to `mat-dialog-content` and `class="dialog-actions"` to `mat-dialog-actions`.

**Step 4: Build to verify**

```bash
cd frontend && bun run build 2>&1 | tail -20
```

**Step 5: Final test run**

```bash
cd frontend && bun run test --run 2>&1 | tail -30
```
Expected: all tests pass.

**Step 6: Final commit**

```bash
git add frontend/src/app/features/shadowing/
git commit -m "feat: apply TELUS dialog styling to reject dialog"
```

---

## Verification Checklist

After all tasks are done, visually verify each view in the browser (`bun run start` from `frontend/`):

- [ ] Navbar is TELUS purple with green active underline
- [ ] Login page has purple left half, white right panel with card
- [ ] Dashboard has purple hero band, cards have shadow on gray background
- [ ] Dashboard interview list items have purple left border
- [ ] Interview list has purple hero header with green "New Interview" button
- [ ] Status chips are color-coded (purple=SCHEDULED, green=COMPLETED/APPROVED, gray=CANCELLED/REJECTED)
- [ ] Interview detail has purple card header with white text
- [ ] Interview form dialog is wide (560px), with sections separated by dividers, side-by-side fields
- [ ] Reject dialog has purple title accent
- [ ] Page background is light gray (#F4F4F7), not white
