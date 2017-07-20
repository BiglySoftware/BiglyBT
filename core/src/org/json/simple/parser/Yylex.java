package org.json.simple.parser;


class Yylex {
	private final static int YY_BUFFER_SIZE = 512;
	private final static int YY_F = -1;
	private final static int YY_NO_STATE = -1;
	private final static int YY_NOT_ACCEPT = 0;
	//private final static int YY_START = 1;
	private final static int YY_END = 2;
	private final static int YY_NO_ANCHOR = 4;
	private final static int YY_BOL = 65536;
	private final static int YY_EOF = 65537;

private StringBuffer sb=new StringBuffer();
	private java.io.BufferedReader yy_reader;
	private int yy_buffer_index;
	private int yy_buffer_read;
	private int yy_buffer_start;
	private int yy_buffer_end;
	private char yy_buffer[];
	private boolean yy_at_bol;
	private int yy_lexical_state;

	Yylex (java.io.Reader reader) {
		this ();
		if (null == reader) {
			throw (new Error("Error: Bad input stream initializer."));
		}
		yy_reader = new java.io.BufferedReader(reader);
	}

	Yylex (java.io.InputStream instream) {
		this ();
		if (null == instream) {
			throw (new Error("Error: Bad input stream initializer."));
		}
		yy_reader = new java.io.BufferedReader(new java.io.InputStreamReader(instream));
	}

	private Yylex () {
		yy_buffer = new char[YY_BUFFER_SIZE];
		yy_buffer_read = 0;
		yy_buffer_index = 0;
		yy_buffer_start = 0;
		yy_buffer_end = 0;
		yy_at_bol = true;
		yy_lexical_state = YYINITIAL;
	}

