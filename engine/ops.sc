// This file contains most of the "basic" ops. Oscillators, filters, effects, math, utilities.

PTLiteral : PTOp {

	var n;

	*new{ |n|
		^super.newCopyArgs("", 0, n);
	}

	min { |args, resources|
		^n;
	}

	max { |args, resources|
		^n;
	}

	rate { |args, resources|
		^\control;
	}

	commit {}

	instantiate { |args, resources|
		^n;
	}
}

PTConst : PTOp {

	var value;

	*new { |name, value|
		^super.newCopyArgs(name, 0, value);
	}

	min { |args, resources|
		^value;
	}

	max { |args, resources|
		^value;
	}

	rate { |args, resources|
		^\control;
	}

	instantiate { |args, resources|
		^value;
	}
}

PTOscOp : PTOp {
	var delegate, delegateLF;

	*new{ |name, nargs, delegate, delegateLF|
		^super.newCopyArgs(name, nargs, delegate, delegateLF);
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		var f = iargs[0];
		^switch(this.rate(args),
			\audio, {delegate.ar(freq: f)},
			\control, {delegateLF.kr(freq: f)},
			{delegate.ar(freq: f)},
		);
	}

	rate { |args, resources|
		// Assume the first arg is frequency.
		^if(args[0].max > 10, { \audio }, { \control });
	}
}

PTNoiseOp : PTOp {
	var delegate;
	*new { |name, delegate|
		^super.newCopyArgs(name, 0, delegate);
	}

	rate {
		^\audio;
	}

	instantiate {
		PTDbg << "Instantiating the " << delegate << "\n";
		^delegate.ar;
	}
}

PTEnvOp : PTOp {

	var envFunc;

	* new { |name, nargs, envFunc|
		^super.newCopyArgs(name, nargs, envFunc);
	}

	check { |args|
		args[1..].do { |a|
			if (a.isConstant.not, {
				PTCheckError.new("Envelopes have constant parameters")
			});
		};
	}

	min {^0}
	max {^1}
	rate {^\control}

	instantiate { |args, resources|
		var signal = args[0];
		var parameters = args[1..].collect({|x| x.min});
		^EnvGen.kr(envFunc.value(*parameters), signal.instantiate);
	}
}

PTScaledEnvOp : PTOp {

	var envFunc;

	* new { |name, nargs, envFunc|
		^super.newCopyArgs(name, nargs, envFunc);
	}

	check { |args|
		args[2..].do { |a|
			if (a.isConstant.not, {
				PTCheckError.new("Envelopes have constant parameters").throw;
			});
		};
	}

	min {^0}
	max {^1}
	rate {^\control}

	instantiate { |args, resources|
		var signal = args[0];
		var timeScale = args[1];
		var parameters = args[2..].collect({|x| x.min});
		^EnvGen.kr(envFunc.value(*parameters), signal.instantiate, timeScale: timeScale.instantiate);
	}
}

PTAREnvOp : PTScaledEnvOp {
	check { |args|
		super.check(args);
		if ((args[2].min < 0) || (args[2].min > 1), {
			PTCheckError.new("Attack must be between 0 and 1").throw;
		});
	}
}

PTOscOpWidth : PTOscOp {

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		var f = iargs[0];
		var width = iargs[1];
		^switch(this.rate(args),
			\audio, {delegate.ar(freq: f, width: width)},
			\control, {delegateLF.kr(freq: f, width: width)},
			{delegate.ar(freq: f, width: width)},
		);
	}
}

PTOscOpPhase : PTOscOp {

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		var f = iargs[0];
		var phase = iargs[1];
		^switch(this.rate(args),
			\audio, {delegate.ar(freq: f, phase: phase)},
			\control, {delegateLF.kr(freq: f, phase: phase)},
			{delegate.ar(freq: f, phase: phase)},
		);
	}
}


// An audio-rate zero
PTSilenceOp : PTConst {
	*new {
		^super.newCopyArgs("SILENCE", 0, 0);
	}

	rate { |args, resources|
		^\audio;
	}
}

