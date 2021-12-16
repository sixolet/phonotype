// The Phonotype Norns engine, the adapter so that Phonotype can back Norns scripts.

Engine_Phonotype : CroneEngine {
	classvar luaOscPort = 10111;

	var <pt; // a Phonotype
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
				PTDbg << "Reporting error to user " << e << "\n";
				cb.value
			};

		};

		// does this auto wire things together? where should the OSCdef live?
		OSCdef(\scCrowOut, { |msg|
			PTDbg << "calling oscdef crow out:";
			PTDbg << msg;
			 luaOscAddr.sendMsg("/crow/out", msg[3].asFloat, msg[4].asFloat, msg[5].asFloat);
			 //luaOscAddr.sendMsg("/crow/out", msg[0], msg[1], msg[3].asFloat, msg[4].asFloat, msg[5].asFloat]);
			//luaOscAddr.sendMsg("/crow/out", msg[0]);
		},"/crow/out");


		//  :/
		pt = PT.new(context.server);
		pt.load("", {
			PTDbg << "Initialized\n";
			pt.out.play;
		}, {
			PTDbg << "Boo\n";
		});

		this.addCommand("load_scene", "iis", { arg msg;
			PTDbg << "Engine load\n";
			executeAndReport.value(msg[1].asInt, msg[2].asInt, { |cb|
				pt.load(msg[3].asString, true, cb);
			});
		});

		this.addCommand("dump", "", {
			var c = CollStream.new;
			c << pt;
			luaOscAddr.sendMsg("/save", c.collection);
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
			var newFadeTime = (msg[4].asFloat * prevFadeTime).clip(0.005, 60);
			executeAndReport.value(msg[1].asInt, msg[2].asInt, { |cb|
				pt.setFadeTime(msg[2].asInt, msg[3].asInt, newFadeTime);
				cb.value;
			}, "fade time: " ++ newFadeTime.asStringPrec(3));
		});

		this.addCommand("load_buffer", "iis", { arg msg;
			pt.loadBuffer(msg[1].asInt, msg[2].asInt, msg[3].asString);
		});

		this.addCommand("note_on", "iiff", { arg msg;
			PTVoiceAllocator.deliverNoteOn(msg[1].asInt, msg[2].asInt, msg[3].asFloat, msg[4].asFloat);
		});

		this.addCommand("note_off", "iif", { arg msg;
			PTVoiceAllocator.deliverNoteOff(msg[1].asInt, msg[2].asInt, msg[3].asFloat);
		});

		this.addCommand("note_bend", "iiff", { arg msg;
			PTVoiceAllocator.deliverNoteBend(msg[1].asInt, msg[2].asInt, msg[3].asFloat, msg[4].asFloat);
		});

		this.addCommand("crow_out", "iff", { arg msg;
			// msg: [output, slew, volts]
			//luaOscAddr.sendMsg("/crow/out", msg[1].asInt, msg[2].asFloat, msg[3].asFloat);
			// How to notify the sc server to call the \scCrowOut OSCdef?
			SendReply.kr(\scCrowOut, "/crow/out", [msg[1].asInt, msg[2].asFloat, msg[3].asFloat]);
		});

		this.addCommand("quant", "iiii", { arg msg;
			var prevQuant = pt.getQuant(msg[2].asInt, msg[3].asInt);
			var prevQuantIndex = if(prevQuant < 1, {((-1 / prevQuant) + 2).round}, {prevQuant.round});
			var newQuantIndex = (msg[4].asInt + prevQuantIndex).clip(-126, 128);
			var newQuant = if (newQuantIndex <= 0, {-1 / (newQuantIndex - 2)}, {newQuantIndex});
			executeAndReport.value(msg[1].asInt, msg[2].asInt, { |cb|
				pt.setQuant(msg[2].asInt, msg[3].asInt, newQuant);
				cb.value;
			}, "schedule on: " ++ newQuant.asStringPrec(3));
		});

		this.addCommand("default_quant", "f", { |msg|
			pt.defaultQuant = msg[1].asFloat;
		});

		this.addCommand("default_fade_time", "f", { |msg|
			pt.defaultFadeTime = msg[1].asFloat;
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

		this.addCommand("debug", "i", { |msg|
			PTDbg.debug = (msg[1].asInt > 0);
			PTDbg.slow = true;
		});
	}

	free {
		pt.clearFully;
	}
}


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
// [x] Output stage
// [ ] Buffer ops
// [x] TANH, FOLD, CLIP, SOFTCLIP, SINEFOLD
// [x] -, /
// [x] Rhythm ops of some kind.
// [x] Norns param ops
// [x] Clock sync with Norns
// [ ] Load and save from Norns
// [ ] Norns hz, gate for basic midi
// [x] Envelopes: PERC, AR, ADSR
// [x] Sequencer ops
// [x] Sample and hold
// [x] Pitch ops
// [x] L.MIX
// [ ] L.SERIES
// [ ] Polyphonic midi ops???