	//private boolean yy_eof_done = false;
	private static final int YYINITIAL = 0;
	private static final int STRING_BEGIN = 1;
	private static final int yy_state_dtrans[] = {
		0,
		39
	};
	private void yybegin (int state) {
		yy_lexical_state = state;
	}
	private int yy_advance ()
		throws java.io.IOException {
		int next_read;
		int i;
		int j;

		if (yy_buffer_index < yy_buffer_read) {
			return yy_buffer[yy_buffer_index++];
		}

		if (0 != yy_buffer_start) {
			i = yy_buffer_start;
			j = 0;
			while (i < yy_buffer_read) {
				yy_buffer[j] = yy_buffer[i];
				++i;
				++j;
			}
			yy_buffer_end = yy_buffer_end - yy_buffer_start;
			yy_buffer_start = 0;
			yy_buffer_read = j;
			yy_buffer_index = j;
			next_read = yy_reader.read(yy_buffer,
					yy_buffer_read,
					yy_buffer.length - yy_buffer_read);
			if (-1 == next_read) {
				return YY_EOF;
			}
			yy_buffer_read = yy_buffer_read + next_read;
		}

		while (yy_buffer_index >= yy_buffer_read) {
			if (yy_buffer_index >= yy_buffer.length) {
				yy_buffer = yy_double(yy_buffer);
			}
			next_read = yy_reader.read(yy_buffer,
					yy_buffer_read,
					yy_buffer.length - yy_buffer_read);
			if (-1 == next_read) {
				return YY_EOF;
			}
			yy_buffer_read = yy_buffer_read + next_read;
		}
		return yy_buffer[yy_buffer_index++];
	}
	private void yy_move_end () {
		if (yy_buffer_end > yy_buffer_start &&
		    '\n' == yy_buffer[yy_buffer_end-1])
			yy_buffer_end--;
		if (yy_buffer_end > yy_buffer_start &&
		    '\r' == yy_buffer[yy_buffer_end-1])
			yy_buffer_end--;
	}
	//private boolean yy_last_was_cr=false;
	private void yy_mark_start () {
		yy_buffer_start = yy_buffer_index;
	}
	private void yy_mark_end () {
		yy_buffer_end = yy_buffer_index;
	}
	private void yy_to_mark () {
		yy_buffer_index = yy_buffer_end;
		yy_at_bol = (yy_buffer_end > yy_buffer_start) &&
		            ('\r' == yy_buffer[yy_buffer_end-1] ||
		             '\n' == yy_buffer[yy_buffer_end-1] ||
		             2028/*LS*/ == yy_buffer[yy_buffer_end-1] ||
		             2029/*PS*/ == yy_buffer[yy_buffer_end-1]);
	}
	private java.lang.String yytext () {
		return (new java.lang.String(yy_buffer,
			yy_buffer_start,
			yy_buffer_end - yy_buffer_start));
	}
	//private int yylength () {
	//	return yy_buffer_end - yy_buffer_start;
	//}
	private char[] yy_double (char buf[]) {
		int i;
		char newbuf[];
		newbuf = new char[2*buf.length];
		for (i = 0; i < buf.length; ++i) {
			newbuf[i] = buf[i];
		}
		return newbuf;
	}
	private static final int YY_E_INTERNAL = 0;
	//private final int YY_E_MATCH = 1;
	private java.lang.String yy_error_string[] = {
		"Error: Internal error.\n",
		"Error: Unmatched input.\n"
	};
	private void yy_error (int code,boolean fatal) {
		java.lang.System.out.print(yy_error_string[code]);
		java.lang.System.out.flush();
		if (fatal) {
			throw new Error("Fatal Error.\n");
		}
	}
	private static int[][] unpackFromString(int size1, int size2, String st) {
		int colonIndex = -1;
		String lengthString;
		int sequenceLength = 0;
		int sequenceInteger = 0;

		int commaIndex;
		String workString;

		int res[][] = new int[size1][size2];
		for (int i= 0; i < size1; i++) {
			for (int j= 0; j < size2; j++) {
				if (sequenceLength != 0) {
					res[i][j] = sequenceInteger;
					sequenceLength--;
					continue;
				}
				commaIndex = st.indexOf(',');
				workString = (commaIndex==-1) ? st :
					st.substring(0, commaIndex);
				st = st.substring(commaIndex+1);
				colonIndex = workString.indexOf(':');
				if (colonIndex == -1) {
					res[i][j]=Integer.parseInt(workString);
					continue;
				}
				lengthString =
					workString.substring(colonIndex+1);
				sequenceLength=Integer.parseInt(lengthString);
				workString=workString.substring(0,colonIndex);
				sequenceInteger=Integer.parseInt(workString);
				res[i][j] = sequenceInteger;
				sequenceLength--;
			}
		}
		return res;
	}
	private static final int yy_acpt[] = {
		/* 0 */ YY_NOT_ACCEPT,
		/* 1 */ YY_NO_ANCHOR,
		/* 2 */ YY_NO_ANCHOR,
		/* 3 */ YY_NO_ANCHOR,
		/* 4 */ YY_NO_ANCHOR,
		/* 5 */ YY_NO_ANCHOR,
		/* 6 */ YY_NO_ANCHOR,
		/* 7 */ YY_NO_ANCHOR,
		/* 8 */ YY_NO_ANCHOR,
		/* 9 */ YY_NO_ANCHOR,
		/* 10 */ YY_NO_ANCHOR,
		/* 11 */ YY_NO_ANCHOR,
		/* 12 */ YY_NO_ANCHOR,
		/* 13 */ YY_NO_ANCHOR,
		/* 14 */ YY_NO_ANCHOR,
		/* 15 */ YY_NO_ANCHOR,
		/* 16 */ YY_NO_ANCHOR,
		/* 17 */ YY_NO_ANCHOR,
		/* 18 */ YY_NO_ANCHOR,
		/* 19 */ YY_NO_ANCHOR,
		/* 20 */ YY_NO_ANCHOR,
		/* 21 */ YY_NO_ANCHOR,
		/* 22 */ YY_NO_ANCHOR,
		/* 23 */ YY_NO_ANCHOR,
		/* 24 */ YY_NO_ANCHOR,
		/* 25 */ YY_NOT_ACCEPT,
		/* 26 */ YY_NO_ANCHOR,
		/* 27 */ YY_NO_ANCHOR,
		/* 28 */ YY_NOT_ACCEPT,
		/* 29 */ YY_NOT_ACCEPT,
		/* 30 */ YY_NOT_ACCEPT,
		/* 31 */ YY_NOT_ACCEPT,
		/* 32 */ YY_NOT_ACCEPT,
		/* 33 */ YY_NOT_ACCEPT,
		/* 34 */ YY_NOT_ACCEPT,
		/* 35 */ YY_NOT_ACCEPT,
		/* 36 */ YY_NOT_ACCEPT,
		/* 37 */ YY_NOT_ACCEPT,
		/* 38 */ YY_NOT_ACCEPT,
		/* 39 */ YY_NOT_ACCEPT,
		/* 40 */ YY_NOT_ACCEPT,
		/* 41 */ YY_NOT_ACCEPT,
		/* 42 */ YY_NOT_ACCEPT,
		/* 43 */ YY_NOT_ACCEPT,
		/* 44 */ YY_NOT_ACCEPT
	};
	private static final int yy_cmap[] = unpackFromString(1,65538,
"11:8,27:2,28,11,27,28,11:18,27,11,2,11:8,16,25,12,14,3,13:10,26,11:6,10:4,1" +
"5,10,11:20,23,1,24,11:3,18,4,10:2,17,5,11:5,19,11,6,11:3,7,20,8,9,11:5,21,1" +
"1,22,11:65410,0:2")[0];

