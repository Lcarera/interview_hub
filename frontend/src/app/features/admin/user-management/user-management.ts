import { Component, OnInit, inject, signal } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { FormsModule } from '@angular/forms';
import { Profile } from '../../../core/models/profile.model';
import { AdminService } from '../../../core/services/admin.service';
import { CreateUserDialogComponent, CreateUserDialogResult } from '../create-user-dialog/create-user-dialog';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [
    FormsModule, MatTableModule, MatButtonModule, MatIconModule,
    MatDialogModule, MatSelectModule, MatPaginatorModule,
  ],
  template: `
    <div class="admin-container">
      <div class="admin-header">
        <h2>User Management</h2>
        <button mat-flat-button (click)="openCreateDialog()">
          <mat-icon>person_add</mat-icon>
          Create User
        </button>
      </div>

      <table mat-table [dataSource]="users()">
        <ng-container matColumnDef="email">
          <th mat-header-cell *matHeaderCellDef>Email</th>
          <td mat-cell *matCellDef="let user">{{ user.email }}</td>
        </ng-container>

        <ng-container matColumnDef="role">
          <th mat-header-cell *matHeaderCellDef>Role</th>
          <td mat-cell *matCellDef="let user">
            <mat-select [ngModel]="user.role" (ngModelChange)="onRoleChange(user, $event)">
              <mat-option value="interviewer">Interviewer</mat-option>
              <mat-option value="admin">Admin</mat-option>
            </mat-select>
          </td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let user">
            <button mat-icon-button color="warn" (click)="deleteUser(user)">
              <mat-icon>delete</mat-icon>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
      </table>

      <mat-paginator
        [length]="totalUsers()"
        [pageSize]="size"
        [pageSizeOptions]="[10, 20, 50]"
        (page)="onPageChange($event)"
      />
    </div>
  `,
  styles: [`
    .admin-container { padding: 0; }
    .admin-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
    .admin-header h2 { margin: 0; }
    table { width: 100%; }
  `],
})
export class UserManagementComponent implements OnInit {
  private adminService = inject(AdminService);
  private dialog = inject(MatDialog);

  users = signal<Profile[]>([]);
  totalUsers = signal(0);
  displayedColumns = ['email', 'role', 'actions'];
  page = 0;
  size = 20;

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.adminService.listUsers(this.page, this.size).subscribe(res => {
      this.users.set(res.content);
      this.totalUsers.set(res.totalElements);
    });
  }

  onPageChange(event: PageEvent): void {
    this.page = event.pageIndex;
    this.size = event.pageSize;
    this.loadUsers();
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(CreateUserDialogComponent, { width: '400px' });
    ref.afterClosed().subscribe((result: CreateUserDialogResult | undefined) => {
      if (result) {
        this.adminService.createUser(result).subscribe(() => this.loadUsers());
      }
    });
  }

  onRoleChange(user: Profile, newRole: string): void {
    this.adminService.updateRole(user.id, newRole).subscribe(() => this.loadUsers());
  }

  deleteUser(user: Profile): void {
    if (confirm(`Delete user ${user.email}?`)) {
      this.adminService.deleteUser(user.id).subscribe(() => this.loadUsers());
    }
  }
}
