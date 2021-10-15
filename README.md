# PHONOTYPE

It is like Teletype but for sound.

To use PHONOTYPE:

* Install it onto Norns
* Restart the Norns
* Unplug keyboard from Teletype
* Plug keyboard into Norns
* Select PHONOTYPE
* Code a sound

## General idea

Like Teletype, PHONOTYPE is a prefix programming language. For example:

```
+ 1 * 2 2.5
```

Is a statement that yields the sum of 1 and the product of 2 and 2.5: 6.

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

ESC nagivates to the scene description.

Arrow keys choose a line to edit; enter key actually changes the line.

Shift-enter inserts a new "passthrough" line above the current one.

E2 adjusts the fade time of the current line.

E3 adjusts when the line will actually be crossfaded, in beat multiples. For
example, choose 4 to fade the line on a barline in 4/4 time.

## Mini-tutorial

Try `SIN 440` in M.

Then on the next line put `* IT PERC QN .5` (`QN` = quarter notes.  `.5` is our env
decay)

Now add `+ IT * .5 DEL.F IT * .75 M 3`. `DEL.F` is a delay w/ feedback. We delay
by a dotted eigth with a 3s decay.

Then go back to the first line.  Hit ctrl-enter to get a new line above. Make it
say `S+H SCL SIN .1 0 7 QN` and change `SIN 440` to say `SIN N.MIN IT`.


## Saving and Loading

While in the scene description, Ctrl-Enter or Alt-Enter saves your scene, with
the filename based on the first line of your scene description.

To load a scene, change the `scene` parameter (on the params page) to a file of
your choice.

To load a fresh scene, press Ctrl-Shift-Escape.

## Operators

Each is listed like so:

`NAME <arg1> <arg2> <arg3>` - description

Longer explanation, if it seems necessary

1. Arg 1 description if necessary
2. Arg 2 description if necessary
3. Etc.

### Math

Unlike Teletype, PHONOTYPE operates on floating point
numbers, not integers.

`+ <arg1> <arg2>` - Add

Mix/add two signals

`* <arg1> <arg2>` - Multiply

Multiply/VCA/Ring modulate two signals.

`- <arg1> <arg2>` - Subtract

`/ <dividend> <divisor>` - Divide

`% <modulend> <modulo>` - Mod

`< <left> <right>` - Less than

`> <left> <right>`  - Greater than

1 if the comparison holds, 0 otherwise.


### Oscillators

`SIN <freq>` - Sine wave

`PSIN <freq> <phase>` - Phase-modulatable sine wave

1. Frequency
2. Phase in radians

`TRI <freq>` - Triangle wave

`SAW <freq>` - Saw wave

`VSAW <freq> <width>` - Variable-width saw

1. Frequency
2. Width, from 0 to 1, saw to triangle to ramp.


`SQUARE <freq>` - Square wave

`PULSE <freq> <width>` - Variable-width pulse

1. Frequency
2. Width, from 0 to 1

`RSTEP <freq>` - Random steps

`RRAMP <freq>` - Random ramps

Steps with linear slew

`RSMOOTH <freq>` - Smooth random

Aka `RSM`

Uses cubic interpolation.

`WHITE` - White noise

`BROWN` - Brown noise

`PINK` - Pink noise

Noise is good dontcha know.

### PRE ops

`L.MIX <low> <high>: <statement>`

`L.M <low> <high>: <statement>`

Evaluates the statement to the right of the colon with `I` bound to each integer
between <low> and <high> inclusive.

For example, this is a LFO-modulated drone of the first six partials of a low A:

```
L.MIX 1 6: * UNI RRAMP I / SIN * 110 I I
```

### Filters

`LPF <signal> <freq>` - Low pass filter

`HPF <signal> <freq>` - High pass filter

`BPF <signal> <freq>` - Band pass filter

Basic.

1. Signal to filter
2. Cutoff frequency. Careful with very low values.


`RLPF <signal> <freq> <reciprocal-q>` - Resonant low pass filter

`RHPF <signal> <freq> <reciprocal-q>` - Resonant high pass filter

Slightly less basic.