	private static final int yy_rmap[] = unpackFromString(1,45,
"0,1:2,2,1:7,3,1:2,4,1:10,5,6,1,7,8,9,10,11,12,13,14,15,16,6,17,18,19,20,21," +
"22")[0];

	private static final int yy_nxt[][] = unpackFromString(23,29,
"1,-1,2,-1:2,25,28,-1,29,-1:3,30,3,-1:7,4,5,6,7,8,9,10:2,-1:42,3,33,34,-1,34" +
",-1:24,11,-1,34,-1,34,-1:12,16,17,18,19,20,21,22,23,40,-1:37,31,-1:23,26,-1" +
":24,42,-1:26,32,-1:34,3,-1:34,35,-1:18,37,-1:32,11,-1:27,38,26,-1:2,38,-1:3" +
"2,37,-1:27,12,-1:26,13,-1:11,1,14,15,27:25,-1:5,44:2,-1:4,44,-1:2,44,-1,44," +
"-1,44:2,-1:14,24:2,-1:4,24,-1:2,24,-1,24,-1,24:2,-1:29,36,-1:13,41:2,-1:4,4" +
"1,-1:2,41,-1,41,-1,41:2,-1:14,43:2,-1:4,43,-1:2,43,-1,43,-1,43:2,-1:10");

