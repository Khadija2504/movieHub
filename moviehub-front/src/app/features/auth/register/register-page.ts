import { Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage, toApiError } from '../../../core/http/api-error.util';
import { Icon } from '../../../shared/components/icon/icon';
import { AuthShell } from '../auth-shell';
import { safeReturnUrl } from '../return-url';

type Field = 'firstName' | 'lastName' | 'email' | 'password' | 'confirmPassword';

/** Same rule as the backend RegisterRequest: at least one letter and one digit. */
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).+$/;

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return password && confirm && password !== confirm ? { passwordMismatch: true } : null;
}

const STRENGTH_LEVELS: Record<number, { label: string; color: string }> = {
  0: { label: '', color: '' },
  1: { label: 'Weak', color: 'bg-danger' },
  2: { label: 'Fair', color: 'bg-warning' },
  3: { label: 'Good', color: 'bg-ember' },
  4: { label: 'Strong', color: 'bg-success' },
};

/** 0-4: length, letters + digits, mixed case, symbols. */
export function passwordScore(password: string): number {
  if (!password) return 0;
  let score = 0;
  if (password.length >= 8) score++;
  if (/[A-Za-z]/.test(password) && /\d/.test(password)) score++;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++;
  if (/[^A-Za-z0-9]/.test(password) || password.length >= 14) score++;
  return Math.max(1, score);
}

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink, AuthShell, Icon],
  templateUrl: './register-page.html',
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly returnUrl = input<string>();

  protected readonly submitting = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      firstName: ['', [Validators.required, Validators.maxLength(100)]],
      lastName: ['', [Validators.required, Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(8),
          Validators.maxLength(72),
          Validators.pattern(PASSWORD_PATTERN),
        ],
      ],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  protected readonly showPassword = signal(false);

  private readonly password = toSignal(this.form.controls.password.valueChanges, { initialValue: '' });

  /** Visual hint only: the real rules are the validators above (and the backend). */
  protected readonly strength = computed(() => {
    const score = passwordScore(this.password());
    return { score, ...STRENGTH_LEVELS[score] };
  });

  /** First error message for a field, or null when it is valid / untouched. */
  protected error(field: Field): string | null {
    const control = this.form.controls[field];
    const showGroupError =
      field === 'confirmPassword' && this.form.hasError('passwordMismatch') && control.touched;

    if (!(control.invalid && (control.touched || control.dirty)) && !showGroupError) {
      return null;
    }
    const e = control.errors ?? {};
    if (e['server']) return e['server'];
    if (e['required']) return 'This field is required';
    if (e['email']) return 'Enter a valid email';
    if (e['minlength']) return `At least ${e['minlength'].requiredLength} characters`;
    if (e['maxlength']) return `At most ${e['maxlength'].requiredLength} characters`;
    if (e['pattern']) return 'Use at least one letter and one digit';
    if (showGroupError) return 'Passwords do not match';
    return null;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.serverError.set(null);

    const { confirmPassword: _, ...request } = this.form.getRawValue();
    this.auth.register(request).subscribe({
      next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl()) ?? '/account'),
      error: (error: unknown) => {
        this.submitting.set(false);
        const apiError = toApiError(error);
        // Show backend validation errors next to their fields.
        if (apiError?.fieldErrors) {
          for (const [field, message] of Object.entries(apiError.fieldErrors)) {
            this.form.get(field)?.setErrors({ server: message });
          }
        }
        this.serverError.set(errorMessage(error));
      },
    });
  }
}
