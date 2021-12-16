// This file contains code for Phonotype's polyphony support, such as it is.

PTVoiceAllocator {
	var <id, <server, <channel, <voices, <active, <inactive;

	classvar <registry;

	*initClass {
        registry = IdentityDictionary.new;
    }

	*new { |server, channel, nVoices, proxyFactory|
		var vs = List.new;
		var ret;
		channel = channel.asInteger;
		nVoices.do { |i|
			var proxy = proxyFactory.value;
			vs.add( (
				proxy: proxy,
				index: i,
				note: nil,
				freq: nil,
				velocity: nil,
				gateBus: Bus.control(numChannels: 1),
				freqBus: Bus.control(numChannels: 1),
				velocityBus: Bus.control(numChannels: 1),
				silent: true) );
			vs[i].freqBus.set(20); // Necessary to avoid a click on first use for some waveforms.
		};
		ret = super.newCopyArgs(PT.randId, server, channel, vs, Dictionary.new, List.newFrom(vs));
		if (registry.includesKey(channel).not) {
			registry[channel] = (Dictionary.new);
		};
		registry[channel][ret.id] = ret;
		^ret;
	}

	*deliverNoteOn { |channel, note, freq, velocity|
		channel = channel.asInteger;
		note = note.asInteger;
		if (registry.includesKey(channel)) {
			registry[channel].do { |a|
				a.noteOn(note, freq, velocity);
			};
		};
	}

	*deliverNoteBend { |channel, note, freq, bendSteps|
		channel = channel.asInteger;
		note = note.asInteger;
		if (registry.includesKey(channel)) {
			registry[channel].do { |a|
				a.noteBend(note, freq, bendSteps);
			};
		};
	}

	*deliverNoteOff { |channel, note, freq|
		channel = channel.asInteger;
		note = note.asInteger;
		if (registry.includesKey(channel)) {
			registry[channel].do { |a|
				a.noteOff(note);
			};
		};
	}

	free {
		registry[channel].removeAt(id);
		if (registry[channel].size == 0) {
			registry.removeAt(channel);
		};
		voices.do { |v|
			v.proxy.clear;
			v.gateBus.free;
			v.freqBus.free;
			v.velocityBus.free;
		};
	}

	allocVoice { |note, freq|
		var ret;
		if (inactive.size <= 1) {
			var best = 0;
			var bestFreqDif = 100000;
			active.do { |v|
				var freqDif = (v.freq - freq).abs;
				if (freqDif < bestFreqDif) {
					best = v.note;
				}
			};
			this.noteOff(best);
		};
		ret = inactive.pop();
		ret.note = note;
		ret.freq = freq;
		active[note] = ret;
		ret.silent = false;
		^ret;
	}

	silent { |index|
		var vox = voices[index];
		vox.silent = true;
		if (active.includesKey(vox.note).not) {
			vox.proxy.parentGroup.run(false);
		}
	}

	noteOn { |note, freq, velocity|
		var voice, msgs;
		if (active.includesKey(note)) {
			voice = active[note];
		} {
			voice = this.allocVoice(note, freq);
		};
		voice.freq = freq;
		voice.velocity = velocity;
		voice.note = note;
		voice.proxy.parentGroup.run(true);
		msgs = List.new;
		msgs.add(voice.freqBus.setMsg(freq));
		msgs.add(voice.velocityBus.setMsg(velocity));
		msgs.add(voice.gateBus.setMsg(1));
		server.listSendBundle(nil, msgs);
	}

	noteOff { |note|
		if (active.includesKey(note)) {
			var voice = active[note];
			voice.gateBus.set(0);
			if (voice.silent) {
				voice.proxy.parentGroup.run(false);
			};
			active.removeAt(note);
			inactive.addFirst(voice);
		};
	}

	noteBend { |note, freq, bendSt|
		if (active.includesKey(note)) {
			var voice = active[note];
			voice.freqBus.set(freq);
		};
	}

}

