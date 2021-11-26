// This file contains ops that deal with turning numbered notes into frequencies.

PTToCPSOp : PTOp {

	var rootBus;

	*new { |name, rootBus|
		^super.newCopyArgs(name, 1, rootBus);
	}

	min { ^0.1}
	max { ^10000 }

	instantiate { |args, resources|
		^(rootBus.kr.cpsmidi + args[0].instantiate).midicps;
	}
}

PTToCPSScaleOp : PTToCPSOp {
	var scale;
	*new { |name, rootBus, scale|
		^super.newCopyArgs(name, 1, rootBus, scale);
	}

	instantiate { |args, resources|
		var toKey = DegreeToKey.kr(
			scale.as(LocalBuf),
			args[0].instantiate,
			scale.stepsPerOctave);
		^(rootBus.kr.cpsmidi + toKey).midicps;
	}
}