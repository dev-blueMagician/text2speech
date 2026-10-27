"""Exercise the pinned C API/model on Windows. This is NOT an Android benchmark.

Requires the matching Windows shared release extracted into .research/sherpa-win.
Parses scalar C config fields from the pinned header to avoid a stale ctypes ABI.
"""
import array
import ctypes as c
import json
import math
import os
from pathlib import Path
import platform
import re
import time
import wave
from bootstrap import ROOT


def open_engine():
    folder = ROOT / '.research/sherpa-win'
    dll_directory = os.add_dll_directory(str(folder))
    lib = c.CDLL(str(folder / 'sherpa-onnx-c-api.dll'))
    header = (ROOT / '.deps/sherpa/sherpa-onnx/c-api/c-api.h').read_text(encoding='utf-8')
    header = re.sub(r'/\*.*?\*/|//[^\n]*', '', header, flags=re.S)
    definitions = dict(re.findall(r'typedef struct (\w+)\s*\{(.*?)\}\s*\1;', header, re.S))
    types = {'int32_t': c.c_int32, 'float': c.c_float, 'const char *': c.c_char_p,
             'const float *': c.POINTER(c.c_float)}

    def struct(name):
        if name in types:
            return types[name]
        result = type(name, (c.Structure,), {})
        fields = []
        for declaration in definitions[name].split(';'):
            declaration = ' '.join(declaration.split()).strip()
            if not declaration:
                continue
            match = re.fullmatch(r'(.+?)(\w+)', declaration)
            if not match:
                raise RuntimeError('Unsupported C field: ' + declaration)
            kind, field = match.groups()
            kind = kind.strip()
            fields.append((field, types[kind] if kind in types else struct(kind)))
        result._fields_ = fields
        types[name] = result
        return result

    Config = struct('SherpaOnnxOfflineTtsConfig')
    Generation = struct('SherpaOnnxGenerationConfig')
    Audio = struct('SherpaOnnxGeneratedAudio')
    lib.SherpaOnnxCreateOfflineTts.argtypes = [c.POINTER(Config)]
    lib.SherpaOnnxCreateOfflineTts.restype = c.c_void_p
    lib.SherpaOnnxOfflineTtsGenerateWithConfig.argtypes = [c.c_void_p, c.c_char_p, c.POINTER(Generation), c.c_void_p, c.c_void_p]
    lib.SherpaOnnxOfflineTtsGenerateWithConfig.restype = c.POINTER(Audio)
    lib.SherpaOnnxDestroyOfflineTtsGeneratedAudio.argtypes = [c.POINTER(Audio)]
    lib.SherpaOnnxDestroyOfflineTts.argtypes = [c.c_void_p]
    model = ROOT / '.models/fast'
    config = Config()
    config.model.kokoro.model = str(model / 'model.int8.onnx').encode()
    config.model.kokoro.voices = str(model / 'voices.bin').encode()
    config.model.kokoro.tokens = str(model / 'tokens.txt').encode()
    config.model.kokoro.data_dir = str(model / 'espeak-ng-data').encode()
    config.model.kokoro.length_scale = 1
    config.model.num_threads = 4
    config.model.provider = b'cpu'
    config.max_num_sentences = 1
    config.silence_scale = 0.15
    started = time.perf_counter()
    tts = lib.SherpaOnnxCreateOfflineTts(c.byref(config))
    load = time.perf_counter() - started
    if not tts:
        raise RuntimeError('Could not load the model')
    return lib, tts, Generation, load, dll_directory


def main():
    lib, tts, Generation, load, dll_directory = open_engine()
    results = {'platform': platform.platform(), 'processor': platform.processor(),
               'runtime': 'sherpa-onnx 1.12.36 Windows x64 CPU', 'threads': 4,
               'model': 'kokoro-int8-en-v0_19', 'load_seconds': load, 'utterances': []}
    try:
        for index, text in enumerate(["I'm glad you're here.", "I'm glad you're here.",
                'The appointment is on October 25, at 10:30 a.m. Please bring your notes.',
                'You finally came. That was just great. I did not expect you to arrive so early.']):
            generation = Generation()
            generation.sid = 0
            generation.speed = 1
            generation.silence_scale = 0.15
            started = time.perf_counter()
            audio = lib.SherpaOnnxOfflineTtsGenerateWithConfig(tts, text.encode('utf-8'), c.byref(generation), None, None)
            elapsed = time.perf_counter() - started
            if not audio:
                raise RuntimeError('No audio returned')
            try:
                a = audio.contents
                samples = list(a.samples[:a.n])
                assert a.sample_rate == 24000 and a.n > 0 and all(math.isfinite(s) for s in samples)
                rms = math.sqrt(sum(s*s for s in samples) / len(samples))
                assert rms > 0.0001
                duration = a.n / a.sample_rate
                results['utterances'].append({'text': text, 'speaker': 0, 'generation_seconds': elapsed,
                    'audio_seconds': duration, 'real_time_factor': elapsed / duration, 'rms': rms})
                print(json.dumps(results['utterances'][-1]), flush=True)
                if index == 0:
                    pcm = array.array('h', (round(max(-1, min(1, s)) * 32767) for s in samples))
                    with wave.open(str(ROOT / 'dist/natural-reading-demo.wav'), 'wb') as output:
                        output.setnchannels(1); output.setsampwidth(2); output.setframerate(24000); output.writeframes(pcm.tobytes())
            finally:
                lib.SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio)
    finally:
        lib.SherpaOnnxDestroyOfflineTts(tts)
        dll_directory.close()
    (ROOT / 'dist/fast-voice-windows-benchmark.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    print('Model load: %.3f seconds' % load)


if __name__ == '__main__':
    main()
