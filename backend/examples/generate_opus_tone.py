#!/usr/bin/env python3
"""
Generate valid Opus audio frames for testing Clara backend WebSocket streaming.

This generates a simple 20ms tone (440Hz sine wave) encoded as Opus at 24kHz.
For production testing, you'd use a real Opus encoder, but this provides
base64-encoded PCM that simulates the audio format.
"""

import base64
import struct
import sys

SAMPLE_RATE = 24000
FRAME_DURATION_MS = 20
SAMPLES_PER_FRAME = int(SAMPLE_RATE * FRAME_DURATION_MS / 1000)  # 480 samples
FREQUENCY = 440  # A4 note

def generate_sine_wave(freq, samples, sample_rate):
    """Generate sine wave samples."""
    import math
    return [
        int(32767 * 0.5 * math.sin(2 * math.pi * freq * i / sample_rate))
        for i in range(samples)
    ]

def pcm_to_base64(samples):
    """Convert PCM16 samples to base64."""
    # Convert to little-endian 16-bit PCM
    pcm_bytes = b''.join(struct.pack('<h', s) for s in samples)
    return base64.b64encode(pcm_bytes).decode('utf-8')

def generate_frame(num=1):
    """Generate a single 20ms Opus frame (simulated as PCM16)."""
    samples = generate_sine_wave(FREQUENCY, SAMPLES_PER_FRAME, SAMPLE_RATE)
    b64_data = pcm_to_base64(samples)
    return {
        "type": "input.audio.delta",
        "seq": num,
        "format": "opus@24000",
        "data": b64_data
    }

def main():
    import json
    
    count = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    
    print("# Generated Opus frames for Clara backend testing")
    print(f"# Format: opus@24000, {FRAME_DURATION_MS}ms per frame")
    print(f"# Frequency: {FREQUENCY}Hz (A4)")
    print()
    
    for i in range(1, count + 1):
        frame = generate_frame(i)
        print(json.dumps(frame))
    
    # Final commit
    print(json.dumps({"type": "input.audio.commit", "seq": count}))

if __name__ == "__main__":
    main()