	public Yytoken yylex ()
		throws java.io.IOException {
		int yy_lookahead;
		int yy_anchor = YY_NO_ANCHOR;
		int yy_state = yy_state_dtrans[yy_lexical_state];
		int yy_next_state = YY_NO_STATE;
		int yy_last_accept_state = YY_NO_STATE;
		boolean yy_initial = true;
		int yy_this_accept;

		yy_mark_start();
		yy_this_accept = yy_acpt[yy_state];
		if (YY_NOT_ACCEPT != yy_this_accept) {
			yy_last_accept_state = yy_state;
			yy_mark_end();
		}
		while (true) {
			if (yy_initial && yy_at_bol) yy_lookahead = YY_BOL;
			else yy_lookahead = yy_advance();
			yy_next_state = YY_F;
			yy_next_state = yy_nxt[yy_rmap[yy_state]][yy_cmap[yy_lookahead]];
			if (YY_EOF == yy_lookahead && true == yy_initial) {
				return null;
			}
			if (YY_F != yy_next_state) {
				yy_state = yy_next_state;
				yy_initial = false;
				yy_this_accept = yy_acpt[yy_state];
				if (YY_NOT_ACCEPT != yy_this_accept) {
					yy_last_accept_state = yy_state;
					yy_mark_end();
				}
			}
			else {
				if (YY_NO_STATE == yy_last_accept_state) {
					throw (new Error("Lexical Error: Unmatched Input."));
				}
				else {
					yy_anchor = yy_acpt[yy_last_accept_state];
					if (0 != (YY_END & yy_anchor)) {
						yy_move_end();
					}
					yy_to_mark();
					switch (yy_last_accept_state) {
					case 1:

					case -2:
						break;
					case 2:
						{ sb.delete(0,sb.length());yybegin(STRING_BEGIN);}
					case -3:
						break;
					case 3:
						{ Long val=Long.valueOf(yytext()); return new Yytoken(Yytoken.TYPE_VALUE,val);}
					case -4:
						break;
					case 4:
						{ return new Yytoken(Yytoken.TYPE_LEFT_BRACE,null);}
					case -5:
						break;
					case 5:
						{ return new Yytoken(Yytoken.TYPE_RIGHT_BRACE,null);}
					case -6:
						break;
					case 6:
						{ return new Yytoken(Yytoken.TYPE_LEFT_SQUARE,null);}
					case -7:
						break;
					case 7:
						{ return new Yytoken(Yytoken.TYPE_RIGHT_SQUARE,null);}
					case -8:
						break;
					case 8:
						{ return new Yytoken(Yytoken.TYPE_COMMA,null);}
					case -9:
						break;
					case 9:
						{ return new Yytoken(Yytoken.TYPE_COLON,null);}
					case -10:
						break;
					case 10:
						{}
					case -11:
						break;
					case 11:
						{ Double val=Double.valueOf(yytext()); return new Yytoken(Yytoken.TYPE_VALUE,val);}
					case -12:
						break;
					case 12:
						{ return new Yytoken(Yytoken.TYPE_VALUE,null);}
					case -13:
						break;
					case 13:
						{ Boolean val=Boolean.valueOf(yytext()); return new Yytoken(Yytoken.TYPE_VALUE,val);}
					case -14:
						break;
					case 14:
						{ sb.append(yytext());}
					case -15:
						break;
					case 15:
						{ yybegin(YYINITIAL);return new Yytoken(Yytoken.TYPE_VALUE,sb.toString());}
					case -16:
						break;
					case 16:
						{sb.append('\\');}
					case -17:
						break;
					case 17:
						{sb.append('"');}
					case -18:
						break;
					case 18:
						{sb.append('/');}
					case -19:
						break;
					case 19:
						{sb.append('\b');}
					case -20:
						break;
					case 20:
						{sb.append('\f');}
					case -21:
						break;
					case 21:
						{sb.append('\n');}
					case -22:
						break;
					case 22:
						{sb.append('\r');}
					case -23:
						break;
					case 23:
						{sb.append('\t');}
					case -24:
						break;
					case 24:
						{	int ch=Integer.parseInt(yytext().substring(2),16);
													sb.append((char)ch);
												}
					case -25:
						break;
					case 26:
						{ Double val=Double.valueOf(yytext()); return new Yytoken(Yytoken.TYPE_VALUE,val);}
					case -26:
						break;
					case 27:
						{ sb.append(yytext());}
					case -27:
						break;
					default:
						yy_error(YY_E_INTERNAL,false);
					case -1:
					}
					yy_initial = true;
					yy_state = yy_state_dtrans[yy_lexical_state];
					yy_next_state = YY_NO_STATE;
					yy_last_accept_state = YY_NO_STATE;
					yy_mark_start();
					yy_this_accept = yy_acpt[yy_state];
					if (YY_NOT_ACCEPT != yy_this_accept) {
						yy_last_accept_state = yy_state;
						yy_mark_end();
					}
				}
			}
		}
	}
}
