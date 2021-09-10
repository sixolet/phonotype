
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

PTBasicOpAR : PTOp {
	var delegate;

	*new{ |name, nargs, delegate|
		^super.newCopyArgs(name, nargs, delegate);
	}

	instantiate { |args|
		^switch(this.rate(args),
			\audio, {delegate.ar(*PTOp.instantiateAll(args))},
			\control, {delegate.kr(*PTOp.instantiateAll(args))},
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

PTParser {
	var <ops, constOp;

	*new { |ops|
		^super.newCopyArgs(ops, PTConst.new());
	}

	*default {
		^PTParser.new(Dictionary.with(*[
			"SIN" -> PTBasicOpAR.new("SIN", 1, SinOsc),
			"+" -> PTPlusOp.new("+", 2),
			"*" -> PTTimesOp.new(),
		]));
	}

	parse { |s|
		var tokens = s.split($ );
		var a = this.parseHelper(tokens, 0);
		^a.value;
	}

	parseHelper {|tokens, pos|
		^case
		{"^-?[0-9]+\.?[0-9]*$".matchRegexp(tokens[pos])} {
			"const % at pos %\n".postf(tokens[pos], pos+1);
			pos+1 -> PTNode.new(constOp, [tokens[pos].asFloat()])
		}
		{pos + 1 >= tokens.size} { Error.new("Expected token; got EOF").throw }
		{ops.includesKey(tokens[pos])} {
			var op = ops[tokens[pos]];
			var p = pos + 1;
			var myArgs = Array.new(maxSize: op.nargs);
			{myArgs.size < op.nargs}.while({
				var a = this.parseHelper(tokens, p);
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

PTScriptRef {
	var <script, args, <proxies,
	*new { |script, args|
		^super.newCopyArgs(script, args, Array.new(script.size)).init();
	}

	add { |line|
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

PTScript {
	var <size, lines, refs;
	*new { |size|
		^super.newCopyArgs(size, Array.new(size), Array.new(0));
	}

	add { |line|
		lines = lines.add(line);
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