PTDelegatedOp : PTOp {
	var c;

	*new { |name, nargs, c|
		^super.newCopyArgs(name, nargs, c);
	}

	instantiate { |args, resources|
		^this.i(c, args);
	}

	min { |args|
		^min(*args.collect({|x| x.min}));
	}

	max { |args|
		^max(*args.collect({|x| x.max}));
	}

}

PT01DelegatedOp : PTOp {
	var c;

	*new { |name, nargs, c|
		^super.newCopyArgs(name, nargs, c);
	}

	instantiate { |args, resources|
		^this.i(c, args);
	}

	min { |args|
		^0;
	}

	max { |args|
		^1;
	}

}

PTPosOp : PTOp {
	*new {
		^super.newCopyArgs("POS", 1);
	}

	min { |args, resources|
		^0;
	}

	max { |args, resources|
		^args[0].max;
	}

	instantiate { |args, resources|
		^args[0].instantiate.clip(0, inf);
	}
}

PTLpgOp : PTOp {
	*new {
		^super.newCopyArgs("LPG", 2);
	}

	min { |args, resources|
		^args[0].min;
	}

	max { |args, resources|
		^args[0].max;
	}

	rate {
		^\audio;
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args, resources);
		var laggy = iargs[1].clip(0, inf).lagud(0.015, 0.15);
		^(laggy * LPF.ar(iargs[0], laggy.linexp(0, 1, 20, 20000)));
	}
}

PTDJFOp : PTOp {
	*new {
		^super.newCopyArgs("DJF", 2);
	}

	min { |args, resources|
		^args[0].min;
	}

	max { |args, resources|
		^args[0].max;
	}

	rate {
		^\audio;
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args, resources);
		var hpf = iargs[1].clip(0, 1).linexp(0, 1, 20, 10000);
		var lpf = (1 + (iargs[1].clip(-1, 0))).linexp(0, 1, 40, 20000);
		^HPF.ar(LPF.ar(iargs[0], lpf), hpf);
	}
}

PTFilterOp : PTOp {
	var c;

	*new { |name, nargs, c|
		^super.newCopyArgs(name, nargs, c);
	}

	min { |args, resources|
		^args[0].min;
	}

	max { |args, resources|
		^args[0].max;
	}

	instantiate { |args, resources|
		^this.i(c, args);
	}
}

PTWrapOp : PTFilterOp {
	*new {
		^super.newCopyArgs("WRAP", 3, Wrap);
	}

	min { |args, resources|
		^args[1].min;
	}

	max { |args, resources|
		^args[2].max;
	}
}

PTClipOp : PTFilterOp {
	*new {
		^super.newCopyArgs("CLIP", 3, Clip);
	}

	min { |args, resources|
		^args[1].min;
	}

	max { |args, resources|
		^args[2].max;
	}
}

PTPlusOp : PTOp {
	*new { |name, nargs|
		^super.newCopyArgs(name, nargs);
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		^iargs[0] + iargs[1];
	}

	min { |args, resources|
		^args.sum {|i| i.min};
	}

	max { |args, resources|
		^args.sum {|i| i.max};
	}

}

PTMinOp : PTOp {
	*new {
		^super.newCopyArgs("MIN", 2);
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		^iargs[0] min: iargs[1];
	}

	min { |args, resources|
		^args[0].min min: args[1].min;
	}

	max { |args, resources|
		^args[0].max min: args[1].max;
	}

}

PTMaxOp : PTOp {
	*new { |name, nargs|
		^super.newCopyArgs("MAX", 2);
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		^iargs[0] max: iargs[1];
	}

	min { |args, resources|
		^args[0].min max: args[1].min;
	}

	max { |args, resources|
		^args[0].max max: args[1].max;
	}

}

PTGTOp : PTOp {
	*new {
		^super.newCopyArgs(">", 2);
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		^iargs[0] > iargs[1];
	}

	min { |args, resources|
		^0;
	}

	max { |args, resources|
		^1;
	}

}

PTLTOp : PTOp {
	*new {
		^super.newCopyArgs("<", 2);
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		^iargs[0] < iargs[1];
	}

	min { |args, resources|
		^0;
	}

	max { |args, resources|
		^1;
	}

}

