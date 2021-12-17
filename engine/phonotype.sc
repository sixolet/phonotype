// This file contains the core mechanics of Phonotype. The parser, the script structures, the code to insert, remove, and replace lines.
// The other files in this directory mostly contain themed groups of ops.


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



PTFreer {
	var f;

	*new {|f|
		^super.newCopyArgs(f);
	}

	free {
		f.value;
	}
}

PTListFreer : PTOp {
	var <>value;

	free {
		if (value != nil, {
			value.do(_.free);
		});
	}
}


PTOp {
	var <name, <nargs;

	*new { |name, nargs|
		^super.newCopyArgs(name, nargs);
	}

	stringWithArgsResources { |args, resources|
		^name;
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

	set { |key, value, args, resources|
		args.do { |x| x.set(key, value) };
	}

	alloc { |args, callSite|
		^nil;
	}

	// commit runs as part of a Routine; it can yield.
	commit { |args, resources, group, dynCtx|
		args.do { |a|
			a.commit(group, dynCtx);
		}
	}

	usesIt { |args|
		^args.any { |x| x.usesIt };
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

	commit { |group, dynCtx|
		op.commit(args, resources, group, dynCtx);
	}

	set { |key, value|
		op.set(key, value, args, resources);
	}

	isConstant {
		^(this.min == this.max);
	}

	rate {
		^op.rate(args, resources);
	}

	usesIt {
		^op.usesIt(args);
	}

	instantiate {
		^op.instantiate(args, resources);
	}

	free {
		PTDbg << "Freeing " << this.op << "\n";
		if (resources != nil, {
			resources.do { |x|
				PTDbg << "Freeing resources " << x << "\n";
				x.free();
			};
		});
		args.do { |x| x.free };
	}

	printOn { | stream |
		stream << "PTNode( " << op.stringWithArgsResources(args, resources) << ", " << this.args << " )";
    }
}

PTParser {
	var <ops, <>counter;

	*new { |ops|
		^super.newCopyArgs(ops, 0);
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
			"RSTEP" -> PTOscOp.new("RSTEP", 1, LFDNoise0, LFDNoise0),
			"RRAMP" -> PTOscOp.new("RRAMP", 1, LFDNoise1, LFDNoise1),
			"RSMOOTH" -> PTOscOp.new("RSMOOTH", 1, LFDNoise3, LFDNoise3),
			"RSM" -> PTOscOp.new("RSMOOTH", 1, LFDNoise3, LFDNoise3),
			"WHITE" -> PTNoiseOp.new("WHITE", WhiteNoise),
			"BROWN" -> PTNoiseOp.new("BROWN", BrownNoise),
			"PINK" -> PTNoiseOp.new("PINK", PinkNoise),

			"LR" -> PTLROp.new,
			"PAN" -> PTFilterOp.new("PAN", 2, Pan2),
			"MONO" -> PTMonoOp.new,
			"ROT" -> PTRotateOp.new,

			"LPF" -> PTFilterOp.new("LPF", 2, LPF),
			"BPF" -> PTFilterOp.new("BPF", 2, BPF),
			"HPF" -> PTFilterOp.new("HPF", 2, HPF),
			"RLPF" -> PTFilterOp.new("RLPF", 3, RLPF),
			"RHPF" -> PTFilterOp.new("RHPF", 3, RHPF),
			"MOOG" -> PTFilterOp.new("MOOG", 3, MoogFF),
			"LPG" -> PTLpgOp.new,
			"DJF" -> PTDJFOp.new,

			"RING" -> PTFilterOp.new("RING", 3, Ringz),

			"LAG" -> PTFilterOp.new("LAG", 2, Lag),
			"SLEW" -> PTFilterOp.new("SLEW", 3, LagUD),
			"PERC" -> PTScaledEnvOp.new("PERC", 2, { Env.perc }),
			"AR" -> PTAREnvOp.new("AR", 3, {|a| Env.perc(a, 1-a)}),
			"AR.L" -> PTAREnvOp.new("AR.L", 3, {|a| Env.perc(a, 1-a, curve: 0)}),
			"AR.C" -> PTAREnvOp.new("AR.C", 4, {|a, c| Env.perc(a, 1-a, curve: c)}),
			"ADSR" -> PTEnvOp.new("ADSR", 5, {|a, d, s, r| Env.adsr(a, d, s, r)}),

			"XF" -> PTDelegatedOp.new("XF", 3, XFade2),
			"S+H" -> PTFilterOp.new("S+H", 2, Latch),
			"SEL2" -> PTSelectOp.new("SEL2", 3),
			"SEL3" -> PTSelectOp.new("SEL3", 4),
			"SEL4" -> PTSelectOp.new("SEL4", 5),
			"SEL5" -> PTSelectOp.new("SEL5", 6),
			"SEQ2" -> PTSequenceOp.new("SEQ2", 4),
			"SEQ3" -> PTSequenceOp.new("SEQ3", 5),
			"SEQ4" -> PTSequenceOp.new("SEQ4", 6),
			"SEQ5" -> PTSequenceOp.new("SEQ5", 7),
			"STEP" -> PTStepOp.new,
			"LEAP" -> PTLeapOp.new,
			"COUNT" -> PTCountOp.new,

			"CDIV" -> PTFilterOp.new("CDIV", 2, PulseDivider),
			"DUR" -> PT01DelegatedOp("DUR", 2, Trig1),
			"PROB" -> PT01DelegatedOp("PROB", 2, CoinGate),

			"DEL" -> PTDelayOp.new,
			"DEL.F" -> PTAllPassOp.new("DEL.F", CombN, CombL),
			"DEL.A" -> PTAllPassOp.new("DEL.A", AllpassN, AllpassL),

			"ABS" -> PTAbsOp.new,
			"SIGN" -> PTSignOp.new,
			"INV" -> PTInvOp.new,
			"WRAP" -> PTWrapOp.new,
			"WRP" -> PTWrapOp.new,
			"POS" -> PTPosOp.new,
			"CLIP" -> PTClipOp.new,
			"MIN" -> PTMinOp.new,
			"MAX" -> PTMaxOp.new,
			"SCL" -> PTScaleOp.new,
			"SCL.V" -> PTSclVOp.new,
			"SCL.F" -> PTSclFOp.new,
			"SCL.X" -> PTScaleExpOp.new,
			"BI" -> PTBiOp.new,
			"UNI" -> PTUniOp.new,
			"FLOOR" -> PTFloorOp.new,
			"TANH" -> PTTanhOp.new,
			"FOLD" -> PTFoldOp.new,
			"SINFOLD" -> PTSinFoldOp.new,
			"CRUSH" -> PTCrushOp.new,

			"SILENCE" -> PTSilenceOp.new,
			"+" -> PTPlusOp.new("+", 2),
			"*" -> PTTimesOp.new(),
			"-" -> PTMinusOp.new("-", 2),
			"/" -> PTDivOp.new(),
			"%" -> PTModOp.new(),
			">" -> PTGTOp.new,
			"<" -> PTLTOp.new,
		]));
	}

	parseStrum {|poly, preTokens, tokens, ctx|
		var end;
		var trig = this.parseHelper(preTokens, 1, ctx);
		var freq = this.parseHelper(preTokens, trig.key, ctx);
		var vel = this.parseHelper(preTokens, freq.key, ctx);
		var results = List.newFrom([trig, freq, vel].collect(_.value));
		var newNode;
		var strum = ctx[preTokens[0].asSymbol];
		if (vel.key < preTokens.size, {
			PTParseError.new("Expected :, found " ++ preTokens[vel.key]).throw;
		});
		poly.do { |i|
			var subctx = (
				I: PTConst.new("I", i),
				G: PTPolyTrigOp.new("G", i),
				F: PTPolyArgCaptureOp.new("F", 1, 20, 20000, i),
				V: PTPolyArgCaptureOp.new("V", 2, 0, 1, i),
			);
			var elt;
			subctx.parent = ctx;
			elt = this.parseHelper(tokens, 0, subctx);
			end = elt.key;
			results.add(elt.value);
		};
		newNode = PTNode.new(
			strum,
			results,
			ctx['callSite']);
		^(end -> newNode);
	}

	parseMix { |preTokens, tokens, ctx|
		var end;
		var low = this.parseHelper(preTokens, 1, ctx);
		var high = this.parseHelper(preTokens, low.key, ctx);
		var results = List.new;
		var newNode;
		PTDbg << "low " << low << " high " << high << "\n";
		if (low.value.isConstant.not || high.value.isConstant.not || (high.value.min <= low.value.min), {
			PTParseError.new("L.MIX takes two constants").throw;
		});
		if (high.key < preTokens.size, {
			PTParseError.new("Expected :, found " ++ preTokens[high.key]).throw;
		});
		(1 + high.value.min - low.value.min).do { |x|
			var i = x + low.value.min;
			var subctx = (I: PTConst.new("I", i));
			var elt;
			subctx.parent = ctx;
			elt = this.parseHelper(tokens, 0, subctx);
			end = elt.key;
			results.add(elt.value);
		};
		newNode = PTNode.new(PTMixOp.new("L.MIX", results.size), results, ctx['callSite']);
		^(end -> newNode);
	}

	parseMidi { |preTokens, tokens, ctx|
		var end, newNode, channel, results, midi;
		PTDbg << "Parsing channel " << preTokens << "\n";
		channel = this.parseHelper(preTokens, 1, ctx);
		PTDbg << "Parsed channel\n";
		results = List.newFrom([channel.value]);
		midi = ctx[preTokens[0].asSymbol];
		if (channel.key < preTokens.size, {
			PTParseError.new("Expected :, found " ++ preTokens[channel.key]).throw;
		});
		PTDbg << "Parsed pre\n";
		midi.poly.do { |i|
			var elt;
			var subctx = (
				I: PTConst.new("I", i),
				G: PTDynBusOp.new("G", \control, \gate, 0, 1),
				F: PTDynBusOp.new("F", \control, \freq, 20, 20000),
				V: PTDynBusOp.new("V", \control, \velocity, 0, 1),
			);
			subctx.parent = ctx;
			elt = this.parseHelper(tokens, 0, subctx);
			end = elt.key;
			results.add(elt.value);
		};
		newNode = PTNode.new(midi, results, ctx['callSite']);
		^(end -> newNode);
	}

	parse { |str, context=nil|
		var ctx = context ? (callSite: nil);
		var s = if ( (str == nil) || (str == ""), {"IT"}, {str});
		var sides = s.split($:);
		var preTokens, a, end, tokens;
		counter = counter + 1;
		PTDbg << "parse " << str << "\n";
		if (sides.size == 1, {
			tokens = sides[0].split($ );
			a = this.parseHelper(tokens, 0, ctx);
			end = a.key;
		}, {
			tokens = sides[1].split($ );
			preTokens = sides[0].split($ );
			case
			{(preTokens[0] == "L.MIX") || (preTokens[0] == "L.M")} {
				a = this.parseMix(preTokens, tokens, ctx);
				end = a.key;
			}
			{preTokens[0].beginsWith("STRUM") && ctx.includesKey(preTokens[0].asSymbol)} {
				var strumStr = preTokens[0];
				var poly = strumStr.split($.)[1].asInt;
				a = this.parseStrum(poly, preTokens, tokens, ctx);
				end = a.key;
			}
			{preTokens[0].beginsWith("MIDI") && ctx.includesKey(preTokens[0].asSymbol)} {
				a = this.parseMidi(preTokens, tokens, ctx);
				end = a.key;
			}
			{true} {
				PTParseError.new("Unknown PRE: " ++ preTokens[0]).throw;
			};
		});
		while({end < tokens.size}, {
			if (tokens[end] != "", {
				PTParseError.new("Unexpected " ++ tokens[end] ++ "; expected end").throw;
			});
			end = end + 1;
		});
		^a.value;
	}

	parseHelper {|tokens, pos, context|
		var newNode;
		// PTDbg << "parse helper " << counter << " pos " << pos << "\n";
		counter = counter + 1;
		if (PTDbg.slow && (counter > 6000), {
			Error.new("Something blew up in the parser").throw;
		});
		^case
		{pos >= tokens.size} { PTParseError.new("Expected token; got EOF").throw }
		{"^-?[0-9]+\\.?[0-9]*$".matchRegexp(tokens[pos]) || "^\\.[0-9]+$".matchRegexp(tokens[pos])} {
			newNode = PTNode.new(PTLiteral.new(tokens[pos].asFloat()), [], callSite: context.callSite);
			pos+1 -> newNode
		}
		{ (context ? ()).includesKey(tokens[pos].asSymbol)} {
			var op = context[tokens[pos].asSymbol];
			var p = pos + 1;
			var myArgs = Array.new(maxSize: op.nargs);
			{myArgs.size < op.nargs}.while({
				var a;
				if(p >= tokens.size, {
					PTParseError.new(op.name ++ " expects " ++ op.nargs ++ " args").throw
				});
				try {
					a = this.parseHelper(tokens, p, context);
				} { |e|
					myArgs.do { |a| a.free };
					e.throw;
				};
				myArgs = myArgs.add(a.value);
				p = a.key;
			});
			newNode = PTNode.new(op, myArgs, callSite: context.callSite);
			p -> newNode
		}
		{ops.includesKey(tokens[pos])} {
			var op = ops[tokens[pos]];
			var p = pos + 1;
			var myArgs = Array.new(maxSize: op.nargs);
			{myArgs.size < op.nargs}.while({
				var a;
				if(p >= tokens.size, {
					PTParseError.new(op.name ++ " expects " ++ op.nargs ++ " args").throw
				});
				try {
					a = this.parseHelper(tokens, p, context);
				} { |e|
					myArgs.do { |a| a.free };
					e.throw;
				};
				myArgs = myArgs.add(a.value);
				p = a.key;
			});
			newNode = PTNode.new(op, myArgs, callSite: context.callSite);
			p -> newNode
		}
		{tokens[pos] == ""} {
			this.parseHelper(tokens, pos+1, context)
		}
		{true} {
			var c = context;
			while({context != nil},{
				context = context.parent;
			});
			PTParseError.new("Unknown word: " ++ tokens[pos] ++ ".").throw;
		};
	}
}

