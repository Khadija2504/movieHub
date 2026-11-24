import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, computed, effect, inject, input, numberAttribute, signal, viewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import type Hls from 'hls.js';
import { firstValueFrom } from 'rxjs';

import { errorMessage } from '../../../core/http/api-error.util';
import { StreamSession, StreamSource } from '../../../core/models/streaming';
import { API_ORIGIN, StreamingService } from '../../../core/services/streaming.service';
import { Icon } from '../../../shared/components/icon/icon';
import { VIA_LABELS, clock, qualityLabel, timeLeft } from '../streaming-format';

type State = 'loading' | 'ready' | 'denied' | 'unavailable' | 'error';

interface QualityOption {
  label: string;
  /** HLS: level index (-1 = automatic); MP4: index in mp4Sources. */
  value: number;
}

/** How often the position is saved while playing. */
const SAVE_EVERY_MS = 10_000;
/** Below this, opening a movie by mistake does not create a history entry. */
const MIN_SAVED_POSITION = 5;

@Component({
  selector: 'app-player-page',
  imports: [RouterLink, Icon],
  host: { class: 'block bg-black' },
  template: `
    <div class="flex h-dvh flex-col bg-black text-white">
      <!-- Top bar -->
      <header class="flex items-center gap-3 border-b border-white/10 bg-black/80 px-3 py-2.5 sm:px-5">
        <a [routerLink]="['/movies', id()]" class="btn btn-ghost btn-sm shrink-0 text-white/80 hover:text-white" aria-label="Back to the movie">
          <app-icon name="chevron-left" class="size-5" /> <span class="hidden sm:inline">Back</span>
        </a>
        <div class="min-w-0 flex-1">
          <h1 class="truncate font-display text-base font-semibold sm:text-lg">{{ session()?.title ?? 'Loading…' }}</h1>
          @if (session(); as s) {
            <p class="truncate text-xs text-white/55">
              {{ via() }}
              @if (s.rentalEndsAt) {
                · <span [class.text-warning]="rentalSoon()">{{ rentalLeft() }}</span>
              }
              @if (s.maxQuality) {
                · max {{ maxQualityLabel() }} on your plan ·
                <a routerLink="/plans" class="font-semibold text-white/80 underline underline-offset-2 hover:text-white">Upgrade</a>
              }
            </p>
          }
        </div>
        @if (qualities().length > 1) {
          <label class="flex shrink-0 items-center gap-2 text-xs text-white/60">
            <app-icon name="settings" class="size-4" />
            <span class="sr-only sm:not-sr-only">Quality</span>
            <select
              class="rounded-lg border border-white/15 bg-white/10 px-2 py-1.5 text-sm text-white outline-none focus-visible:ring-2 focus-visible:ring-primary"
              [value]="quality()"
              (change)="setQuality(+$any($event.target).value)"
              aria-label="Video quality"
            >
              @for (q of qualities(); track q.value) {
                <option [value]="q.value" class="bg-neutral-900">{{ q.label }}{{ q.value === -1 && autoLevel() ? ' (' + autoLevel() + ')' : '' }}</option>
              }
            </select>
          </label>
        }
      </header>

      <!-- Video -->
      <div class="relative flex min-h-0 flex-1 items-center justify-center">
        @switch (state()) {
          @case ('loading') {
            <span class="size-12 animate-spin rounded-full border-4 border-white/15 border-t-primary" aria-label="Loading"></span>
          }
          @case ('denied') {
            <div class="max-w-md px-6 text-center">
              <span class="mx-auto grid size-16 place-items-center rounded-2xl bg-primary/15 text-primary ring-1 ring-primary/30"><app-icon name="lock" class="size-7" /></span>
              <h2 class="mt-5 font-display text-2xl font-bold">This movie isn't in your library</h2>
              <p class="mt-2 text-white/65">{{ message() }}</p>
              <div class="mt-7 flex flex-wrap justify-center gap-3">
                <a [routerLink]="['/movies', id()]" class="btn btn-primary">Rent or buy</a>
                <a routerLink="/plans" class="btn btn-secondary">See plans</a>
              </div>
            </div>
          }
          @case ('unavailable') {
            <div class="max-w-md px-6 text-center">
              <h2 class="font-display text-2xl font-bold">Not available to stream</h2>
              <p class="mt-2 text-white/65">{{ message() }}</p>
              <a [routerLink]="['/movies', id()]" class="btn btn-secondary mt-7">Back to the movie</a>
            </div>
          }
          @case ('error') {
            <div class="max-w-md px-6 text-center">
              <span class="mx-auto grid size-16 place-items-center rounded-2xl bg-danger/15 text-danger ring-1 ring-danger/30"><app-icon name="alert" class="size-7" /></span>
              <h2 class="mt-5 font-display text-2xl font-bold">Playback problem</h2>
              <p class="mt-2 text-white/65">{{ message() }}</p>
              <button type="button" class="btn btn-primary mt-7" (click)="retry()"><app-icon name="refresh" class="size-4" /> Try again</button>
            </div>
          }
        }
        <video
          #video
          class="h-full w-full bg-black object-contain"
          [class.hidden]="state() !== 'ready'"
          controls
          playsinline
          preload="metadata"
          [attr.poster]="session()?.backdropUrl"
          (pause)="saveNow()"
          (ended)="saveNow()"
          (error)="onVideoError()"
          (playing)="buffering.set(false)"
          (waiting)="buffering.set(true)"
        ></video>

        @if (buffering() && state() === 'ready') {
          <span class="pointer-events-none absolute size-12 animate-spin rounded-full border-4 border-white/15 border-t-primary" aria-hidden="true"></span>
        }

        @if (resumeNotice(); as at) {
          <div class="absolute bottom-20 left-1/2 flex -translate-x-1/2 items-center gap-3 rounded-full bg-black/80 py-2 pr-2 pl-4 text-sm shadow-xl ring-1 ring-white/15 backdrop-blur animate-fade-up" role="status">
            Resumed at {{ at }}
            <button type="button" class="rounded-full bg-white/15 px-3 py-1 font-semibold hover:bg-white/25" (click)="startOver()">Start over</button>
          </div>
        }
      </div>

      @if (session()?.credit; as credit) {
        <p class="truncate border-t border-white/10 px-4 py-2 text-center text-[0.7rem] text-white/45">{{ credit }}</p>
      }
    </div>
  `,
})
export class PlayerPage {
  private readonly streaming = inject(StreamingService);

