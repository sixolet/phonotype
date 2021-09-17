
PTOp {
	var <name, <nargs;

	*new { |name, nargs|
		^super.newCopyArgs(name, nargs);
	}

	min { |args, resources|
		^-1;
	}

	max { |args, resources|
		^1;
	}

	rate { |args, resources|
		var ret = \control;
		args.do({ |x|
			if (x.rate == \audio, {ret = \audio}, {});
		});
		^ret;
	}

	alloc { |args|
		^nil;
	}

	*instantiateAll { |args, resources|
		^args.collect({|x| x.instantiate()});
	}

}

PTNode {
	var <op, <args, <resources;
	*new { |op, args|
		^super.newCopyArgs(op, args, op.alloc(args));
	}

	min {
		^op.min(args, resources);
	}

	max {
		^op.max(args, resources);
	}

	rate {
		^op.rate(args, resources);
	}

	instantiate {
		^op.instantiate(args);
	}

	free {
		if (resources != nil, {
			resources.do { |x| x.free };
			args.do { |x| x.free };
		});
	}

	printOn { | stream |
        stream << "PTNode( " << this.op << ", " << this.args << " )";
    }
}


PTConst : PTOp {

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

PTItOp : PTOp {
	var prevLine;

	*new { |prevLine| // A PTNode
		^super.newCopyArgs("IT", 0, prevLine);
	}

	rate { |args, resources|
		^prevLine.rate;
	}

	min { |args, resources|
		^prevLine.min;
	}

	max { |args, resources|
		^prevLine.max;
	}

	instantiate { |args, resources|
		// "it rate is % prevLine is %\n".postf(this.rate, prevLine);
		^case
		{this.rate == \audio} {
			\in.ar(0)
		}
		{this.rate == \control} {
			\in.kr(0)
		}
		{true} {
			Error.new("Unknown rate for IT.").throw;
		}
	}
}

PTParser {
	var <ops, constOp;

	*new { |ops|
		^super.newCopyArgs(ops, PTConst.new());
	}

	*default {
		^PTParser.new(Dictionary.with(*[
			"SIN" -> PTOscOp.new("SIN", 1, SinOsc),
			"TRI" -> PTOscOp.new("TRI", 1, VarSaw),
			"VSAW" -> PTOscOp.new("VSAW", 2, VarSaw),
			"SAW" -> PTOscOp.new("SAW", 1, Saw),
			"+" -> PTPlusOp.new("+", 2),
			"*" -> PTTimesOp.new(),
		]));
	}

	parse { |str, it = nil|
		var s = if ( (str == nil) || (str == ""), {"IT"}, {str});
		var tokens = s.split($ );
		var itt = (if (it == nil) { PTNode.new(constOp, [0])} { it });
		var a = this.parseHelper(tokens, 0, itt);
		^a.value;
	}

	parseHelper {|tokens, pos, it|
		// "parseHelper % % %\n".postf(tokens, pos, it);
		^case
		{pos >= tokens.size} { Error.new("Expected token; got EOF").throw }
		{"^-?[0-9]+\.?[0-9]*$".matchRegexp(tokens[pos])} {
			// "const % at pos %\n".postf(tokens[pos], pos+1);
			pos+1 -> PTNode.new(constOp, [tokens[pos].asFloat()])
		}
		{tokens[pos] == "IT"} {
			pos+1 -> PTNode.new(PTItOp.new(prevLine: it), [])
		}

		{ops.includesKey(tokens[pos])} {
			var op = ops[tokens[pos]];
			var p = pos + 1;
			var myArgs = Array.new(maxSize: op.nargs);
			{myArgs.size < op.nargs}.while({
				var a = this.parseHelper(tokens, p, it);
				myArgs = myArgs.add(a.value);
				p = a.key;
			});
			// "new node % args % at pos %\n".postf(op.name, myArgs, p);
			p -> PTNode.new(op, myArgs)
		}
		{true} {
			// tokens.post;
			Error.new("None of the above ." + tokens[pos] + ".").throw;
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
	var parser, <order, <dict, <id, script, argProxies;

	*new { |parser, lines, args=nil, script=nil|
		var i;
		var o = NodeProxy.new;
		var aa = args ? [PT.zeroNode];
		var argProxies = List.new;
		4.do { |i|
			var a = aa[i];
			var n = NodeProxy.new;
			if (
				a != nil,
				{ n.source = { a.instantiate } },
				{ n.source = {0.0} }
			);
			argProxies.add(n);
		};
		i = argProxies[0];
		PTScriptNet.makeOut(o, i.rate);
		o.set(\in, i);
		^super.newCopyArgs(parser,
			List.newUsing(["in", "out"]),
			Dictionary.newFrom([
				"in", (line: nil, node: aa[0], proxy: i),
				"out", (line: nil, node: nil, proxy: o),
		]), PT.randId, script, argProxies).init(lines);
	}

	newProxy { |rate=nil|
		var ret = NodeProxy.new(rate: rate);
		ret.set(\i1, argProxies[0]);
		ret.set(\i2, argProxies[1]);
		ret.set(\i3, argProxies[2]);
		ret.set(\i4, argProxies[3]);
		^ret;
	}


	init { |l|
		if (script != nil, {script.refs[id] = this});
		l.do { |x| this.add(x) };
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
			out.source = { \in.ar() };
		}
		{ rate == \control } {
			out.source = { \in.kr() };
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
			node: parser.parse("IT", it: prevEntry.node),
			proxy: this.newProxy,
		);
		entry.proxy.source = { entry.node.instantiate };
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
			// p.postln;
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
		var newNode = parser.parse(line, it: prevEntry.node);
		var propagate = (newNode.rate != oldEntry.node.rate);
		var newEntry = (
			line: line,
			node: newNode,
			proxy: if(propagate, {this.newProxy(rate: newNode.rate)}, {oldEntry.proxy}),
		);

		^(
			commit: {
				newEntry.proxy.source = { newEntry.node.instantiate };
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

	prepareReplace { |index, line|
		var i;
		var id = order[index];
		var prevEntry = dict[order[index-1]];
		var preparations = List.new;
		var p = this.prepareReplaceOne(id, line, prevEntry);
		preparations.add(p);
		i = index + 1;
		while({ (i < order.size) && (p.propagate) }, {
			p = this.prepareReparse(order[i], p.output);
			// p.postln;
			i = i + 1;
			preparations.add(p);
		});
		// "LAST PREP".postln;
		// preparations.last.postln;
		// preparations.postln;
		^PTScriptNet.combinePreparations(preparations);
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
		// clear all my proxies
		dict.do { |entry| entry.proxy.clear };
		// remove myself from ref tracking.
		if (script != nil, {script.refs.removeAt(id)});
	}

}

PTScriptOp : PTOp {
	var parser, script;

	*new { |name, nargs, parser, script|
		^super.newCopyArgs(name, nargs, parser, script);
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

	alloc { |args|
		var net = PTScriptNet.new(parser, script.lines, args, script);
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
	var <size, <lines, <fadeTimes, <refs;
	*new { |size|
		^super.newCopyArgs(size, List.new, List.new, Dictionary.new);
	}

	add { |line|
		var idx = lines.size;
		insertPassthrough(idx);
		replace(idx, line);
	}

	validateIndex { |index|
		if (index < 0, { Error.new("Index must be greater than zero").throw });
		if (index > lines.size, { Error.new("Index must be less than the current number of lines").throw });
	}

	insertPassthrough { |index|
		if (lines.size >= size, {
			Error.new("Can't insert another line").throw
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

	makeHappen { |f|
		var preparations = List.new;
		try {
			refs.do { |r| preparations.add(f.value(r)) };
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

	removeAt { |index|
		this.validateIndex(index);
		this.makeHappen({ |r| r.prepareRemoveAt(index+1) });
		lines.removeAt(index);
		fadeTimes.removeAt(index);
	}

	replace { |index, line|
		this.validateIndex(index);
		this.makeHappen({ |r| r.prepareReplace(index+1, line)});
		lines[index] = line;
	}

	setFadeTime { |index, time|
		this.validateIndex(index);
		refs.do { |r| r.setFadeTime(index+1, time) };
		fadeTimes[index] = time;
	}
}

PT {
	const vowels = "aeiou";
	const consonants = "abcdefghijklmnopqrstuvwxyz";

	*zeroNode {
		PTNode.new(PTConst.new(), [0])
	}

	*randId {
		^"".catList([
			consonants, vowels, consonants,
			consonants, vowels, consonants,
			consonants, vowels, consonants].collect({ |x| x.choose }));
	}
}


// [x] Each Script keeps track of its Nets in `refs`.
// [x] Change edits to be two-phase: 1. Typecheck, 2. Commit.
// [x] Give a Net a free method.
// [x] When a Script line is edited, make the same edits to each Net. First do all Typechecks, then do all Commits.
// When a Script is called, that generates a new Net. Link the Script to the Net, so it can edit the net when called. Keep the net in a per-line `resources` slot. On replacing or deleting a line, free all old `resources` after the xfade time. 