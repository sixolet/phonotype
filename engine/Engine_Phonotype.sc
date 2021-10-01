PTCheckError : Error {
	errorString {
		^what
	}
}

PTParseError : Error {
	errorString {
		^what
	}
}

PTEditError : Error {
	errorString {
		^what
	}
}

PTOp {
	var <name, <nargs;

	*new { |name, nargs|
		^super.newCopyArgs(name, nargs);
	}

	check { |args| }

	min { |args, resources|
		^-1;
	}

	max { |args, resources|
		^1;
	}

	rate { |args, resources|
		var ret = \control;
		args.do({ |x|
			if (x.rate == \audio, {ret = \audio});
		});
		^ret;
	}

	alloc { |args, callSite|
		^nil;
	}

	// commit runs as part of a Routine; it can yield.
	commit { |args, resources|
		args.do { |a|
			a.commit;
		}
	}

	*instantiateAll { |args, resources|
		^args.collect({|x| x.instantiate()});
	}

	instantiateHelper { |c, rate, iargs|
		^if (rate == \audio, {c.ar(*iargs)}, {c.kr(*iargs)});
	}

	i { |c, args|
		var iargs = PTOp.instantiateAll(args);
		^this.instantiateHelper(c, this.rate(args), iargs);
	}

}

PTNode {
	var <op, <args, <resources;
	*new { |op, args, callSite=nil|
		try {
			if (args.size != op.nargs, {
				Error.new(op.name ++ " Args size " ++ args ++ " does not match number of args " ++ op.nargs).throw;
			});
			op.check(args);
		} { |e|
			args.do { |a|
				a.free;
			};
			e.throw;
		};
		^super.newCopyArgs(op, args, op.alloc(args, callSite));
	}

	min {
		^op.min(args, resources);
	}

	max {
		^op.max(args, resources);
	}

	commit {
		op.commit(args, resources);
	}

	isConstant {
		^(this.min == this.max);
	}

	rate {
		^op.rate(args, resources);
	}

	instantiate {
		^op.instantiate(args, resources);
	}

	free {
		// Post << "Freeing " << this.op << "\n";
		if (resources != nil, {
			resources.do { |x|
				// Post << "Freeing resources " << x << "\n";
				x.free();
			};
		});
		args.do { |x| x.free };
	}

	printOn { | stream |
        stream << "PTNode( " << this.op << ", " << this.args << " )";
    }
}


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

