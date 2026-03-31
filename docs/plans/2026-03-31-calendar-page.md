# Calendar Page

## Context
The team wants to see the shared Google Calendar (used for interview events) directly within Interview Hub. A new page embeds the existing public Google Calendar iframe, accessible to all authenticated users via a "Calendar" nav link.

## Approach
Add a lazy-loaded `/calendar` route under the shell (auth-protected). The `CalendarComponent` renders a `MatCard` containing a responsive iframe that fills the viewport height. No backend changes required.

## Files to Modify

### `frontend/src/app/app.routes.ts`
Add under the shell children:
```ts
{
  path: 'calendar',
  loadComponent: () => import('./features/calendar/calendar').then(m => m.CalendarComponent),
}
```

### `frontend/src/app/features/shell/shell.ts`
Add nav link after "Candidates":
```html
<a mat-button routerLink="/calendar" routerLinkActive="active">Calendar</a>
```

## New Files

### `frontend/src/app/features/calendar/calendar.ts`
```ts
@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [MatCardModule],
  templateUrl: './calendar.html',
  styleUrl: './calendar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarComponent {}
```

### `frontend/src/app/features/calendar/calendar.html`
```html
<mat-card class="calendar-card">
  <iframe
    src="https://calendar.google.com/calendar/embed?src=22451694651aab6f1cd53d8d70cc263fae62b15062406217b72c61755cac9a3a%40group.calendar.google.com&ctz=America%2FArgentina%2FBuenos_Aires"
    style="border: 0"
    width="100%"
    [style.height.px]="iframeHeight"
    frameborder="0"
    scrolling="no"
  ></iframe>
</mat-card>
```

`iframeHeight` is a computed value (`window.innerHeight - 112`) to fill the viewport below the toolbar.

### `frontend/src/app/features/calendar/calendar.scss`
```scss
.calendar-card {
  margin: 24px;
  padding: 0;
  overflow: hidden;

  iframe {
    display: block;
  }
}
```

### `frontend/src/app/features/calendar/calendar.spec.ts`
- Smoke test: component renders without error
- Assert iframe `src` contains the calendar group ID

## Verification
1. `bun run start` from `frontend/` → navigate to `/calendar`
2. Verify "Calendar" nav link appears in toolbar for authenticated users
3. Verify calendar iframe renders and fills the content area
4. `bun run test` — all tests pass
