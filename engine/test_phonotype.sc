
PTTestCallback {
	var d;

	*new {
		^super.newCopyArgs(false);
	}

	value {
		d = true;
	}

	done {
		^{d};
	}
}

TestPT : UnitTest {

	var p, freeBusses, numSynths, numUGens;

	setUp {
		freeBusses = Server.default.audioBusAllocator.countFree;
		numUGens = Server.default.numUGens;
		numSynths = Server.default.numSynths;
		p = PT.new(Server.default);
	}

	tearDown {
		var done = false;
		this.waitCb("clearFully", 2, { |cb|
			p.clearFully(cb);
		});
		SystemClock.sched(1, {done = true});
		this.wait({done}, "waiting for some time to let cleanup happen", 3);
		this.assertEquals(Server.default.audioBusAllocator.countFree, freeBusses, "Verify no busses leaked");
		this.assertEquals(Server.default.numUGens, numUGens, "Verify no ugens leaked");
		this.assertEquals(Server.default.numSynths, numSynths, "Verify no synths leaked");
	}

	waitCb { |msg, time, f|
		var cb = PTTestCallback.new;
		f.value(cb);
		this.wait(cb.done, msg, time);
	}

	test_loadFresh {
		this.waitCb("load", 2, { |cb|
		p.load("
#1
TRI I1
#9
SIN 440
* IT $1.1 0.5
", cb);
		});
	}

	test_removeLast {
		this.waitCb("load", 2, { |cb|
			p.load("
#9
SIN 440
",
				cb)
		});
		this.waitCb("remove", 1, { |cb| p.removeAt(8, 0, true, cb) });
	 }

	test_removeExposingControlRate {
		this.waitCb("load", 2, { |cb|
		p.load("
#9
X= UNI VSAW 1 0
* SIN 440 X
", cb);
		});
		// This should fail.
		this.assertException({
			p.removeAt(8, 1);
		}, PTCheckError);
	}

	test_removeDoesntExist {
		this.waitCb("load", 2, { |cb|
			p.load("
#9
SIN 440
", cb
			);
		});
		this.waitCb("remove", 1, {|cb| p.removeAt(8, 0, true, cb)});
		this.assertException({
			p.removeAt(8, 0);
		}, PTEditError);
	}

	test_lotsOfOpsLoadWithoutError {
		this.waitCb("load", 2, { |cb|
			p.load("
#1
+ 0.5 * 0.5 SIN I1
#9
SIN 440
PSIN 220 * PI IT
* IT $1.1 0.5
", cb)});
		this.waitCb("load", 2, { |cb|
			p.load("
#9
PULSE LR 217 223 UNI LR SIN 0.3 SIN 0.4
* 0.3 PAN RLPF IT SCL SIN 0.2  200 1000 0.3 SIN 1
+ IT * 0.5 DEL.F IT + UNI SIN 0.02 0.04 3
", cb)});

		this.waitCb("load", 2, { |cb|
			p.load("
#9
AB= 3 * SIN 440 X
X= SIN 1
* * AB 3 0.2 SIN 0.3
", cb)});

		this.waitCb("load", 4, { |cb|
			p.load("
DRONESTART
I AM ABOUT TO DO SOMETHING
THAT CRASHED BEFORE

#1

#2

#3

#4

#5
* SIN * 2 N.MIN I1 PERC I2 0.5
PSIN N.MIN I1 * PI IT
* IT PERC I2 0.75
#6
K= EN.ER 3 8 0,0.01,0.0625
SEQ4 K WN 0 .3 .2 .6,0.01,0.0625
* * IT 1 10,0.01,0.0625
* SIN N.MIN + -12 IT PERC K 0.5,3,0.0625
#7
K= SCL RSM 0.1 0 48,0.01,0.0625
SAW + N.MIN I1 LR I1 * -1 I1,5.1199941138953,0.0625
* IT UNI RSM 0.1,3.0443660801604,0.0625
LPF IT N K,0.01,0.0625

#8
K= SCL RSM 0.1 0 24,0.01,0.0625
L.M 0 5: $7.1 * 2 I,4.3053846857528,0.0625
+ IT * * 2 P 4 BPF J N K,1.2799988554795,0.0625
A= TANH IT,0.01,0.0625
J= DEL IT + M * RSM 0.1 0.01,0.01,0.0625
A,0.01,0.0625

#9
AB= 0 $8,0.01,0.0625
AB= 1 $6,0.01,0.0625
IT,0.01,0.0625
IT,0.01,0.0625
IT,0.01,0.0625
L.MIX 0 3: AB I", cb)});

	}


	 test_tooManyArgs {
	 	var error = nil;
		this.waitCb("loadFail", 2, {|cb|
	 		p.load("
#9
SIN 440 220
",
				nil, cb);
		});
		this.assertEquals(p.scripts[8].lines.size, 0);
	}

	test_loadFailUnwinds {
	 	var error = nil;
		this.waitCb("loadFail", 2, {|cb|
	 		p.load("
#1
SIN I1
#9
$1.1 6
SIN 440 IT
",
				nil, cb);
		});
		this.assertEquals(p.scripts[0].refs.size, 0, "No refs when load failure");
		this.assertEquals(p.scripts[0].lines.size, 0, "No script contents when load failure");
	}

	 test_setFadeTime {
		this.waitCb("load", 2, { |cb|

			p.load("
#1
TRI I1
#9
SIN 440
* IT $1.1 0.5
", cb)});
			p.setFadeTime(0, 0, 0.4);
			this.assertEquals(p.scripts[0].refs.size, 1);
			p.scripts[0].refs.do { |r|
				this.assertEquals(r[1].line, "TRI I1");
				this.assertEquals(r[1].proxy.fadeTime, 0.4);
			};
		}

	 test_replaceStaysControl {
		this.waitCb("load", 2, { |cb|
			p.load("
#1
TRI I1
#9
SIN 440
* IT $1.1 0.5
"
				, cb)});
		this.waitCb("replace", 2, { |cb| p.replace(0, 0, "+ 0.5 * 0.5 TRI I1", true, cb)});
	 	this.assertEquals(p.scripts[0].refs.size, 1);
	 	p.scripts[0].refs.do { |r|
	 		this.assertEquals(r.out.rate, \control);
	 	};
	}

	 test_replaceGoesAudioInScriptCall {
		this.waitCb("load", 2, { |cb|
			p.load("
#1
TRI I1
#9
SIN 440
* IT $1.1 0.5
",
				cb)});
		this.waitCb("replace", 2, { |cb| p.replace(0, 0, "+ 0.5 * 0.5 TRI 220", true, cb)});
		this.assertEquals(p.scripts[0].refs.size, 1);
		p.scripts[0].refs.do { |r|
			this.assertEquals(r.out.rate, \audio);
		};
	 }

	 test_replaceFailsChangesRateOfWholeThing {
		this.waitCb("load", 2, { |cb|
			p.load("
#9
SIN 440
* IT 0.5
",
				cb)});
		this.assertException({
			p.replace(8, 1, "SIN 0.5");
		}, PTCheckError);
	 	this.assertEquals(p.scripts[8].lines, List.newFrom(["SIN 440", "* IT 0.5"]));
	}

	 test_replaceFailsThenSucceeds1 {
		this.waitCb("load", 2, { |cb|
			p.load("
#9
SIN 440
* IT 0.5
",
				cb)});
		this.assertException({
			p.replace(8, 1, "SIN 0.5");
		}, PTCheckError);
		this.waitCb("replace", 2, { |cb| p.replace(8, 1, "* IT 0.4", true, cb)});
	 	this.assertEquals(p.scripts[8].lines, List.newFrom(["SIN 440", "* IT 0.4"]));
	}

	test_replaceFailsThenSucceeds2 {
		this.waitCb("load", 2, { |cb|
			p.load("
#9
SIN 440
* IT 0.5
",
				cb)});
		this.assertException({
			p.replace(8, 1, "SIN ASDF");
		}, PTParseError);
		this.waitCb("replace", 2, { |cb| p.replace(8, 1, "* IT 0.4", true, cb)});
	 	this.assertEquals(p.scripts[8].lines, List.newFrom(["SIN 440", "* IT 0.4"]));
	}

	test_replaceFailsThenSucceedsInScriptCall {
		this.waitCb("load", 2, { |cb|
			p.load("
#1
TRI I1
#9
SIN 440
* IT $1.1 0.5
", cb, {|e| e.reportError;})});
		this.assertException({
			p.replace(0, 0, "ASDF");
		}, PTParseError);
	 	this.assertEquals(p.scripts[0].lines, List.newUsing(["TRI I1"]));
	 	this.assertEquals(p.scripts[0].refs.size, 1);
		this.waitCb("replace", 2, { |cb| p.replace(0, 0, "TRI * 2 I1", true, cb)});
		this.assertEquals(p.scripts[0].lines, List.newUsing(["TRI * 2 I1"]));
		this.assertEquals(p.scripts[0].refs.size, 1);
	}

	test_replaceWithWrongScriptCall {
		this.waitCb("load", 2, { |cb|
			p.load("
#7
SIN N I1
#8
SIN 220
#9
A= $8
SILENCE
", cb)});
		Post << "LOADED "<< p << "\n";
		this.waitCb("replace", 2, { |cb| p.replace(7, 0, "$1.1 0", true, cb)});
		Post << "REFS ARE " << p.scripts[0].refs << "\n";
		this.assertEquals(p.scripts[0].refs.size, 1, "Number of script refs");
	}

	test_replaceWithRightScriptCall {
		this.waitCb("load", 2, { |cb|
			p.load("
#7
SIN N I1
#8
SIN 220
#9
A= $8
SILENCE
", cb)});
		Post << "LOADED\n";
		this.waitCb("replace", 2, { |cb| p.replace(7, 0, "$7.1 0", true, cb)});
	}

	test_replaceDoesntLeakRefs {
		this.waitCb("load", 2, { |cb|
			p.load("
#1
TRI I1
#9
SIN 440
* IT $1.1 0.5
", cb)});
		this.waitCb("replace", 2, { |cb|
			p.replace(8, 1, "* SIN 1 IT", true, cb);
		});
	 	this.assertEquals(p.scripts[8].lines, List.newUsing(["SIN 440", "* SIN 1 IT"]));
	 	this.assertEquals(p.scripts[0].refs.size, 0);
	}
}
