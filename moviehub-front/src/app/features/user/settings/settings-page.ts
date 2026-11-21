import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';

import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/http/api-error.util';
import { ProfileService } from '../../../core/services/profile.service';
import { ToastService } from '../../../core/ui/toast.service';
import { Icon } from '../../../shared/components/icon/icon';
import { passwordScore } from '../../auth/register/register-page';
import { PageHeader } from '../components/page-header';

const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).+$/;
const STRENGTH_COLORS = ['', 'bg-danger', 'bg-warning', 'bg-ember', 'bg-success'];

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const a = group.get('newPassword')?.value;
  const b = group.get('confirmPassword')?.value;
  return a && b && a !== b ? { passwordMismatch: true } : null;
}

@Component({
  selector: 'app-settings-page',
  imports: [ReactiveFormsModule, Icon, PageHeader],
  host: { '(document:keydown.escape)': 'closeDelete()' },
  template: `
    <app-page-header heading="Account Settings" icon="settings" subheading="Security and account management" />

    <div class="space-y-6">
      <!-- Password -->
      <section class="card p-6 sm:p-8">
        <div class="flex items-start gap-4">
          <span class="grid size-11 shrink-0 place-items-center rounded-xl bg-white/5 ring-1 ring-border"><app-icon name="lock" class="size-5" /></span>
          <div>
            <h2 class="font-display text-lg font-semibold">Change password</h2>
            <p class="mt-1 text-sm text-muted">For your security, this signs you out on all other devices.</p>
          </div>
        </div>

        <form class="mt-6 grid max-w-xl gap-5" [formGroup]="passwordForm" (ngSubmit)="changePassword()" novalidate>
          <div>
            <label class="label" for="currentPassword">Current password</label>
            <input id="currentPassword" type="password" class="input" formControlName="currentPassword" autocomplete="current-password"
              [class.!border-danger]="error('currentPassword')" />
            @if (error('currentPassword'); as msg) {
              <p class="mt-1.5 text-xs text-danger">{{ msg }}</p>
            }
          </div>
          <div>
            <label class="label" for="newPassword">New password</label>
            <input id="newPassword" type="password" class="input" formControlName="newPassword" autocomplete="new-password"
              [class.!border-danger]="error('newPassword')" />
            <div class="mt-2.5 flex gap-1.5" aria-hidden="true">
              @for (i of [1, 2, 3, 4]; track i) {
                <span class="h-1 flex-1 rounded-full transition-colors" [class]="strength() >= i ? strengthColor() : 'bg-white/10'"></span>
              }
            </div>
            @if (error('newPassword'); as msg) {
              <p class="mt-1.5 text-xs text-danger">{{ msg }}</p>
            } @else {
              <p class="mt-1.5 text-xs text-muted">8+ characters with at least one letter and one digit.</p>
            }
          </div>
          <div>
            <label class="label" for="confirmPassword">Confirm new password</label>
            <input id="confirmPassword" type="password" class="input" formControlName="confirmPassword" autocomplete="new-password"
              [class.!border-danger]="error('confirmPassword')" />
            @if (error('confirmPassword'); as msg) {
              <p class="mt-1.5 text-xs text-danger">{{ msg }}</p>
            }
          </div>
          <div>
            <button type="submit" class="btn btn-primary" [disabled]="changing()">
              {{ changing() ? 'Updating…' : 'Update password' }}
            </button>
          </div>
        </form>
      </section>

      <!-- Danger zone -->
      @if (!auth.isAdmin()) {
        <section class="card border-danger/30 p-6 sm:p-8">
          <div class="flex flex-wrap items-start gap-4">
            <span class="grid size-11 shrink-0 place-items-center rounded-xl bg-danger/10 text-danger ring-1 ring-danger/25"><app-icon name="alert" class="size-5" /></span>
            <div class="min-w-0 flex-1">
              <h2 class="font-display text-lg font-semibold">Delete account</h2>
              <p class="mt-1 text-sm text-muted">Permanently removes your account, watchlist and reviews. This cannot be undone.</p>
            </div>
            <button type="button" class="btn border border-danger/40 text-danger hover:bg-danger/10" (click)="deleteOpen.set(true)">Delete my account</button>
          </div>
        </section>
      }
    </div>

    <!-- Delete confirmation dialog -->
    @if (deleteOpen()) {
      <div class="fixed inset-0 z-[60] grid place-items-center bg-black/70 p-4 backdrop-blur-sm animate-fade-in" (click)="closeDelete()">
        <div class="card w-full max-w-md bg-surface p-6 shadow-2xl animate-scale-in sm:p-8" role="alertdialog" aria-modal="true" aria-labelledby="delete-title" (click)="$event.stopPropagation()">
          <span class="grid size-12 place-items-center rounded-2xl bg-danger/10 text-danger ring-1 ring-danger/25"><app-icon name="alert" class="size-6" /></span>
          <h2 id="delete-title" class="mt-5 font-display text-xl font-bold">Delete your account?</h2>
          <p class="mt-2 text-sm text-muted">Your watchlist, reviews and profile will be permanently deleted. Enter your password to confirm.</p>
          <form class="mt-6 space-y-4" (submit)="deleteAccount($event, password.value)">
            <label class="sr-only" for="delete-password">Password</label>
            <input #password id="delete-password" type="password" class="input" placeholder="Your password" autocomplete="current-password" required />
            @if (deleteError(); as msg) {
              <p class="text-xs text-danger">{{ msg }}</p>
            }
            <div class="flex justify-end gap-2">
              <button type="button" class="btn btn-ghost" (click)="closeDelete()">Cancel</button>
              <button type="submit" class="btn bg-danger text-white hover:brightness-110" [disabled]="deleting()">
                {{ deleting() ? 'Deleting…' : 'Delete forever' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    }
  `,
})
export class SettingsPage {
  protected readonly auth = inject(AuthService);
  private readonly profile = inject(ProfileService);
  private readonly toast = inject(ToastService);

