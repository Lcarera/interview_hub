import { ChangeDetectionStrategy, Component, HostListener, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [MatCardModule],
  templateUrl: './calendar.html',
  styleUrl: './calendar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarComponent {
  protected readonly iframeHeight = signal(window.innerHeight - 112);

  @HostListener('window:resize')
  onResize(): void {
    this.iframeHeight.set(window.innerHeight - 112);
  }
}
