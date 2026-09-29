import { advance, clampTime, nextSpeed, stepTime, type Timeline } from "@/logic/timeline";

export interface ClockState {
  time: number;
  playing: boolean;
  speed: number;
}

export interface ReplayClock {
  getSnapshot: () => ClockState;
  subscribe: (listener: () => void) => () => void;
  play: () => void;
  pause: () => void;
  toggle: () => void;
  seek: (time: number) => void;
  step: (delta: 1 | -1) => void;
  changeSpeed: (delta: 1 | -1) => void;
  setSpeed: (speed: number) => void;
  dispose: () => void;
}

export function createClock(timeline: Timeline, initialTime = timeline.start): ReplayClock {
  let state: ClockState = { time: clampTime(timeline, initialTime), playing: false, speed: 1 };
  const listeners = new Set<() => void>();
  let frame = 0;
  let last = 0;

  const emit = (next: Partial<ClockState>) => {
    state = { ...state, ...next };
    listeners.forEach((listener) => listener());
  };

  const tick = (now: number) => {
    const elapsed = last ? Math.min(250, now - last) : 0;
    last = now;
    const result = advance(timeline, state.time, elapsed, state.speed);
    if (result.finished) {
      frame = 0;
      last = 0;
      emit({ time: result.time, playing: false });
      return;
    }
    emit({ time: result.time });
    frame = requestAnimationFrame(tick);
  };

  const stopLoop = () => {
    if (frame) cancelAnimationFrame(frame);
    frame = 0;
    last = 0;
  };

  const play = () => {
    if (state.playing) return;
    const time = state.time >= timeline.end ? timeline.start : state.time;
    emit({ playing: true, time });
    frame = requestAnimationFrame(tick);
  };

  const pause = () => {
    stopLoop();
    if (state.playing) emit({ playing: false });
  };

  return {
    getSnapshot: () => state,
    subscribe: (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    play,
    pause,
    toggle: () => (state.playing ? pause() : play()),
    seek: (time) => emit({ time: clampTime(timeline, time) }),
    step: (delta) => {
      pause();
      emit({ time: clampTime(timeline, stepTime(timeline, state.time, delta)) });
    },
    changeSpeed: (delta) => emit({ speed: nextSpeed(state.speed, delta) }),
    setSpeed: (speed) => emit({ speed }),
    dispose: () => {
      stopLoop();
      listeners.clear();
    },
  };
}