  /** Route param :id (TMDB id). */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly state = signal<State>('loading');
  protected readonly message = signal('');
  protected readonly session = signal<StreamSession | null>(null);
  protected readonly qualities = signal<QualityOption[]>([]);
  protected readonly quality = signal(-1);
  /** Height picked by automatic HLS quality ("720p"). */
  protected readonly autoLevel = signal<string | null>(null);
  protected readonly resumeNotice = signal<string | null>(null);
  protected readonly buffering = signal(false);
  private readonly now = signal(Date.now());

  protected readonly via = computed(() => {
    const via = this.session()?.via;
    return via ? VIA_LABELS[via] : '';
  });
  protected readonly rentalLeft = computed(() => timeLeft(this.session()?.rentalEndsAt ?? null, this.now()));
  protected readonly rentalSoon = computed(() => {
    const end = this.session()?.rentalEndsAt;
    return !!end && new Date(end).getTime() - this.now() < 3 * 3600_000;
  });
  protected readonly maxQualityLabel = computed(() => qualityLabel(this.session()?.maxQuality ?? 0));

  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  private hls: Hls | null = null;
  private mp4Sources: StreamSource[] = [];
  private noticeTimer: ReturnType<typeof setTimeout> | undefined;
  /** Position to restore after reloading the stream (expired links, MP4 quality change). */
  private restoreAt: number | null = null;
  private refreshedOnce = false;
  private destroyed = false;

