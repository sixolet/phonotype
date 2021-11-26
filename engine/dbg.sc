// Debugging code.

PTDbg : Post {
	classvar <>debug = true;
	classvar <>slow = false;
	classvar <>complexity = 0;
	classvar <>f = nil;

	* manageFile {
		if(PTDbg.debug, {
			if (PTDbg.f == nil, {
				if (PathName.new("~/data/phonotype").isFolder, {
					PTDbg.f = File.open(PathName.new("~/dust/data/phonotype/debug.txt").fullPath, "w");
				}, {
					PTDbg.f = File.open(PathName.new("~/debug.txt").fullPath, "w");
				});
			});
		}, {
			if (PTDbg.f != nil, {
				f.close;
				f = nil;
			});
		});
	}

	* complex {
		complexity = complexity + 1;
		if (slow && (complexity > 6000), {
			Error.new("Too complex").throw;
		});
	}

	* put { arg item;
		PTDbg.manageFile;
		if (PTDbg.debug, {
			PTDbg.f.put(item);
			PTDbg.f.flush;
			item.post;
		});
	}
	* putAll { arg aCollection;
		PTDbg.manageFile;
		if (PTDbg.debug, {
			PTDbg.f.putAll(aCollection);
			PTDbg.f.flush;
			aCollection.post;
		});
	}
}