1. Signal to filter
2. Cutoff frequency. Careful with very low values.
3. Damping. 0.01 - 0.3 or so is quite resonant, 1 is not very resonant, 2 is very gentle.


`RING <signal> <freq> <ringtime>` - Ringing resonant bandpass filter

Combine these to get some basic physical modelling.

1. Signal to filter
2. Frequency
3. Resonance: Time to -60dB impulse response

`MOOG <signal> <freq> <resonance>` - Moog emulation filter

1. Signal to filter
2. Cutoff frequency
3. Resonance. Zero to 4. Units unknown, but higher is resonanter.

`LPG <signal> <gate>` - 2 - Low-pass gate

Including a little bit of emulated "slowness" so you can get little "pok" sounds
by pinging it.

1. Signal to gate
2. Gain. > 1 has the filter all the way open.

`DJF <signal> <slider>` - DJ filter

1. Signal to filter
2. From 20hz lowpass at -1 to transparent at 0 to 20hz highpass at 1

`LAG <signal> <time>` - Lag/slew the same up and down

1. Singal to lag
2. Time to get most of the way

`SLEW <signal> <attack> <release>` - Lag/slew time different up and down
1. Signal to lag
2. Time to go up
3. Time to go down

### Delay and stuff

`DEL <signal> <time>` - Delay

Uses linear interpolation if it senses delay time will vary.

1. Signal to delay
2. Delay time

`DEL.F <signal> <time> <decaytime>` - Delay with feedback

Also known as "comb filter".

1. Signal to delay
2. Delay time
3. Time to decay by -60dB

`DEL.A <signal> <time> <decaytime>` - All-pass filter

1. Signal to delay
2. Delay time
3. Time to decay by -60dB


### Panning

`LR <left> <right>` - Stereo

Makes the arguments mono, then uses them as the left and right channels of the
new signal.

`PAN <signal> <side>` - Pan

1. Signal to pan
2. Side. -1 is left, 1 is right.

`MONO <signal>` - Mono

`ROT <signal> <rotation>` - Stereo rotate

I think you can get some mid/side translations with -.5 and .5

1. Signal to rotate
2. Amount to rotate, -1 to 1

### Rhythm

There are a pile of rhythm ops, all 0-argument, that yield different places in
the measure. So far we're only supporting 4/4 time, but within that you can use:

* `SN` - Trigger on every sixteenth note
* `EN` - Trigger on every eigth note
* `QN` - Trigger on every quarter note
* `HN` - Trigger on every half note
* `WN` - Trigger on every whole note
* `SNT`, `ENT`, `QNT`, and so on - sixteenth note triplets, eighth note triplets, etc.
* `B1` - Trigger on every beat-1 of a measure. `B2` triggers on every beat-2, etc.
* `B1.E` - Trigger on the second sixteenth note of beat-1 of every measure. Also
  `B2.E`, `B1.&`, `B4.A`, and every combination like that.

`EVERY <beats> <offset>` - Do every N beats

Aka `EV`

1. Trigger every this number of beats.
2. Offset, in beats. All `EVERY` ops share the same clock, and the
same clock as the beat operations above. That means that
`EV 16 0` will happen on the downbeat every 4 measurs of 4/4.

`ER <fill> <length> <offset> <noteduration>` - 4 - Euclidean rhythms

All arguments except duration round down to the nearest integer

1. Of the slots in the pattern, how many are notes?
2. How many slots are there in the pattern?
3. Offset, in slots.
4. Duration of notes, in beats (must be constant)

`SN.ER <fill> <length> <offset>` - Sixteenth-note euclidean rhythms

`EN.ER <fill> <length> <offset>` - Eigth-note euclidean rhythms

`QN.ER <fill> <length> <offset>` - Quarter-note euclidean rhythms

`PROB <chance> <triggers>` - Trigger chance

Note that, like most ops, when given a stereo signal this operates on each
channel in stereo. If you are passing in a bus and don't want weird hard-panned
random notes, apply `MONO` to the input. Me, I kind of like the wierd
hard-panned random notes.

1. Chance to pass the trigger
2. Trigger to maybe pass

