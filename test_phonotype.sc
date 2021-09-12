TestPTScriptNet : UnitTest {
	var parser;
	var net;

	setUp {
        // this will be called before each test
		parser = PTParser.default;
		net = PTScriptNet.new(parser, 6, [], parser.parse("SIN 440"));
    }

	test_initializationToSin {
		this.assert(net.out.rate == \audio);
	}

	test_keepAudioRate {
		var oldOut = net.add("* IT SIN 1");
		this.assert(oldOut == nil);
		this.assert(net.out.rate == \audio);
	}

	test_makeControlRate {
		var oldOut = net.add("SIN 1");
		this.assert(oldOut != nil);
		this.assert(net.out.rate == \control);
	}
}