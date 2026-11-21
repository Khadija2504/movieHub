import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage, toApiError } from '../../../core/http/api-error.util';
import { ProfileService } from '../../../core/services/profile.service';
import { ToastService } from '../../../core/ui/toast.service';
import { Avatar } from '../../../shared/components/avatar/avatar';
import { Icon } from '../../../shared/components/icon/icon';
import { PageHeader } from '../components/page-header';

const ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/webp'];
const MAX_BYTES = 2 * 1024 * 1024;

@Component({
  selector: 'app-profile-page',
  imports: [ReactiveFormsModule, DatePipe, Avatar, Icon, PageHeader],
  template: `
    <app-page-header heading="My Profile" icon="user" subheading="How you appear on MovieHub" />

    @if (auth.user(); as user) {
      <div class="space-y-6">
        <!-- Photo -->
        <section class="card p-6 sm:p-8">
          <h2 class="font-display text-lg font-semibold">Profile photo</h2>
          <p class="mt-1 text-sm text-muted">Shown next to your reviews. PNG, JPEG or WebP, up to 2 MB.</p>

          <div class="mt-6 flex flex-wrap items-center gap-6">
            <div class="relative">
              @if (preview(); as url) {
                <img [src]="url" alt="" class="size-24 rounded-full object-cover ring-4 ring-primary/30" />
              } @else {
                <app-avatar class="size-24 ring-4 ring-border" [name]="user.firstName + ' ' + user.lastName" [url]="user.avatarUrl" />
              }
              @if (uploading()) {
                <span class="absolute inset-0 grid place-items-center rounded-full bg-black/60">
                  <span class="size-6 animate-spin rounded-full border-2 border-white/30 border-t-white"></span>
                </span>
              }
            </div>
            <div class="flex flex-wrap gap-2">
              <label class="btn btn-secondary cursor-pointer" [class.pointer-events-none]="uploading()">
                <app-icon name="plus" class="size-4" /> {{ user.avatarUrl ? 'Change photo' : 'Upload photo' }}
                <input type="file" class="sr-only" [accept]="allowedTypes" (change)="onFile($event)" [disabled]="uploading()" />
              </label>
              @if (user.avatarUrl) {
                <button type="button" class="btn btn-ghost text-danger" [disabled]="uploading()" (click)="removePhoto()">Remove</button>
              }
            </div>
          </div>
        </section>

        <!-- Details -->
        <section class="card p-6 sm:p-8">
          <h2 class="font-display text-lg font-semibold">Personal information</h2>
          <form class="mt-6 grid gap-5 sm:grid-cols-2" [formGroup]="form" (ngSubmit)="save()" novalidate>
            <div>
              <label class="label" for="firstName">First name</label>
              <input id="firstName" class="input" formControlName="firstName" autocomplete="given-name"
                [class.!border-danger]="invalid('firstName')" [attr.aria-invalid]="invalid('firstName')" />
              @if (invalid('firstName')) {
                <p class="mt-1.5 text-xs text-danger">{{ fieldError('firstName') }}</p>
              }
            </div>
            <div>
              <label class="label" for="lastName">Last name</label>
              <input id="lastName" class="input" formControlName="lastName" autocomplete="family-name"
                [class.!border-danger]="invalid('lastName')" [attr.aria-invalid]="invalid('lastName')" />
              @if (invalid('lastName')) {
                <p class="mt-1.5 text-xs text-danger">{{ fieldError('lastName') }}</p>
              }
            </div>
            <div class="sm:col-span-2">
              <label class="label" for="email">Email</label>
              <div class="relative">
                <app-icon name="lock" class="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-subtle" />
                <input id="email" class="input pl-11 text-muted" [value]="user.email" disabled />
              </div>
              <p class="mt-1.5 text-xs text-subtle">Your email is used to sign in and can't be changed.</p>
            </div>
            <div class="flex flex-wrap items-center gap-3 sm:col-span-2">
              <p class="text-xs text-subtle">Member since {{ user.createdAt | date: 'longDate' }}</p>
              <div class="ml-auto flex gap-2">
                <button type="button" class="btn btn-ghost" [disabled]="form.pristine || saving()" (click)="reset()">Discard</button>
                <button type="submit" class="btn btn-primary" [disabled]="form.pristine || saving()">
                  {{ saving() ? 'Saving…' : 'Save changes' }}
                </button>
              </div>
            </div>
          </form>
        </section>
      </div>
    }
  `,
})
export class ProfilePage {
  protected readonly auth = inject(AuthService);
  private readonly profile = inject(ProfileService);
  private readonly toast = inject(ToastService);

  protected readonly allowedTypes = ALLOWED_TYPES.join(',');
  protected readonly saving = signal(false);
  protected readonly uploading = signal(false);
  protected readonly preview = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
  });

  constructor() {
    this.reset();
  }

  protected invalid(field: 'firstName' | 'lastName'): boolean {
    const c = this.form.controls[field];
    return c.invalid && (c.touched || c.dirty);
  }

  protected fieldError(field: 'firstName' | 'lastName'): string {
    const e = this.form.controls[field].errors ?? {};
    if (e['server']) return e['server'];
    if (e['required']) return 'This field is required';
    return 'At most 100 characters';
  }

  protected reset(): void {
    const user = this.auth.user();
    this.form.reset({ firstName: user?.firstName ?? '', lastName: user?.lastName ?? '' });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const { firstName, lastName } = this.form.getRawValue();
    this.profile.updateName(firstName.trim(), lastName.trim()).subscribe({
      next: () => {
        this.saving.set(false);
        this.reset();
        this.toast.success('Profile updated');
      },
      error: (e: unknown) => {
        this.saving.set(false);
        for (const [field, message] of Object.entries(toApiError(e)?.fieldErrors ?? {})) {
          this.form.get(field)?.setErrors({ server: message });
        }
        this.toast.error(errorMessage(e, 'Could not update your profile'));
      },
    });
  }

  protected onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // allow choosing the same file again
    if (!file) return;
    if (!ALLOWED_TYPES.includes(file.type)) {
      this.toast.error('Please choose a PNG, JPEG or WebP image');
      return;
    }
    if (file.size > MAX_BYTES) {
      this.toast.error('Image must be 2 MB or smaller');
      return;
    }

    const url = URL.createObjectURL(file);
    this.preview.set(url);
    this.uploading.set(true);
    this.profile.uploadAvatar(file).subscribe({
      next: () => this.finishUpload(url, 'Profile photo updated'),
      error: (e: unknown) => {
        this.finishUpload(url);
        this.toast.error(errorMessage(e, 'Could not upload the photo'));
      },
    });
  }

  protected removePhoto(): void {
    this.uploading.set(true);
    this.profile.removeAvatar().subscribe({
      next: () => {
        this.uploading.set(false);
        this.toast.success('Profile photo removed');
      },
      error: (e: unknown) => {
        this.uploading.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  private finishUpload(previewUrl: string, message?: string): void {
    this.uploading.set(false);
    this.preview.set(null);
    URL.revokeObjectURL(previewUrl);
    if (message) this.toast.success(message);
  }
}