  constructor() {
    const title = inject(Title);
    effect(() => {
      const s = this.session();
      if (s) title.setTitle(`${s.title} · Watch · MovieHub`);
    });

    effect(() => void this.load(this.id()));

    const ticker = setInterval(() => this.now.set(Date.now()), 30_000);
    const saver = setInterval(() => {
      const v = this.videoEl();
      if (v && !v.paused) this.saveNow();
    }, SAVE_EVERY_MS);
    const onPageHide = () => this.saveOnExit();
    window.addEventListener('pagehide', onPageHide);

    inject(DestroyRef).onDestroy(() => {
      this.saveNow();
      this.destroyed = true;
      clearInterval(ticker);
      clearInterval(saver);
      clearTimeout(this.noticeTimer);
      window.removeEventListener('pagehide', onPageHide);
      this.hls?.destroy();
    });
  }

  private videoEl(): HTMLVideoElement | null {
    return this.video()?.nativeElement ?? null;
  }

  private async load(id: number): Promise<void> {
    this.state.set('loading');
    try {
      const session = await firstValueFrom(this.streaming.session(id));
      if (this.destroyed) return;
      this.session.set(session);
      if (!session.sources.length) {
        this.fail('unavailable', 'No video is attached to this movie yet.');
        return;
      }
      this.state.set('ready');
      await this.attach(session, this.restoreAt ?? session.resumeAt);
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 403) {
        this.fail('denied', errorMessage(e));
      } else if (e instanceof HttpErrorResponse && e.status === 404) {
        this.fail('unavailable', errorMessage(e));
      } else {
        this.fail('error', errorMessage(e));
      }
    }
  }

  /** Plays the session: HLS when the browser can, otherwise the MP4 files. */
  private async attach(session: StreamSession, startAt: number): Promise<void> {
    const video = this.videoEl();
    if (!video) return;
    this.hls?.destroy();
    this.hls = null;
    const hlsSource = session.sources.find((s) => s.type === 'HLS');
    this.mp4Sources = session.sources.filter((s) => s.type === 'MP4');

    if (hlsSource) {
      const { default: HlsJs } = await import('hls.js');
      if (this.destroyed) return;
      if (HlsJs.isSupported()) {
        this.playHls(HlsJs, video, playUrl(hlsSource), startAt);
        this.afterStart(startAt);
        return;
      }
      if (video.canPlayType('application/vnd.apple.mpegurl')) {
        // Safari / iOS play HLS natively (automatic quality only).
        this.qualities.set([]);
        this.setSource(video, playUrl(hlsSource), startAt, true);
        this.afterStart(startAt);
        return;
      }
    }
    if (!this.mp4Sources.length) {
      this.fail('error', 'Your browser cannot play this video.');
      return;
    }
    this.qualities.set(this.mp4Sources.map((s, i) => ({ label: qualityLabel(s.quality ?? 0), value: i })));
    const keep = this.mp4Sources.length > this.quality() && this.quality() >= 0 ? this.quality() : 0;
    this.quality.set(keep);
    this.setSource(video, playUrl(this.mp4Sources[keep]), startAt, true);
    this.afterStart(startAt);
  }

  private playHls(HlsJs: typeof Hls, video: HTMLVideoElement, url: string, startAt: number): void {
    const hls = new HlsJs({ startPosition: startAt > 0 ? startAt : -1 });
    this.hls = hls;
    hls.on(HlsJs.Events.MANIFEST_PARSED, (_event, data) => {
      const levels = data.levels
        .map((level, index) => ({ label: qualityLabel(level.height), value: index, height: level.height }))
        .sort((a, b) => b.height - a.height);
      this.qualities.set([{ label: 'Auto', value: -1 }, ...levels.map(({ label, value }) => ({ label, value }))]);
      this.quality.set(-1);
      void video.play().catch(() => undefined);
    });
    hls.on(HlsJs.Events.LEVEL_SWITCHED, (_event, data) => {
      const level = hls.levels[data.level];
      this.autoLevel.set(level ? qualityLabel(level.height) : null);
    });
    hls.on(HlsJs.Events.ERROR, (_event, data) => {
      if (!data.fatal) return;
      const status = data.response?.code;
      if (data.type === HlsJs.ErrorTypes.NETWORK_ERROR && status === 403) {
        this.refreshLinks();
      } else if (data.type === HlsJs.ErrorTypes.NETWORK_ERROR) {
        hls.startLoad();
      } else if (data.type === HlsJs.ErrorTypes.MEDIA_ERROR) {
        hls.recoverMediaError();
      } else {
        this.fail('error', 'The video could not be played. Please try again.');
      }
    });
    hls.loadSource(url);
    hls.attachMedia(video);
  }

  private setSource(video: HTMLVideoElement, url: string, startAt: number, play: boolean): void {
    video.addEventListener(
      'loadedmetadata',
      () => {
        if (startAt > 0 && startAt < video.duration) video.currentTime = startAt;
        if (play) void video.play().catch(() => undefined);
      },
      { once: true },
    );
    video.src = url;
    video.load();
  }

  /** Shows "Resumed at 12:34" when the movie does not start at the beginning. */
  private afterStart(startAt: number): void {
    this.restoreAt = null;
    if (startAt >= MIN_SAVED_POSITION && !this.refreshedOnce) {
      this.resumeNotice.set(clock(startAt));
      clearTimeout(this.noticeTimer);
      this.noticeTimer = setTimeout(() => this.resumeNotice.set(null), 8000);
    }
  }

  protected startOver(): void {
    const video = this.videoEl();
    if (video) video.currentTime = 0;
    this.resumeNotice.set(null);
    void video?.play().catch(() => undefined);
  }

  protected setQuality(value: number): void {
    this.quality.set(value);
    if (this.hls) {
      // -1 = automatic (adapts to the connection).
      this.hls.currentLevel = value;
      if (value === -1) this.autoLevel.set(null);
      return;
    }
    const video = this.videoEl();
    const source = this.mp4Sources[value];
    if (!video || !source) return;
    const wasPlaying = !video.paused;
    this.setSource(video, playUrl(source), video.currentTime, wasPlaying);
  }

  /** The play links ran out (or a network error): get new ones and continue where we were. */
  private refreshLinks(): void {
    const video = this.videoEl();
    this.restoreAt = video?.currentTime ?? null;
    this.refreshedOnce = true;
    void this.load(this.id());
  }

  protected onVideoError(): void {
    if (this.hls || this.state() !== 'ready') return;
    const expiresAt = this.session()?.expiresAt;
    if (expiresAt && new Date(expiresAt).getTime() - Date.now() < 60_000) {
      this.refreshLinks();
      return;
    }
    this.fail('error', 'The video could not be played. Check your connection and try again.');
  }

  protected retry(): void {
    const video = this.videoEl();
    this.restoreAt = video && video.currentTime > 0 ? video.currentTime : null;
    void this.load(this.id());
  }

  private fail(state: State, message: string): void {
    this.state.set(state);
    this.message.set(message);
    this.hls?.destroy();
    this.hls = null;
  }

  // ---------------------------------------------------------------- progress

  private position(): { position: number; duration: number } | null {
    const video = this.videoEl();
    const session = this.session();
    if (!video || !session || this.state() !== 'ready') return null;
    const duration = video.duration;
    if (!Number.isFinite(duration) || duration <= 0 || video.currentTime < MIN_SAVED_POSITION) return null;
    return { position: video.currentTime, duration };
  }

  protected saveNow(): void {
    const p = this.position();
    if (p) this.streaming.saveProgress(this.id(), p.position, p.duration).subscribe({ error: () => undefined });
  }

  private saveOnExit(): void {
    const p = this.position();
    if (p) this.streaming.saveProgressOnExit(this.id(), p.position, p.duration);
  }
}

/** Absolute URL of a signed play path. */
function playUrl(source: StreamSource): string {
  return API_ORIGIN + source.url;
}
