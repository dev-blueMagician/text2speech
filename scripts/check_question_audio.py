"""Numerical DSP check on real Kokoro audio; no phone/listening claim.

QA-only dependencies: numpy, praat-parselmouth. Build question_prosody.cpp as
a Windows DLL at .research/prosody/ember_question.dll and supply ready/tea/now/
mountains raw mono float32 clips at 24 kHz using make_question_audio.py.
Uses the exact C++ processor compiled into JNI. --compare-dll optionally
compares a separate processor; current validation needs no legacy binary.
"""
import argparse
import ctypes as c
import json
from pathlib import Path
import time
import wave
import numpy as np
import parselmouth
from bootstrap import ROOT


def pitch_rise(samples, start, end, octave_cost=.08):
    # Fixed female voice. Creaky endings can produce subharmonic tracks;
    # record octave sensitivity separately rather than treating this as listening QA.
    pitch = parselmouth.Sound(samples, 24000).to_pitch_ac(time_step=.005, pitch_floor=100, pitch_ceiling=500, octave_cost=octave_cost)
    hz, times = pitch.selected_array['frequency'], pitch.xs()
    begin = hz[(times >= start) & (times < start + .08) & (hz > 0)]
    finish = hz[(times >= end - .08) & (times <= end) & (hz > 0)]
    assert len(begin) >= 3 and len(finish) >= 3, 'Not enough independently detected voiced frames'
    return float(12*np.log2(np.median(finish)/np.median(begin)))


def wave_file(path, samples):
    with wave.open(str(path), 'wb') as output:
        output.setnchannels(1); output.setsampwidth(2); output.setframerate(24000)
        output.writeframes((np.clip(samples, -1, 1)*32767).astype('<i2').tobytes())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compare-dll', type=Path, help='Optional baseline processor DLL')
    args = parser.parse_args()
    folder = ROOT / '.research/prosody'
    lib = c.CDLL(str(folder / 'ember_question.dll'))
    pointer = np.ctypeslib.ndpointer(dtype=np.float32, ndim=1, flags='C_CONTIGUOUS')
    lib.ember_raise_question.argtypes = [pointer, c.c_int, c.c_int, pointer]
    lib.ember_raise_question.restype = c.c_int
    results = []
    old_lib = None
    if args.compare_dll:
        old_lib = c.CDLL(str(args.compare_dll.resolve()))
        old_lib.ember_raise_question.argtypes = lib.ember_raise_question.argtypes
        old_lib.ember_raise_question.restype = c.c_int
    for name in ['ready', 'tea', 'now', 'mountains']:
        before = np.fromfile(folder / (name + '.f32'), dtype=np.float32)
        after = before.copy()
        diagnostic = np.zeros(6, dtype=np.float32)
        started = time.perf_counter()
        status = lib.ember_raise_question(after, len(after), 24000, diagnostic)
        milliseconds = (time.perf_counter()-started)*1000
        assert status == 1, (name, status, diagnostic.tolist())
        start, end = map(float, diagnostic[:2])
        assert end - start >= .30, (name, 'Correction must cover the ending, not just a tiny uptick')
        unchanged = max(0, int(start*24000) - 600 - 1)
        assert np.array_equal(before[:unchanged], after[:unchanged])
        assert len(before) == len(after) and np.isfinite(after).all() and np.abs(after).max() <= 1
        rise_before = pitch_rise(before, start, end)
        rise_after = pitch_rise(after, start, end)
        try:
            default_rise = pitch_rise(after, start, end, .01)
        except AssertionError:
            default_rise = None
        previous = before.copy()
        if old_lib:
            old_lib.ember_raise_question(previous, len(previous), 24000, np.zeros(6, dtype=np.float32))
        rise_previous = pitch_rise(previous, start, end)
        record = {'clip': name, 'correction_ms_windows': milliseconds, 'duration_seconds': len(after)/24000,
                  'tail_start_seconds': start, 'tail_end_seconds': end,
                  'rise_before_semitones': rise_before, 'rise_after_semitones': rise_after,
                  'comparison': 'supplied_processor' if old_lib else 'raw_model',
                  'rise_comparison_same_span_semitones': rise_previous,
                  'rise_after_default_octave_cost_semitones': default_rise,
                  'endpoint_correction_semitones': float(diagnostic[3]), 'prefix_bit_exact': True}
        print(json.dumps(record), flush=True)
        results.append(record)
        wave_file(folder / (name + '-after.wav'), after)
        if name == 'mountains':
            wave_file(ROOT / 'dist/question-current-before.wav', previous)
            wave_file(ROOT / 'dist/question-current-after.wav', after)
            wave_file(ROOT / 'dist/question-current-comparison.wav', np.concatenate([previous, np.zeros(14400), after]))
        assert rise_after > 2, record
        if not old_lib and name in ('ready', 'now'):
            assert rise_after > rise_previous + 2, record
        if not old_lib and name == 'mountains':
            assert rise_after > rise_previous + .5, record
    for name, signal in [('silence', np.zeros(24000, dtype=np.float32)),
                         ('noise', np.random.default_rng(0).normal(0, .03, 24000).astype(np.float32))]:
        original = signal.copy()
        diagnostic = np.zeros(6, dtype=np.float32)
        status = lib.ember_raise_question(signal, len(signal), 24000, diagnostic)
        assert status == 0 and np.array_equal(original, signal), name
    # An existing strong rise should not be exaggerated again.
    time_axis = np.arange(24000)/24000
    contour = 180*2**(8*np.clip((time_axis-.65)/.3, 0, 1)/12)
    phase = 2*np.pi*np.cumsum(contour)/24000
    rising = (.08*np.sin(phase) + .04*np.sin(2*phase)).astype(np.float32)
    original = rising.copy()
    diagnostic = np.zeros(6, dtype=np.float32)
    assert lib.ember_raise_question(rising, len(rising), 24000, diagnostic) == 0
    assert np.array_equal(original, rising)
    # Known falling harmonic source: the processor must reverse its whole tail.
    contour = 220*2**(-5*np.clip((time_axis-.55)/.4, 0, 1)/12)
    phase = 2*np.pi*np.cumsum(contour)/24000
    falling = (.08*np.sin(phase) + .04*np.sin(2*phase)).astype(np.float32)
    diagnostic = np.zeros(6, dtype=np.float32)
    assert lib.ember_raise_question(falling, len(falling), 24000, diagnostic) == 1
    assert pitch_rise(falling, float(diagnostic[0]), float(diagnostic[1])) > 2
    (ROOT / 'dist/question-current-validation.json').write_text(json.dumps(results, indent=2)+'\n', encoding='utf-8')
    print('Real-speech pitch rises, equal durations, exact prefixes, numeric bounds, and unvoiced guards passed.')


if __name__ == '__main__':
    main()
