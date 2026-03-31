import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { CalendarComponent } from './calendar';

describe('CalendarComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CalendarComponent],
      providers: [provideAnimationsAsync()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(CalendarComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render an iframe with the shared calendar src', () => {
    const fixture = TestBed.createComponent(CalendarComponent);
    fixture.detectChanges();
    const iframe: HTMLIFrameElement = fixture.nativeElement.querySelector('iframe');
    expect(iframe).toBeTruthy();
    expect(iframe.src).toContain('22451694651aab6f1cd53d8d70cc263fae62b15062406217b72c61755cac9a3a');
  });
});
