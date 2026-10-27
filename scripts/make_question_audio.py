"""Generate unchanged-model source clips for check_question_audio.py on Windows."""
import array
import ctypes as c
import time
from benchmark_fast_voice import open_engine
from bootstrap import ROOT


def main():
    folder = ROOT / '.research/prosody'
    folder.mkdir(parents=True, exist_ok=True)
    lib, tts, Generation, _, directory = open_engine()
    try:
        for name, text in [('ready', 'Are you ready?'), ('tea', 'Do you want some tea?'), ('now', 'Can we go now?'),
                           ('mountains', 'Do you like mountains?')]:
            config = Generation()
            config.sid = 0; config.speed = 1; config.silence_scale = .15
            started = time.perf_counter()
            audio = lib.SherpaOnnxOfflineTtsGenerateWithConfig(tts, text.encode(), c.byref(config), None, None)
            if not audio:
                raise RuntimeError('No audio generated')
            try:
                a = audio.contents
                assert a.sample_rate == 24000 and a.n > 0
                samples = array.array('f', a.samples[:a.n])
                (folder / (name + '.f32')).write_bytes(samples.tobytes())
                print(name, 'generated in', round(time.perf_counter()-started, 3), 'seconds', flush=True)
            finally:
                lib.SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio)
    finally:
        lib.SherpaOnnxDestroyOfflineTts(tts)
        directory.close()


if __name__ == '__main__':
    main()