PTLine {
	var <>id, <>newLine, <>line, <>newNode, <>node, <>proxy, <>fadeTime, <>quant, <>connected, <>connectRate;

	*new { |newLine, newNode, id=nil, fadeTime=0.01, quant=0|
		^super.newCopyArgs(id ? PT.randId, newLine, nil, newNode, nil, nil, fadeTime, quant, nil, nil);
	}

	maybeConnectIt { |other, deferrals|
		var timeToFree = TempoClock.beats;
		case (
			{other == nil}, {},
			{(newNode ? node).usesIt && (connected == nil)}, {
				// If unconnected, and we need a connection, connect
				PTDbg << "Connecting for the first time\n";
				proxy.set(\in, other.proxy);
				(newNode ? node).set(\in, other.proxy);
				connected = other.id;
				connectRate = other.proxy.rate;
			},
			{(connected != nil) && ((connected != other.id) || (connectRate != other.proxy.rate))}, {
				// If connected and the connection quality changed, what to do depends on whether we would use a connection.
				var xsetToThis;
				if ((newNode ? node).usesIt, {
					PTDbg << "Connecting new node or rate\n";
					connected = other.id;
					connectRate = other.proxy.rate;
					xsetToThis = other.proxy;
				}, {
					PTDbg << "Disconnecting for now\n";
					connected = nil;
					connectRate = nil;
					xsetToThis = nil;
				});
				timeToFree = this.timeToFree;
				PTDbg << "Resheduling free for " << timeToFree << "\n";
				deferrals.add( {
					proxy.xset(\in, xsetToThis);
					// TODO make this crossfade
					(newNode ? node).set(\in, xsetToThis);
				});
			},
			{PTDbg << "No connection necessary\n"}
		);
		^timeToFree;
	}

	timeToFree {
		^TempoClock.nextTimeOnGrid(quant: quant ? 0) + (TempoClock.tempo * (fadeTime ? 0));
	}

	usesIt {
		^if ((newNode ? node) == nil, {true}, {(newNode ? node).usesIt});
	}
}

