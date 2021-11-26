// This file contains ops that deal with buffers and their support code.


// from https://github.com/supercollider-quarks/wslib/blob/master/wslib-classes/Extensions/UGens/PlayBufCF.sc
// This class by Wouter Snoei
PTVendoredPlayBufCF {
	// dual play buf which crosses from 1 to the other at trigger

	*ar { arg numChannels, bufnum=0, rate=1.0, trigger=1.0, startPos=0.0, loop = 0.0,
			lag = 0.1, n = 2; // alternative for safemode

		var index, method = \ar, on;

		switch ( trigger.rate,
			 \audio, {
				index = Stepper.ar( trigger, 0, 0, n-1 );
			 },
			 \control, {
				index = Stepper.kr( trigger, 0, 0, n-1 );
				method = \kr;
			},
			\demand, {
				trigger = TDuty.ar( trigger ); // audio rate precision for demand ugens
				index = Stepper.ar( trigger, 0, 0, n-1 );
			},
			{ ^PlayBuf.ar( numChannels, bufnum, rate, trigger, startPos, loop ); } // bypass
		);

		on = n.collect({ |i|
			//on = (index >= i) * (index <= i); // more optimized way?
			InRange.perform( method, index, i-0.5, i+0.5 );
		});

		switch ( rate.rate,
			\demand,  {
				rate = on.collect({ |on, i|
					Demand.perform( method, on, 0, rate );
				});
			},
			\control, {
				rate = on.collect({ |on, i|
					Gate.kr( rate, on ); // hold rate at crossfade
				});
			},
			\audio, {
				rate = on.collect({ |on, i|
					Gate.ar( rate, on );
				});
			},
			{
				rate = rate.asCollection;
			}
		);

		if( startPos.rate == \demand ) {
			startPos = Demand.perform( method, trigger, 0, startPos )
		};

		lag = 1/lag.asArray.wrapExtend(2);

		^Mix(
			on.collect({ |on, i|
				PlayBuf.ar( numChannels, bufnum, rate.wrapAt(i), on, startPos, loop )
					* Slew.perform( method, on, lag[0], lag[1] ).sqrt
			})
		);

	}

}

PTPhasorOp :PTOp {

	var server, metro_bus;

	// opts should be a list of some of \rate, \bpm,
	*new { |name, server, metro_bus|
		^super.newCopyArgs(name, 4, server, metro_bus);
	}

	instantiate { |args, resources|
		var rate = args[1].instantiate;
		var start = args[2].instantiate*server.sampleRate;
		var end = args[3].instantiate*server.sampleRate;
		if (metro_bus != nil, {
			var secondsPerBeat = In.kr(metro_bus);
			start = start*secondsPerBeat;
			end = end*secondsPerBeat;
		})
		^Phasor.ar(args[0].instantiate, rate, start, end);
	}

	min { |args, resources|
		^min(args[2].min*server.sampleRate, args[3].min*server.sampleRate);
	}

	max { |args, resources|
		^max(args[2].max*server.sampleRate, args[3].max*server.sampleRate);
	}

	rate { |args|
		^\audio;
	}
}

PTBufWrOp : PTOp {
	var buffers, loop, min, max;

	*new { |name, buffers, loop, min= -1, max= 1|
		^super.newCopyArgs(name, 3, buffers, loop, min, max);
	}

	check { |args|
		if (args[0].isConstant.not, {
			PTCheckError.new(name ++ " needs constant buffer number").throw;
		});
		if (args[0].min >= buffers.size, {
			PTCheckError.new(name ++ " max buffer number is " ++ buffers.size).throw;
		});
	}

	rate { |args|
		^if ((args[2].rate == \audio) || (args[1].rate == \audio) || ((args[1].max - args[1].min) > 1000), {\audio}, {\control});
	}

	instantiate { |args, resources|
		var phase = args[1].instantiate;
		var toRecord = PTScriptNet.maybeMakeStereo(args[2].instantiate);
		case (
			{phase.rate == \audio}, {
				BufWr.ar(toRecord, 2, args[0].min, phase, loop);
			},
			{this.rate == \audio}, {
				BufWr.ar(toRecord, 2, args[0].min, K2A.ar(phase), loop);
			},
			{true}, {
				BufWr.kr(toRecord, 2, args[0].min, phase, loop);
			}
		);
		toRecord;
	}

	min { |args, resources|
		^min;
	}

	max { |args, resources|
		^max;
	}
}