  protected readonly changing = signal(false);
  protected readonly deleteOpen = signal(false);
  protected readonly deleting = signal(false);
  protected readonly deleteError = signal<string | null>(null);

  protected readonly passwordForm = inject(FormBuilder).nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), Validators.pattern(PASSWORD_PATTERN)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  private readonly newPassword = toSignal(this.passwordForm.controls.newPassword.valueChanges, { initialValue: '' });
  protected readonly strength = computed(() => passwordScore(this.newPassword()));
  protected readonly strengthColor = computed(() => STRENGTH_COLORS[this.strength()]);

  protected error(field: 'currentPassword' | 'newPassword' | 'confirmPassword'): string | null {
    const control = this.passwordForm.controls[field];
    const mismatch = field === 'confirmPassword' && this.passwordForm.hasError('passwordMismatch') && control.touched;
    if (!(control.invalid && (control.touched || control.dirty)) && !mismatch) return null;
    const e = control.errors ?? {};
    if (e['server']) return e['server'];
    if (e['required']) return 'This field is required';
    if (e['minlength']) return 'At least 8 characters';
    if (e['maxlength']) return 'At most 72 characters';
    if (e['pattern']) return 'Use at least one letter and one digit';
    return mismatch ? 'Passwords do not match' : null;
  }

  protected changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    this.changing.set(true);
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.profile.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.changing.set(false);
        this.passwordForm.reset();
        this.toast.success('Password updated. Other devices have been signed out.');
      },
      error: (e: unknown) => {
        this.changing.set(false);
        const message = errorMessage(e, 'Could not change your password');
        if (message.toLowerCase().includes('current password')) {
          this.passwordForm.controls.currentPassword.setErrors({ server: message });
        } else {
          this.toast.error(message);
        }
      },
    });
  }

  protected closeDelete(): void {
    if (this.deleting()) return;
    this.deleteOpen.set(false);
    this.deleteError.set(null);
  }

  protected deleteAccount(event: Event, password: string): void {
    event.preventDefault();
    if (!password) {
      this.deleteError.set('Please enter your password');
      return;
    }
    this.deleting.set(true);
    this.deleteError.set(null);
    this.profile.deleteAccount(password).subscribe({
      next: () => this.toast.info('Your account has been deleted. Sorry to see you go!'),
      error: (e: unknown) => {
        this.deleting.set(false);
        this.deleteError.set(errorMessage(e, 'Could not delete your account'));
      },
    });
  }
}
