
TestPT : UnitTest {

	var p, freeBusses;

	setUp {
		p = PT.new(Server.default);
		freeBusses = Server.default.audioBusAllocator.countFree;
	}

	tearDown {
		var done = false;
		p.clear;
		SystemClock.sched(0.5, {done = true});
		this.wait({done}, "waiting for some time to let cleanup happen", 1);
	}

	test_loadFresh {
		p.load("
#1
TRI I1
#9
SIN 440
* IT $1.1 0.5
"
		);
	}

	test_removeLast {
		p.load("
#9
SIN 440
"
		);
		p.removeAt(8, 0);
	}

	test_removeExposingControlRate {
		p.load("
#9
X= UNI VSAW 1 0
* SIN 440 X
"
		);
		// This should fail.
		this.assertException({
			p.removeAt(8, 1);
		}, PTCheckError);
	}

	test_removeDoesntExist {
		p.load("
#9
SIN 440
"
		);
		p.removeAt(8, 0);
		this.assertException({
			p.removeAt(8, 0);
		}, PTCheckError);
	}

	test_lotsOfOpsLoadWithoutError {
		p.load("
#1
+ 0.5 * 0.5 SIN I1
#9
SIN 440
PSIN 220 * PI IT
* IT $1.1 0.5
");

		p.load("
#9
PULSE LR 217 223 UNI LR SIN 0.3 SIN 0.4
* 0.3 PAN RLPF IT SCL SIN 0.2  200 1000 0.3 SIN 1
+ IT * 0.5 DEL.F IT + UNI SIN 0.02 0.04 3
");

p.load("
#9
AB= 3 * SIN 440 X
X= SIN 1
* * AB 3 0.2 SIN 0.3
");
	}

	test_sclOp {
		p.load("
#1
SCL TRI I1 0 5
#9
SIN 440
* IT $1.1 0.5
"
		);
		this.assertEquals(p.scripts[0].refs.size, 1);
		p.scripts[0].refs.do { |r|
			this.assertEquals(r[1].node.min, 0);
			this.assertEquals(r[1].node.max, 5);
		};
	}

	test_tooManyArgs {
		var error = nil;
		this.assertException( {
			p.load("
#9
SIN 440 220
"
			);
		}, Error);
	}

	test_setFadeTime {
		p.load("
#1
TRI I1
#9
SIN 440
* IT $1.1 0.5
"
		);
		p.setFadeTime(0, 0, 0.4);
		this.assertEquals(p.scripts[0].refs.size, 1);
		p.scripts[0].refs.do { |r|
			this.assertEquals(r[1].line, "TRI I1");
			this.assertEquals(r[1].proxy.fadeTime, 0.4);
		};
	}

	test_replaceStaysControl {
		p.load("
#1
TRI I1
#9
SIN 440
* IT $1.1 0.5
"
		);
		p.replace(0, 0, "+ 0.5 * 0.5 TRI I1");
		this.assertEquals(p.scripts[0].refs.size, 1);
		p.scripts[0].refs.do { |r|
			this.assertEquals(r.out.rate, \control);
		};
	}

	test_replaceGoesAudioInScriptCall {
		var done = false;
		p.load("
#1
TRI I1
#9
SIN 440
* IT $1.1 0.5
"
		);
		p.replace(0, 0, "+ 0.5 * 0.5 TRI 220");
		SystemClock.sched(0.2, {done = true});
		this.wait({done}, "waiting for some time to let cleanup happen", 1);
		this.assertEquals(p.scripts[0].refs.size, 1);
		p.scripts[0].refs.do { |r|
			this.assertEquals(r.out.rate, \audio);
		};
	}

	test_replaceFailsChangesRateOfWholeThing {
		var done = false;
		var error = nil;
		p.load("
#9
SIN 440
* IT 0.5
"
		);
		try {
		  p.replace(8, 1, "SIN 0.5");
		} { |err|
			error = err;
		};
		this.assert(error != nil);
		SystemClock.sched(0.2, {done = true});
		this.wait({done}, "waiting for some time to let cleanup happen", 1);
		this.assertEquals(p.scripts[8].lines, List.newFrom(["SIN 440", "* IT 0.5"]));
	}

	test_replaceFailsChangesRateOfWholeThing2 {
		var done = false;
		var error = nil;
		p.load("
#9
SIN 440
* IT 0.5
"
		);
		try {
		  p.replace(8, 1, "0");
		} { |err|
			"THE ERROR IS".postln;
			err.errorString.postln;
			error = err;
		};
		this.assert(error != nil);
		SystemClock.sched(0.2, {done = true});
		this.wait({done}, "waiting for some time to let cleanup happen", 1);
		this.assertEquals(p.scripts[8].lines, List.newFrom(["SIN 440", "* IT 0.5"]));
	}

	test_replaceDoesntLeakRefs {
		var done = false;
		p.load("
#1
TRI I1
#9
SIN 440
* IT $1.1 0.5
"
		);
		p.replace(8, 1, "* SIN 1 IT");
		SystemClock.sched(0.2, {done = true});
		this.wait({done}, "waiting for some time to let cleanup happen", 1);
		this.assertEquals(p.scripts[8].lines, List.newUsing(["SIN 440", "* SIN 1 IT"]));
		this.assertEquals(p.scripts[0].refs.size, 0);
	}
}

TestPTScriptNet : UnitTest {
	var parser;
	var net;

	setUp {
        // this will be called before each test
		parser = PTParser.default;
		net = PTScriptNet.new(Server.default, parser, [], [parser.parse("SIN 440")]);
    }

	test_initializationToSin {
		this.assertEquals(net.out.rate, \audio);
	}

	test_insertPassthrough {
		net.insertPassthrough(1);
		this.assertEquals(net.lines[0], "IT");
	}

	test_insert {
		net.insert(1, "* IT SIN 1");
		this.assertEquals(net.lines[0], "* IT SIN 1");
		net.insert(1, "* IT SIN 2");
		this.assertEquals(net.lines[0], "* IT SIN 2");
		this.assertEquals(net.lines[1], "* IT SIN 1");
	}

	test_keepAudioRate {
		var p = net.add("* IT SIN 1");
		this.assert(p.propagate.not);
		this.assert(net.out.rate == \audio);
		this.assertEquals(net.lines[0], "* IT SIN 1");
	}

	test_makeControlRate {
		var p = net.add("SIN 1");
		this.assert(p.propagate);
		this.assertEquals(net.out.rate, \control);
	}

	test_replaceOnly {
		var p = net.add("* IT SIN 1");
		this.assert(p.propagate.not);
		p = net.prepareReplace(1, "SIN 1");
		this.assert(p.propagate);
		p.commit;
		this.assertEquals(net.out.rate, \control);
		this.assertEquals(net.lines[0], "SIN 1");
	}

	test_replacePropagates {
		var p;
		net = PTScriptNet.new(Server.default, parser, ["SIN 1", "* IT SIN 2"], [parser.parse("SIN 440")]);
		this.assertEquals(net.out.rate, \control);
		p = net.prepareReplace(1, "SIN 440");
		this.assert(p.propagate);
		p.commit;
		this.assertEquals(net.out.rate, \audio);
		this.assertEquals(net.lines[0], "SIN 440");
		this.assertEquals(net.lines[1], "* IT SIN 2");
	}

	test_replaceFails {
		var p;
		var e;
		net = PTScriptNet.new(Server.default, parser, ["SIN 1", "* IT SIN 2"], [parser.parse("SIN 440")]);
		this.assertEquals(net.out.rate, \control);
		try {
			p = net.prepareReplace(1, "WOW BAD");
		} { |err|
			e = err;
		};
		this.assert(e != nil);
		this.assertEquals(net.lines[0], "SIN 1");
		this.assertEquals(net.lines[1], "* IT SIN 2");
	}
}