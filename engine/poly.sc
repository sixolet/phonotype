// This file contains code for Phonotype's polyphony support, such as it is.

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