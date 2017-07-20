package com.biglybt.ui.common.util;

import java.util.regex.Pattern;

/* Class to replace http://www.programmers-friend.org/apidoc/org/pf/text/StringPattern.html
 org.pf.text.StringPattern makes * match anything wich is not really standard regexp
 syntax */

public class StringPattern {

    private Pattern p;
    private boolean hasWildcard;

    public StringPattern(String sp) {

	hasWildcard = (sp.contains("*") ||
		       sp.contains("?"));
	
	//fix up pattern to standard regexp syntax, i.e
	// * -> .*
	// ? -> ?
	sp=sp.replaceAll("\\?","\\.");
	sp=sp.replaceAll("\\*","\\.\\*");
	p = Pattern.compile(sp);
    }

    public boolean hasWildcard() {
	return hasWildcard;
    }

    public void setIgnoreCase(boolean ignoreCase) {
	p=Pattern.compile(p.pattern(),ignoreCase?Pattern.CASE_INSENSITIVE:0);
    }

    public boolean matches(String probe) {
	return p.matcher(probe).matches();
    }

    /* test */
    public static void main(String[] argv) {
	StringPattern sp = new StringPattern(argv[0]);
	System.out.println("hasWildcard: "+ sp.hasWildcard());
	sp.setIgnoreCase(true);
	System.out.println("matches:"+sp.matches(argv[1]));
    }
}
