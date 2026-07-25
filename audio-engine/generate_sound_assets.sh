#!/usr/bin/env bash
set -euo pipefail

ffmpeg_bin="${FFMPEG:-ffmpeg}"
output_dir="${1:-../app/src/main/assets/audio}"
common=(-hide_banner -loglevel error -ac 1 -ar 48000 -c:a libvorbis -q:a 4
  -metadata license=CC0-1.0 -metadata source="Noise Shield generated" -y)

seam="[source]asplit=3[body0][tail0][head0];[body0]atrim=start=0.75:end=30,asetpts=PTS-STARTPTS[body];[tail0]atrim=start=30:end=30.75,asetpts=PTS-STARTPTS[tail];[head0]atrim=start=0:end=0.75,asetpts=PTS-STARTPTS[head];[tail][head]acrossfade=d=0.75:c1=tri:c2=tri[seam];[body][seam]concat=n=2:v=0:a=1,loudnorm=I=-23:LRA=7:TP=-3,alimiter=limit=0.707:level=false[out]"

"$ffmpeg_bin" -f lavfi \
  -i "anoisesrc=color=brown:amplitude=0.8:sample_rate=48000:duration=30.75:seed=101,lowpass=f=700,tremolo=f=0.1:d=0.65" \
  -filter_complex "[0:a]anull[source];$seam" -map "[out]" \
  "${common[@]}" "$output_dir/ocean_waves.ogg"

"$ffmpeg_bin" -f lavfi \
  -i "anoisesrc=color=white:amplitude=0.65:sample_rate=48000:duration=30.75:seed=102,highpass=f=1800,lowpass=f=12000" \
  -filter_complex "[0:a]anull[source];$seam" -map "[out]" \
  "${common[@]}" "$output_dir/rain.ogg"

"$ffmpeg_bin" \
  -f lavfi -i "anoisesrc=color=pink:amplitude=0.45:sample_rate=48000:duration=30.75:seed=103" \
  -f lavfi -i "sine=frequency=120:sample_rate=48000:duration=30.75" \
  -f lavfi -i "sine=frequency=22:sample_rate=48000:duration=30.75" \
  -filter_complex "[0:a][1:a][2:a]amix=inputs=3:weights='0.60 0.22 0.12':normalize=1[source];$seam" \
  -map "[out]" "${common[@]}" "$output_dir/fan.ogg"

"$ffmpeg_bin" \
  -f lavfi -i "anoisesrc=color=brown:amplitude=0.45:sample_rate=48000:duration=30.75:seed=104" \
  -f lavfi -i "sine=frequency=60:sample_rate=48000:duration=30.75" \
  -f lavfi -i "sine=frequency=120:sample_rate=48000:duration=30.75" \
  -filter_complex "[0:a][1:a][2:a]amix=inputs=3:weights='0.62 0.18 0.08':normalize=1,lowpass=f=3500[source];$seam" \
  -map "[out]" "${common[@]}" "$output_dir/air_conditioner.ogg"

"$ffmpeg_bin" \
  -f lavfi -i "anoisesrc=color=pink:amplitude=0.42:sample_rate=48000:duration=30.75:seed=105" \
  -f lavfi -i "sine=frequency=180:sample_rate=48000:duration=30.75" \
  -f lavfi -i "sine=frequency=233:sample_rate=48000:duration=30.75" \
  -f lavfi -i "sine=frequency=311:sample_rate=48000:duration=30.75" \
  -filter_complex "[0:a][1:a][2:a][3:a]amix=inputs=4:weights='0.70 0.05 0.04 0.03':normalize=1,highpass=f=90,lowpass=f=6500,tremolo=f=0.2:d=0.15[source];$seam" \
  -map "[out]" "${common[@]}" "$output_dir/cafe_ambience.ogg"

sha256sum "$output_dir"/*.ogg