`DUR <trig> <duration>` - 2 - Trigger to gate converter

Upon recieving a trigger, remain high for the duration.

1. Trigger in
2. Duration of gate

`CDIV <trig> <ratio>` - Clock divider

### Envelopes

`PERC <trig> <time>` - Percussive envelope

1. Trigger
2. Duration

`AR <trig> <time> <attack>` - Attack-release envelope.

Exponential-ish shape.

1. Trigger
2. Duration
3. Portion of duration spent in attack

`AR.L <trig> <time> <attack>` - Linear attack-release envelope

1. Trigger
2. Duration
3. Portion of duration spent in attack

`AR.C <trig> <time> <attack> <curve>` - Variable-curvature attack-release envelope

1. Trigger
2. Duration
3. Portion of duration spent in attack
4. Curvature. -4 is like `AR`, 0 is like `AR.L`, and positive is funny.

`ADSR <trig> <attacktime> <decaytime> <sustainlevel> <releasetime>` - Attack-decay-sustain-release envelope

### Pitch

All pitch ops are relative to the root: a note of 0 is always the root.

`ROOT` - Root, in CPS.

Default 440, set in parameters.

`N <halfstep>` - Half-steps to Hz.

Unquantized. N 0 is the root. Note it's also useful for filter cutoffs.

`N.QT <halfstep>` - Half-steps to Hz, quantized.

`N.MAJ <degree>` - Scale degrees to CPS, major

`N.MIN <degree>` - Scale degrees to CPS, minor

`N.HM <degree>` - Scale degrees to CPS, harmonic minor

`N.MAJP <degree>` - Scale degrees to CPS, major pentatonic

`N.MINP <degree>` - Scale degrees to CPS, minor pentatonic

`N.DOR <degree>` - Scale degrees to CPS, dorian

### Busses

