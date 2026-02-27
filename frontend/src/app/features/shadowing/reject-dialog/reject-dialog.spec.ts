import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MatDialogRef } from '@angular/material/dialog';
import { RejectDialogComponent } from './reject-dialog';

describe('RejectDialogComponent', () => {
  let closeFn: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    closeFn = vi.fn();
    await TestBed.configureTestingModule({
      imports: [RejectDialogComponent],
      providers: [
        provideAnimationsAsync(),
        { provide: MatDialogRef, useValue: { close: closeFn } },
      ],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(RejectDialogComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should not close when reason is empty', () => {
    const fixture = TestBed.createComponent(RejectDialogComponent);
    fixture.componentInstance.reason = '   ';
    fixture.componentInstance.confirm();
    expect(closeFn).not.toHaveBeenCalled();
  });

  it('should close with trimmed reason', () => {
    const fixture = TestBed.createComponent(RejectDialogComponent);
    fixture.componentInstance.reason = '  Not ready  ';
    fixture.componentInstance.confirm();
    expect(closeFn).toHaveBeenCalledWith('Not ready');
  });
});
