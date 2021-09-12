
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

	*instantiateAll { |args|
		^args.collect({|x| x.instantiate()});
	}

}

PTNode {
	var <op, <args;
	*new { |op, args|
		^super.newCopyArgs(op, args);
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
		"it rate is % prevLine is %\n".postf(this.rate, prevLine);
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

	parse { |s, it = nil|
		var tokens = s.split($ );
		var itt = (if (it == nil) { PTNode.new(constOp, [0])} { it });
		var a = this.parseHelper(tokens, 0, itt);
		^a.value;
	}

	parseHelper {|tokens, pos, it|
		"parseHelper % % %\n".postf(tokens, pos, it);
		^case
		{pos >= tokens.size} { Error.new("Expected token; got EOF").throw }
		{"^-?[0-9]+\.?[0-9]*$".matchRegexp(tokens[pos])} {
			"const % at pos %\n".postf(tokens[pos], pos+1);
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
			"new node % args % at pos %\n".postf(op.name, myArgs, p);
			p -> PTNode.new(op, myArgs)
		}
		{true} {
			tokens.post;
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
	var parser, <lines, <nodes, <proxies, <out, <in, <inNode;

	*new { |parser, size, lines, inNode=nil|
		var i = NodeProxy.new;
		i.source = { inNode.instantiate };
		^super.newCopyArgs(parser, Array.new(size), Array.new(size), Array.new(size), NodeProxy.new, i, inNode).init(lines);
	}

	init { |l|
		l.do { |x| this.add(x) };
		if (lines.size == 0, {
			this.makeOut(in.rate);
			out.set(\in, in);
		});
	}

	printOn { | stream |
        stream << "PTScriptNet(\n";
		lines.do { |l| stream << l << "\n" };
		stream << ")";
    }

	makeOut { |rate|
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

	/**
	* Adds a line to the script net.
	* Returns nil if the same script output node will continue to work; the old node if not.
	* This is the case if the script changes its output rate. In the case this method returns non-nil,
	* the calling line should be re-interpereted and the new output should be monitored; the old output should
	* be ended after the crossfade time.
	*/
	add { |line|
		var node = parser.parse(line, it: nodes.last ? inNode);
		var proxy = NodeProxy.new;
		var oldOut = nil;
		proxy.source = { node.instantiate };
		if ( out.rate != node.rate, {
			oldOut = out;
			out = NodeProxy.new;
		});
		if ( out.source == nil, {
			this.makeOut(node.rate);
		}, {});
		if( (proxies.size > 0), {
			proxies.last <>> proxy <>> out;
		}, {
			proxy.set(\in, in);
			proxy <>> out;
		});
		lines.add(line);
		nodes.add(node);
		proxies.add(proxy);
		^oldOut;
	}

	insertPassthrough { |index|
		var prevProxy = proxies[index-1] ? in;
		var nextProxy = proxies[index] ? out;
		var prevNode = nodes[index-1] ? inNode;
		var line = "IT";
		var node = parser.parse(line, it: prevNode);
		var proxy = NodeProxy.new;
		proxy.source = { node.instantiate };
		prevProxy <>> proxy <>> nextProxy;
		lines.insert(index, line);
		nodes.insert(index, node);
		proxies.insert(index, proxy);
	}

	insert { |index, line|
		this.insertPassthrough(index);
		^this.replace(index, line);
	}

	removeAt { |index|
		var prevProxy = proxies[index-1] ? in;
		var nextProxy = proxies[index+1] ? out;
		var proxy = proxies[index];
		var prevNode = nodes[index-1] ? inNode;
		var oldOut = nil;
		nextProxy.xset(\in, prevProxy);
		lines.removeAt(index);
		proxies.removeAt(index);
		nodes.removeAt(index);
		if ( proxy.rate != prevProxy.rate, {
			// Replace the line that moved up with itself, to propagate the new
			// signal rate.
			oldOut = replace(index, lines[index]);
		});
		SystemClock.sched(nextProxy.fadeTime, {proxy.end()});
		^oldOut;
	}

	replaceInternal { |index, line|
		var node = parser.parse(line, it: nodes[index-1] ? inNode);
		var proxy = proxies[index];
		var prevControl = proxies[index-1] ? in;
		var nextProxy = proxies[index+1] ? out;
		var oldProxy = nil;
		if ( proxy.rate != node.rate, {
			oldProxy = proxy;
			proxy = NodeProxy.new;
			proxy.fadeTime = oldProxy.fadeTime;
			nextProxy.xset(\in, proxy);
			proxy.set(\in, prevControl);
			SystemClock.sched(nextProxy.fadeTime, {oldProxy.end()});
		});
		proxy.source = { node.instantiate };
		proxies[index] = proxy;
		lines[index] = line;
		nodes[index] = node;
		^ oldProxy == nil;
	}

	replace { |index, line|
		var l = line;
		var i = index;
		var done = false;
		var oldOut = nil;
		while({ (i < lines.size) && (done.not) }, {
			done = this.replaceInternal(i, l);
			i = i + 1;
			l = lines[i];
		});

		if ( done.not, {
			oldOut = out;
			out = NodeProxy.new;
			this.makeOut(nodes.last.rate);
			proxies.last <>> out;
		});
		^oldOut;
	}

	setFadeTime { |index, time|
		proxies[index].fadeTime = time;
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


// When I return: A PTScript will be an array of 6 proxy nodes. It will support:
// * ReplaceLine, which will xfade one of the nodes to a newly parsed version (if it has changed)
// * InsertLine, which will fail if all six lines are full, or insert in the middle if not.
//      Note that I plan to implement an `IT` op, referring to the node of the previous line.
//      I must take care when inserting a line to crossfade the line *below* to using the new previous line as IT, but not crossfade all the other node proxies.

// Another task:  Implement automated rate selection.