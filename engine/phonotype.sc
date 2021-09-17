
PTOp {
	var <name, <nargs;

	*new { |name, nargs|
		^super.newCopyArgs(name, nargs);
	}

	min { |args|
		^-1;
	}

	max { |args|
		^1;
	}

	constant { |args|
		^(this.min() == this.max());
	}

	rate { |args|
		var ret = \control;
		args.do({ |x|
			if (x.rate == \audio, {ret = \audio}, {});
		});
		^ret;
	}

	alloc { |args|
		^nil;
	}

	*instantiateAll { |args|
		^args.collect({|x| x.instantiate()});
	}

}

PTNode {
	var <op, <args, <resources;
	*new { |op, args|
		^super.newCopyArgs(op, args, op.alloc(args));
	}

	min {
		^op.min(args);
	}

	max {
		^op.max(args);
	}

	rate {
		^op.rate(args);
	}

	constant {
		^op.constant(args);
	}

	instantiate {
		^op.instantiate(args);
	}

	free {
		if (resources != nil, { resources.do { |x| x.free } });
	}

	printOn { | stream |
        stream << "PTNode( " << this.op << ", " << this.args << " )";
    }
}


PTConst : PTOp {

	*new{
		^super.newCopyArgs("", "1");
	}

	min { |args|
		^args[0];
	}

	max { |args|
		^args[0];
	}

	rate { |args|
		^\constant;
	}

	instantiate { |args|
		^args[0];
	}
}

PTOscOp : PTOp {
	var delegate;

	*new{ |name, nargs, delegate|
		^super.newCopyArgs(name, nargs, delegate);
	}

	instantiate { |args|
		var iargs = PTOp.instantiateAll(args);
		var f = iargs[0];
		var width = iargs[1] ? 0.5;
		^switch(this.rate(args),
			\audio, {delegate.ar(freq: f, width: width)},
			\control, {delegate.kr(freq: f, width: width)},
			{delegate.ar(*PTOp.instantiateAll(args))},
		);
	}

	rate { |args|
		// Assume the first arg is frequency.
		^if(args[0].max > 10, { \audio }, { \control });
	}
}

PTPlusOp : PTOp {
	*new { |name, nargs|
		^super.newCopyArgs(name, nargs);
	}

	instantiate { |args|
		var iargs = PTOp.instantiateAll(args);
		^Mix.ar(iargs);
	}

	min { |args|
		^args.sum {|i| i.min};
	}

	max { |args|
		^args.sum {|i| i.max};
	}

}

PTTimesOp : PTOp {
	*new {
		^super.newCopyArgs("*", 2);
	}

	instantiate { |args|
		var iargs = PTOp.instantiateAll(args);
		^iargs[0] * iargs[1];
	}

	min { |args|
		^[
			args[0].min*args[1].min,
			args[0].min*args[1].max,
			args[0].max*args[1].min,
			args[0].max*args[1].max

		].minItem;
	}

	max { |args|
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

	rate { |args|
		^prevLine.rate;
	}

	min { |args|
		^prevLine.min;
	}

	max { |args|
		^prevLine.max;
	}

	instantiate { |args|
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
	var parser, <order, <dict, <id;

	*new { |parser, size, lines, inNode=nil|
		var i = NodeProxy.new;
		var o = NodeProxy.new;
		i.source = { inNode.instantiate };
		PTScriptNet.makeOut(o, i.rate);
		o.set(\in, i);
		^super.newCopyArgs(parser,
			["in", "out"],
			Dictionary.newFrom([
				"in", (line: nil, node: inNode, proxy: i),
				"out", (line: nil, node: nil, proxy: o),
		]), PT.randId).init(lines);
	}


	init { |l|
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
			proxy: NodeProxy.new,
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
			proxy: if(propagate, {NodeProxy.new(rate: newNode.rate)}, {oldEntry.proxy}),
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

}


PTScript : PTOp {
	var <size, <lines, refs;
	*new { |size|
		^super.newCopyArgs(size, Array.new(size), Array.new(0));
	}

	add { |line|
		if (lines.size >= size, {
			Error.new("Can't add another line").throw
		}, {});
		lines = lines.add(line);
		refs.do { |r|
			r.add(line);
		};
	}

	insert { |index, line|
	}

	replace { |index, line|
	}

	delete { |index|
	}

	setFadeTime { |index, time|
	}
}

PT {
	const vowels = "aeiou";
	const consonants = "abcdefghijklmnopqrstuvwxyz";

	*randId {
		^"".catList([consonants, vowels, consonants, consonants, vowels, consonants, consonants, vowels, consonants].collect({ |x| x.choose }));
	}
}


// Each Script keeps track of its Nets in `refs`.
// Change edits to be two-phase: 1. Typecheck, 2. Commit.
// Give a Net a free method.
// When a Script line is edited, make the same edits to each Net. First do all Typechecks, then do all Commits.
// When a Script is called, that generates a new Net. Link the Script to the Net, so it can edit the net when called. Keep the net in a per-line `resources` slot. On replacing or deleting a line, free all old `resources` after the xfade time. 