TestPTScriptNet : UnitTest {
	var parser;
	var net;

	setUp {
        // this will be called before each test
		parser = PTParser.default;
		net = PTScriptNet.new(parser, 6, [], parser.parse("SIN 440"));
    }

	test_initializationToSin {
		this.assertEquals(net.out.rate, \audio);
	}

	test_keepAudioRate {
		var p = net.prepareAdd("* IT SIN 1");
		this.assert(p.propagate.not);
		p.commit;
		this.assert(net.out.rate == \audio);
		this.assertEquals(net.lines[0], "* IT SIN 1");
	}

	test_makeControlRate {
		var p = net.prepareAdd("SIN 1");
		this.assert(p.propagate);
		p.commit;
		this.assert(net.out.rate == \control);
	}

	test_replaceOnly {
		var p = net.prepareAdd("* IT SIN 1");
		this.assert(p.propagate.not);
		p.commit;
		p = net.prepareReplace(0, "SIN 1");
		this.assert(p.propagate);
		p.commit;
		this.assertEquals(net.out.rate, \control);
		this.assertEquals(net.lines[0], "SIN 1");
	}

	test_replacePropagates {
		var p;
		net = PTScriptNet.new(parser, 6, ["SIN 1", "* IT SIN 2"], parser.parse("SIN 440"));
		this.assertEquals(net.out.rate, \control);
		p = net.prepareReplace(0, "SIN 440");
		this.assert(p.propagate);
		p.commit;
		this.assertEquals(net.out.rate, \audio);
		this.assertEquals(net.lines[0], "SIN 440");
		this.assertEquals(net.lines[1], "* IT SIN 2");
	}

	test_replaceFails {
		var p;
		var e;
		net = PTScriptNet.new(parser, 6, ["SIN 1", "* IT SIN 2"], parser.parse("SIN 440"));
		this.assertEquals(net.out.rate, \control);
		try {
			p = net.prepareReplace(0, "WOW BAD");
		} { |err|
			e = err;
		};
		this.assert(e != nil);
		this.assertEquals(net.lines[0], "SIN 1");
		this.assertEquals(net.lines[1], "* IT SIN 2");
	}
}