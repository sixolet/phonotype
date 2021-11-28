// This file contains ops that read and write from busses.

PTBusOp : PTOp {

	var rate, busses, min, max, lag;

	*new { |name, rate, busses, min= -10, max= 10, lag=nil|
		^super.newCopyArgs(name, 1, rate, busses, min, max, lag);
	}

	check { |args|
		if (args[0].isConstant.not, {
			PTCheckError.new(name ++ " needs constant bus number").throw;
		});
		if (args[0].min >= busses.size, {
			PTCheckError.new(name ++ " max bus number is " ++ busses.size).throw;
		});
	}

	min { |args, resources|
		^min;
	}

	max { |args, resources|
		^max;
	}

	instantiate { |args, resources|
		var n = args[0].min;
		var ret = if (rate == \audio, {InFeedback.ar(busses[n].index, numChannels: 2)}, {busses[n].kr});
		^ if (lag == nil, {ret}, {ret.lag(lag)});
	}

	rate { |args|
		^rate
	}
}

PTDynBusOp : PTOp {
	var rate, symbol, min, max;

	*new { |name, rate, symbol, min, max|
		^super.newCopyArgs(name, 0, rate, symbol, min, max);
	}

	min { |args, resources|
		^min;
	}

	max { |args, resources|
		^max;
	}

	alloc {
		^[nil];
	}

	commit { |args, resources, group, dynCtx|
		PTDbg << "Storing away dynamic bus num for " << symbol << " " << dynCtx[symbol] << "\n";
		resources[0] = dynCtx[symbol];
	}

	instantiate { |args, resources|
		^if (rate == \audio) {
			In.ar(resources[0], 1);
		} {
			In.kr(resources[0], 1);
		}
	}

}

PTBusSendOp : PTOp {

	var rate, busses;

	*new { |name, rate, busses|
		^super.newCopyArgs(name, 2, rate, busses);
	}

	check { |args|
		if (args[0].isConstant.not, {
			PTCheckError.new(name ++ " needs constant bus number").throw;
		});
		if (args[0].min >= busses.size, {
			PTCheckError.new(name ++ " max bus number is " ++ busses.size).throw;
		});
	}

	min { |args, resources|
		^args[1].min;
	}

	max { |args, resources|
		^args[1].max;
	}

	*prepareAudio { |a|
		^if(a.rate != \audio,
				{ K2A.ar(a) },
				{a});
	}

	instantiate { |args, resources|
		var n = args[0].min;
		var a = PTScriptNet.maybeMakeStereo(args[1].instantiate);
		^if (rate == \audio, {
				var aa = PTBusSendOp.prepareAudio(a);
				Out.ar(busses[n], aa);
				aa;
			},
			{ Out.kr(busses[n], a); a});
	}
}


PTNamedBusOp : PTOp {

	var rate, bus, min, max;

	*new { |name, rate, bus, min= -10, max=10|
		^super.newCopyArgs(name, 0, rate, bus, min, max);
	}

	min { |args, resources|
		^min;
	}

	max { |args, resources|
		^max;
	}

	instantiate { |args, resources|
		^if (rate == \audio, {InFeedback.ar(bus.index, numChannels: 2)}, {bus.kr});
	}

	rate { |args|
		^rate
	}
}

PTNamedLazyBusOp : PTNamedBusOp {

	*new { |name, rate, bus, min= -10, max=10|
		^super.newCopyArgs(name, 0, rate, bus, min, max);
	}

	instantiate { |args, resources|
		^if (rate == \audio, {InFeedback.ar(bus.get.index, numChannels: 2)}, {bus.get.kr});
	}
}

PTLazyBus {
	var server, rate, bus;

	*new { |server, rate|
		^super.newCopyArgs(server, rate, nil);
	}

	get {
		if (bus == nil, {
			bus = Bus.alloc(rate, numChannels: 2, server: server);
		});
		^bus;
	}

	free {
		if (bus != nil, {bus.free});
	}
}

PTNamedBusSendOp : PTOp {

	var rate, bus;

	*new { |name, rate, bus|
		^super.newCopyArgs(name, 1, rate, bus);
	}

	min { |args, resources|
		^args[0].min;
	}

	max { |args, resources|
		^args[0].max;
	}

	instantiate { |args, resources|
		var a = PTScriptNet.maybeMakeStereo(args[0].instantiate);
		^if (rate == \audio, {
				var aa = PTBusSendOp.prepareAudio(a);
				Out.ar(bus, aa);
				aa;
			},
			{ Out.kr(bus, a); a});
	}
}

PTNamedLazyBusSendOp : PTNamedBusSendOp {

	instantiate { |args, resources|
		var a = PTScriptNet.maybeMakeStereo(args[0].instantiate);
		var b = bus.get;
		if (b == nil, {
			Error.new("Oh no bus nil " ++ bus ++ " and its get "  ++ b).throw;
		});
		^if (rate == \audio,{
				var aa = PTBusSendOp.prepareAudio(a);
				Out.ar(b, aa);
				aa;
			},
			{ Out.kr(b, a); a});
	}
}