PTMixOp : PTOp {

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		^if (this.rate(args, resources) == \audio, {
			Mix.ar(iargs);
		}, {
			Mix.kr(iargs);
		});
	}

	min { |args, resources|
		^args.sum {|i| i.min};
	}

	max { |args, resources|
		^args.sum {|i| i.max};
	}
}

PTMinusOp : PTOp {
	*new { |name, nargs|
		^super.newCopyArgs(name, nargs);
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		^iargs[0] - iargs[1];
	}

	min { |args, resources|
		^args[0].min - args[1].max;
	}

	max { |args, resources|
		^args[0].max - args[1].min;
	}

}

PTTimesOp : PTOp {
	*new {
		^super.newCopyArgs("*", 2);
	}

	instantiate { |args, resources|
		var iargs;
		iargs = PTOp.instantiateAll(args);
		^iargs[0] * iargs[1];
	}

	min { |args, resources|
		^[
			args[0].min*args[1].min,
			args[0].min*args[1].max,
			args[0].max*args[1].min,
			args[0].max*args[1].max

		].minItem;
	}

	max { |args, resources|
		^[
			args[0].min*args[1].min,
			args[0].min*args[1].max,
			args[0].max*args[1].min,
			args[0].max*args[1].max

		].maxItem;
	}
}

PTDivOp : PTOp {
	*new {
		^super.newCopyArgs("/", 2);
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		^iargs[0] / iargs[1];
	}

	check { |args, resources|
		var denom = args[1];
		if ( (denom.min == 0) || (denom.max == 0) || (denom.min.sign != denom.max.sign), {
			PTCheckError.new("Denominator must exclude 0").throw;
		});
	}

	min { |args, resources|
		^[
			args[0].min/args[1].min,
			args[0].min/args[1].max,
			args[0].max/args[1].min,
			args[0].max/args[1].max

		].minItem;
	}

	max { |args, resources|
		^[
			args[0].min/args[1].min,
			args[0].min/args[1].max,
			args[0].max/args[1].min,
			args[0].max/args[1].max

		].maxItem;
	}
}

PTModOp : PTOp {
	*new {
		^super.newCopyArgs("%", 2);
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		^iargs[0] % iargs[1];
	}

	min { |args, resources|
		^0;
	}

	max { |args, resources|
		^args[1].max;
	}
}


PTArgOp : PTOp {
	var symbol, r, minVal, maxVal, channels, initValue;

	*new { |name, symbol, rate, minVal = -10, maxVal = 10, channels = 2, initValue = 0|
		^super.newCopyArgs(name, 0, symbol, rate, minVal, maxVal, channels, initValue);
	}

	rate { |args, resources|
		^r;
	}

	min { |args, resources|
		^minVal;
	}

	max { |args, resources|
		^maxVal;
	}

	usesIt {
		^(symbol == \in);
	}

	instantiate { |args, resources|
		^case
		{this.rate == \audio} {
			symbol.ar(initValue!channels)
		}
		{this.rate == \control} {
			symbol.kr(initValue!channels)
		}
		{true} {
			symbol.kr(initValue!channels)
		}
	}
}

PTInOp : PTOp {

	*new {
		^super.newCopyArgs("IN", 0);
	}

	rate { |args, resources|
		^\audio;
	}

	min { |args, resources|
		^-1;
	}

	max { |args, resources|
		^1;
	}

	instantiate { |args, resources|
		^SoundIn.ar([0, 1]);
	}
}

PTLROp : PTOp {

	*new {
		^super.newCopyArgs("LR", 2);
	}

	check { |args|
		if (args[0].rate != args[1].rate, {
			PTCheckError.new("LR args must be same rate").throw;
		});
	}

	min { |args, resources|
		^if(args[0].min < args[1].min, {args[0].min}, {args[1].min});
	}

	max { |args, resources|
		^if(args[0].max < args[1].max, {args[0].max}, {args[1].max});
	}

	*mono { |ugen|
		^if (ugen.size == 0, {ugen}, {ugen.sum / ugen.size});
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args, resources);
		PTDbg << "IARGS " << iargs << "\n";
		^[PTLROp.mono(iargs[0]), PTLROp.mono(iargs[1])];
	}
}

