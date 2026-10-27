#include "question_prosody.h"
#include "signalsmith-stretch.h"
#include <algorithm>
#include <cmath>
#include <vector>

namespace {
struct Pitch { int center; float hz; };

float median(std::vector<float> values) {
    if (values.empty()) return 0;
    auto middle = values.begin() + values.size()/2;
    std::nth_element(values.begin(), middle, values.end());
    return *middle;
}

// YIN-style normalized difference, at 8 kHz, over the final voiced phrase.
// Unvoiced/fricative frames are rejected; silence never determines the endpoint.
std::vector<Pitch> pitches(const float *audio, int count, int rate) {
    const int stride = rate/8000;
    const int frame = 400, maxLag = 80, minLag = 18;
    const int hop = rate/100;
    std::vector<Pitch> result;
    float input[frame + maxLag], difference[maxLag + 1];
    int first = std::max((frame + maxLag)*stride/2, count - rate*2);
    for (int center = first; center + (frame + maxLag)*stride/2 < count; center += hop) {
        int begin = center - (frame + maxLag)*stride/2;
        float energy = 0, mean = 0;
        for (int i = 0; i < frame + maxLag; ++i) {
            float v = 0;
            for (int j = 0; j < stride; ++j) v += audio[begin + i*stride + j];
            input[i] = v/stride; mean += input[i];
        }
        mean /= frame + maxLag;
        for (float &v : input) { v -= mean; energy += v*v; }
        if (energy/(frame + maxLag) < 0.000001f) continue;
        float cumulative = 0;
        difference[0] = 1;
        for (int lag = 1; lag <= maxLag; ++lag) {
            float sum = 0;
            for (int i = 0; i < frame; ++i) { float d = input[i] - input[i+lag]; sum += d*d; }
            cumulative += sum;
            difference[lag] = cumulative > 1e-12f ? sum*lag/cumulative : 1;
        }
        int best = 0;
        for (int lag = minLag; lag < maxLag; ++lag) {
            if (difference[lag] < 0.18f) {
                while (lag+1 < maxLag && difference[lag+1] < difference[lag]) ++lag;
                best = lag; break;
            }
        }
        if (!best) continue;
        float period = best;
        float left = difference[best-1], middle = difference[best], right = difference[best+1];
        float denominator = left - 2*middle + right;
        if (std::abs(denominator) > 1e-8f) period += std::clamp(0.5f*(left-right)/denominator, -0.5f, 0.5f);
        result.push_back({center, 8000.0f/period});
    }
    return result;
}

float smooth(float x) { x = std::clamp(x, 0.0f, 1.0f); return x*x*(3-2*x); }

float pitchAt(const std::vector<Pitch> &track, float time) {
    auto next = std::lower_bound(track.begin(), track.end(), time,
        [](const Pitch &p, float t) { return p.center < t; });
    if (next == track.begin()) return track.front().hz;
    if (next == track.end()) return track.back().hz;
    auto previous = next - 1;
    float mix = (time - previous->center)/(next->center - previous->center);
    return std::exp2(std::log2(previous->hz)*(1-mix) + std::log2(next->hz)*mix);
}
}