PTTimesOp : PTOp {
	*new {
		^super.newCopyArgs("*", 2);
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
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

PTArgOp : PTOp {
	var symbol, r;

	*new { |name, symbol, rate|
		^super.newCopyArgs(name, 0, symbol, rate);
	}

	rate { |args, resources|
		^r;
	}

	min { |args, resources|
		^-10;
	}

	max { |args, resources|
		^10;
	}

	instantiate { |args, resources|
		^case
		{this.rate == \audio} {
			symbol.ar([0,0])
		}
		{this.rate == \control} {
			symbol.kr([0,0])
		}
		{true} {
			symbol.kr([0,0])
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
		^In.ar(0, 2);
	}
}

PTLROp : PTOp {

	*new {
		^super.newCopyArgs("LR", 2);
	}

	check { |args|
		if (args[0].rate != args[1].rate, {
			PTCheckError.new("LR args must be the same rate").throw;
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
		^[PTLROp.mono(args[0].instantiate), PTLROp.mono(args[1].instantiate)];
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

	*new {
		^super.newCopyArgs("DEL.F", 3);
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
			this.instantiateHelper(AllpassN, this.rate(args), [args[0].instantiate, args[1].max, args[1].max, args[2].max]);
		}, {
			this.instantiateHelper(AllpassL, this.rate(args), [args[0].instantiate, args[1].max, args[1].instantiate, args[2].instantiate]);
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

		^ ((args[0].instantiate - oldMin)/(oldMax - oldMin)) * (newMax - newMin) + newMin;
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
		^ ((args[0].instantiate - oldMin)/(oldMax - oldMin));
	}
}

PTBusOp : PTOp {

	var rate, busses, min, max;

	*new { |name, rate, busses, min= -10, max= 10|
		^super.newCopyArgs(name, 1, rate, busses, min, max);
	}

	check { |args|
		if (args[0].isConstant.not, {
			PTCheckError.new(name ++ " needs constant bus number").throw;
		});
		if (args[0].min >= busses.size, {
			PTCheckError.new(name ++ " max bus number is " ++ busses.size).throw;
		});
	}

	min { |args, resources|
		^min;
	}

	max { |args, resources|
		^max;
	}

	instantiate { |args, resources|
		var n = args[0].min;
		^if (rate == \audio, {InFeedback.ar(busses[n].index, numChannels: 2)}, {busses[n].kr});
	}

	rate { |args|
		^rate
	}
}

PTBusSendOp : PTOp {

	var rate, busses;

	*new { |name, rate, busses|
		^super.newCopyArgs(name, 2, rate, busses);
	}

	check { |args|
		if (args[0].isConstant.not, {
			PTCheckError.new(name ++ " needs constant bus number").throw;
		});
		if (args[0].min >= busses.size, {
			PTCheckError.new(name ++ " max bus number is " ++ busses.size).throw;
		});
	}

	min { |args, resources|
		^args[1].min;
	}

	max { |args, resources|
		^args[1].max;
	}

	instantiate { |args, resources|
		var n = args[0].min;
		var a = PTScriptNet.maybeMakeStereo(args[1].instantiate);
		^if (rate == \audio,
			{ Out.ar(busses[n], a); a},
			{ Out.kr(busses[n], a); a});
	}
}

PTToCPSOp : PTOp {

	var rootBus;

	*new { |name, rootBus|
		^super.newCopyArgs(name, 1, rootBus);
	}

	min { ^0.1}
	max { ^10000 }

	instantiate { |args, resources|
		^(rootBus.kr.cpsmidi + args[0].instantiate).midicps;
	}
}

PTToCPSScaleOp : PTToCPSOp {
	var scale;
	*new { |name, rootBus, scale|
		^super.newCopyArgs(name, 1, rootBus, scale);
	}

	instantiate { |args, resources|
		var toKey = DegreeToKey.kr(
			scale.as(LocalBuf),
			args[0].instantiate,
			scale.stepsPerOctave);
		^(rootBus.kr.cpsmidi + toKey).midicps;
	}
}

PTNamedBusOp : PTOp {

	var rate, bus, min, max;

	*new { |name, rate, bus, min= -10, max=10|
		^super.newCopyArgs(name, 0, rate, bus, min, max);
	}

	min { |args, resources|
		^min;
	}

	max { |args, resources|
		^max;
	}

	instantiate { |args, resources|
		^if (rate == \audio, {InFeedback.ar(bus.index, numChannels: 2)}, {bus.kr});
	}

	rate { |args|
		^rate
	}
}

PTNamedLazyBusOp : PTNamedBusOp {

	*new { |name, rate, bus, min= -10, max=10|
		^super.newCopyArgs(name, 0, rate, bus, min, max);
	}

	instantiate { |args, resources|
		^if (rate == \audio, {InFeedback.ar(bus.get.index, numChannels: 2)}, {bus.get.kr});
	}
}

PTLazyBus {
	var server, rate, bus;

	*new { |server, rate|
		^super.newCopyArgs(server, rate, nil);
	}

	get {
		if (bus == nil, {
			bus = Bus.new(rate, numChannels: 2, server: server);
		});
		^bus;
	}

	free {
		if (bus != nil, {bus.free});
	}
}

PTNamedBusSendOp : PTOp {

	var rate, bus;

	*new { |name, rate, bus|
		^super.newCopyArgs(name, 1, rate, bus);
	}

	min { |args, resources|
		^args[0].min;
	}

	max { |args, resources|
		^args[0].max;
	}

	instantiate { |args, resources|
		var a = PTScriptNet.maybeMakeStereo(args[0].instantiate);
		^if (rate == \audio,
			{ Out.ar(bus, a); a},
			{ Out.kr(bus, a); a});
	}
}

PTNamedLazyBusSendOp : PTNamedBusSendOp {

	var rate, bus;

	*new { |name, rate, bus|
		^super.newCopyArgs(name, 1, rate, bus);
	}

	instantiate { |args, resources|
		var a = PTScriptNet.maybeMakeStereo(args[0].instantiate);
		^if (rate == \audio,
			{ Out.ar(bus.get, a); a},
			{ Out.kr(bus.get, a); a});
	}
}

PTParser {
	var <ops;

	*new { |ops|
		^super.newCopyArgs(ops);
	}

	*default {
		^PTParser.new(Dictionary.with(*[
			"IN" -> PTInOp.new(),
			"PI" -> PTConst.new("PI", pi),
			"SIN" -> PTOscOp.new("SIN", 1, SinOsc, SinOsc),
			"PSIN" -> PTOscOpPhase("PSIN", 2, SinOsc, SinOsc),
			"TRI" -> PTOscOp.new("TRI", 1, VarSaw, VarSaw),
			"VSAW" -> PTOscOpWidth.new("VSAW", 2, VarSaw, VarSaw),
			"SAW" -> PTOscOp.new("SAW", 1, Saw, LFSaw),
			"SQUARE" -> PTOscOp.new("SQUARE", 1, Pulse, LFPulse),
			"PULSE" -> PTOscOpWidth.new("PULSE", 2, Pulse, LFPulse),

			"LR" -> PTLROp.new,
			"PAN" -> PTFilterOp.new("PAN", 2, Pan2),

			"LPF" -> PTFilterOp.new("LPF", 2, LPF),
			"BPF" -> PTFilterOp.new("BPF", 2, BPF),
			"HPF" -> PTFilterOp.new("HPF", 2, HPF),
			"RLPF" -> PTFilterOp.new("RLPF", 3, RLPF),
			"RHPF" -> PTFilterOp.new("RHPF", 3, RHPF),
			"MOOG" -> PTFilterOp.new("MOOG", 3, MoogFF),

			"RING" -> PTFilterOp.new("RING", 3, Ringz),

			"LAG" -> PTFilterOp.new("LAG", 2, Lag),
			"SLEW" -> PTFilterOp.new("SLEW", 3, LagUD),
			"PERC" -> PTScaledEnvOp.new("PERC", 2, { Env.perc }),
			"AR" -> PTAREnvOp.new("AR", 3, {|a| Env.perc(a, 1-a)}),
			"AR.L" -> PTAREnvOp.new("AR.L", 3, {|a| Env.perc(a, 1-a, curve: 0)}),
			"AR.C" -> PTAREnvOp.new("AR.C", 4, {|a, c| Env.perc(a, 1-a, curve: c)}),
			"ADSR" -> PTEnvOp.new("ADSR", 5, {|a, d, s, r| Env.adsr(a, d, s, r)}),

			"DEL" -> PTDelayOp.new,
			"DEL.F" -> PTAllPassOp.new,

			"SCL" -> PTScaleOp.new,
			"UNI" -> PTUniOp.new,

			"SILENCE" -> PTSilenceOp.new,
			"+" -> PTPlusOp.new("+", 2),
			"*" -> PTTimesOp.new(),
		]));
	}

	parse { |str, context=nil|
		var ctx = context ? (callSite: nil);
		var s = if ( (str == nil) || (str == ""), {"IT"}, {str});
		var tokens = s.split($ );
		var a = this.parseHelper(tokens, 0, ctx);
		var end = a.key;
		while({end < tokens.size}, {
			if (tokens[end] != "", {
				PTParseError.new("Unexpected " ++ tokens[end] ++ "; expected end").throw;
			});
		});
		^a.value;
	}

	debug { |context, str|
		context.includesKey[\debug].if({
			context[\debug].if({
				Post << "DEBUG: " << str << "\n"
			})
		});
	}

	parseHelper {|tokens, pos, context|
		^case
		{pos >= tokens.size} { PTParseError.new("Expected token; got EOF").throw }
		{"^-?[0-9]+\.?[0-9]*$".matchRegexp(tokens[pos]) || "^\.[0-9]+$".matchRegexp(tokens[pos])} {
			pos+1 -> PTNode.new(PTLiteral.new(tokens[pos].asFloat()), [], callSite: context.callSite)
		}
		{ (context ? ()).includesKey(tokens[pos].asSymbol)} {
			var op = context[tokens[pos].asSymbol];
			var p = pos + 1;
			var myArgs = Array.new(maxSize: op.nargs);
			{myArgs.size < op.nargs}.while({
				var a;
				try {
					a = this.parseHelper(tokens, p, context);
				} { |e|
					myArgs.do { |a| a.free };
					e.throw;
				};
				myArgs = myArgs.add(a.value);
				p = a.key;
			});
			p -> PTNode.new(op, myArgs, callSite: context.callSite)
		}
		{ops.includesKey(tokens[pos])} {
			var op = ops[tokens[pos]];
			var p = pos + 1;
			var myArgs = Array.new(maxSize: op.nargs);
			{myArgs.size < op.nargs}.while({
				var a;
				try {
					a = this.parseHelper(tokens, p, context);
				} { |e|
					myArgs.do { |a| a.free };
					e.throw;
				};
				myArgs = myArgs.add(a.value);
				p = a.key;
			});
			p -> PTNode.new(op, myArgs, callSite: context.callSite)
		}
		{tokens[pos] == ""} {
			this.parseHelper(tokens, pos+1, context)
		}
		{true} {
			var c = context;
			while({context != nil},{
				context = context.parent;
			});
			PTParseError.new("Unknown token: " ++ tokens[pos] ++ ".").throw;
		};
	}
}

/*
Rules for scripts and busses and stuff (at least for now):
* you can only refer to smaller-numbered scripts from larger-numbered scripts.
* Maybe script calls have intrinsic rates, to make things simpler? That way at worst you have to change "the rest of one script"
* Busses have intrinsic rate.
*/

PTScriptNet {
	var server, parser, <order, <newOrder, <dict, <id, <script, <args, <argProxies, <callSite, jBus, kBus;

	*new { |server, parser, lines, args=nil, script=nil, callSite|
		var i;
		var o;
		var aa = List.newFrom(args ? []);
		// Need to evaluate args *in the context of the call site* but *only in the commit phase*
		while {aa.size < 4} {aa.add(PT.zeroNode)};
		^super.newCopyArgs(server, parser,
			List.newUsing(["in", "out"]),
			nil,
			Dictionary.newFrom([
				// Possibly don't need an in node anymore, as long as we make the right context for each line.
				"in", (newLine: "I1", line: nil, newNode: PTNode.new(PTArgOp("I1", \i1, aa[0].rate)), node: nil, proxy: nil),
				"out", (newLine: "IT", line: nil, newNode: PTNode.new(PTArgOp("IT", \in, aa[0].rate)), node: nil, proxy: nil),
		]), PT.randId, script, aa, nil, callSite, nil, nil).init(lines);
	}

	*maybeMakeStereo { |ugen|
		var u = ugen;
		if (ugen.class == Function, {
			u = ugen.value;
		});
		^if (ugen.size == 0, {
			ugen!2
		}, {ugen});
	}

	prevEntryOf { |id|
		var o = newOrder ? order;
		var idx = o.indexOf(id);
		var prevId = o[idx-1];
		^dict[prevId];
	}

	initArgProxies {
		argProxies = List.new;
		4.do { |i|
			var a = args[i];
			var n = if (callSite != nil, {
				var p = callSite.net.newProxy;
				p.set(\in, callSite.net.prevEntryOf(callSite.id).proxy);
				p;
			}, {
				NodeProxy.new(
					server,
					rate: if(a != nil, {a.rate}, {\control}),
					numChannels: if(a != nil, {2}, {1}))
			});
			if (
				a != nil,
				{
					Post << "Setting arg source to " << a << "\n";
					n.source = { PTScriptNet.maybeMakeStereo(a.instantiate) };
				},
				{ n.source = {0.0} }
			);
			argProxies.add(n);
		};
	}

	// Get a context for evaluation where the previous line has rate r.
	contextWithItRate { |r, id|
		var ret = (
			I1: PTArgOp("I1", \i1, args[0].rate),
			I2: PTArgOp("I2", \i2, args[1].rate),
			I3: PTArgOp("I3", \i3, args[2].rate),
			I4: PTArgOp("I4", \i1, args[3].rate),
			IT: PTArgOp("IT", \in, r),
			J: PTNamedLazyBusOp("J", \audio, jBus),
			K: PTNamedLazyBusOp("K", \control, kBus),
			'J=': PTNamedLazyBusSendOp("J", \audio, jBus),
			'K=': PTNamedLazyBusSendOp("K", \control, kBus),
			callSite: (net: this, id: id),
		);
		if (script != nil, {ret.parent = script.context});
		^ret;
	}

	newProxy { |rate=nil|
		var ret = NodeProxy.new(server, rate: rate, numChannels: 2);
		ret.quant = 0.01;
		ret.set(\i1, argProxies[0]);
		ret.set(\i2, argProxies[1]);
		ret.set(\i3, argProxies[2]);
		ret.set(\i4, argProxies[3]);
		^ret;
	}

	startEdit {
		newOrder = List.newFrom(order);
	}

	assertEditing {
		if (newOrder == nil, {
			Error.new("I thought we were editing").throw;
		});
	}

	init { |l|
		if (script != nil, {script.refs[id] = this});
		this.startEdit;
		if (script != nil, {
			Post << "Initializing network from script " << script << script.linesOrDraft << "\n";
			script.linesOrDraft.do { |x|
				Post << "Adding on init " << x << "\n";
				this.stageAdd(x);
			};
		}, {
			l.do { |x| this.stageAdd(x) };
		});
		jBus = PTLazyBus.new(server, \audio);
		kBus = PTLazyBus(server, \control);
	}

	lines {
		^order.collect({|x| dict[x].line}).reject({|x| x == nil});
	}

	out {
		^dict[order.last].proxy;
	}

	printOn { | stream |
        stream << "PTScriptNet(\n";
		this.lines.do { |l| stream << l << "\n" };
		stream << ")";
    }

	*makeOut { |out, rate|
		case { rate == \audio } {
			out.source = { \in.ar([0, 0]) };
		}
		{ rate == \control } {
			out.source = { \in.kr([0, 0]) };
		}
		{ true } {
			Error.new("Unknown output rate for script").throw;
		};
	}

	*nodeOf { |entry| ^entry.newNode ? entry.node }

	stageAdd { |line|
		this.assertEditing;
		^this.stageInsert(newOrder.size - 1, line);
	}

	stageInsertPassthrough { |index|
		var id = PT.randId;
		var prevEntry = this[index-1];
		var nextEntry = this[index];
		var entry = (
			line: nil,
			node: nil,
			newLine: "IT",
			newNode: parser.parse("IT", this.contextWithItRate(PTScriptNet.nodeOf(prevEntry).rate, id: id)),
			proxy: nil,
		);
		this.assertEditing;
		dict[id] = entry;
		newOrder.insert(index, id);
	}

	stageInsert { |index, line|
		this.assertEditing;
		this.stageInsertPassthrough(index);
		^this.stageReplace(index, line);
	}

	at { |index|
		^if(index.class === String, {dict[index]}, {dict[(newOrder ? order)[index]]});
	}

	newOutputRate {
		^dict[newOrder.last].newNode.rate;
	}

	outputRate {
		^dict[order.last].node.rate;
	}

	stageRemoveAt { |index|
		var prev = this[index-1];
		var next = this[index+1];
		var toRemove = this[index];
		var id = order[index];
		var propagate = (PTScriptNet.nodeOf(prev).rate != PTScriptNet.nodeOf(toRemove).rate);
		var i = index + 1;
		this.assertEditing;
		newOrder.removeAt(index);
		^if (propagate, {
			stageReplace(index, next.newLine ? next.line);
		}, {
			this;
		});
	}

	outputChanged {
		// Post << "output rate is " << this.outputRate << " new output rate is " << this.newOutputRate << "\n";
		^(this.outputRate != nil) && (this.newOutputRate != nil) && (this.newOutputRate != this.outputRate);
	}

	reevaluate { |id|
		var entry = this[id];
		var idx = newOrder.indexOf(id);
		^this.stageReplace(idx, entry.newLine ? entry.line);
	}

	stageReplace { |idx, line|
		var id, entry, prev, next, propagate;
		this.assertEditing;
		id = newOrder[idx];
		if (id == nil, {
			Error.new("Bad replace id " ++ idx ++ " size " ++ newOrder.size).throw
		});
		entry = dict[id];
		prev = this[idx-1];
		next = this[idx+1];
		entry['newLine'] = line;
		entry['newNode'] = parser.parse(line, context: this.contextWithItRate(PTScriptNet.nodeOf(prev).rate, id: id));
		propagate = false;
		case (
			{entry.node == nil}, { propagate = true;},
			{entry.node.rate != entry.newNode.rate}, {propagate = true;},
		);
		if (propagate && (next != nil), {
			this.stageReplace(idx+1, next.newLine ? next.line);
		});
		^if (this.outputChanged && (callSite != nil), {
			// Post << "reevaluating call site\n";
			callSite.net.reevaluate(callSite.id);
		}, {
			this
		});
	}

	setFadeTime { |index, time|
		dict[order[index]].proxy.fadeTime = time;
	}

	abort {
		order.do { |id|
			var entry = dict[id];
			entry['newLine'] = nil;
			entry['newNode'] = nil;
		};
		newOrder.do { |id|
			var entry = dict[id];
			if (entry.newNode != nil, {
				dict.removeAt(id);
			});
		};
		newOrder = nil;
	}

	commit { |cb|
		var outEntry = dict[newOrder.last];
		if (argProxies == nil, {
			// Post << "INITIALIZING ARG PROXIES\n";
			this.initArgProxies;
		});
		^Routine.new({
			var freeProxies = List.new;
			var freeNodes = List.new;
			var prevEntry = nil;
			var prevId = nil;
			var prevProxyIsNew = false;
			var connect;
			var lastFadeTime = 0;
			var entriesToLeaveBehind;
			var deferredConnections = List.new;
			// Stage 1: allocate all the new node proxies, and connect them together.
			Post << "Beginning commit routine for scriptNet " << id << "\n";
			newOrder.do { |id, idx|
				var entry = dict[id];
				var node = PTScriptNet.nodeOf(entry);
				var oldIdx = order.indexOf(id);
				// Allocate a proxy if needed
				var proxyIsNew = false;
				var oldPreviousWasDifferent = false;
				case (
					{entry.proxy == nil}, {
						// New entry
						entry['proxy'] = this.newProxy(node.rate);
						proxyIsNew = true;
						// Post << "new proxy for " << idx << " due to newness\n";
					},
					{entry.proxy.rate != node.rate}, {
						var oldFadeTime = entry.proxy.fadeTime;
						// Rate change entry
						// Schedule the old proxy for freeing
						freeProxies.add(entry.proxy);
						// Make the new one.
						entry['proxy'] = this.newProxy(node.rate);
						entry.proxy.fadeTime = oldFadeTime;
						proxyIsNew = true;
						// Post << "new proxy for " << idx << " due to rate change\n";
					},
					{ oldIdx == nil }, {},
					{ (oldIdx != nil) && (oldIdx > 0) && (order[oldIdx-1] != prevId) }, {
						// Possibly removed entry
						oldPreviousWasDifferent = true;
					}
				);
				case(
					{prevEntry == nil}, {/*pass*/},
					{proxyIsNew }, {
						// Post << "connecting proxies for " << idx << "\n";
						prevEntry.proxy <>> entry.proxy;
					},
					{oldPreviousWasDifferent || prevProxyIsNew}, {
						deferredConnections.add( (from: prevEntry, to: entry));
					}
				);
				prevProxyIsNew = proxyIsNew;
				prevId = id;
				prevEntry = entry;
			};
			server.sync;
			// Stage 2: Set the source of all the node proxies.
			Post << "Instantiating nodes for " << newOrder << "\n";
			newOrder.do { |id|
				var entry = dict[id];
				if (entry.newNode != nil, {
					// Post << "Committing new node " << entry.newNode << "\n";
					entry.newNode.commit;
					// Post << "Scheduling for free " << entry.node << " because we have " << entry.newNode << "\n";
					freeNodes.add(entry.node);
					Post << "Instantiating source for " << id << " to be " << entry.newNode << "\n";
					entry.proxy.source = { PTScriptNet.maybeMakeStereo(entry.newNode.instantiate) };
					entry.node = entry.newNode;
					lastFadeTime = entry.proxy.fadeTime;
					entry.line = entry.newLine;
					entry.newNode = nil;
					entry.newLine = nil;
				});
			};
			server.sync;
			0.07.yield;
			// Stage 3: Connect new inputs to any "live" proxies
			Post << "Deferred connecting proxies " << deferredConnections << "\n";
			deferredConnections.do { |x| x.to.proxy.xset(\in, x.from.proxy) };
			// Stage 4: Collect anything no longer needed. Exit the transaction.
			entriesToLeaveBehind = order.reject({|x| newOrder.includes(x)});
			entriesToLeaveBehind.do { |id|
				var entry = dict[id];
				freeNodes.add(entry.node);
				freeProxies.add(entry.proxy);
				dict.removeAt(id);
			};
			order = newOrder;
			// Indicate we are done with everything but cleanup
			cb.value;
			lastFadeTime.yield;
			// Stage 5, later: free some stuff
			freeNodes.do({|x| x.free});
			freeProxies.do({|x|
				Post << "Freeing proxy\n";
				x.clear;
			});
		});
	}

	free {
		// clear all my proxies, free all my nodes
		this.out.source = { 0 };
		dict.do { |entry|
			entry.proxy.clear;
			entry.node.free;
		};
		argProxies.do { |p| p.clear };
		jBus.free;
		kBus.free;
		// remove myself from ref tracking.
		if (script != nil, {script.refs.removeAt(id)});
	}

}

PTFreer {
	var f;

	*new {|f|
		^super.newCopyArgs(f);
	}

	free {
		f.value;
	}
}

PTCountdownLatch {
	var n, cb, id;
	*new { |n, cb|
		^super.newCopyArgs(n, cb, PT.randId).init;
	}

	init {
		// Post << "Initialize latch " << id << " with " << n << "\n";
		if (n == 0, {
			SystemClock.sched(0, {
				// Post << "Boom " << id << "\n";
				cb.value;
			});
		});
	}

	value {
		n = n - 1;
		if (n == 0, {
			// Post << "Bang " << id << "\n";
			cb.value;
		}, {
			// Post << "Tick " << n << id << "\n";
		});
	}
}

PTRhythmOp : PTOp {
	var server, quant, phase;

	*new { |name, nargs, server, quant, phase=0|
		^super.newCopyArgs(name, nargs, server, quant, phase)
	}

	min { ^0 }

	max { ^1 }

	rate { ^\control }

	alloc { |args, callSite|
		^[nil, nil];
	}

	instantiate { |args, resources|
		var b, idx, esp, freer, pattern;
		var q = Quant.new(quant, phase: phase);
		if (resources[0] == nil, {
			b = Bus.control(server, numChannels: 1);
			pattern = Pbind(\instrument, \tick, \dur, quant, \bus, b.index);
			Post << "Bus " << b << " server " << server << "\n";
			idx = b.index;
			if (quant == 0, { Error.new("OOPOS quant zero").throw; });
			esp = pattern.play(TempoClock.default, quant: q);
			Post << "Starting beat" << idx << "\n";
			freer = PTFreer({
				Post << "Stopping beat" << idx << "\n";
				esp.stop;
			});
			resources[0] = b;
			resources[1] = freer;
		}, {
			b = resources[0];
		});
		^b.kr;
	}
}

PTScriptOp : PTOp {
	var server, parser, script;

	*new { |server, name, nargs, parser, script|
		^super.newCopyArgs(name, nargs, server, parser, script);
	}

	min { |args, resources|
		^-10;
	}

	max { |args, resources|
		^10;
	}

	rate { |args, resources|
		var net = resources[0];
		^net.out.rate;
	}

	alloc { |args, callSite|
		var net = PTScriptNet.new(
			server: server, parser: parser,
			lines: script.linesOrDraft, args: args,
			script: script, callSite: callSite);
		^[net];
	}

	commit { |args, resources|
		var net = resources[0];
		Post << "Committing args " << args << "\n";
		args.do { |a|
			a.commit;
		};
		Post << "Committing net " << net << "\n";
		net.commit.do { |w| w.yield };
	}

	instantiate { |args, resources|
		var net = resources[0];
		^switch (net.out.rate,
			\audio, { net.out.ar },
			\control, { net.out.kr },
			{ Error.new("Unknown rate").throw },
		);
	}
}

PTScript {
	var <size, <lines, <fadeTimes, <refs, <context, <linesDraft;

	*new { |size, context|
		^super.newCopyArgs(size, List.new, List.new, Dictionary.new, context, nil);
	}

	linesOrDraft {
		^(linesDraft ? lines)
	}

	load { |newLines, topLevel=false, callback|
		Post << "load new lines " << newLines << "\n";
		linesDraft = List.newFrom(lines);
		newLines.do { |line|
			linesDraft.add(line);
		};
		this.makeHappen({ |net|
			net.startEdit;
			newLines.do {|line|
				Post << "loading line " << line << "\n";
				net.stageAdd(line);
			};
			// Return the net we staged
			net
		}, topLevel, callback);
		newLines.do {
			fadeTimes.add(0.01);
		};
	}

	add { |line, topLevel=false, callback|
		linesDraft = List.newFrom(lines);
		linesDraft.add(line);
		this.makeHappen({ |net|
			net.startEdit;
			net.stageAdd(line);
		}, topLevel, callback);
		fadeTimes.add(0.01);
	}

	validateIndex { |index, allowSize=true|
		if (index < 0, { PTEditError.new("Index must be greater than zero").throw });
		if (index > lines.size, { PTEditError.new("Index must be less than the current number of lines").throw });
		if ((index == lines.size) && (allowSize.not), { PTEditError.new("Cant operate on index " ++ index).throw });
	}

	insertPassthrough { |index, topLevel=false, callback|
		if (lines.size >= size, {
			PTEditError.new("Can't insert another line").throw
		});
		this.validateIndex(index);
		linesDraft = List.newFrom(lines);
		linesDraft.insert(index, "IT");
		this.makeHappen({ |net|
			net.startEdit;
			net.stageInsertPassthrough(index+1);
		}, topLevel, callback);
		// Inserting a passthrough should never fail.
		fadeTimes.insert(index, 0.01);
	}

	makeHappen { |f, topLevel, callback|
		var toCommit = List.new;
		var latch;
		try {
			// Post << "Doing to all refs " << refs << "\n";
			refs.do { |r| toCommit.add(f.value(r)) };
			// Post << "Check top level\n";
			if (topLevel && (toCommit.select({|p| p.outputChanged}).size > 0), {
				PTCheckError.new("Output must be audio").throw;
			});
		} { |err|
			toCommit.do { |p|
				p.abort;
			};
			linesDraft = nil;
			err.throw;
		};
		//Post << "committing to lines " << linesDraft << "\n";
		lines = linesDraft;
		linesDraft = nil;
		// Post << "new latch of size " << toCommit.size << " and callback " << callback << "\n";
		latch = PTCountdownLatch.new(toCommit.size, callback);
		Post << "About to commit " << toCommit << "\n";
		toCommit.do { |p|
			p.commit(latch).play;
		};
	}

	removeAt { |index, topLevel=false, callback=nil|
		this.validateIndex(index);
		linesDraft = List.newFrom(lines);
		linesDraft.removeAt(index);
		this.makeHappen({ |r|
			r.startEdit;
			r.stageRemoveAt(index+1);
		}, topLevel, callback);
		fadeTimes.removeAt(index);
	}

	replace { |index, line, topLevel=false, callback=nil|
		"REPLACING % %\n".postf(index, line);
		this.validateIndex(index);
		linesDraft = List.newFrom(lines);
		linesDraft[index] = line;
		this.makeHappen({ |r|
			r.startEdit;
			r.stageReplace(index+1, line)
		}, topLevel, callback);
		lines[index] = line;
	}

	setFadeTime { |index, time|
		this.validateIndex(index);
		refs.do { |r| r.setFadeTime(index+1, time) };
		fadeTimes[index] = time;
	}

	getFadeTime { |index|
		^fadeTimes[index];
	}

	clear { |topLevel=false, callback|
		linesDraft = List.new;
		this.makeHappen( {|r|
			r.startEdit;
			(r.lines.size-2).reverseDo { |i|
				r.stageRemoveAt(i+1);
			};
			r;
		}, topLevel, callback);
		fadeTimes.clear;
	}
}

PT {
	const vowels = "aeiou";
	const consonants = "abcdefghijklmnopqrstuvwxyz";
	const numScripts = 9;
	const scriptSize = 6;

	var server, <scripts, parser, <main, audio_busses, control_busses, param_busses;

	*new { |server|
		SynthDef(\tick, { |bus|
			var env = Env(levels: [0, 1, 0], times: [0, 0.01], curve: 'hold');
			Out.kr(bus, EnvGen.kr(env, doneAction: Done.freeSelf));
		}).add;

		^super.newCopyArgs(server, nil, PTParser.default, nil, nil, nil, nil).init;
	}

	*wrapWithCallbacks { |r, successCallback, errorCallback|
		^Routine.new {
			try {
				var res = r.next;
				while( {res != nil}, {
					res.yield;
					res = r.next;
				});
				successCallback.value;
			} { |err|
				errorCallback.value(err);
			};
		}
	}

	putBusOps { |ctx, name, bus, rate|
		ctx[name.asSymbol] = PTNamedBusOp.new(name, rate, bus);
		ctx[(name ++ "=").asSymbol] = PTNamedBusSendOp.new(name ++ "=", rate, bus);
	}

	initBusses { |ctx|
		audio_busses = List.new;
		20.do { |i|
			var bus = Bus.audio(server, numChannels: 2);
			audio_busses.add(bus);
		};
		ctx['AB'] = PTBusOp.new("AB", \audio, audio_busses);
		ctx['AB='] = PTBusSendOp.new("AB=", \audio, audio_busses);
		this.putBusOps(ctx, "A", audio_busses[16], \audio);
		this.putBusOps(ctx, "B", audio_busses[17], \audio);
		this.putBusOps(ctx, "C", audio_busses[18], \audio);
		this.putBusOps(ctx, "D", audio_busses[19], \audio);

		control_busses = List.new;
		20.do { |i|
			var bus = Bus.control(server, numChannels: 2);
			control_busses.add(bus);
		};
		ctx['CB'] = PTBusOp.new("CB", \control, control_busses);
		ctx['CB='] = PTBusSendOp.new("CB=", \control, control_busses);
		this.putBusOps(ctx, "W", control_busses[16], \control);
		this.putBusOps(ctx, "X", control_busses[17], \control);
		this.putBusOps(ctx, "Y", control_busses[18], \control);
		this.putBusOps(ctx, "Z", control_busses[19], \control);

		param_busses = List.new;
		18.do { |i|
			var bus = Bus.control(server, numChannels: 2);
			param_busses.add(bus);
		};
		ctx['PARAM'] = PTBusOp.new("PARAM", \control, param_busses, 0, 1);
		ctx['PRM'] = ctx['PARAM'];
		ctx['M'] = PTNamedBusOp.new("M", \control, param_busses[16]);

		// Set up the note operations
		param_busses[17].value = 440;
		ctx['ROOT'] = PTNamedBusOp.new("ROOT", \control, param_busses[17], 20, 10000);
		ctx['N'] = PTToCPSOp.new("N", param_busses[17]);
		ctx['N.QT'] = PTToCPSScaleOp.new("N.QT", param_busses[17], Scale.chromatic);
		ctx['N.MAJ'] = PTToCPSScaleOp.new("N.MAJ", param_busses[17], Scale.major);
		ctx['N.MIN'] = PTToCPSScaleOp.new("N.MIN", param_busses[17], Scale.minor);
		ctx['N.HM'] = PTToCPSScaleOp.new("N.HM", param_busses[17], Scale.harmonicMinor);
		ctx['N.MAJP'] = PTToCPSScaleOp.new("N.MAJP", param_busses[17], Scale.majorPentatonic);
		ctx['N.MINP'] = PTToCPSScaleOp.new("N.MINP", param_busses[17], Scale.minorPentatonic);
		ctx['N.DOR'] = PTToCPSScaleOp.new("N.DOR", param_busses[17], Scale.dorian);
	}

	initBeats { |ctx|
		ctx[\QN] = PTRhythmOp("QN", 0, server, 1);
		ctx[\HN] = PTRhythmOp("HN", 0, server, 2);
		ctx[\WN] = PTRhythmOp("WN", 0, server, 4);
		4.do { |i|
			var beat = i + 1;
			var name = "BT" ++ beat;
			ctx[name.asSymbol] = PTRhythmOp(name, 0, server, 4, i);
			ctx[(name ++ ".&").asSymbol] = PTRhythmOp(name ++ ".&", 0, server, 4, i + 0.5);
			ctx[(name ++ ".E").asSymbol] = PTRhythmOp(name ++ ".E", 0, server, 4, i + 0.25);
			ctx[(name ++ ".A").asSymbol] = PTRhythmOp(name ++ ".A", 0, server, 4, i + 0.75);
		}
	}

	init {
		var ctx = ();
		this.initBusses(ctx);
		this.initBeats(ctx);
		scripts = Array.new(numScripts);
		numScripts.do { |i|
			var script = PTScript.new(scriptSize, ctx);
			var oldCtx = ctx;
			scripts.add(script);
			ctx = ();
			ctx.parent = oldCtx;
			5.do { |nargs|
				var name = "$" ++ (i + 1);
				if (nargs > 0, { name = (name ++ "." ++ nargs) });
				ctx[name.asSymbol] = PTScriptOp.new(server, name, nargs, parser, script);
			}
		};
		main = PTScriptNet.new(server: server, parser: parser, lines: [],
			args: [PTNode.new(PTInOp.new, [], nil)], script: scripts[numScripts-1]);
	}

	replace { |script, index, line, topLevel=true, callback|
		scripts[script].replace(index, line, topLevel: topLevel, callback: callback);
	}

	insertPassthrough { |script, index, topLevel=true, callback|
		scripts[script].insertPassthrough(index, topLevel: topLevel, callback: callback);
	}

	setParam { |param, v|
		param_busses[param].value = v;
	}

	removeAt { |script, index, topLevel, callback|
		scripts[script].removeAt(index, topLevel: topLevel, callback:callback);
	}

	add { |script, line, topLevel=true, callback|
		scripts[script].add(line, topLevel: topLevel, callback: callback);
	}

	printOn { | stream |
		scripts.do { |script, i|
			stream << "#" << (i + 1) << "\n";
			script.lines.do { |l|
				stream << l << "\n";
			};
			stream << "\n";
		}
    }

	setFadeTime { |script, index, time|
		scripts[script].setFadeTime(index, time);
	}

	getFadeTime { |script, index|
		^scripts[script].getFadeTime(index);
	}

	clear { |callback|
		var latch = PTCountdownLatch(scripts.size, {
			Post << "Free busses\n";
			audio_busses.do { |b| b.free };
			control_busses.do { |b| b.free };
			callback.value;
		});
		Post << "CLEARING old script data\n";
		Post << "Free main\n";
		main.free;
		Post << "Clear scripts\n";
		scripts.do { |s|
			s.clear(topLevel: false, callback: latch);
		};

	}

	load { |str, callback, errCallback|
		this.clear({
			Post << "Done with clear\n";
			this.loadOnly(str, callback, errCallback);
		});
	}

	loadHelper{ |scriptChunks, scriptIndex, topCallback|
		var callback = if (scriptIndex == (scripts.size - 1), {
			topCallback
		}, {
			{this.loadHelper(scriptChunks, scriptIndex+1, topCallback)}
		});
		Post << "loading script " << scriptIndex << " with " << scriptChunks[scriptIndex] << "\n";
		scripts[scriptIndex].load(scriptChunks[scriptIndex], topLevel: false, callback: callback);
	}

	loadOnly { |str, callback, errCallback|
		var lines = str.split($\n);
		var curScript = 0;
		var scriptChunks;
		Post << "INITIALIZING new script data\n";
		this.init;
		scriptChunks = Array.fill(scripts.size, {List.new});
		lines.do { |l|
			case {l[0] == $#} {
				curScript = (l[1..].asInteger - 1);
			}
			{l == ""} {
				// pass
			}
			{true} {
				scriptChunks[curScript].add(l);
			};
		};
		Post << "SCRIPT CHUNKS " << scriptChunks << "\n";
		this.loadHelper(scriptChunks, 0, {
			if (this.out.rate != \audio, {
				this.clear;
				this.init;
				errCallback.value(PTCheckError.new("Output of loaded script was not audio"));
			}, {
				callback.value;
			});
		});
	}

	out {
		^main.out;
	}

	*zeroNode {
		^PTNode.new(PTLiteral.new(0), [])
	}

	*randId {
		^"".catList([
			consonants, vowels, consonants,
			consonants, vowels, consonants,
			consonants, vowels, consonants].collect({ |x| x.choose }));
	}

}

// norns glue
Engine_Phonotype : CroneEngine {
	classvar luaOscPort = 10111;

	var pt; // a Phonotype
	*new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}



	alloc {
		var luaOscAddr = NetAddr("localhost", luaOscPort);
		var executeAndReport = { |i, s, f, msg=nil|
			var e = nil;
			var cb = {
				// The "/report" message is:
				// int - request id, the one that made us report on it
				// int - script that we're reporting about, 0-indexed
				// string - error, if any
				// string - current newline-separated lines of that script
				luaOscAddr.sendMsg("/report", i, s, (e ? msg ? ""), "".catList(pt.scripts[s].lines.collect({ |l| l ++ "\n" })));
			};
			try {
				f.value(cb);
			} { |err|
				err.reportError;
				e = err.errorString;
				Post << "Reporting error to user " << e << "\n";
				cb.value
			};

		};
		//  :/
		pt = PT.new(context.server);
		pt.load("", {
			Post << "Initialized\n";
			pt.out.play;
		}, {
			Post << "Boo\n";
		});


		this.addCommand("insert_passthrough", "iii", { arg msg;
			executeAndReport.value(msg[1].asInt, msg[2].asInt, { |cb| pt.insertPassthrough(msg[2].asInt, msg[3].asInt, true, cb)});
		});

		this.addCommand("replace", "iiis", { arg msg;
			executeAndReport.value(msg[1].asInt, msg[2].asInt, { |cb|
				pt.replace(msg[2].asInt, msg[3].asInt, msg[4].asString, true, cb)
			});
		});

		this.addCommand("add", "iis", { arg msg;
			executeAndReport.value(msg[1].asInt, msg[2].asInt, { |cb|
				pt.add(msg[2].asInt, msg[3].asString, true, cb)
			});
		});

		this.addCommand("set_param", "if", { arg msg;
			pt.setParam(msg[1].asInt, msg[2].asFloat);
		});

		this.addCommand("remove", "iii", { arg msg;
			executeAndReport.value(msg[1].asInt, msg[2].asInt, { |cb|
				pt.removeAt(msg[2].asInt, msg[3].asInt, true, cb)
			});
		});

		this.addCommand("fade_time", "iiif", { arg msg;
			var prevFadeTime = pt.getFadeTime(msg[2].asInt, msg[3].asInt);
			var newFadeTime = msg[4].asFloat * prevFadeTime;
			executeAndReport.value(msg[1].asInt, msg[2].asInt, { |cb|
				pt.setFadeTime(msg[2].asInt, msg[3].asInt, newFadeTime);
				cb.value;
			}, "fade time: " ++ newFadeTime.asStringPrec(3));
		});

		this.addCommand("just_report", "ii", { arg msg;
			executeAndReport.value(msg[1].asInt, msg[2].asInt, {|cb| cb.value});
		});

		this.addCommand("tempo_sync", "ff", { arg msg;
			var beats = msg[1].asFloat;
			var tempo = msg[2].asFloat;
			var beatDifference = beats - TempoClock.default.beats;
			var nudge = beatDifference % 4;
			if (nudge > 2, {nudge = nudge - 4});
			if ( (tempo != TempoClock.default.tempo) || (nudge.abs > 1), {
				TempoClock.default.beats = TempoClock.default.beats + nudge;
				TempoClock.default.tempo = tempo;
			}, {
				TempoClock.default.beats = TempoClock.default.beats + (0.05 * nudge);
			});
			// Set M to be the duration of a beat.
			pt.setParam(16, 1/tempo);
		});
	}

	free {
		pt.clear;
	}
}


/**
A PTScriptNet has LINES which have PTNodes and PTProxies
*/


// [x] Fix race conditions
// [x] Reintegrate fixed race conditions with norns
// [ ] Write a replacement for NodeProxy that doesn't restart its synth when the input is set -- use a bus on input
// [ ] Figure out why sometimes using busses is buggy and/or does not clean up old connections & fix
// [ ] Adjust tests for fixed race conditions
// [x] Each Script keeps track of its Nets in `refs`.
// [x] Change edits to be two-phase: 1. Typecheck, 2. Commit.
// [x] Give a Net a free method.
// [x] When a Script line is edited, make the same edits to each Net. First do all Typechecks, then do all Commits.
// [x] Full multi-script setup with editing.
// [x] Script op
// [x] When a ScriptNet ends up with a `propagate` operation that propagates all the way to the end of script, blow up the calling line and replace it entire.
//    * The problem is that the "replace entire" operation needs to see the *new version* of the line. Figure out how to put that in the context.
// [x] Check for various leaks
// [x] When a Script is called, that generates a new Net. Link the Script to the Net, so it can edit the net when called. Keep the net in a per-line `resources` slot. On replacing or deleting a line, free all old `resources` after the xfade time.
// [x] Busses, both private and global
// [ ] Output stage
// [ ] Buffer ops
// [ ] TANH, FOLD, CLIP, SOFTCLIP, SINEFOLD
// [ ] -, /
// [x] Rhythm ops of some kind.
// [x] Norns param ops
// [x] Clock sync with Norns
// [ ] Load and save from Norns
// [ ] Norns hz, gate for basic midi
// [x] Envelopes: PERC, AR, ADSR
// [ ] Sequencer ops
// [ ] Pitch ops
// [ ] Polyphonic midi ops???
