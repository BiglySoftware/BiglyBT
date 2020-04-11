package com.biglybt.ui.android.internat;

import com.biglybt.ui.none.internat.BuildMessageBundleNone;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Created by TuxPaper on 4/11/20.
 */
public class BuildMessageBundleAndroid {
	public static void main(String[] args) throws IOException, URISyntaxException {
		BuildMessageBundleNone.main(new String[] {
			new File(args.length > 0 ? args[0] : "core").getAbsolutePath(),
			"uis/src/com/biglybt/ui/android/internat"
		});
	}
}