PTScriptNet {
	var server, <parser, <order, <newOrder, <dict, <id, <script, <args, <argProxies, <callSite, <jBus, <kBus, <parentGroup;

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
				"in", PTLine.new("I1", PTNode.new(PTArgOp("I1", \i1, aa[0].rate)), "in"),
				"out", PTLine.new("IT", PTNode.new(PTArgOp("IT", \in, aa[0].rate)), "out"),
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
		^if (prevId == nil, {dict["in"]}, {dict[prevId]});
	}

	initArgProxies {
		argProxies = List.new;
		4.do { |i|
			var a = args[i];
			var n = if (callSite != nil, {
				var p;
				PTDbg << "New proxy on init for " << this << "\n";
				p = callSite.net.newProxy(rate: nil, fadeTime: 0, quant: 0);
				p.set(\in, callSite.net.prevEntryOf(callSite.id).proxy);
				p;
			}, {
				var newN = NodeProxy.new(
					server,
					rate: if(a != nil, {a.rate}, {\control}),
					numChannels: if(a != nil, {2}, {1}));
				if (parentGroup != nil, {
					newN.parentGroup = parentGroup
				});
				newN;
			});
			if (
				a != nil,
				{
					PTDbg << "Setting arg source to " << a << "\n";
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
			I1: PTArgOp("I1", \i1, args[0].rate, args[0].min, args[0].max),
			I2: PTArgOp("I2", \i2, args[1].rate, args[1].min, args[1].max),
			I3: PTArgOp("I3", \i3, args[2].rate, args[2].min, args[2].max),
			I4: PTArgOp("I4", \i4, args[3].rate, args[3].min, args[3].max),
			IT: PTArgOp("IT", \in, r),
			'IT.F': PTArgOp("IT.F", \in, r, 20, 20000),
			'IT.U': PTArgOp("IT.F", \in, r, 0, 1),
			'IT.B': PTArgOp("IT.F", \in, r, -1, 1),
			J: PTNamedLazyBusOp("J", \audio, jBus),
			K: PTNamedLazyBusOp("K", \control, kBus),
			'J=': PTNamedLazyBusSendOp("J", \audio, jBus),
			'K=': PTNamedLazyBusSendOp("K", \control, kBus),
			callSite: (net: this, id: id),
		);
		if (script != nil, {ret.parent = script.context});
		^ret;
	}

	newProxy { |rate=nil, fadeTime, quant, numChannels = 2|
		var ret = NodeProxy.new(server, rate: rate, numChannels: numChannels);
		if (parentGroup != nil, {
			ret.parentGroup = parentGroup;
		});
		ret.fadeTime = fadeTime;
		if (quant != nil, {
			ret.quant = Quant.new(quant, -0.01);
		});
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
		try {
			this.startEdit;
			jBus = PTLazyBus.new(server, \audio);
			kBus = PTLazyBus(server, \control);
			if (script != nil, {
				PTDbg << "Initializing net " << id << " from script " << script << script.linesOrDraft << "\n";
				script.linesOrDraft.do { |x|
					PTDbg << "Adding on init " << x << " to " << id << "\n";
					this.stageAdd(x);
				};
			}, {
				l.do { |x| this.stageAdd(x) };
			});
		} { |e|
			this.free;
			e.throw;
		};
	}

	lines {
		^order.collect({|x| dict[x].line}).reject({|x| x == nil});
	}

	out {
		^dict[order.last].proxy;
	}

	printOn { | stream |
        stream << "PTScriptNet(\n";
		stream << id << "\n";
		stream << "order " << order << "\n";
		stream << "newOrder " << newOrder << "\n";
		stream << this.dict;
		stream << ")\n";
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

	stageAdd { |line, fadeTime, quant|
		this.assertEditing;
		^this.stageInsert(newOrder.size - 1, line, fadeTime, quant);
	}

	stageInsertPassthrough { |index, fadeTime, quant|
		var id = PT.randId;
		var prevEntry = this[index-1];
		var nextEntry = this[index];
		var parsed = parser.parse("IT", this.contextWithItRate(PTScriptNet.nodeOf(prevEntry).rate, id: id));
		var entry = PTLine.new("IT", parsed, id, fadeTime, quant);
		this.assertEditing;
		dict[id] = entry;
		newOrder.insert(index, id);
	}

	stageInsert { |index, line, fadeTime, quant|
		this.assertEditing;
		this.stageInsertPassthrough(index, fadeTime, quant);
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

	rate {
		^(this.newOutputRate ? this.outputRate);
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
			this.stageReplace(index, next.newLine ? next.line);
		}, {
			this;
		});
	}

	outputChanged {
		// PTDbg << "output rate is " << this.outputRate << " new output rate is " << this.newOutputRate << "\n";
		^(this.outputRate != nil) && (this.newOutputRate != nil) && (this.newOutputRate != this.outputRate);
	}

	// Reevaluate the entry at id. If it's already being reevaluated (because, say, the user changed it), return nil.
	reevaluate { |id|
		var entry = this[id];
		var idx = newOrder.indexOf(id);
		^if (entry.newLine == nil, {
			this.stageReplace(idx, entry.newLine ? entry.line);
		}, {
			PTDbg << "We are already being reevaluated\n";
			nil
		});
	}

	stageReplace { |idx, line|
		var id, entry, prev, next, propagate, parsedLine;
		this.assertEditing;
		id = newOrder[idx];
		if (id == nil, {
			Error.new("Bad replace id " ++ idx ++ " size " ++ newOrder.size).throw;
		});
		entry = dict[id];
		if (entry == nil, {
			Error.new("No entry " ++ id ++ " in dict " ++ dict).throw;
		});
		prev = this[idx-1];
		next = this[idx+1];
		entry.newLine = line;
		if (entry.newNode != nil, {
			PTDbg << "Found it -- replaceing a new node with another" << entry.newNode <<"\n";
			entry.newNode.free;
		});
		PTDbg << "Replace gonna parse\n";
		parsedLine = parser.parse(line, context: this.contextWithItRate(PTScriptNet.nodeOf(prev).rate, id: id));
		PTDbg << "Replace parsed\n";
		entry.newNode = parsedLine;
		propagate = false;
		case (
			{entry.node == nil}, { propagate = true;},
			{entry.node.rate != entry.newNode.rate}, {propagate = true;},
		);
		^if (propagate && (next != nil), {
			PTDbg.complex;
			if (next.usesIt, {
				PTDbg << "reevaluating next line " << (idx+1) << "\n";
				this.stageReplace(idx+1, next.newLine ? next.line);
			}, {this});
		}, {
			if (this.outputChanged && (callSite != nil), {
				PTDbg << "reevaluating call site of " << this.id << "\n";
				// When we reevaluate the call site, it could *already* be
				// part of this mess of things we're attempting to do and commit.
				// In that case, reevaluate is designed to return nil.
				PTDbg.complex;
				callSite.net.reevaluate(callSite.id);
			}, {
				this
			});
		});
	}

	setFadeTime { |index, time|
		var node = dict[order[index]];
		node.fadeTime = time;
		if (node.proxy != nil, {
			node.proxy.fadeTime = time;
		});
	}

	setQuant { |index, quant|
		var node = dict[order[index]];
		node.quant = quant;
		if (node.proxy != nil, {
			node.proxy.quant = Quant.new(quant, -0.01);
		});
	}

	abort {
		order.do { |id|
			var entry = dict[id];
			PTDbg.complex;
			if( entry.newNode != nil, {
				entry.newNode.free;
			});
			entry.newLine = nil;
			entry.newNode = nil;
		};
		// At this point anything in newOrder w a newNode is *not* in order
		newOrder.do { |id|
			var entry = dict[id];
			PTDbg.complex;
			if (entry.newNode != nil, {
				entry.newNode.free;
				dict.removeAt(id);
			});
		};
		newOrder = nil;
	}

	commit { |cb, group|
		var outEntry = dict[newOrder.last];
		if (group != nil, {
			parentGroup = group;
		});
		if (argProxies == nil, {
			PTDbg << "INITIALIZING ARG PROXIES\n";
			this.initArgProxies;
		});
		^Routine.new({
			var freeProxies = List.new;
			var freeNodes = List.new;
			var prevEntry = nil;
			var prevId = nil;
			var prevProxyIsNew = false;
			var connect;
			var timeToFree = TempoClock.beats;
			var entriesToLeaveBehind;
			var deferredConnections = List.new;
			var freeFn;
			// Stage 1: allocate all the new node proxies, and connect them together.
			PTDbg << "Beginning commit routine for scriptNet " << id << "\n";
			server.sync;

			newOrder.do { |id, idx|
				var entry = dict[id];
				var node = PTScriptNet.nodeOf(entry);
				var oldIdx = order.indexOf(id);
				// Allocate a proxy if needed
				var proxyIsNew = false;
				var oldPreviousWasDifferent = false;
				if (PTDbg.slow && (1.0.rand < 0.05), { (0.05).yield });
				PTDbg.complex;
				case (
					{entry.proxy == nil}, {
						// New entry
						var newP;
						PTDbg << "new proxy for " << idx << " due to newness " << node.rate << "\n";
						newP = this.newProxy(node.rate, entry.fadeTime, entry.quant);
						entry.proxy = newP;
						proxyIsNew = true;
						if (node.rate == nil, {
							PTDbg << "Nil rate node!! " << node << "\n";
						});
					},
					{entry.proxy.rate != node.rate}, {
						var newP;
						// Rate change entry
						// Schedule the old proxy for freeing
						freeProxies.add(entry.proxy);
						// Make the new one.
						PTDbg << "new proxy for " << idx << " due to rate change " << node.rate << "\n";
						newP = this.newProxy(node.rate, entry.fadeTime, entry.quant);
						entry.proxy = newP;
						proxyIsNew = true;
					},
					{ oldIdx == nil }, {},
					{ (oldIdx != nil) && (oldIdx > 0) && (order[oldIdx-1] != prevId) }, {
						// Possibly removed entry
						oldPreviousWasDifferent = true;
					}
				);
				timeToFree = max(timeToFree, entry.maybeConnectIt(prevEntry, deferredConnections));
				prevProxyIsNew = proxyIsNew;
				prevId = id;
				prevEntry = entry;
			};
			server.sync;
			// Stage 2: Set the source of all the node proxies.
			PTDbg << "Instantiating nodes for " << newOrder << "\n";
			newOrder.do { |id|
				var entry = dict[id];
				if (PTDbg.slow && (1.0.rand < 0.05), { (0.05).yield });
				PTDbg.complex;
				if (entry.newNode != nil, {
					PTDbg << "Committing new node " << entry.newNode << "\n";
					entry.newNode.commit(parentGroup, dynCtx: ());
					PTDbg << "Scheduling for free " << entry.node << " because we have " << entry.newNode << "\n";
					freeNodes.add(entry.node);
					timeToFree = max(timeToFree, entry.timeToFree);
					if (entry.newNode == nil, {
						PTDbg << "WTF " << entry << "\n" << this << "\n";
					});
					PTDbg << "Instantiating source for " << id << " to be " << entry.newNode << "\n";
					entry.proxy.source = { PTScriptNet.maybeMakeStereo(entry.newNode.instantiate) };
					entry.node = entry.newNode;
					entry.line = entry.newLine;
					entry.newNode = nil;
					entry.newLine = nil;
				});
			};
			server.sync;
			0.07.yield;
			// Stage 3: Connect new inputs to any "live" proxies
			PTDbg << "Deferred connecting proxies " << deferredConnections << "\n";
			deferredConnections.do { |x| x.value };
			// Stage 4: Collect anything no longer needed. Exit the transaction.
			entriesToLeaveBehind = order.reject({|x| newOrder.includes(x)});
			entriesToLeaveBehind.do { |id|
				var entry = dict[id];
				PTDbg.complex;
				freeNodes.add(entry.node);
				freeProxies.add(entry.proxy);
				dict.removeAt(id);
			};
			order = newOrder;
			// Indicate we are done with everything but cleanup
			cb.value;
			server.sync;
			freeFn = {
				// Stage 5, later: free some stuff
				freeNodes.do({|x| x.free});
				freeProxies.do({|x|
					PTDbg << "Freeing proxy\n";
					x.clear;
				});
			};
			// If we needed to fade a proxy, schedule the free for after the fade.
			PTDbg << "It is " << TempoClock.beats << " and we will free things at " << (timeToFree + server.latency) << "\n";
			TempoClock.schedAbs(timeToFree + server.latency, freeFn);
		});
	}

	free {
		// clear all my proxies, free all my nodes
		dict.do { |entry|
			entry.proxy.clear;
			entry.node.free;
			entry.newNode.free;

		};

		argProxies.do { |p| p.clear };
		jBus.free;
		kBus.free;
		// remove myself from ref tracking.
		if (script != nil, {script.refs.removeAt(id)});
	}

}

PTScriptOp : PTOp {
	var server, parser, script;

	*new { |server, name, nargs, parser, script|
		^super.newCopyArgs(name, nargs, server, parser, script);
	}

	stringWithArgsResources { |args, resources|
		^if (resources == nil, {
			"Empty PTScriptOp";
		}, {
			var net = resources[0];
			"Script: " ++ net.id;
		});
	}

	set { |key, value, args, resources|
		var net = resources[0];
		super.set(key, value, args, resources);
		net.argProxies.do { |p|
			p.set(key, value);
		};
	}

	min { |args, resources|
		^-10;
	}

	max { |args, resources|
		^10;
	}

	rate { |args, resources|
		var net = resources[0];
		^net.rate;
	}

	alloc { |args, callSite|
		var net = PTScriptNet.new(
			server: server, parser: parser,
			lines: script.linesOrDraft, args: args,
			script: script, callSite: callSite);
		^[net];
	}

	commit { |args, resources, group, dynCtx|
		var net = resources[0];
		PTDbg << "Committing args " << args << " context " << dynCtx.size << "\n";
		args.do { |a|
			a.commit(group, dynCtx);
		};
		PTDbg << "Committing net from op " << net.id << "\n";
		net.commit(group).do { |w| w.yield };
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

PTCountdownLatch {
	var n, cb, id;
	*new { |n, cb|
		^super.newCopyArgs(n, cb, PT.randId).init;
	}

	init {
		// PTDbg << "Initialize latch " << id << " with " << n << "\n";
		if (n == 0, {
			SystemClock.sched(0, {
				// PTDbg << "Boom " << id << "\n";
				cb.value;
			});
		});
	}

	value {
		n = n - 1;
		if (n == 0, {
			// PTDbg << "Bang " << id << "\n";
			cb.value;
		}, {
			// PTDbg << "Tick " << n << id << "\n";
		});
	}
}

PTDescription {
	var <size, <lines;

	*new { |size|
		^super.newCopyArgs(size, List.new);
	}

	linesOrDraft {
		^lines
	}

	load { |newLines, topLevel=false, callback|
		lines.clear.addAll(newLines);
		callback.value;
	}

	add { |line, topLevel=false, callback|
		lines.add(line);
		callback.value;
	}

	validateIndex { |index, allowSize=true|
		if (index < 0, { PTEditError.new("Index must be > 0").throw });
		if (index > lines.size, { PTEditError.new("Index must be < number of lines").throw });
		if ((index == lines.size) && (allowSize.not), { PTEditError.new("Cant operate on index " ++ index).throw });
	}

	insertPassthrough { |index, topLevel=false, callback|
		lines.insert(index, "");
		callback.value;
	}

	removeAt { |index, topLevel=false, callback=nil|
		this.validateIndex(index);
		lines.removeAt(index);
		callback.value;
	}

	replace { |index, line, topLevel=false, callback=nil|
		this.validateIndex(index);
		lines[index] = line;
		callback.value;
	}

	setFadeTime { |index, time|}

	getFadeTime { |index|
		^0;
	}

	setQuant { |index, q|}

	getQuant { |index|
		^1;
	}

	clear { |topLevel=false, callback|
		lines.clear;
		callback.value;
	}
}

PTScript {
	var <size, <lines, <fadeTimes, <quants, <refs, <context, <linesDraft, working;

	*new { |size, context|
		^super.newCopyArgs(size, List.new, List.new, List.new, Dictionary.new, context, nil, false);
	}

	linesOrDraft {
		^(linesDraft ? lines)
	}

	defaultFadeTime {
		^if (context.includesKey('defaultFadeTime'), {context['defaultFadeTime']}, {0.01});
	}

	defaultQuant {
		^if (context.includesKey('defaultQuant'), {context['defaultQuant']}, {1/16});
	}

	load { |newLines, topLevel=false, callback|
		var newFadeTimes = List.new;
		var newQuants = List.new;
		var newLinesActual = List.new;
		PTDbg << "load new lines " << newLines << "\n";
		linesDraft = List.newFrom(lines);
		newLines.do { |line|
			var commaSep = line.split($,);
			linesDraft.add(commaSep[0]);
			newLinesActual.add(commaSep[0]);
			newFadeTimes.add((commaSep[1] ? this.defaultFadeTime).asFloat);
			newQuants.add((commaSep[2] ? this.defaultQuant).asFloat);
		};
		this.makeHappen({ |net|
			net.startEdit;
			newLinesActual.do {|line, i|
				PTDbg << "loading line " << line << "\n";
				net.stageAdd(line, newFadeTimes[i], newQuants[i]);
			};
			// Return the net we staged
			net
		}, topLevel, callback, "load");
		newFadeTimes.do { |x|
			fadeTimes.add(x);
		};
		newQuants.do { |x|
			quants.add(x);
		};
	}

	add { |line, topLevel=false, callback|
		linesDraft = List.newFrom(lines);
		linesDraft.add(line);
		this.makeHappen({ |net|
			net.startEdit;
			net.stageAdd(line, this.defaultFadeTime, this.defaultQuant);
		}, topLevel, callback, "add");
		fadeTimes.add(this.defaultFadeTime);
		quants.add(this.defaultQuant);
	}

	validateIndex { |index, allowSize=true|
		if (index < 0, { PTEditError.new("Index must be >= 0").throw });
		if (index > lines.size, { PTEditError.new("Index must be < number of lines").throw });
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
			net.stageInsertPassthrough(index+1, this.defaultFadeTime, this.defaultQuant);
		}, topLevel, callback, "insertPassthrough");
		// Inserting a passthrough should never fail.
		fadeTimes.insert(index, this.defaultFadeTime);
		quants.insert(index, this.defaultQuant);
	}

	makeHappen { |f, topLevel, callback, from|
		var toCommit = List.new;
		var latch;
		// if (working, {
		//	Error.new("OMG IM WORKING").throw;
		// });
		working = true;
		try {
			var todo = List.newFrom(refs);
			PTDbg << "staging change to " << todo.size << "\n";
			todo.do { |r|
				PTDbg.complex;
				try {
					var candidate;
					PTDbg << "in stage loop\n";
					candidate = f.value(r);
					PTDbg << "got candidate\n";
					if (candidate != nil, {toCommit.add(candidate)});
				} { |err|
					// If we error in the middle of adjusting a net, we need to abort that net too, along with any others.
					r.abort;
					err.throw;
				};
			};
			PTDbg << "staged " << todo.size << "\n";
			// PTDbg << "Check top level\n";
			if (topLevel && (toCommit.select({|p| p.outputChanged}).size > 0), {
				PTCheckError.new("Output must be audio").throw;
			});
		} { |err|
			Post << "Aborting; this error may be expected: \n";
			err.reportError;
			toCommit.do { |p|
				if (p == nil, {
					PTDbg << "Thing to commit is nil?\n"
				}, {
					PTDbg << "aborting\n";
					p.abort;
				});
			};
			linesDraft = nil;
			working = false;
			err.throw;
		};
		//PTDbg << "committing to lines " << linesDraft << "\n";
		lines = linesDraft;
		linesDraft = nil;
		// PTDbg << "new latch of size " << toCommit.size << " and callback " << callback << "\n";
		latch = PTCountdownLatch.new(toCommit.size, {
			working = false;
			callback.value;
		});
		PTDbg << "About to commit asynchronously " << from << " " << toCommit << "\n";
		toCommit.do { |p|
			PTDbg << "Committing " << p.id << " from makeHappen\n";
			p.commit(latch).play;
		};
	}

	removeAt { |index, topLevel=false, callback=nil|
		this.validateIndex(index, allowSize: false);
		linesDraft = List.newFrom(lines);
		linesDraft.removeAt(index);
		this.makeHappen({ |r|
			r.startEdit;
			r.stageRemoveAt(index+1);
		}, topLevel, callback, "removeAt");
		fadeTimes.removeAt(index);
		quants.removeAt(index);
	}

	replace { |index, line, topLevel=false, callback=nil|
		this.validateIndex(index, allowSize: false);
		linesDraft = List.newFrom(lines);
		linesDraft[index] = line;
		this.makeHappen({ |r|
			PTDbg << "replace starting edit\n";
			r.startEdit;
			PTDbg << "replace stage replace\n";
			r.stageReplace(index+1, line)
		}, topLevel, callback, "replace");
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

	setQuant { |index, q|
		this.validateIndex(index);
		refs.do { |r| r.setQuant(index+1, q) };
		quants[index] = q;
	}

	getQuant { |index|
		^quants[index];
	}

	clear { |topLevel=false, callback|
		linesDraft = List.new;
		this.makeHappen( {|r|
			r.startEdit;
			(r.lines.size-2).reverseDo { |i|
				r.stageRemoveAt(i+1);
			};
			r;
		}, topLevel, callback, "clear");
		fadeTimes.clear;
		quants.clear;
	}
}

PT {
	const vowels = "aeiou";
	const consonants = "abcdefghijklmnopqrstuvwxyz";
	const numScripts = 9;
	const scriptSize = 6;

	var server, <scripts, <description, <parser, <main, audio_busses, control_busses, param_busses, <buffers, out_proxy, ctx;

	*new { |server|
		PTDbg << "Adding tick\n";
		SynthDef(\tick, { |bus|
			var env = Env(levels: [0, 1, 0], times: [0, 0.01], curve: 'hold');
			Out.kr(bus, EnvGen.kr(env, doneAction: Done.freeSelf));
		}).add;

		^super.newCopyArgs(server, nil, nil, PTParser.default, nil, nil, nil, nil, nil, nil).init;
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

	reset { parser.counter = 0; PTDbg.complexity = 0; }

	putBusOps { |ctx, name, bus, rate|
		ctx[name.asSymbol] = PTNamedBusOp.new(name, rate, bus);
		ctx[(name ++ ".F").asSymbol] = PTNamedBusOp.new(name ++ ".F", rate, bus, 20, 20000);
		ctx[(name ++ ".U").asSymbol] = PTNamedBusOp.new(name ++ ".U", rate, bus, 0, 1);
		ctx[(name ++ ".B").asSymbol] = PTNamedBusOp.new(name ++ ".B", rate, bus, -1, 1);
		ctx[(name ++ "=").asSymbol] = PTNamedBusSendOp.new(name ++ "=", rate, bus);
	}

	initBusses { |ctx|
		if (buffers == nil, {
			var bufAllocs = List.new;
			buffers = List.new;
			16.do { |i|
				var buf = Buffer.new(server, 8*server.sampleRate, 2);
				bufAllocs.add(buf.allocMsg);
				buffers.add(buf);
			};
			server.listSendBundle(server.latency, bufAllocs);
		});
		if (audio_busses == nil, {
			audio_busses = List.new;
			20.do { |i|
				var bus = Bus.audio(server, numChannels: 2);
				audio_busses.add(bus);
			};
		});
		ctx['AB'] = PTBusOp.new("AB", \audio, audio_busses);
		ctx['AB.F'] = PTBusOp.new("AB.F", \audio, audio_busses, 20, 20000);
		ctx['AB.U'] = PTBusOp.new("AB.U", \audio, audio_busses, 0, 1);
		ctx['AB.B'] = PTBusOp.new("AB.B", \audio, audio_busses, -1, 1);
		ctx['AB='] = PTBusSendOp.new("AB=", \audio, audio_busses);
		this.putBusOps(ctx, "A", audio_busses[16], \audio);
		this.putBusOps(ctx, "B", audio_busses[17], \audio);
		this.putBusOps(ctx, "C", audio_busses[18], \audio);
		this.putBusOps(ctx, "D", audio_busses[19], \audio);

		if (control_busses == nil, {
			control_busses = List.new;
			20.do { |i|
				var bus = Bus.control(server, numChannels: 2);
				control_busses.add(bus);
			};
		});
		ctx['CB'] = PTBusOp.new("CB", \control, control_busses);
		ctx['CB.F'] = PTBusOp.new("CB.F", \control, control_busses, 20, 20000);
		ctx['CB.U'] = PTBusOp.new("CB.U", \control, control_busses, 0, 1);
		ctx['CB.B'] = PTBusOp.new("CB.B", \control, control_busses, -1, 1);
		ctx['CB='] = PTBusSendOp.new("CB=", \control, control_busses);
		this.putBusOps(ctx, "W", control_busses[16], \control);
		this.putBusOps(ctx, "X", control_busses[17], \control);
		this.putBusOps(ctx, "Y", control_busses[18], \control);
		this.putBusOps(ctx, "Z", control_busses[19], \control);

		if (param_busses == nil, {
			param_busses = List.new;
			// Special parameter busses:
			// 16 is the duration of a beat (exposed as M)
			// 17 is the root note (exposed as ROOT)
			// 18 is the output gain (not exposed internally)
			// 19 is the Frequency bus
			// 20 is the Gate bus
			// 21 is the Velocity bus.
			22.do { |i|
				var bus = Bus.control(server, numChannels: 2);
				param_busses.add(bus);
			};
			// Starting tempo
			param_busses[16].value = TempoClock.tempo.reciprocal;
			// Default output gain.
			param_busses[18].value = 0.4;
			// default frequency
			param_busses[19].value = 440;
		});
		// 10 ms lag on params so they crunch less with midi controllers
		ctx['PARAM'] = PTBusOp.new("PARAM", \control, param_busses, 0, 1, 0.01);
		ctx['PRM'] = ctx['PARAM'];
		ctx['P'] = ctx['PARAM'];
		ctx['M'] = PTNamedBusOp.new("M", \control, param_busses[16], 0.1, 2);
		ctx['F'] = PTNamedBusOp.new("F", \control, param_busses[19], 20, 10000);
		ctx['G'] = PTNamedBusOp.new("G", \control, param_busses[20], 0, 1);
		ctx['V'] = PTNamedBusOp.new("V", \control, param_busses[21], 0, 1);

		// Set up buffer operations
		ctx['RD'] = PTBufRdOp.new("RD", buffers, 0);
		ctx['LEN'] = PTBufDurOp.new;
		ctx['RD.L'] = PTBufRdOp.new("RD.L", buffers, 1);
		ctx['WR'] = PTBufWrOp.new("WR", buffers, 0);
		ctx['WR.L'] = PTBufWrOp.new("WR.L", buffers, 1);
		ctx['PHASOR'] = PTPhasorOp.new("PHASOR", server, nil);
		ctx['PHASOR.B'] = PTPhasorOp.new("PHASOR", server, param_busses[16]);

		ctx['PLAY'] = PTBufPlayOp.new("PLAY", [], buffers, param_busses[16]);
		[\bpm -> "T", \beats -> "B", \rate -> "R", \octave -> "O"].do { |assc|
			["PLAY", "LOOP"].do { |baseName, loop|
				var name = baseName ++ "." ++ assc.value;
				var nameCue = name ++ "C";
				var nameS = name ++ "S";
				var nameCueS = nameCue ++ "S";
				var nameX = name ++ "X";
				var nameCueX = nameCue ++ "X";
				ctx[name.asSymbol] = PTBufPlayOp.new(name, [assc.key], buffers, param_busses[16], fade: false, loop: loop);
				ctx[nameCue.asSymbol] = PTBufPlayOp.new(nameCue, [assc.key, \cue], buffers, param_busses[16], fade: false, loop: loop);
				ctx[nameS.asSymbol] = PTBufPlayOp.new(nameS, [assc.key], buffers, param_busses[16], fade: true, loop: loop);
				ctx[nameCueS.asSymbol] = PTBufPlayOp.new(nameCueS, [assc.key, \cue], buffers, param_busses[16], fade: true, loop: loop);
				ctx[nameX.asSymbol] = PTBufPlayOp.new(nameX, [assc.key, \crossfade], buffers, param_busses[16], fade: true, loop: loop);
				ctx[nameCueX.asSymbol] = PTBufPlayOp.new(nameCueX, [assc.key, \cue, \crossfade], buffers, param_busses[16], fade: true, loop: loop);

			};
		};

		ctx['SR'] = PTConst('SR', server.sampleRate);

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
		ctx[\SN] = PTRhythmOp("SN", 0, server, 0.25);
		ctx[\SNT] = PTRhythmOp("SN", 0, server, 0.25/3);
		ctx[\EN] = PTRhythmOp("EN", 0, server, 0.5);
		ctx[\ENT] = PTRhythmOp("ENT", 0, server, 0.5/3);
		ctx[\QN] = PTRhythmOp("QN", 0, server, 1);
		ctx[\QNT] = PTRhythmOp("QNT", 0, server, 1/3);
		ctx[\HN] = PTRhythmOp("HN", 0, server, 2);
		ctx[\HNT] = PTRhythmOp("HNT", 0, server, 2/3);
		ctx[\WN] = PTRhythmOp("WN", 0, server, 4);
		ctx[\WNT] = PTRhythmOp("WN", 0, server, 4/3);

		ctx[\EVERY] = PTEveryOp("EVERY", server);
		ctx[\EV] = PTEveryOp("EVERY", server);

		ctx[\ER] = PTEuclideanOp(server);
		ctx['SN.ER'] = PTConstEuclideanOp("SN.ER", server, 0.25);
		ctx['EN.ER'] = PTConstEuclideanOp("EN.ER", server, 0.5);
		ctx['QN.ER'] = PTConstEuclideanOp("QN.ER", server, 1);



		4.do { |i|
			var beat = i + 1;
			var name = "BT" ++ beat;
			ctx[name.asSymbol] = PTRhythmOp(name, 0, server, 4, i);
			ctx[(name ++ ".&").asSymbol] = PTRhythmOp(name ++ ".&", 0, server, 4, i + 0.5);
			ctx[(name ++ ".E").asSymbol] = PTRhythmOp(name ++ ".E", 0, server, 4, i + 0.25);
			ctx[(name ++ ".A").asSymbol] = PTRhythmOp(name ++ ".A", 0, server, 4, i + 0.75);
			ctx[(name ++ ".PL").asSymbol] = PTRhythmOp(name ++ ".PL", 0, server, 4, i + 0.333);
			ctx[(name ++ ".ET").asSymbol] = PTRhythmOp(name ++ ".PL", 0, server, 4, i + 0.666);
		}
	}

	initPoly { |ctx|
		ctx['MIDI.1'] = PTMidiOp.new("MIDI.2", server, 2);
		ctx['MIDI.2'] = PTMidiOp.new("MIDI.2", server, 3);
		ctx['MIDI.4'] = PTMidiOp.new("MIDI.4", server, 5);
		ctx['MIDI.6'] = PTMidiOp.new("MIDI.6", server, 7);
		ctx['MIDI.8'] = PTMidiOp.new("MIDI.6", server, 9);
		ctx['STRUM.4'] = PTStrumOp.new(server, 4);
	}

	init {
		this.reset;
		ctx = ();
		this.initBusses(ctx);
		this.initBeats(ctx);
		this.initPoly(ctx);
		ctx['PAUSE'] = PTPauseOp.new(server);
		if (out_proxy == nil, {
			out_proxy = NodeProxy.new(server, \audio, 2);
			out_proxy.source = { (param_busses[18].kr * \in.ar([0, 0])).tanh / 2 };
		});
		description = PTDescription.new(6);
		scripts = Array.new(numScripts+1);
		numScripts.do { |i|
			var script = PTScript.new(scriptSize, ctx);
			var oldCtx = ctx;
			scripts.add(script);
			ctx = ();
			ctx.parent = oldCtx;
			5.do { |nargs|
				var scriptOp;
				var name = "$" ++ (i + 1);
				if (nargs > 0, { name = (name ++ "." ++ nargs) });
				scriptOp = PTScriptOp.new(server, name, nargs, parser, script);
				ctx[name.asSymbol] = scriptOp;
			}
		};
		scripts.add(description);
		main = PTScriptNet.new(server: server, parser: parser, lines: [],
			args: [PTNode.new(PTInOp.new, [], nil)], script: scripts[numScripts-1]);
	}

	loadBuffer { |i, size, filepath|
		Routine.new({
			if (filepath != "", {
				buffers[i].allocRead(filepath);
			}, {
				buffers[i].numFrames = size;
				buffers[i].alloc;
			});
			server.sync;
			buffers[i].updateInfo;
		}).play;
	}

	defaultQuant_ { |q|
		ctx['defaultQuant'] = q;
	}

	defaultQuant{
		^if (ctx.includesKey('defaultQuant'), {ctx['defaultQuant'];}, {1/16});
	}

	defaultFadeTime_ { |t|
		ctx['defaultFadeTime'] = t;
	}

	defaultFadeTime{
		^if (ctx.includesKey('defaultFadeTime'), {ctx['defaultFadeTime'];}, {0.01});
	}

	replace { |script, index, line, topLevel=true, callback|
		this.reset;
		scripts[script].replace(index, line, topLevel: topLevel, callback: callback);
	}

	insertPassthrough { |script, index, topLevel=true, callback|
		this.reset;
		scripts[script].insertPassthrough(index, topLevel: topLevel, callback: callback);
	}

	setParam { |param, v|
		param_busses[param].value = v;
	}

	removeAt { |script, index, topLevel=true, callback=nil|
		this.reset;
		scripts[script].removeAt(index, topLevel: topLevel, callback:callback);
	}

	add { |script, line, topLevel=true, callback|
		this.reset;
		scripts[script].add(line, topLevel: topLevel, callback: callback);
	}

	printOn { | stream |
		description.lines.do { |l|
			stream << l << "\n";
		};
		stream << "\n";
		numScripts.do { |i|
			var script = scripts[i];
			stream << "#" << (i + 1) << "\n";
			script.lines.do { |l, i|
				stream << l << "," << script.fadeTimes[i] << "," << script.quants[i] <<"\n";
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

	setQuant { |script, index, q|
		scripts[script].setQuant(index, q);
	}

	getQuant { |script, index|
		^scripts[script].getQuant(index);
	}

	clear { |callback|
		this.reset;
		Routine({
			var latch;
			out_proxy.set(\in, [0,0]);
			server.sync;
			latch = PTCountdownLatch(numScripts, {
				PTDbg << "Clear done\n";
				callback.value;
			});
			PTDbg << "CLEARING old script data\n";
			PTDbg << "Free main\n";
			main.free;
			PTDbg << "Clear scripts\n";
			description.clear;
			scripts.do { |s|
				s.clear(topLevel: false, callback: latch);
			};
		}).play;
	}

	clearFully { |callback|
		this.clear({
			PTDbg << "Free busses\n";
			audio_busses.do { |b| b.free };
			control_busses.do { |b| b.free };
			buffers.do { |b| b.free};
			out_proxy.clear;
			callback.value;
			// Removed because I think norns calls
			// alloc before it is done with free.
			// SynthDef.removeAt(\tick);
		});
	}

	load { |str, callback, errCallback|
		this.reset;
		this.clear({
			PTDbg << "Done with clear\n";
			this.loadOnly(str, {
				Routine({
					try {
						server.sync;
						out_proxy.set(\in, main.out);
						callback.value;
					} { |e|
						errCallback.value(e);
					};
				}).play;
			}, errCallback);
		});
	}

	loadHelper{ |scriptChunks, scriptIndex, topCallback, errCallback|
		var callback = if (scriptIndex == (numScripts - 1), {
			topCallback
		}, {
			{
				PTDbg << "Done with script " << scriptIndex << " loading next\n";
				this.loadHelper(scriptChunks, scriptIndex+1, topCallback, errCallback);
			}
		});
		PTDbg << "loading script " << scriptIndex << " with " << scriptChunks[scriptIndex] << "\n";
		try {
			scripts[scriptIndex].load(scriptChunks[scriptIndex], topLevel: false, callback: callback);
		} { |e|
			PTDbg << "Error on load; calling error callback\n";
			errCallback.value(e);
		};
	}

	loadOnly { |str, callback, errCallback|
		var lines = str.split($\n);
		var curScript = nil;
		var scriptChunks;
		var myErrCallback = { |err|
			this.clear({errCallback.value(err);});
		};
		PTDbg << "INITIALIZING new script data\n";
		this.init;
		scriptChunks = Array.fill(numScripts, {List.new});
		lines.do { |l|
			case {l[0] == $#} {
				curScript = (l[1..].asInteger - 1);
			}
			{l == ""} {
				// pass
			}
			{curScript == nil} {
				description.add(l);
			}
			{true} {
				scriptChunks[curScript].add(l);
			};
		};
		PTDbg << "SCRIPT CHUNKS " << scriptChunks << "\n";
		this.loadHelper(scriptChunks, 0, {
			if (this.out.rate != \audio, {
				//this.init;
				myErrCallback.value(PTCheckError.new("Output of loaded script was not audio"));
			}, {
				callback.value;
			});
		}, myErrCallback);
	}

	out {
		^out_proxy;
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