PTMonoOp : PTOp {
	*new {
		^super.newCopyArgs("MONO", 1);
	}


	min { |args, resources|
		^args[0].min;
	}

	max { |args, resources|
		^args[0].max;
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args, resources);
		^PTLROp.mono(iargs[1]);
	}
}

PTRotateOp : PTOp {
	*new {
		^super.newCopyArgs("ROT", 2);
	}


	min { |args, resources|
		^args[0].min;
	}

	max { |args, resources|
		^args[0].max;
	}

	*mono { |ugen|
		^if (ugen.size == 0, {ugen}, {ugen.sum / ugen.size});
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args, resources);
		var stereo = PTScriptNet.maybeMakeStereo(iargs[0]);
		^this.instantiateHelper(Rotate2, this.rate(args), [stereo[0], stereo[1], PTLROp.mono(iargs[1])]);
	}
}

PTDelayOp : PTOp {

	*new {
		^super.newCopyArgs("DEL", 2);
	}

	check { |args|
		if (args[1].min < 0, {
			PTCheckError.new("DEL time should be positive").throw;
		});
		if (args[1].max > 10, {
			PTCheckError.new("DEL time should be < 10").throw;
		});
	}

	min { |args, resources|
		^args[0].min;
	}

	max { |args, resources|
		^args[0].max;
	}

	instantiate { |args, resources|
		^if (args[1].isConstant, {
			this.instantiateHelper(DelayN, this.rate(args), [args[0].instantiate, args[1].max, args[1].max]);
		}, {
			this.instantiateHelper(DelayL, this.rate(args), [args[0].instantiate, args[1].max, args[1].instantiate]);
		});
	}
}

PTAllPassOp : PTOp {

	var delegateN, delegateL;

	*new { |name, delegateN, delegateL|
		^super.newCopyArgs(name, 3, delegateN, delegateL);
	}

	check { |args|
		if (args[1].min < 0, {
			PTCheckError.new("DEL time should be positive").throw;
		});
		if (args[1].max > 10, {
			PTCheckError.new("DEL time should be < 10").throw;
		});
	}

	min { |args, resources|
		^args[0].min;
	}

	max { |args, resources|
		^args[0].max;
	}

	instantiate { |args, resources|
		^if (args[1].isConstant && args[2].isConstant, {
			this.instantiateHelper(delegateN, this.rate(args), [args[0].instantiate, args[1].max, args[1].max, args[2].max]);
		}, {
			this.instantiateHelper(delegateL, this.rate(args), [args[0].instantiate, args[1].max, args[1].instantiate, args[2].instantiate]);
		});
	}
}

PTSelectOp : PTOp {
	*new { |name, nargs|
		^super.newCopyArgs(name, nargs);
	}

	min { |args|
		^min(*args[1..].collect({|x| x.min}));
	}

	max { |args|
		^max(*args[1..].collect({|x| x.max}));
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		^if( this.rate == \audio, {
			Select.ar(iargs[0], iargs[1..]);
		}, {
			Select.kr(iargs[0], iargs[1..]);
		});
	}

}

PTSequenceOp : PTOp {
	*new { |name, nargs|
		^super.newCopyArgs(name, nargs);
	}

	min { |args|
		^min(*args[1..].collect({|x| x.min}));
	}

	max { |args|
		^max(*args[1..].collect({|x| x.max}));
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		var st = Stepper.kr(iargs[0], iargs[1], min: 0, max: nargs-3);
		^if( this.rate == \audio, {
			Select.ar(st, iargs[2..]);
		}, {
			Select.kr(st, iargs[2..]);
		});
	}

}