PTBufDurOp : PTOp {
	*new {
		^super.newCopyArgs("LEN", 1);
	}

	check { |args|
		if (args[0].isConstant.not, {
			PTCheckError.new(name ++ " needs constant buffer number").throw;
		});
	}

	rate { |args| ^\control}

	instantiate { |args, resources|
		^BufDur.kr(args[0].min);
	}

	min {^0}

	max {^60}

}

PTBufRdOp : PTOp {
	var buffers, loop, min, max;

	*new { |name, buffers, loop, min= -1, max= 1|
		^super.newCopyArgs(name, 2, buffers, loop, min, max);
	}

	check { |args|
		if (args[0].isConstant.not, {
			PTCheckError.new(name ++ " needs constant buffer number").throw;
		});
		if (args[0].min >= buffers.size, {
			PTCheckError.new(name ++ " max buffer number is " ++ buffers.size).throw;
		});
	}

	rate { |args|
		^if ((args[1].rate == \audio) || ((args[1].max - args[1].min) > 1000), {\audio}, {\control});
	}

	instantiate { |args, resources|
		var phase = args[1].instantiate;
		if (phase.size > 0, {phase = phase[0]});
		^case (
			{phase.rate == \audio}, {
				BufRd.ar(2, args[0].min, phase, loop);
			},
			{(args[1].max - args[1].min) > 1000}, {
				BufRd.ar(2, args[0].min, K2A.ar(phase), loop);
			},
			{true}, {
				BufRd.kr(2, args[0].min, phase, loop);
			}
		);
	}

	min { |args, resources|
		^min;
	}

	max { |args, resources|
		^max;
	}
}

PTBufPlayOp :PTOp {
	var opts, buffers, metro_bus, fade, loop, min, max;

	// opts should be a list of some of \rate, \bpm,
	*new { |name, opts, buffers, metro_bus, fade=false, loop=0.0, min= -1, max= 1|
		^super.newCopyArgs(name, opts.size + 2, opts, buffers, metro_bus, fade, loop, min, max);
	}

	check { |args|
		if (args[0].isConstant.not, {
			PTCheckError.new(name ++ " needs constant buffer number").throw;
		});
		if (args[0].min >= buffers.size, {
			PTCheckError.new(name ++ " max buffer number is " ++ buffers.size).throw;
		});
	}

	instantiate { |args, resources|
		var n = args[0].min;
		var rate = 1.0;
		var cueMult = 1.0;
		var cue = 0.0;
		var xfTime = 0.01;
		opts.do { |o, i|
			switch(o,
				\rate, {
					rate = args[i+2].instantiate;
				},
				\octave, {
					rate = 2.pow(args[i+2].instantiate);
				},
				\beats, {
					var beatsTotal = args[i+2].instantiate;
					var secondsTotal = BufDur.kr(n);
					var beatsPerSecond = beatsTotal/secondsTotal;
					rate = (In.kr(metro_bus)*(beatsPerSecond)).reciprocal;
					cueMult = beatsPerSecond.reciprocal.abs;
				},
				\bpm, {
					var beatsPerSecond = args[i+2].instantiate/60;
					rate = (In.kr(metro_bus)*(beatsPerSecond)).reciprocal;
					cueMult = beatsPerSecond.reciprocal.abs;
				},
				\cue, {
					cue = cueMult * args[i+2].instantiate;
				},
				\crossfade, {
					xfTime = args[i+2].instantiate;
				}
			);
		};
		^if(fade, {
			PTVendoredPlayBufCF.ar(2, n,
				rate: BufRateScale.kr(n) * rate,
				trigger: args[1].instantiate,
				startPos: cue * BufSampleRate.kr(n),
				doneAction: Done.none,
				loop: loop,
				lag: xfTime,
			);
		}, {
			PlayBuf.ar(2, n,
				rate: BufRateScale.kr(n) * rate,
				trigger: args[1].instantiate,
				startPos: cue * BufSampleRate.kr(n),
				doneAction: Done.none,
				loop: loop,
			);
		});
	}

	min { |args, resources|
		^min;
	}

	max { |args, resources|
		^max;
	}

	rate { |args|
		^\audio;
	}
}