PTMidiOp : PTOp {
	var server, <poly;

	*new { |name, server, poly|
		^super.newCopyArgs(name, poly+1, server, poly);
	}

	min { |args|
		^args[1..].collect(_.min).sum;
	}

	max { |args|
		^args[1..].collect(_.max).sum;
	}

	check { |args|
		if (args[0].isConstant.not) {
			PTCheckError.new("MIDI channel must be constant.").throw;
		}
	}

	rate { |args|
		^\audio;
	}

	set { |key, value, args, resources|
		var voiceAllocator = resources[0].value;
		super.set(key, value, args, resources);
		voiceAllocator.voices.do { |v|
			v.proxy.set(key, value);
		};
	}

	alloc { |args, callSite|
		// VoiceAllocator
		^[nil, (site: callSite, free: {}), PTListFreer.new, nil];
	}

	commit { |args, resources, group, dynCtx|
		var callSite = resources[1].site;
		var parentGroups = List.new;
		var allocator = PTVoiceAllocator.new(server, args[0].min, poly, {
			var proxy =  callSite.net.newProxy(rate: \audio, fadeTime: 0, quant: 0);
			var newGroup = Group.new(group);
			var prevProxy = callSite.net.prevEntryOf(callSite.id).proxy;
			NodeWatcher.register(newGroup, assumePlaying: true);
			proxy.parentGroup = newGroup;
			parentGroups.add(proxy.parentGroup);
			proxy.set(\in, prevProxy);
			proxy;
		});
		args[1..].do { |a, i|
			var vox = allocator.voices[i];
			var newCtx = (
				gate: vox.gateBus,
				freq: vox.freqBus,
				velocity: vox.velocityBus,
			);
			newCtx.parent = dynCtx;
			a.commit(vox.proxy.parentGroup, newCtx);
			vox.proxy.source = { a.instantiate };
		};
		resources[0] = allocator;
		resources[2] = parentGroups;
		resources[3] = OSCdef.new(allocator.id, { |msg|
			var i = msg[2];
			PTDbg << "Allowing pause " << i << "\n";
			allocator.silent(i);
		}, ("/" ++ allocator.id).asSymbol);
	}

	instantiate { |args, resources|
		var allocator = resources[0];
		var voiceUgens = List.new;
		allocator.voices.do { |voice, i|
			var voiceUgen = voice.proxy.ar(2);
			voiceUgens.add(voiceUgen);
			SendReply.kr(A2K.kr(DetectSilence.ar(Impulse.ar(0) + Mix.ar(voiceUgen.abs), doneAction: Done.none)), ("/" ++ allocator.id).asSymbol, [], i);
		};
		^Mix.ar(voiceUgens);
	}
}

PTCrowOut : PTOp {
	//
	var server, index, frequency, iargs, v;

	*new { |name, server|
		^super.newCopyArgs("CROW", 1, server);
	}

	rate { |args, resources|
		^\audio;
	}

	min { |args, resources|
		^0;
	}

	max { |args, resources|
		^1;
	}

	instantiate { |args, resources|
	    // should SendReply happen here?
	    //PTDbg << "crow out args, resources\n";
	    //PTDbg << args;
	    //PTDbg << resources;
	    var iargs = PTOp.instantiateAll(args);
	    var v = iargs[0];
	    ^SendReply.kr(Impulse.kr(0), "/crow/out", values: [1, 0.01, v]);
	}
	// TODO: do we need a commit method?

}


PTPolyArgCaptureOp : PTOp {
	var orgIdx, minVal, maxVal, index;

	*new { |name, orgIdx, minVal = -10, maxVal = 10, index=0|
		^super.newCopyArgs(name, 0, orgIdx, minVal, maxVal, index);
	}

	rate { |args, resources|
		^\control;
	}

	min { |args, resources|
		^minVal;
	}

	max { |args, resources|
		^maxVal;
	}

	instantiate { |args, resources|
		var ret;
		var org = \organize.kr([0, 440, 0, 0]);
		ret = Latch.kr(Impulse.kr(0) + org[orgIdx.asInteger], BinaryOpUGen('==', org[3], index));
		^ret;
	}
}

PTPolyTrigOp : PTOp {
	var index;

	*new { |name, index=0|
		^super.newCopyArgs(name, 0, index);
	}

	rate { |args, resources|
		^\control;
	}

	min { |args, resources|
		^0;
	}

	max { |args, resources|
		^1;
	}

	instantiate { |args, resources|
		var org = \organize.kr([0, 440, 0, 0]);
		^DelayN.kr(org[0] * BinaryOpUGen('==', org[3], index), 0.002, 0.002);
	}
}


