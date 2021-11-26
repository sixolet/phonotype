// Rhythmic ops. These mostly start a pattern on the sclang side that causes a trigger in a given rhythm.

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
			PTDbg << "Bus " << b << " server " << server << "\n";
			idx = b.index;
			if (quant == 0, { Error.new("OOPOS quant zero").throw; });
			esp = pattern.play(TempoClock.default, quant: q);
			PTDbg << "Starting beat" << idx << "\n";
			freer = PTFreer({
				PTDbg << "Stopping beat" << idx << "\n";
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

PTEuclideanOp : PTOp {
	var server;

	*new { |server|
		// Args are fill, len, offset, duration (per-beat)
		^super.newCopyArgs("ER", 4, server)
	}

	min { ^0 }

	max { ^1 }

	rate { ^\control }

	alloc { |args, callSite|
		// Resources will be filled with:
		// 0: result bus
		// 1: length and offset bus
		// 2: synth that sets length and offset bus
		// 3: freer of pbind
		^[nil, nil, nil, nil];
	}

	check { |args|
		args[1].isConstant.not.if {
			PTCheckError.new("ER length must be constant").throw;
		};
		args[3].isConstant.not.if {
			PTCheckError.new("ER duration must be constant").throw;
		};
	}

	mono {|x|
		^if (x.size == 0) {x} {x[0]}
	}

	getDur { |args|
		^args[3].min;
	}

	instantiate { |args, resources|
		var lenInNotes = args[1].min;
		var dur = this.getDur(args);
		var length = lenInNotes*dur;
		var p, b, idx, esp, freer, pattern;
		var q = Quant.new(length);
		var beats = (Rest(dur))!lenInNotes;

		var getter = { |arr|
			var fill = arr[0].floor.asInteger;
			var offset = arr[1].floor.asInteger;
			beats = (fill / lenInNotes * (0..lenInNotes - 1)).floor.differentiate.asInteger.min(1)[0] = if (fill <= 0) { 0 } { 1 };
			beats = beats.rotate(offset);
		};

		var euclideanRoutine = Routine({
			while {true} {
				beats.size.do { |i|
					p.get(getter);
					if(beats[i] == 0, {
						Rest(dur).yield;
					}, {
						dur.yield;
					});
				};
			};
		});

		// Initialize beats with min fill and offset
		getter.value([args[0].min, args[2].min]);

		if (resources[0] == nil, {
			b = Bus.control(server, numChannels: 1);
			p = Bus.control(server, numChannels: 2);
			pattern = Pbind(
				\instrument, \tick, \dur, euclideanRoutine,
				\bus, b.index,
			);
			idx = b.index;
			esp = pattern.play(TempoClock.default, quant: q);
			PTDbg << "Starting euclidean" << idx << "\n";
			freer = PTFreer({
				PTDbg << "Stopping euclidean" << idx << "\n";
				esp.stop;
			});
			resources[0] = b;
			resources[1] = p;
			PTDbg << "Making\n";
			resources[2] = NodeProxy.new;
			resources[2].source = {
				var fill = this.mono(args[0].instantiate);
				var offset = this.mono(args[2].instantiate);
				Out.kr(p, [fill, offset]);
			};
			resources[3] = freer;
			p.get(getter);
		}, {
			b = resources[0];
		});
		^b.kr;
	}
}

PTConstEuclideanOp : PTEuclideanOp {

	var dur;

	*new { |name, server, dur|
		// Args are fill, len, offset, duration (per-beat)
		^super.newCopyArgs(name, 3, server, dur)
	}

	getDur { |args|
		^dur;
	}

	check { |args|
		args[1].isConstant.not.if {
			PTCheckError.new("ER length must be constant").throw;
		};
	}
}

PTEveryOp : PTOp {
	var server;

	*new { |name, server|
		^super.newCopyArgs(name, 2, server)
	}

	min { ^0 }

	max { ^1 }

	check { |args|
		if (args[0].isConstant.not || args[1].isConstant.not, {
			PTCheckError.new("EVERY args must be constant");
		});
	}

	rate { ^\control }

	alloc { |args, callSite|
		^[nil, nil];
	}

	instantiate { |args, resources|
		var b, idx, esp, freer, pattern;
		var quant = args[0].min;
		var phase = args[1].min;
		var q = Quant.new(quant, phase: phase);
		if (resources[0] == nil, {
			b = Bus.control(server, numChannels: 1);
			pattern = Pbind(\instrument, \tick, \dur, quant, \bus, b.index);
			PTDbg << "Bus " << b << " server " << server << "\n";
			idx = b.index;
			if (quant == 0, { Error.new("OOPOS quant zero").throw; });
			esp = pattern.play(TempoClock.default, quant: q);
			PTDbg << "Starting beat" << idx << "\n";
			freer = PTFreer({
				PTDbg << "Stopping beat" << idx << "\n";
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