* Busses are the one major place where the underlying Supercollider "rates" get
  exposed in PHONOTYPE. Busses have an intrinsic rate. Mostly this is important
  in that you'll need to use the audio busses (`A`, `B`, `C`, `D`, `J`, `AB
  <n>`) for sound, and you'll use a lot less CPU when you use the control busses
  (`W`, `X`, `Y`, `Z`, `K`, `CB <n>`) for control signals.

* The result of a bus send operation is the signal being sent. This allows you
  to send to a bus but still use the expression in a different way.

* All busses are assumed to be "modular level" for range calculations. If you
  pass hz-level (3000) type numbers on them, you may end up with oscillators at
  the wrong rate. To fix this, and some issues with other operations that
  require range information to be accurate, we have provided "type cast" aliases
  for the busses.  For most busses (other than the parameter busses), you can
  append a `.F` to the op to have PHONOTYPE assume it's in the "sound frequency"
  range (20 to 20000), `.U` to assume it's unipolar (0 to 1), and `.B` to assume
  it's in the "bipolar" oscillator range (-1 to 1). This doesn't clip the bus,
  it just adjusts the implicit assumptions we make about its range, for example
  to determine whether to use a control-rate or audio-rate generator, or for the
  automatic scaling operations downstream.

`A` - Audio Bus

`B` - Audio Bus

`C` - Audio Bus

`D` - Audio Bus

Global audio rate busses. Without an `=`, reads from the bus.


`A= <signal>` - Audio Bus send

`B= <signal>` - Audio Bus send

`C= <signal>` - Audio Bus send

`D= <signal>` - Audio Bus send

Global audio rate bus sends. Mixes on the bus.


`W` - Control Bus

`X` - Control Bus

`Y` - Control Bus

`Z` - Control Bus

Global control rate busses. Without an `=`, reads from the bus.


`W= <signal>` - Control Bus send

`X= <signal>` - Control Bus send

`Y= <signal>` - Control Bus send

`Z= <signal>` - Control Bus send

Global control rate bus sends. Mixes on the bus.

`AB <n>` - Audio bus array

`CB <n>` - Control bus array

Read from the global bus arrays, 16 each of audio and control indexed busses to
play with.

For example, `AB 2` is a read from audio bus 2.

1. The number of the bus. Must be a constant 0-20. The last four busses are
aliases for A, B, C, D or X, Y, Z, W (relatively for audio and control arrays).

`AB= <n> <signal>` - Audio bus array send
`CB= <n> <signal>` - Control bus array send

For example, `AB= 2 SIN 440` sends a 440-hz size to audio bus 2.

1. The number of the bus. Must be a constant 0-20. The last four busses are A,
B, C, D or X, Y, Z, W.
2. The signal to send to the bus.


`PARAM <n>` - Norns parameters

Aka `PRM` aka just `P`

PHONOTYPE is equipped with a bank of 16 general-purpose Norns parameters, mapped to
`PARAM 0` through `PARAM 15`. Each ranges from 0 to 1. 16n or similar recommended.

`IT` - The previous line

This is the coolest shit. `IT` allows you to smoothly patch in new bits in the
middle of your chain. `IT` is the only bus operation that is flexible about its
rate - it will be whatever rate the previous line was.

`IN` - The input

Whatever you've told the Norns mixer to pass to the engine from outside.

`F` - Frequency from monophonic midi in

`G` - Gate from monophonic midi in

`V` - Velocity from monophonic midi in


### Sequencing

`SEL2 <selector> <a> <b>` - Select by value from two inputs

1. Selector, an integer
2. A
3. B

`SEL3`, `SEL4`, `SEL5` - Select from various numbers of inputs, similarly

`SEQ2 <adv> <reset> <a> <b>` - Sequential switch/Sequencer

1. Advance trigger
2. Reset trigger
3. Value 1
4. Value 2

`SEQ3`, `SEQ4`, `SEQ5` - Similar for various numbers of inputs

`S+H <signal> <trig>` - Sample-and-hold


`XF <a> <b> <fade>` - Crossfade

Equal-power.

1. A
2. B
3. Crossfade amount. 0 is a mix, -1 is A, 1 is B.

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

`PI` - Pi.

Utility constant.

`SILENCE` - Audio-rate zero

Literal `0` is considered "control-rate" by PHONOTYPE and cannot be output;
`SILENCE` is an audio-rate version you can have as your output.

`SCL <signal> <min> <max>` - Scale

Do our best to scale the input to the given range. Note that some estimates of
mins and maxes of signals are approximate, so this won't guarantee all signals
in range.

1. Signal to scale
2. New min. Must be constant.
3. New max. Must be constant.

`SCL.X <signal> <min> <max>` - Scale exponential

Same as `SCL` but the output range is exponential.

`SCL.F <signal>` - Scale to frequency range

Scales the input from its natural input range to the exponential range 20 to
20000 - the range of human hearing in hz.

`SCL.V <signal>` - Scale to volume range

Scales the input to a 0 to 1 range, but using a curve (similar to the one in the
perc envelope) that better accounts for human volume perception being
logarhythmic than a linear scale would.

`UNI <signal>`  - Unipolar

Scale 0 to 1.

`CLIP <signal> <min> <max>` - Clip

`WRAP <signal> <min> <max>` - Wrap

Aka `WRP`

`POS <signal>` - Half-wave rectify

Returns only the positive part of the signal.

`FOLD <signal> <foldamount>` - Wavefolder

The "amount to fold" signal is chosen so an envelope is a productive thing to
put into it, and 0 is "no folding for -1 to 1 range signals". Amplitude
decreases as folding increases so as to maintain a roughly consistent overall
volume.

`SINFOLD <signal> <foldamount>` - Sinusoidal wavefolder

The "amount to fold" signal is chosen so an envelope is a productive thing to
put into it, and 0 is "gentle folding for -1 to 1 range signals". Amplitude
decreases as folding increases so as to maintain a roughly consistent overall
volume.

`CRUSH <signal> <crush>` - Bitcrusher

The "amount to crush" signal is chosen so that an attenuated envelope is a
productive thing to put into it, and 0 is "sample rate like 44k" and 1 is
"sample rate like 440".

`TANH <signal>` - Hyperbolic tangent

Oh man the hyperbolic tangent is the most amazing thing you can do to any audio
signal, and it will instantly make whatever you put through it sound gooder.
