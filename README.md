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
``

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

## Operators

Each is listed as `NAME` - number of args - description

1. Arg 1 description
2. Arg 2 description
3. Etc.

### Oscillators

`SIN` - 1 - Sine wave

1. Frequency

`TRI` - 1 - Triangle wave

1. Frequency

`SAW` - 1 - Saw wave

1. Frequency

`VSAW` - 2 - Variable-width saw

1. Frequency
2. Width, from 0 to 1, saw to triangle to ramp.

## Busses

`IT` - 0 - The previous line

This is the coolest shit. `IT` allows you to smoothly patch in new bits in the
middle of your chain.

`IN` - 0 - The input

Theoretical. I have not tried it. It might work.

## Misc

`PI` - 0 - Pi.

Utility constant.

`SILENCE` - 0 - Audio-rate zero

Literal `0` is considered "control-rate" by PHONOTYPE and cannot be output;
`SILENCE` is an audio-rate version you can have as your output.