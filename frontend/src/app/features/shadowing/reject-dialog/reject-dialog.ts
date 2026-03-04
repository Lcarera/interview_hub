import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-reject-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title class="dialog-title">Reject Shadowing Request</h2>
    <mat-dialog-content class="dialog-content">
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Reason</mat-label>
        <textarea matInput [(ngModel)]="reason" rows="3" required></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end" class="dialog-actions">
      <button mat-stroked-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" [disabled]="!reason.trim()" (click)="confirm()">Reject</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog-title {
      border-left: 4px solid var(--telus-purple);
      padding-left: 1rem !important;
      color: var(--telus-purple);
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
  `],
})
export class RejectDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<RejectDialogComponent>);
  reason = '';

  confirm(): void {
    if (this.reason.trim()) {
      this.dialogRef.close(this.reason.trim());
    }
  }
}