int ember_raise_question(float *audio, int count, int rate, float *diagnostics) {
    if (diagnostics) std::fill(diagnostics, diagnostics+6, 0.0f);
    if (!audio || rate != 24000 || count < rate/4 || count > rate*60) return 0;
    try {
        for (int i = 0; i < count; ++i) if (!std::isfinite(audio[i])) return -1;
        auto voiced = pitches(audio, count, rate);
        if (diagnostics) diagnostics[5] = float(voiced.size());
        if (voiced.size() < 8) return 0;
        int end = voiced.back().center;
        // A tiny uptick at the very end can still sound like a falling word.
        // Cover the final phrase and compensate its existing pitch contour.
        int start = std::max(voiced.front().center, end - int(rate*0.40f));
        for (const auto &p : voiced) if (p.center >= start) { start = p.center; break; }
        if (diagnostics) { diagnostics[0] = float(start)/rate; diagnostics[1] = float(end)/rate; }
        if (end - start < rate/12) return 0;
        std::vector<float> initial, final;
        for (const auto &p : voiced) {
            if (p.center >= start && p.center < start + rate*0.08f) initial.push_back(p.hz);
            if (p.center > end - rate/12) final.push_back(p.hz);
        }
        if (initial.size() < 3 || final.size() < 3) return 0;
        float base = median(initial), ending = median(final);
        float existingRise = 12*std::log2(ending/base);
        // Correct a flat/falling ending; leave an already clear rise alone.
        // Give a low ending more lift without driving a higher phrase into a
        // strained register. These bounds are for the app's fixed female voice.
        float targetRise = std::clamp(12*std::log2(285.0f/base), 4.0f, 5.5f);
        float added = std::clamp(targetRise - existingRise, 0.0f, 12.0f);
        if (diagnostics) { diagnostics[2] = existingRise; diagnostics[3] = added; }
        if (existingRise >= 3.6f) return 0;
        std::vector<Pitch> contour = voiced;
        for (size_t i = 0; i < voiced.size(); ++i) {
            std::vector<float> neighbors;
            for (size_t j = i > 2 ? i-2 : 0; j < std::min(voiced.size(), i+3); ++j)
                if (std::abs(voiced[j].center-voiced[i].center) <= rate/25) neighbors.push_back(voiced[j].hz);
            contour[i].hz = median(neighbors);
        }
        int fadeStart = std::max(0, start - rate/40);
        int regionStart = std::max(0, fadeStart - rate/5);
        int regionCount = count - regionStart;
        signalsmith::stretch::SignalsmithStretch<float> shift;
        shift.presetDefault(1, float(rate));
        shift.setFormantFactor(1.0f, true);
        shift.setFormantBase(base/rate);
        int inputLatency = shift.inputLatency(), outputLatency = shift.outputLatency();
        std::vector<float> input(regionCount + inputLatency, 0);
        std::copy(audio + regionStart, audio + count, input.begin());
        std::vector<float> output(regionCount + outputLatency, 0);
        const float *seek[] = {input.data()};
        shift.seek(seek, inputLatency, 1.0);
        for (int pos = 0; pos < regionCount;) {
            int size = std::min(128, regionCount - pos);
            float time = regionStart + pos + size*0.5f;
            float fraction = (time - start)/std::max(1, end-start);
            float target = base*std::exp2(targetRise*smooth(fraction)/12);
            float localPitch = pitchAt(contour, time);
            float correction = std::clamp(12*std::log2(target/localPitch), -3.0f, 12.0f);
            correction *= smooth((time-start)/(rate*0.06f));
            shift.setTransposeSemitones(correction, 4500.0f/rate);
            const float *in[] = {input.data() + inputLatency + pos};
            float *out[] = {output.data() + pos};
            shift.process(in, size, out, size);
            pos += size;
        }
        float *flush[] = {output.data() + regionCount};
        shift.flush(flush, outputLatency);
        for (int i = fadeStart; i < count; ++i) {
            float processed = output[i-regionStart + outputLatency];
            if (!std::isfinite(processed)) return -1;
        }
        // Crossfade only at the start of the corrected tail. Earlier words stay exact.
        for (int i = fadeStart; i < count; ++i) {
            float mix = smooth(float(i-fadeStart)/(rate/20));
            float processed = output[i-regionStart + outputLatency];
            audio[i] = std::clamp(audio[i]*(1-mix) + processed*mix, -1.0f, 1.0f);
        }
        if (diagnostics) {
            diagnostics[0] = float(start)/rate; diagnostics[1] = float(end)/rate;
            diagnostics[2] = existingRise; diagnostics[3] = added;
            diagnostics[4] = float(regionCount); diagnostics[5] = float(voiced.size());
        }
        return 1;
    } catch (...) { return -1; }
}
