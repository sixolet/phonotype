# PHONOTYPE

It is like Teletype but for sound.

To use PHONOTYPE:

* Install it onto Norns
* Unplug keyboard from Teletype
* Plug keyboard into Norns
* Select PHONOTYPE
* Code a sound

## General idea

Like Teletype, PHONOTYPE is a prefix programming language. For example:

```
+ 1 * 2 3
```

Is a statement that yields the sum of 1 and the product of 2 and 3: 7.

Every operation in PHONOTYPE is *declarative*. Instead of executing, like a line
of Teletype code, it tells the audio engine (SuperCollider) what to connect to
what. You can think of this much like patching a modular synth. The last line of
your main (`M`) script is output to the output jack.

The `IT` op is special: it means "the result of the above line".

Here is a program that outputs a FM drone:

```
SIN 440
SIN + 220 * 220 IT
```

## Navigation

F1..F8 on the keyboard navigates to script 1 through 8.

F9 on the keyboard navigates to the `M` (main) script. This is the script that
outputs sound.

Arrow keys choose a line to edit; enter key actually changes the line.

Shift-enter inserts a new "passthrough" line above the current one.

K2 adjusts the fade time of the current line.

## Operators

Each is listed as `NAME` - number of args - description

1. Arg 1 description
2. Arg 2 description
3. Etc.

### Math

`+` - 2 - Add

Mix/add two signals

`*` - 2 - Multiply

Multiply/VCA/Ring modulate two signals.

### Oscillators

`SIN` - 1 - Sine wave

1. Frequency

`PSIN` - 2 - Phase-modulatable sine wave

1. Frequency
2. Phase

`TRI` - 1 - Triangle wave

1. Frequency

`SAW` - 1 - Saw wave

1. Frequency

`VSAW` - 2 - Variable-width saw

1. Frequency
2. Width, from 0 to 1, saw to triangle to ramp.


`SQUARE` - 1 - Square wave

1. Frequency

`PULSE` - 2 - Variable-width pulse

1. Frequency
2. Width, from 0 to 1


### Filters

`LPF` - 2 - Low pass filter

`HPF` - 2 - High pass filter

`BPF` - 2 - Band pass filter

Basic.

1. Signal to filter
2. Cutoff frequency. Careful with very low values.


`RLPF` - 3 - Resonant low pass filter

`RHPF` - 3 - Resonant high pass filter

Slightly less basic.

1. Signal to filter
2. Cutoff frequency. Careful with very low values.
3. Damping. 0.01 - 0.3 or so is quite resonant, 1 is not very resonant, 2 is very gentle.


`RING` - 3 - Ringing resonant bandpass filter

Combine these to get some basic physical modelling.

1. Singal to filter
2. Frequency
3. Resonance: Time to -60dB impulse response

`MOOG` - 3 - Moog emulation filter

1. Signal to filter
2. Cutoff frequency
3. Resonance. Zero to 4.

`LAG` - 2 - Lag/slew the same up and down

1. Singal to lag
2. Time to get most of the way

`SLEW` - 3 - Lag/slew time different up and down
1. Signal to lag
2. Time to go up
3. Time to go down

### Delay and stuff

`DEL` - 2 - Delay

Uses linear interpolation if it senses delay time will vary.

1. Signal to delay
2. Delay time

`DEL.F` - 3 - Delay with feedback

Also known as "all-pass filter".

1. Signal to delay
2. Delay time
3. Time to decay by -60dB

### Panning

`LR` - 2 - Stereo

1. Left
2. Right

`PAN` - 2 - Pan

1. Signal to pan
2. Side. -1 is left, 1 is right.

### Busses

* Busses are the one major place where the underlying Supercollider rates get
  exposed in PHONOTYPE. Busses have an intrinsic rate.

* The result of a bus send operation is the signal being sent. This allows you
  to send to a bus but still use the expression in a different way.

* All busses are assumed to be "modular level" for range calculations. If you
  pass hz-level (3000) type numbers on them, you may end up with oscillators at
  the wrong rate. When transmitting frequency on a bus, consider transmitting it
  as octaves.

`A` - 0 - Audio Bus

`B` - 0 - Audio Bus

`C` - 0 - Audio Bus

`D` - 0 - Audio Bus

Global audio rate busses. Without an `=`, reads from the bus.


`A=` - 1 - Audio Bus send

`B=` - 1 - Audio Bus send

`C=` - 1 - Audio Bus send

`D=` - 1 - Audio Bus send

Global audio rate bus sends. Mixes on the bus.

1. The signal to send to the bus.


`A` - 0 - Control Bus

`B` - 0 - Control Bus

`C` - 0 - Control Bus

`D` - 0 - Control Bus

Global control rate busses. Without an `=`, reads from the bus.


`A=` - 1 - Control Bus send

`B=` - 1 - Control Bus send

`C=` - 1 - Control Bus send

`D=` - 1 - Control Bus send

Global control rate bus sends. Mixes on the bus.

1. The signal to send to the bus.

`AB` - 1 - Audio bus array
`CB` - 1 - Control bus array

Global bus arrays, 16 each of audio and control indexed busses to play with.

1. The number of the bus. Must be a constant 0-20. The last four busses are A,
B, C, D or X, Y, Z, W.

`AB=` - 2 - Audio bus array send
`CB=` - 2 - Control bus array send

1. The number of the bus. Must be a constant 0-20. The last four busses are A,
B, C, D or X, Y, Z, W.
2. The signal to send to the bus.

`PARAM` (alias `PRM`) - 1 - Norns parameters

PHONOTYPE is equipped with a bank of 16 general-purpose Norns parameters, mapped to
`PARAM 0` through `PARAM 15`. Each ranges from 0 to 1. 16n or similar recommended.

`IT` - 0 - The previous line

This is the coolest shit. `IT` allows you to smoothly patch in new bits in the
middle of your chain. `IT` is the only bus operation that is flexible about its
rate - it will be whatever rate the previous line was.

`IN` - 0 - The input

Theoretical. I have not tried it. It might work.

### Scripts

You call a script by prepending its number with a `$` (and no space). You can
only call scripts from higher-numbered scripts; you can call any script from
`M`. If you want to pass arguments, put a `.`, and the the number of arguments
you want to pass. For example:

```
$1.3 IT SIN 1 4
```

Calls script 1 with 3 arguments, namely `IT`, `SIN 1`, and `4`.

Within script 1, those are available as `I1`, `I2`, and `I3`. The first input is
also available on `IT` of the first line in the script.

### Misc

`PI` - 0 - Pi.

Utility constant.

`SILENCE` - 0 - Audio-rate zero

Literal `0` is considered "control-rate" by PHONOTYPE and cannot be output;
`SILENCE` is an audio-rate version you can have as your output.

`SCL` - 3 - Scale

Do our best to scale the input to the given range. Note that some estimates of
mins and maxes of signals are approximate, so this won't guarantee all signals
in range.

1. Signal to scale
2. New min. Must be constant.
3. New max. Must be constant.

`UNI` - 1 - Unipolar

Scale 0 to 1.

1. Signal to scale