PTScaleOp : PTOp {
	*new {
		^super.newCopyArgs("SCL", 3);
	}

	check { |args|
		var oldMin = args[0].min;
		var oldMax = args[0].max;
		var newMin = args[1].min;
		var newMax = args[2].min;
		if (args[1].isConstant.not, {
			PTCheckError.new("Expected constant, got " ++ args[1].op.name).throw;
		});
		if (args[2].isConstant.not, {
			PTCheckError.new("Expected constant, got " ++ args[2].op.name).throw;
		});
		if (oldMin >= oldMax, {
			PTCheckError.new("Signal is constant or bad range data").throw;
		});
		if (newMin >= newMax, {
			PTCheckError.new("Min greater than max").throw;
		});
	}

	min { |args, resources|
		var oldMin = args[0].min;
		var oldMax = args[0].max;

		^args[1].min;
	}

	max { |args, resources|
		var oldMin = args[0].min;
		var oldMax = args[0].max;
		^args[2].max;
	}

	instantiate { |args, resources|
		var oldMin = args[0].min;
		var oldMax = args[0].max;
		var newMin = args[1].min;
		var newMax = args[2].min;

		^ args[0].instantiate.linlin(oldMin, oldMax, newMin, newMax, clip: nil);
	}
}

PTScaleExpOp : PTOp {
	*new {
		^super.newCopyArgs("SCL.X", 3);
	}

	check { |args|
		var oldMin = args[0].min;
		var oldMax = args[0].max;
		var newMin = args[1].min;
		var newMax = args[2].min;
		if (args[1].isConstant.not, {
			PTCheckError.new("Expected constant, got " ++ args[1].op.name).throw;
		});
		if (args[2].isConstant.not, {
			PTCheckError.new("Expected constant, got " ++ args[2].op.name).throw;
		});
		if (oldMin >= oldMax, {
			PTCheckError.new("Signal is constant or bad range data").throw;
		});
		if (newMin >= newMax, {
			PTCheckError.new("Min greater than max").throw;
		});
		if ((newMin <= 0) || (newMax <= 0), {
			PTCheckError.new("Output must be greater than zero");
		});
	}

	min { |args, resources|
		var oldMin = args[0].min;
		var oldMax = args[0].max;

		^args[1].min;
	}

	max { |args, resources|
		var oldMin = args[0].min;
		var oldMax = args[0].max;
		^args[2].max;
	}

	instantiate { |args, resources|
		var oldMin = args[0].min;
		var oldMax = args[0].max;
		var newMin = args[1].min;
		var newMax = args[2].min;

		^ args[0].instantiate.linexp(oldMin, oldMax, newMin, newMax, clip: 'minmax');
	}
}

PTUniOp : PTOp {
	*new {
		^super.newCopyArgs("UNI", 1);
	}

	check { |args|
		var oldMin = args[0].min;
		var oldMax = args[0].max;
		if (oldMin >= oldMax, {
			PTCheckError.new("Signal is constant or bad range data").throw;
		});
	}

	min { |args, resources|
		^0;
	}

	max { |args, resources|
		^1;
	}

	instantiate { |args, resources|
		var oldMin = args[0].min;
		var oldMax = args[0].max;
		^args[0].instantiate.linlin(oldMin, oldMax, 0, 1);
	}
}

PTSclVOp : PTOp {
	*new {
		^super.newCopyArgs("SCL.V", 1);
	}

	check { |args|
		var oldMin = args[0].min;
		var oldMax = args[0].max;
		if (oldMin >= oldMax, {
			PTCheckError.new("Signal is constant or bad range data").throw;
		});
	}

	min { |args, resources|
		^0;
	}

	max { |args, resources|
		^1;
	}

	instantiate { |args, resources|
		var oldMin = args[0].min;
		var oldMax = args[0].max;
		^args[0].instantiate.lincurve(oldMin, oldMax, 0, 1, curve: 6);
	}
}

PTBiOp : PTOp {
	*new {
		^super.newCopyArgs("BI", 1);
	}

	min { |args, resources|
		^-1;
	}

	max { |args, resources|
		^1;
	}

	instantiate { |args, resources|
		var oldMin = args[0].min;
		var oldMax = args[0].max;
		^args[0].instantiate.linlin(oldMin, oldMax, -1, 1, clip: nil);
	}
}

PTSclFOp : PTOp {
	*new {
		^super.newCopyArgs("SCL.F", 1);
	}

	min { |args, resources|
		^20;
	}

	max { |args, resources|
		^20000;
	}

	instantiate { |args, resources|
		var oldMin = args[0].min;
		var oldMax = args[0].max;
		^args[0].instantiate.linexp(oldMin, oldMax, 20, 20000, clip: 'minmax');
	}
}

