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
		if (resources != nil, {
			resources.do { |x| x.free };
		});
		args.do { |x| x.free };
	}

	printOn { | stream |
        stream << "PTNode( " << this.op << ", " << this.args << " )";
    }
}


PTLiteral : PTOp {

	*new{
		^super.newCopyArgs("", "1");
	}

	min { |args, resources|
		^args[0];
	}

	max { |args, resources|
		^args[0];
	}

	rate { |args, resources|
		^\control;
	}

	instantiate { |args, resources|
		^args[0];
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
	var delegate;

	*new{ |name, nargs, delegate|
		^super.newCopyArgs(name, nargs, delegate);
	}

	instantiate { |args, resources|
		var iargs = PTOp.instantiateAll(args);
		var f = iargs[0];
		var width = iargs[1] ? 0.5;
		^switch(this.rate(args),
			\audio, {delegate.ar(freq: f, width: width)},
			\control, {delegate.kr(freq: f, width: width)},
			{delegate.ar(*PTOp.instantiateAll(args))},
		);
	}

	rate { |args, resources|
		// Assume the first arg is frequency.
		^if(args[0].max > 10, { \audio }, { \control });
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
		^Mix.ar(iargs);
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

PTParser {
	var <ops, constOp;

	*new { |ops|
		^super.newCopyArgs(ops, PTLiteral.new());
	}

	*default {
		^PTParser.new(Dictionary.with(*[
			"IN" -> PTInOp.new(),
			"PI" -> PTConst.new("PI", pi),
			"SIN" -> PTOscOp.new("SIN", 1, SinOsc),
			"TRI" -> PTOscOp.new("TRI", 1, VarSaw),
			"VSAW" -> PTOscOp.new("VSAW", 2, VarSaw),
			"SAW" -> PTOscOp.new("SAW", 1, Saw),

			"LR" -> PTLROp.new, // Needs doc
			"PAN" -> PTFilterOp.new("PAN", 2, Pan2), // Needs doc

			"LPF" -> PTFilterOp.new("LPF", 2, LPF),
			"BPF" -> PTFilterOp.new("BPF", 2, BPF),
			"HPF" -> PTFilterOp.new("HPF", 2, HPF),
			"MOOG" -> PTFilterOp.new("MOOG", 3, MoogFF),

			"DEL" -> PTDelayOp.new,
			"DEL.F" -> PTAllPassOp.new,

			"SCL" -> PTScaleOp.new, // Needs doc
			"UNI" -> PTUniOp.new, // Needs doc

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

	parseHelper {|tokens, pos, context|
		^case
		{pos >= tokens.size} { PTParseError.new("Expected token; got EOF").throw }
		{"^-?[0-9]+\.?[0-9]*$".matchRegexp(tokens[pos])} {
			pos+1 -> PTNode.new(constOp, [tokens[pos].asFloat()], callSite: context.callSite)
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
	var server, parser, <order, <dict, <id, <script, <argProxies, <callSite;

	*new { |server, parser, lines, args=nil, script=nil, callSite|
		var i;
		var o;
		var aa = args ? [PT.zeroNode];
		var argProxies = List.new;
		4.do { |i|
			var a = aa[i];
			var n = NodeProxy.new(
				server,
				rate: if(a != nil, {a.rate}, {\control}),
				numChannels: if(a != nil, {2}, {1}));
			if (
				a != nil,
				{ n.source = { PTScriptNet.maybeMakeStereo(a.instantiate) } },
				{ n.source = {0.0} }
			);
			argProxies.add(n);
		};
		i = argProxies[0];
		o = NodeProxy.new(server, i.rate, numChannels: 2);
		PTScriptNet.makeOut(o, i.rate);
		o.set(\in, i);
		^super.newCopyArgs(server, parser,
			List.newUsing(["in", "out"]),
			Dictionary.newFrom([
				"in", (line: nil, node: aa[0], proxy: i),
				"out", (line: nil, node: nil, proxy: o),
		]), PT.randId, script, argProxies, nil).init(lines, callSite);
	}

	*maybeMakeStereo { |ugen|
		^if (ugen.size == 0, {
			ugen!2
		}, {ugen});
	}

	// Get a context for evaluation where the previous line has rate r.
	contextWithItRate { |r, id|
		var ret = (
			I1: PTArgOp("I1", \i1, argProxies[0].rate),
			I2: PTArgOp("I2", \i2, argProxies[1].rate),
			I3: PTArgOp("I3", \i3, argProxies[2].rate),
			I4: PTArgOp("I4", \i1, argProxies[3].rate),
			IT: PTArgOp("IT", \in, r),
			callSite: (net: this, id: id),
		);
		if (script != nil, {ret.parent = script.context});
		^ret;
	}

	newProxy { |rate=nil|
		var ret = NodeProxy.new(server, rate: rate, numChannels: 2);
		"new % proxy\n".postf(rate);
		ret.set(\i1, argProxies[0]);
		ret.set(\i2, argProxies[1]);
		ret.set(\i3, argProxies[2]);
		ret.set(\i4, argProxies[3]);
		^ret;
	}


	init { |l, cs|
		if (script != nil, {script.refs[id] = this});
		l.do { |x| this.add(x) };
		callSite = cs;
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

	add { |line|
		^this.insert(order.size - 1, line);
	}

	insertPassthrough { |index|
		var id = PT.randId;
		var prevEntry = dict[order[index-1]];
		var nextEntry = dict[order[index]];
		var entry = (
			line: "IT",
			node: parser.parse("IT", this.contextWithItRate(prevEntry.node.rate, id: id)),
			proxy: this.newProxy(prevEntry.node.rate),
		);
		entry.proxy.source = { PTScriptNet.maybeMakeStereo(entry.node.instantiate) };
		prevEntry.proxy <>> entry.proxy <>> nextEntry.proxy;
		dict[id] = entry;
		order.insert(index, id);
	}

	insert { |index, line|
		this.insertPassthrough(index);
		^this.replace(index, line);
	}

	at { |index|
		^if(index.class === String, {dict[index]}, {dict[order[index]]});
	}

	prepareRemoveAt { |index|
		var prev = this[index-1];
		var next = this[index+1];
		var toRemove = this[index];
		var id = order[index];
		var propagate = (prev.node.rate != toRemove.node.rate);
		var preparations = List.new;
		var i = index + 1;
		var p = (
			commit: {
				if(propagate, {
					//
				}, {
					next.proxy.xset(\in, prev.proxy);
				});
				order.removeAt(index);
				dict.removeAt(id);
			},
			abort: {},
			cleanup: {
				toRemove.proxy.clear;
				toRemove.node.free;
			},
			time: next.proxy.fadeTime,
			output: prev,
			propagate: propagate,
		);
		preparations.add(p);
		i = index + 1;
		while({ (i < order.size) && (p.propagate) }, {
			p = this.prepareReparse(order[i], p.output);
			i = i + 1;
			preparations.add(p);
		});
		^PTScriptNet.combinePreparations(preparations);
	}

	removeAt { |index|
		var ret = this.prepareRemoveAt(index);
		ret.commit;
		^ret;
	}

	prepareReplaceOne { |id, line, prevEntry|
		var oldEntry = dict[id];
		// TODO: This might leak if a node is alloced but then we raise an exception before storing it
		// so it can be freed. Consider how to maybe tie any alloc'd resources to the exception so they
		// can be properly freed.
		var newNode = parser.parse(line, context: this.contextWithItRate(prevEntry.node.rate, id: id));
		var propagate = (newNode.rate != oldEntry.node.rate);
		var newEntry = (
			line: line,
			node: newNode,
			proxy: if(propagate, {this.newProxy(rate: newNode.rate)}, {oldEntry.proxy}),
		);

		^(
			commit: {
				newEntry.proxy.source = { PTScriptNet.maybeMakeStereo(newEntry.node.instantiate) };
				if (propagate, {
					newEntry.proxy.fadeTime = oldEntry.proxy.fadeTime;
					newEntry.proxy.set(\in, prevEntry.proxy);
				}, {
					newEntry.proxy.xset(\in, prevEntry.proxy);
				});
				dict[id] = newEntry;
			},
			cleanup: {
				oldEntry.node.free;
				if(propagate, {
					oldEntry.proxy.clear;
				});
			},
			abort: {
				newEntry.node.free;
				if(propagate, {
					newEntry.proxy.clear;
				});
			},
			output: newEntry,
			propagate: propagate,
			time: oldEntry.proxy.fadeTime,
		);
	}

	prepareReparse { |id, prevEntry|
		^this.prepareReplaceOne(id, dict[id].line, prevEntry);
	}

	*combinePreparations { |preparations|
		^(
			commit: {
				preparations.do({ |x| x.commit });
			},
			abort: {
				preparations.do({ |x| x.abort });
			},
			cleanup: {
				preparations.do({|x| x.cleanup});
			},
			output: preparations.last.output,
			propagate: preparations.last.propagate,
			time: { preparations.collect({ |x| x.time }).maxItem },
		);
	}

	reevaluate { |id|
		var line = dict[id].line;
		var index = order.indexOf(id);
		^this.prepareReplace(index, line);
	}

	prepareReplace { |index, line|
		var i;
		var id = order[index];
		var prevEntry = dict[order[index-1]];
		var preparations = List.new;
		var p = this.prepareReplaceOne(id, line, prevEntry);
		var ret;
		preparations.add(p);
		i = index + 1;
		while({ (i < order.size) && (p.propagate) }, {
			p = this.prepareReparse(order[i], p.output);
			i = i + 1;
			preparations.add(p);
		});

		// If we have changed the output rate of this script, reevaluate the call site instead.
		if (p.propagate, {
			if ( callSite != nil, {
				preparations.do { |p| p.abort };
				ret = callSite.net.reevaluate(callSite.id);
			}, {
				ret = PTScriptNet.combinePreparations(preparations);
			});
		}, {
			ret = PTScriptNet.combinePreparations(preparations);
		});
		^ret;
	}

	replace { |index, line|
		var ret = this.prepareReplace(index: index, line: line);
		ret.commit;
		SystemClock.sched(ret.time, {ret.cleanup});
		^ret;
	}

	setFadeTime { |index, time|
		dict[order[index]].proxy.fadeTime = time;
	}

	free {
		// clear all my proxies, free all my nodes
		this.out.source = { 0 };
		dict.do { |entry|
			entry.proxy.clear;
			entry.node.free;
		};
		// remove myself from ref tracking.
		if (script != nil, {script.refs.removeAt(id)});
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

	add { |line, topLevel=false|
		var idx = lines.size;
		"ADDING %\n".postf(line);
		this.insertPassthrough(idx);
		this.replace(idx, line, topLevel);
	}

	validateIndex { |index|
		if (index < 0, { PTEditError.new("Index must be greater than zero").throw });
		if (index > lines.size, { PTEditError.new("Index must be less than the current number of lines").throw });
	}

	insertPassthrough { |index|
		"INSERTING PASSTHROUGH %\n".postf(index);
		if (lines.size >= size, {
			PTEditError.new("Can't insert another line").throw
		});
		this.validateIndex(index);
		// Inserting a passthrough should never fail.
		refs.do { |r|
			// Indexes in the PTScriptNet are always one greater to account for the input row.
			r.insertPassthrough(index + 1);
		};
		lines.insert(index, "IT");
		fadeTimes.insert(index, 0.01);
	}

	makeHappen { |f, topLevel|
		var preparations = List.new;
		try {
			refs.do { |r| preparations.add(f.value(r)) };
			if (topLevel && (preparations.select({|p| p.propagate}).size > 0), {
				PTCheckError.new("Output must be audio").throw;
			});
		} { |err|
			preparations.do { |p|
				p.abort;
			};
			err.throw;
		};
		preparations.do { |p|
			p.commit;
			SystemClock.sched(p.time, {p.cleanup});
		};
	}

	removeAt { |index, topLevel=false|
		this.validateIndex(index);
		linesDraft = List.newFrom(lines);
		linesDraft.removeAt(index);
		this.makeHappen({ |r| r.prepareRemoveAt(index+1) }, topLevel);
		lines.removeAt(index);
		fadeTimes.removeAt(index);
		linesDraft = nil;
	}

	replace { |index, line, topLevel=false|
		"REPLACING % %\n".postf(index, line);
		this.validateIndex(index);
		linesDraft = List.newFrom(lines);
		linesDraft[index] = line;
		this.makeHappen({ |r| r.prepareReplace(index+1, line)}, topLevel);
		lines[index] = line;
		linesDraft = nil;
	}

	setFadeTime { |index, time|
		this.validateIndex(index);
		refs.do { |r| r.setFadeTime(index+1, time) };
		fadeTimes[index] = time;
	}

	getFadeTime { |index|
		^fadeTimes[index];
	}

	clear {
		lines.size.reverseDo { |i|
			this.removeAt(i);
		};
	}
}

PT {
	const vowels = "aeiou";
	const consonants = "abcdefghijklmnopqrstuvwxyz";
	const numScripts = 9;
	const scriptSize = 6;

	var server, <scripts, parser, <main;

	*new { |server|
		^super.newCopyArgs(server, Array.new(numScripts), PTParser.default, nil).init;
	}

	init {
		var ctx = ();
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

	replace { |script, index, line|
		scripts[script].replace(index, line, topLevel: true);
	}

	insertPassthrough { |script, index|
		scripts[script].insertPassthrough(index);
	}

	removeAt { |script, index|
		scripts[script].removeAt(index, topLevel: true);
	}

	add { |script, line|
		scripts[script].add(line, topLevel: true);
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

	clear {
		main.free;
		scripts.do { |s|
			s.clear;
		};
	}

	load { |str|
		var lines = str.split($\n);
		var curScript = 0;
		this.clear();
		this.init();
		lines.do { |l|
			case {l[0] == $#} {
				curScript = (l[1..].asInteger - 1);
			}
			{l == ""} {
				// pass
			}
			{true} {
				this.add(curScript, l);
			};
		}
	}

	out {
		^main.out;
	}

	*zeroNode {
		PTNode.new(PTLiteral.new(), [0])
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
			try {
				f.value;
			} { |err|
				e = err.errorString;
			};
			// The "/report" message is:
			// int - request id, the one that made us report on it
			// int - script that we're reporting about, 0-indexed
			// string - error, if any
			// string - current newline-separated lines of that script
			luaOscAddr.sendMsg("/report", i, s, (e ? msg ? ""), "".catList(pt.scripts[s].lines.collect({ |l| l ++ "\n" })));
		};
		//  :/
		pt = PT.new(context.server);


		this.addCommand("insert_passthrough", "iii", { arg msg;
			executeAndReport.value(msg[1].asInt, msg[2].asInt, {pt.insertPassthrough(msg[2].asInt, msg[3].asInt)});
		});

		this.addCommand("replace", "iiis", { arg msg;
			executeAndReport.value(msg[1].asInt, msg[2].asInt, {
				pt.replace(msg[2].asInt, msg[3].asInt, msg[4].asString)
			});
		});

		this.addCommand("add", "iis", { arg msg;
			executeAndReport.value(msg[1].asInt, msg[2].asInt, {
				pt.add(msg[2].asInt, msg[3].asString)
			});
		});

		this.addCommand("remove", "iii", { arg msg;
			executeAndReport.value(msg[1].asInt, msg[2].asInt, {pt.removeAt(msg[2].asInt, msg[3].asInt)});
		});

		this.addCommand("fade_time", "iiif", { arg msg;
			var prevFadeTime = pt.getFadeTime(msg[2].asInt, msg[3].asInt);
			var newFadeTime = msg[4].asFloat * prevFadeTime;
			executeAndReport.value(msg[1].asInt, msg[2].asInt, {
				pt.setFadeTime(msg[2].asInt, msg[3].asInt, newFadeTime)
			}, "fade time: " ++ newFadeTime.asStringPrec(3));
		});

		this.addCommand("just_report", "ii", { arg msg;
			executeAndReport.value(msg[1].asInt, msg[2].asInt, {});
		});

		pt.out.play;
	}

	free {
		pt.clear;
	}
}

// [x] Each Script keeps track of its Nets in `refs`.
// [x] Change edits to be two-phase: 1. Typecheck, 2. Commit.
// [x] Give a Net a free method.
// [x] When a Script line is edited, make the same edits to each Net. First do all Typechecks, then do all Commits.
// [x] Full multi-script setup with editing.
// [x] Script op
// [x] When a ScriptNet ends up with a `propagate` operation that propagates all the way to the end of script, blow up the calling line and replace it entire.
//    * The problem is that the "replace entire" operation needs to see the *new version* of the line. Figure out how to put that in the context.
// [x] Check for various leaks
// When a Script is called, that generates a new Net. Link the Script to the Net, so it can edit the net when called. Keep the net in a per-line `resources` slot. On replacing or deleting a line, free all old `resources` after the xfade time. 