+ ContiguousBlockAllocator {
	countFree {
		var free = 0;
		array.do({ |item, i|
			item.notNil.if({
				item.used.not.if({
					free = free + item.size;
				});
			});
		});
		freed.keysValuesDo({ |size, set|
			free = free + size;
		});
		^free;
	}
}