PTStrumOp : PTOp {
	var server, poly;

	*new { |server, poly|
		^super.newCopyArgs("STRUM", poly + 3, server, poly);
	}

	min { |args|
		^args[3..].collect(_.min).sum;
	}

	max { |args|
		^args[3..].collect(_.max).sum;
	}

	rate { |args|
		^\audio;
	}

	set { |key, value, args, resources|
		var synthProxies = resources[1].value;
		super.set(key, value, args, resources);
		synthProxies.do { |p|
			p.set(key, value);
		};
	}

	alloc { |args, callSite|
		// groups, synthproxies, gateproxy, callSite
		// 0       1             2          3
		^[PTListFreer.new, PTListFreer.new, nil, (site: callSite, free: {})];
	}

	commit { |args, resources, group|
		var myGroups, synthProxies, gateProxy, placeOfCall, prevProxy;
		var trig = args[0];
		var freq = args[1];
		var velocity = args[2];
		var bodies = args[3..];

		placeOfCall = resources[3].site;

		// Initialize groups
		myGroups = poly.collect({Group.new});
		resources[0].value = myGroups;
		myGroups.do{|x| NodeWatcher.register(x, assumePlaying: true)};

		// Initialize synth proxies
		synthProxies = poly.collect({placeOfCall.net.newProxy(rate: \audio, fadeTime: 0, quant: 0)});
		resources[1].value = synthProxies;

		// Initialize gate proxy.
		gateProxy = placeOfCall.net.newProxy(rate: \control, fadeTime: 0, quant: 0, numChannels: 4);
		resources[2] = gateProxy;

		// plumb input.
		prevProxy = placeOfCall.net.prevEntryOf(placeOfCall.id).proxy;
		gateProxy.set(\in, prevProxy);

		// Commit the nodes for the outer parameters
		trig.commit(group);
		freq.commit(group);
		velocity.commit(group);

		// Match up bodies with voices, commit them
		PTDbg << "Making synths\n";
		synthProxies.do { |x, i|
			var body = bodies[i];
			x.parentGroup = myGroups[i];
			x.set(\in, prevProxy);
			x.set(\organize, gateProxy);
			gateProxy.set(("voice" ++ i).asSymbol, x);
			body.commit(myGroups[i]);
			x.source = {
				var inst = body.instantiate;
				PTScriptNet.maybeMakeStereo(inst);
			};
		};

		// Make the gate proxy.
		PTDbg << "Making gate\n";
		gateProxy.source = {
			var res, detect, t, frq, vel, count;

			t = trig.instantiate;
			if (t.size > 0, {
				t = Mix.kr(t);
			});
			frq = freq.instantiate;
			if (frq.size > 0, {
				frq = frq[0];
			});
			vel = velocity.instantiate;
			if (vel.size > 0, {
				vel = Mix.kr(vel)/vel.size;
			});
			PTDbg << "Stepper\n";
			count = Stepper.kr(t, min: 0, max: poly - 1);
			PTDbg << "Voices\n";

			poly.do { |i|
				var res = ("voice" ++ i).asSymbol.ar([0,0]);
				var imp = Impulse.kr(0);
				detect = Mix.kr(A2K.kr(res).abs);
				Pause.kr(imp + BinaryOpUGen('==', count, i) + DetectSilence.kr(imp + detect, doneAction: Done.none).not, myGroups[i].nodeID);
			};
			[t, frq, vel, count];
		};
		PTDbg << "Gate is set\n";

	}

	instantiate { |args, resources|
		^Mix.ar(resources[1].value);
	}
}

PTPauseOp : PTOp {
	var server;

	*new { |server|
		^super.newCopyArgs("PAUSE", 2, server);
	}

	min { |args|
		^args[1].min;
	}

	max { |args|
		^args[1].max;
	}

	rate { |args|
		^args[1].rate;
	}

	set { |key, value, args, resources|
		super.set(key, value, args, resources);
		resources[1..2].do { |p|
			p.set(key, value);
		};
	}

	commit { |args, resources, group|
		var myGroup, synthProxy, gateProxy, placeOfCall;
		placeOfCall = resources[3].site;
		PTDbg << "Committing pause called from " << placeOfCall.net.id << " line " << placeOfCall.id << "\n";

		myGroup = Group.new;
		synthProxy = placeOfCall.net.newProxy(rate: args[1].rate, fadeTime: 0, quant: 0);
		gateProxy = placeOfCall.net.newProxy(rate: \control, fadeTime: 0, quant: 0);
		resources[0] = myGroup;
		resources[1] = synthProxy;
		resources[2] = gateProxy;
		NodeWatcher.register(myGroup, assumePlaying: true);
		server.sync;
		synthProxy.parentGroup = myGroup;
		synthProxy.set(\in, placeOfCall.net.prevEntryOf(placeOfCall.id).proxy);
		gateProxy.set(\in, placeOfCall.net.prevEntryOf(placeOfCall.id).proxy);
		gateProxy.set(\side, synthProxy);
		args[0].commit(group);
		args[1].commit(myGroup);
		synthProxy.source = {
			PTScriptNet.maybeMakeStereo(args[1].instantiate);
		};
		gateProxy.source = {
			var res, detect, gate;
			res = \side.kr([0, 0]);
			gate = args[0].instantiate;
			if (gate.size > 0, {
				gate = Mix.kr(gate);
			});
			detect = Mix.kr(res.abs);
			Pause.kr(gate + DetectSilence.kr(detect, doneAction: Done.none).not, myGroup.nodeID);
			detect;
		};

	}

	alloc { |args, callSite|
		// group, synthproxy, gateproxy, callSite
		^[nil, nil, nil, (site: callSite, free: {})];
	}

	instantiate { |args, resources|
		^if(this.rate(args) == \control, {
			resources[1].kr;
		}, {
			resources[1].ar;
		});
	}
}