PTFloorOp : PTOp {
	*new {
		^super.newCopyArgs("FLOOR", 1);
	}

	min { |args, resources|
		^args[0].min.floor;
	}

	max { |args, resources|
		^args[0].max.floor;
	}

	instantiate { |args, resources|
		^args[0].instantiate.floor;
	}
}

PTTanhOp : PTOp {
	*new {
		^super.newCopyArgs("TANH", 1);
	}

	instantiate { |args, resources|
		^args[0].instantiate.tanh;
	}
}

PTAbsOp : PTOp {
	*new {
		^super.newCopyArgs("ABS", 1);
	}

	max { |args|
		^max(args[0].max, -1 * args[0].min);
	}

	min {
		^0;
	}

	instantiate { |args, resources|
		^args[0].instantiate.abs;
	}
}

PTCountOp : PTOp {
	// trig, reset
	*new {
		^super.newCopyArgs("COUNT", 2);
	}

	max { |args|
		^10;
	}

	min {
		^0;
	}

	instantiate { |args, resources|
		^this.i(PulseCount, args);
	}
}

PTStepOp : PTOp {
	*new {
		// trig, reset, min, max
		^super.newCopyArgs("STEP", 4);
	}

	max { |args|
		^args[3].max;
	}

	min { |args|
		^args[2].min;
	}

	instantiate { |args, resources|
		var mn = args[2].instantiate;
		var mx = args[3].instantiate;
		var normal = (mn < mx);
		^this.instantiateHelper(Stepper, this.rate, [
			args[0].instantiate,
			args[1].instantiate,
			Select.kr(normal, mn, mx),
			Select.kr(normal, mx, mn),
			(mx-mn).sign]);
	}
}

PTLeapOp : PTOp {
	*new {
		// trig, reset, min, max, interval
		^super.newCopyArgs("LEAP", 5);
	}

	max { |args|
		^args[3].max;
	}

	min { |args|
		^args[2].min;
	}

	instantiate { |args, resources|
		var mn = args[2].instantiate;
		var mx = args[3].instantiate;
		var normal = (mn < mx);
		^this.instantiateHelper(Stepper, this.rate, [
			args[0].instantiate,
			args[1].instantiate,
			Select.kr(normal, mn, mx),
			Select.kr(normal, mx, mn),
			(mx-mn).sign * args[4].instantiate]);
	}
}

PTSignOp : PTOp {
	*new {
		^super.newCopyArgs("SIGN", 1);
	}

	max { |args|
		^1;
	}

	min {
		^-1;
	}

	instantiate { |args, resources|
		^args[0].instantiate.sign;
	}

}

PTInvOp : PTOp {
	*new {
		^super.newCopyArgs("INV", 1);
	}

	max { |args|
		^(-1 * args[0].min);
	}

	min { |args|
		^(-1 * args[0].max);
	}

	instantiate { |args, resources|
		^(-1 * args[0].instantiate);
	}
}

PTFoldOp : PTOp {

	*new {
		^super.newCopyArgs("FOLD", 2);
	}

	*foldval { |x| ^(0.2/(0.2 + x.abs)) }

	min { |args|
		^args[0].min;
	}

	max { |args|
		^args[0].max;
	}

	instantiate { |args, resources|
		^args[0].instantiate.fold2(PTFoldOp.foldval(args[1].instantiate));
	}
}

PTSinFoldOp : PTOp {

	*new {
		^super.newCopyArgs("SINFOLD", 2);
	}

	*foldval { |x| ^ pi *  x.exp }

	instantiate { |args, resources|
		var f = PTSinFoldOp.foldval(args[1].instantiate);
		^( (f*args[0].instantiate).sin) / f;
	}
}

PTCrushOp : PTOp {

	*new {
		^super.newCopyArgs("CRUSH", 2);
	}

	*foldval { |x| ^44100.0 * (0.01/(0.01 + x.abs)) }

	min { |args|
		^args[0].min;
	}

	max { |args|
		^args[0].max;
	}

	rate { ^\audio }

	instantiate { |args, resources|
		var f = PTCrushOp.foldval(args[1].instantiate);
		^SmoothDecimator.ar(args[0].instantiate, f);
	}
}
