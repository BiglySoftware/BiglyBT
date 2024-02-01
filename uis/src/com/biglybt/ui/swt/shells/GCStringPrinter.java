/*
 * File    : GCStringPrinter.java
 * Created : 16 mars 2004
 * By      : Olivier
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.biglybt.ui.swt.shells;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.SWTThread;
import com.biglybt.ui.swt.mainwindow.SWTThreadAlreadyInstanciatedException;

/**
 * @author Olivier Chalouhi
 * @author TuxPaper (rewrite)
 */
public class GCStringPrinter
{
	private static final char ELLIPSIS = '\u2026';	// "..." - same as &hellip; in html
	
	private static final boolean DEBUG = false;

	private static final String GOOD_STRING = "(/|,jI~`gy";

	public static final int FLAG_SKIPCLIP = 1;

	public static final int FLAG_FULLLINESONLY = 2;

	public static final int FLAG_NODRAW = 4;

	public static final int FLAG_KEEP_URL_INFO = 8;

	private static final Pattern patHREF = Pattern.compile(
			"<\\s*?a\\s.*?href\\s*?=\\s*?\"(.+?)\".*?>(.*?)<\\s*?/a\\s*?>",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern patAHREF_TITLE = Pattern.compile(
			"title=\\\"([^\\\"]+)", Pattern.CASE_INSENSITIVE);

	private static final Pattern patAHREF_TARGET = Pattern.compile(
			"target=\\\"([^\\\"]+)", Pattern.CASE_INSENSITIVE);

	//private static final Pattern patOver1000 = Pattern.compile("[^\n]{1010,}");

	// Limit word/line length as OSX crashes on stringExtent on very very long words
	private static final int MAX_LINE_LEN = 4000;

	// max Word length can be same as line length since words are auto-split
	// across lines
	private static final int MAX_WORD_LEN = 4000;

	private boolean cutoff;
	private boolean truncated;
	
	private boolean isWordCut;

	private GC gc;

	private String string;

	private Rectangle printArea;

	private int swtFlags;

	private int printFlags;

	private Point size;
	private Point preferredSize;

	private Color urlColor;

	private List<URLInfo> listUrlInfo;

	private Image[] images;

	private float[] imageScales;

	private int iCurrentHeight;

	private boolean wrap;

	private Rectangle drawRect;

	public static class URLInfo
	{
		public String url;

		public String text;

		public Color urlColor;

		//public Color dropShadowColor;

		int relStartPos;

		// We could use a region, but that uses a resource that requires disposal
		public List<Rectangle> hitAreas = null;

		int titleLength;

		public String fullString;

		public String title;

		public String target;

		public boolean urlUnderline;

		// @see java.lang.Object#toString()
		public String toString() {
			return super.toString() + ": relStart=" + relStartPos + ";url=" + url
					+ ";title=" + text + ";hit="
					+ (hitAreas == null ? 0 : hitAreas.size());
		}
	}

	private static class LineInfo
	{
		String originalLine;

		String lineOutputed;

		int excessPos;

		public int relStartPos;

		public int imageIndexes[];

		public Point outputLineExtent 			= new Point(0, 0);
		public Point outputLinePreferredExtent 	= new Point(0, 0);
		public int outputLineStartX;

		public LineInfo(String originalLine, int relStartPos) {
			this.originalLine = originalLine;
			this.relStartPos = relStartPos;
		}

		// @see java.lang.Object#toString()
		public String toString() {
			return super.toString() + ": relStart=" + relStartPos + ";xcess="
					+ excessPos + ";orig=" + originalLine + ";output=" + lineOutputed;
		}
	}

	public static boolean printString(GC gc, String string, Rectangle printArea) {
		return printString(gc, string, printArea, false, false);
	}

	public static boolean printString(GC gc, String string, Rectangle printArea,
			boolean skipClip, boolean fullLinesOnly) {
		return printString(gc, string, printArea, skipClip, fullLinesOnly, SWT.WRAP
				| SWT.TOP);
	}

	/**
	 *
	 * @param gc GC to print on
	 * @param string Text to print
	 * @param printArea Area of GC to print text to
	 * @param skipClip Don't set any clipping on the GC.  Text may overhang
	 *                 printArea when this is true
	 * @param fullLinesOnly If bottom of a line will be chopped off, do not display it
	 * @param swtFlags SWT flags.  SWT.CENTER, SWT.BOTTOM, SWT.TOP, SWT.WRAP
	 * @return whether it fit
	 */
	public static boolean printString(GC gc, String string, Rectangle printArea,
			boolean skipClip, boolean fullLinesOnly, int swtFlags) {
		try {
			GCStringPrinter sp = new GCStringPrinter(gc, string, printArea, skipClip,
					fullLinesOnly, swtFlags);
			return sp.printString();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	private boolean _printString() {
		if (Constants.isWindows) {
			return swt_printString_NoAdvanced();
		}
		return swt_printString();
	}

	private boolean swt_printString_NoAdvanced() {
		boolean b = false;
		try {
			boolean wasAdvanced = gc.getAdvanced();
			Rectangle clipping = null;
			// With Advanced on text antialias in SWT.DEFAULT is not the system's
			// default (Try flipping the "Turn on ClearType" checkbox on
			// the ClearType Text Tuner", and you'll see the text redraw correctly
			// when advanced is off, but not when it's on)
			// Other problems with text and GDIP, see http://social.msdn.microsoft.com/Forums/en-US/winforms/thread/362ab21b-1dc4-4140-a39a-a366beea9e40

			// Turn off Advanced while drawing text so it antialiases based on
			// system prefs.
			// NOTE: This messes up any Transforms :(
			if (gc.getAdvanced() && gc.getTextAntialias() == SWT.DEFAULT
					&& gc.getAlpha() == 255) {
				clipping = gc.getClipping();
				gc.setAdvanced(false);
				Utils.setClipping(gc, clipping);
			}
			b = __printString();
			if (wasAdvanced) {
				gc.setAdvanced(true);
				Utils.setClipping(gc, clipping);
			}
		} catch (Throwable t) {
			Debug.out(t);
		}

		if (DEBUG) {
			System.out.println("");
		}

		return b;
	}

	private boolean swt_printString() {
		boolean b = false;
		try {
			b = __printString();
		} catch (Throwable t) {
			Debug.out(t);
		}

		if (DEBUG) {
			System.out.println("");
		}

		return b;
	}

	/**
	 * @param gc
	 * @param string
	 * @param printArea
	 * @param printFlags
	 * @param swtFlags
	 * @return
	 *
	 * @since 3.0.4.3
	 */
	private boolean __printString() {
		size = new Point(0, 0);
		preferredSize = new Point(0, 0);
		
		isWordCut = false;

		if (string == null) {
			return false;
		}

		if (printArea == null || printArea.isEmpty()) {
			return false;
		}

		ArrayList<LineInfo> lines = new ArrayList<>(1);

		while (string.indexOf('\t') >= 0) {
			string = string.replace('\t', ' ');
		}

		if (string.indexOf("  ") > 0) {
			string = string.replaceAll("  +", " ");
		}

		boolean hasSlashR = string.indexOf('\r') > 0;

		boolean fullLinesOnly = (printFlags & FLAG_FULLLINESONLY) != 0;
		boolean skipClip = (printFlags & FLAG_SKIPCLIP) != 0;
		boolean noDraw = (printFlags & FLAG_NODRAW) != 0;
		wrap = (swtFlags & SWT.WRAP) != 0;

		if ((swtFlags & (SWT.TOP | SWT.BOTTOM)) == 0) {
			// center vertically -- must be fullLinesOnly
			fullLinesOnly = true;
			printFlags |= FLAG_FULLLINESONLY;
		}

		if (string.indexOf('<') >= 0) {
  		if ((printFlags & FLAG_KEEP_URL_INFO) == 0) {
  			Matcher htmlMatcher = patHREF.matcher(string);
  			boolean hasURL = htmlMatcher.find();
  			if (hasURL) {
  				listUrlInfo = new ArrayList<>(1);

  				while (hasURL) {
  					URLInfo urlInfo = new URLInfo();

  					// Store the full ahref string once, then use substring which doesn't
  					// create real strings :)
  					urlInfo.fullString = htmlMatcher.group();
  					urlInfo.relStartPos = htmlMatcher.start(0);

  					urlInfo.url = string.substring(htmlMatcher.start(1),
  							htmlMatcher.end(1));
  					urlInfo.text = string.substring(htmlMatcher.start(2),
  							htmlMatcher.end(2));
  					urlInfo.titleLength = urlInfo.text.length();

  					Matcher matcherTitle = patAHREF_TITLE.matcher(urlInfo.fullString);
  					if (matcherTitle.find()) {
  						urlInfo.title = string.substring(urlInfo.relStartPos
  								+ matcherTitle.start(1), urlInfo.relStartPos
  								+ matcherTitle.end(1));
  					}

  					Matcher matcherTarget = patAHREF_TARGET.matcher(urlInfo.fullString);
  					if (matcherTarget.find()) {
  						urlInfo.target = string.substring(urlInfo.relStartPos
  								+ matcherTarget.start(1), urlInfo.relStartPos
  								+ matcherTarget.end(1));
  					}

  					//System.out.println("URLINFO! " + urlInfo.fullString
  					//		+ "\ntarget="
  					//		+ urlInfo.target + "\ntt=" + urlInfo.title + "\nurl="
  					//		+ urlInfo.url + "\ntext=" + urlInfo.text + "\n\n");

  					string = htmlMatcher.replaceFirst(urlInfo.text.replaceAll("\\$",
  							"\\\\\\$"));

  					listUrlInfo.add(urlInfo);
  					htmlMatcher = patHREF.matcher(string);
  					hasURL = htmlMatcher.find(urlInfo.relStartPos);
  				}
  			}
  		} else {
  			Matcher htmlMatcher = patHREF.matcher(string);
  			string = htmlMatcher.replaceAll("$2");
  		}
		}

		Rectangle lineDrawRect = new Rectangle(printArea.x, printArea.y,
				printArea.width, printArea.height);
		drawRect = new Rectangle(printArea.x, printArea.y,
			printArea.width, printArea.height);

		Rectangle oldClipping = null;
		try {
			if (!skipClip && !noDraw) {
				oldClipping = gc.getClipping();

				// Protect the GC from drawing outside the drawing area
				Utils.setClipping(gc, printArea);
			}

			// Process string line by line
			iCurrentHeight = 0;
			int currentCharPos = 0;

			int posNewLine = string.indexOf('\n');
			if (hasSlashR) {
  			int posR = string.indexOf('\r');
  			if (posR == -1) {
  				posR = posNewLine;
  			}
  			posNewLine = Math.min(posNewLine, posR);
			}
			if (posNewLine < 0) {
				posNewLine = string.length();
			}
			int posLastNewLine = 0;
			while (posNewLine >= 0 && posLastNewLine < string.length()) {
				String sLine = string.substring(posLastNewLine, posNewLine);

				do {
					LineInfo lineInfo = new LineInfo(sLine, currentCharPos);
					lineInfo = processLine(gc, lineInfo, printArea,  fullLinesOnly,
							false, !wrap);
					String sProcessedLine = lineInfo.lineOutputed;

					if (sProcessedLine != null && sProcessedLine.length() > 0) {
						if (lineInfo.outputLineExtent.x == 0 || lineInfo.outputLineExtent.y == 0) {
							lineInfo.outputLineExtent = stringExtent(gc,sProcessedLine);
						}
						iCurrentHeight += lineInfo.outputLineExtent.y;
						boolean isOverY = iCurrentHeight > printArea.height;

						if (DEBUG) {
							System.out.println("Adding Line: [" + sProcessedLine + "]"
									+ sProcessedLine.length() + "; h=" + iCurrentHeight + "("
									+ printArea.height + "). fullOnly? " + fullLinesOnly
									+ ". Excess: " + lineInfo.excessPos + ". isOverY? " + isOverY);
						}

						if (isOverY && !fullLinesOnly) {
							//fullLinesOnly = true; // <-- don't know why we needed this
							lines.add(lineInfo);
						} else if (isOverY && fullLinesOnly && lines.size() > 0) {
							LineInfo prev = lines.get( lines.size()-1);

							if (wrap) {
								if (DEBUG) {
									System.out.println("reprocess");
								}
								prev = processLine(gc, prev, printArea, fullLinesOnly,
									false, true);
								prev.outputLineExtent = stringExtent(gc,prev.lineOutputed);
								if (prev.excessPos == -1) {
									return true;
								}
							}

							/*
							String excess = lineInfo.excessPos >= 0
									? sLine.substring(lineInfo.excessPos) : null;
							if (excess != null) {
								if (fullLinesOnly) {
									if (lines.size() > 0) {
										lineInfo = lines.remove(lines.size() - 1);
										sProcessedLine = lineInfo.originalLine.length() > MAX_LINE_LEN
												? lineInfo.originalLine.substring(0, MAX_LINE_LEN)
												: lineInfo.originalLine;
										//sProcessedLine = ((LineInfo) lines.remove(lines.size() - 1)).originalLine;
										extent = gc.stringExtent(sProcessedLine);
									} else {
										if (DEBUG) {
											System.out.println("No PREV!?");
										}
										return false;
									}
								} else {
									sProcessedLine = sProcessedLine.length() > MAX_LINE_LEN
											? sProcessedLine.substring(0, MAX_LINE_LEN)
											: sProcessedLine;
								}

								if (excess.length() > MAX_LINE_LEN) {
									excess = excess.substring(0, MAX_LINE_LEN);
								}

								StringBuffer outputLine = new StringBuffer(sProcessedLine);
								lineInfo.outputLineExtent.x = extent.x;
								wrap = false;
								int newExcessPos = processWord(gc, sProcessedLine,
										" " + excess, printArea, lineInfo, outputLine,
										new StringBuffer());
								if (DEBUG) {
									System.out.println("  (overY+full+lineSize>0) with word [" + excess + "] len is "
											+ lineInfo.outputLineExtent.x + "(" + printArea.width + ") w/excess "
											+ newExcessPos);
								}

								lineInfo.lineOutputed = outputLine.toString();
								lines.add(lineInfo);
								if (DEBUG) {
									System.out.println("replace prev line with: "
											+ outputLine.toString());
								}
							} else {
								if (DEBUG) {
									System.out.println("No Excess");
								}
							}
							*/
							String str = prev.lineOutputed;
							if (str.length() > 2 && prev.outputLineExtent.x
									+ gc.stringExtent("" + ELLIPSIS).x >= printArea.width) {
								str = str.substring(0, str.length() - 2);
							}
							prev.lineOutputed = truncate( str );
							truncated = cutoff = true;
							if (DEBUG) {
								System.out.println("set cutoff (0)");
							}
							return false;
						} else {
							lines.add(lineInfo);
						}
						sLine = lineInfo.excessPos >= 0 && wrap
								? sLine.substring(lineInfo.excessPos) : null;
					} else {
						if (DEBUG) {
							System.out.println("Line process resulted in no text: " + sLine);
						}
						iCurrentHeight += lineInfo.outputLineExtent.y;
						lines.add(lineInfo);
						currentCharPos++;
						break;
						//return false;
					}

					currentCharPos += lineInfo.excessPos >= 0 ? lineInfo.excessPos
							: lineInfo.lineOutputed.length();
					//System.out.println("output: " + lineInfo.lineOutputed.length() + ";"
					//		+ lineInfo.lineOutputed + ";xc=" + lineInfo.excessPos + ";ccp=" + currentCharPos);
					//System.out.println("lineo=" + lineInfo.lineOutputed.length() + ";" + sLine.length() );
				} while (sLine != null);

				if (string.length() > posNewLine && string.charAt(posNewLine) == '\r'
						&& string.charAt(posNewLine + 1) == '\n') {
					posNewLine++;
				}
				posLastNewLine = posNewLine + 1;
				currentCharPos = posLastNewLine;

				posNewLine = string.indexOf('\n', posLastNewLine);
				if (hasSlashR) {
	  			int posR = string.indexOf('\r', posLastNewLine);
	  			if (posR == -1) {
	  				posR = posNewLine;
	  			}
	  			posNewLine = Math.min(posNewLine, posR);
				}
				if (posNewLine < 0) {
					posNewLine = string.length();
				}
			}
		} finally {

			if (lines.size() > 0) {
				// rebuild full text to get the exact y-extent of the output
				// this may be different (but shouldn't be!) than the height of each
				// line
				/*
				StringBuffer fullText = new StringBuffer(string.length() + 10);
				for (LineInfo lineInfo : lines) {
					if (fullText.length() > 0) {
						fullText.append('\n');
					}
					fullText.append(lineInfo.lineOutputed);
				}

				//size = gc.textExtent(fullText.toString());
				 */

				for (LineInfo lineInfo : lines) {
					size.x = Math.max(lineInfo.outputLineExtent.x, size.x);
					size.y += lineInfo.outputLineExtent.y;
					
					preferredSize.x = Math.max(lineInfo.outputLinePreferredExtent.x, preferredSize.x);
				}

				if ((swtFlags & (SWT.BOTTOM)) != 0) {
					lineDrawRect.y = lineDrawRect.y + lineDrawRect.height - size.y;
				} else if ((swtFlags & SWT.TOP) == 0) {
					// center vert
					lineDrawRect.y = lineDrawRect.y + (lineDrawRect.height - size.y) / 2;
				}

				drawRect.y = lineDrawRect.y;

				if (!noDraw || listUrlInfo != null) {
					drawRect.x = Integer.MAX_VALUE;
					for (LineInfo lineInfo : lines) {
						try {
							drawLine(gc, lineInfo, swtFlags, lineDrawRect, noDraw);
							drawRect.x = Math.min(drawRect.x, lineInfo.outputLineStartX);
						} catch (Throwable t) {
							t.printStackTrace();
						}
					}
					if (drawRect.x == Integer.MAX_VALUE) {
						drawRect.x = printArea.x;
					}
				}

				drawRect.height = size.y;
				drawRect.width = size.x;
				
				preferredSize.y = size.y;
			}

			if (!skipClip && !noDraw) {
				Utils.setClipping(gc, oldClipping);
			}

		}

		truncated = cutoff;
		
		cutoff |= size.y > printArea.height | preferredSize.x > size.x;
		return !cutoff;
	}

	/**
	 * @param hasMoreElements
	 * @param line
	 *
	 * @since 3.0.0.7
	 */
	private LineInfo processLine(final GC gc, final LineInfo lineInfo,
			final Rectangle printArea, final boolean fullLinesOnly,
			boolean hasMoreElements, boolean isLastLine) {

		if (lineInfo.originalLine.length() == 0) {
			lineInfo.lineOutputed = "";
			lineInfo.outputLineExtent = new Point(0, stringExtent(gc,GOOD_STRING).y);
			lineInfo.outputLinePreferredExtent = new Point( 0, lineInfo.outputLineExtent.y );
			return lineInfo;
		}

		StringBuffer outputLine = null;
		int excessPos = -1;

		boolean b = images != null || lineInfo.originalLine.length() > MAX_LINE_LEN;
		if (!b) {
			Point outputLineExtent = stringExtent(gc,lineInfo.originalLine);
			lineInfo.outputLinePreferredExtent = new Point( outputLineExtent.x, outputLineExtent.y );
			b = outputLineExtent.x > printArea.width;
			if (!b) {
				lineInfo.outputLineExtent = outputLineExtent;
			}
		}

		if (b) {
			outputLine = new StringBuffer();
			if (DEBUG) {
				System.out.println("Line to process: " + lineInfo.originalLine);
			}
			StringBuffer space = new StringBuffer(1);

			if (!wrap && images == null) {
				if (DEBUG) {
					System.out.println("No Wrap.. doing all in one line");
				}

				String sProcessedLine = lineInfo.originalLine.length() > MAX_LINE_LEN
						? lineInfo.originalLine.substring(0, MAX_LINE_LEN)
						: lineInfo.originalLine;

				// if it weren't for the elipses, we could do:
				// outputLine.append(sProcessedLine);

				excessPos = processWord(gc, lineInfo.originalLine, sProcessedLine,
						printArea, lineInfo, outputLine, space, isLastLine);
			} else {
				int posLastWordStart = 0;
				int posWordStart = lineInfo.originalLine.indexOf(' ');
				while (posWordStart == 0) {
					posWordStart = lineInfo.originalLine.indexOf(' ', posWordStart + 1);
				}
				if (posWordStart < 0) {
					posWordStart = lineInfo.originalLine.length();
				}
				// Process line word by word
				int curPos = 0;
				while (posWordStart >= 0 && posLastWordStart < lineInfo.originalLine.length()) {
					String word = lineInfo.originalLine.substring(posLastWordStart, posWordStart);
					if (word.length() == 0) {
						excessPos = -1;
						outputLine.append(' ');
					}

					for (int i = 0; i < word.length(); i += MAX_WORD_LEN) {
						String subWord;
						int endPos = i + MAX_WORD_LEN;
						if (endPos > word.length()) {
							subWord = word.substring(i);
						} else {
							subWord = word.substring(i, endPos);
						}

						excessPos = processWord(gc, lineInfo.originalLine, subWord,
								printArea, lineInfo, outputLine, space, isLastLine);
						if (DEBUG) {
							System.out.println("  with word [" + subWord + "] len is "
									+ lineInfo.outputLineExtent.x + "(" + printArea.width + ") w/excess "
									+ excessPos);
						}
						if (excessPos >= 0) {
							excessPos += curPos;
							break;
						}
						if (endPos <= word.length()) {
							space.setLength(0);
						}
						curPos += subWord.length() + 1;
					}
					if (excessPos >= 0) {
						break;
					}

					posLastWordStart = posWordStart + 1;
					posWordStart = lineInfo.originalLine.indexOf(' ', posLastWordStart);
					if (posWordStart < 0) {
						posWordStart = lineInfo.originalLine.length();
					}
				}
			}
		}

		if (!wrap && hasMoreElements && excessPos >= 0) {
			//if (outputLine == null) { dead code, outputLine is always non-null if excessPos >= 0
			//	outputLine = new StringBuffer(lineInfo.originalLine);
			//}
			int len = outputLine.length();
			if (len > 2) {
				len -= 2;
			}
			truncate( outputLine, len );
			cutoff = true;
			if (DEBUG) {
				System.out.println("set cutoff (1)");
			}
		}
		//drawLine(gc, outputLine, swtFlags, rectDraw);
		//		if (!wrap) {
		//			return hasMoreElements;
		//		}
		lineInfo.excessPos = excessPos;
		lineInfo.lineOutputed = outputLine == null ? lineInfo.originalLine : outputLine.toString();
		return lineInfo;
	}
	
	private void
	truncate(
		StringBuffer	buffer,
		int				len )
	{
		if ( len > 0 && Character.isHighSurrogate( buffer.charAt( len-1 ))){
			
			len--;
		}
		
		buffer.setLength(len);
		buffer.append( ELLIPSIS );
	}

	private String
	truncate(
		String			str )
	{
		int len = str.length();
		if ( len > 0 && Character.isHighSurrogate( str.charAt( len-1 ))){
			str = str.substring( 0, len-1 );
		}
		return( str + ELLIPSIS );
	}
	
	/**
	 * @param int Position of part of word that didn't fit
	 *
	 * @since 3.0.0.7
	 */
	private int processWord(final GC gc, final String sLine, String word,
			final Rectangle printArea, final LineInfo lineInfo,
			StringBuffer outputLine, final StringBuffer space, boolean isLastLine) {

		if (word.length() == 0) {
			space.append(' ');
			return -1;
		}

		//System.out.println("PW: " + word);
		if (images != null && word.length() >= 2 && word.charAt(0) == '%') {
			int imgIdx = word.charAt(1) - '0';
			if (images.length > imgIdx && imgIdx >= 0 && images[imgIdx] != null) {
				Image img = images[imgIdx];
				Rectangle bounds = img.getBounds();
				if (imageScales != null && imageScales.length > imgIdx) {
					bounds.width = (int) (bounds.width * imageScales[imgIdx]);
					bounds.height = (int) (bounds.height * imageScales[imgIdx]);
				}

				Point spaceExtent = stringExtent(gc,space.toString());
				int newWidth = lineInfo.outputLineExtent.x + bounds.width + spaceExtent.x;


				if (newWidth > printArea.width) {
					if (bounds.width + spaceExtent.x < printArea.width || lineInfo.outputLineExtent.x > 0) {
						//outputLine.append(space);
						//outputLine.append(word, 0, 2);
						if (DEBUG) {
							System.out.println("excess. w=" + lineInfo.outputLineExtent.x + ";h=" + lineInfo.outputLineExtent.y);
						}
						return 0;
					}
				}

				if (lineInfo.imageIndexes == null) {
					lineInfo.imageIndexes = new int[] { imgIdx };
				}


				//int targetWidth = lineInfo.outputLineExtent.x + newWidth;

				lineInfo.outputLineExtent = new Point(newWidth, Math.max(bounds.height, lineInfo.outputLineExtent.y));

				Point ptWordSize = stringExtent(gc,word.substring(2) + " ");
				if (lineInfo.outputLineExtent.x + ptWordSize.x > printArea.width) {
					outputLine.append(space);
					outputLine.append(word.substring(0,2));
					//System.out.println("w8 = " + lineInfo.outputLineExtent.x + ";h=" + lineInfo.outputLineExtent.y);
					return 2;
				}

				outputLine.append(space);
				space.setLength(0);
				outputLine.append(word.substring(0, 2));
				word = word.substring(2);
				//outputLine.append(word);
				//if (space.length() > 0) {
				//	space.delete(0, space.length());
				//}
				//space.append(' ');

				//System.out.println("w2 = " + lineInfo.outputLineExtent.x + ";h=" + lineInfo.outputLineExtent.y);
				//return -1;
			}

			if (word.length() == 0) {
				space.append(' ');
				return -1;
			}
		}
		
		Point ptLineAndWordSize = stringExtent(gc,outputLine + word + " ");
		//System.out.println(ptLineAndWordSize + ";" + outputLine  + "::WordComp " + (ptLineAndWordSize.x - lineInfo.outputLineExtent.x));
		if (ptLineAndWordSize.x > printArea.width) {
			// word is longer than space avail, split

			Point ptWordSize2 = stringExtent(gc,word + " ");
			boolean bWordLargerThanWidth = ptWordSize2.x > printArea.width;

			if (bWordLargerThanWidth) {
				isWordCut = true;
			}

			// This will split put a word that is longer than a full line onto a new
			// line (when the existing line has text).
			if (bWordLargerThanWidth && lineInfo.outputLineExtent.x > 0 && !isLastLine) {
				if (DEBUG) {
					System.out.println("excess/bWordLargerThanWidth. w=" + lineInfo.outputLineExtent.x + ";h=" + lineInfo.outputLineExtent.y);
				}
				// if we don't have room for another line, we lose the whole "large word". Would be nice to show as much of it as we can
				return 0;
			}

			int endIndex = word.length();
			long diff = endIndex;

			while (ptLineAndWordSize.x != printArea.width) {
				diff = (diff >> 1) + (diff % 2);

				if (diff <= 0) {
					diff = 1;
				}

				//System.out.println("diff=" + diff + ";e=" + endIndex + ";tw=" + targetWidth + ";paw= " + printArea.width);
				if (ptLineAndWordSize.x > printArea.width) {
					endIndex -= diff;
					if (endIndex < 1) {
						endIndex = 1;
					}
				} else {
					endIndex += diff;
					if (endIndex > word.length()) {
						endIndex = word.length();
					}
				}

				ptLineAndWordSize = stringExtent(gc,outputLine + word.substring(0, endIndex) + " ");

				if (diff <= 1) {
					break;
				}
			}
			boolean nothingFit = endIndex == 0;
			if (nothingFit) {
				endIndex = 1;
			}
			if (ptLineAndWordSize.x > printArea.width && endIndex > 1) {
				endIndex--;
				ptLineAndWordSize = stringExtent(gc,outputLine + word.substring(0, endIndex) + " ");
			}

			if (DEBUG) {
				System.out.println("excess starts at " + endIndex + " of "
						+ word.length() + ". "
						+ "wrap?" + wrap);
			}
			/* doesn't appear to be needed anymore */
			if (wrap && (printFlags & FLAG_FULLLINESONLY) != 0) {
				int nextLineHeight = stringExtent(gc,GOOD_STRING).y;
				if (iCurrentHeight + ptLineAndWordSize.y + nextLineHeight > printArea.height) {
					if (DEBUG) {
						System.out.println("turn off wrap");
					}
					wrap = false;
				}
			}
			/**/

			if (endIndex > 0 && outputLine.length() > 0 && !nothingFit) {
				outputLine.append(space);
			}

			//int w = ptLineAndWordSize.x - lineInfo.outputLineExtent.x;
			if (wrap && !nothingFit && !bWordLargerThanWidth && !isLastLine) {
				// whole word is excess
				if (DEBUG) {
					System.out.println("whole word is excess");
				}
				return 0;
			}

			outputLine.append(word.substring(0, endIndex));
			if (!wrap) {
				int len = outputLine.length();
				if (len == 0) {
					if (word.length() > 0) {
						outputLine.append(word.charAt(0));
					} else if (sLine.length() > 0) {
						outputLine.append(sLine.charAt(0));
					}
				} else {
					if (len > 2) {
						len -= 2;
					}
					truncate( outputLine, len );
					cutoff = true;
					if (DEBUG) {
						System.out.println("set cutoff (3)");
					}
				}
			}
			//drawLine(gc, outputLine, swtFlags, rectDraw);
			if (DEBUG) {
				System.out.println("excess " + word.substring(endIndex));
			}
			//System.out.println("w9 = " + lineInfo.outputLineExtent.x + ";h=" + lineInfo.outputLineExtent.y);
			return endIndex;
		}

		lineInfo.outputLineExtent.x = ptLineAndWordSize.x;
		if (lineInfo.outputLineExtent.x > printArea.width) {
			if (space.length() > 0) {
				space.delete(0, space.length());
			}

			if (!wrap) {
				int len = outputLine.length();
				if (len == 0) {
					if (word.length() > 0) {
						outputLine.append(word.charAt(0));
					} else if (sLine.length() > 0) {
						outputLine.append(sLine.charAt(0));
					}
				} else {
					if (len > 2) {
						len -= 2;
					}
					truncate( outputLine, len );
					cutoff = true;
					if (DEBUG) {
						System.out.println("set cutoff (4)");
					}
				}
				//System.out.println("w5 = " + lineInfo.outputLineExtent.x + ";h=" + lineInfo.outputLineExtent.y);
				return -1;
			} else {
				//System.out.println("w6 = " + lineInfo.outputLineExtent.x + ";h=" + lineInfo.outputLineExtent.y);
				return 0;
			}
			//drawLine(gc, outputLine, swtFlags, rectDraw);
		}

		if (outputLine.length() > 0) {
			outputLine.append(space);
		}
		outputLine.append(word);
		if (space.length() > 0) {
			space.delete(0, space.length());
		}
		space.append(' ');

		//System.out.println("w4 = " + lineInfo.outputLineExtent.x + ";h=" + lineInfo.outputLineExtent.y);
		return -1;
	}

	/**
	 * printArea is updated to the position of the next row
	 *
	 * @param gc
	 * @param outputLine
	 * @param swtFlags
	 * @param printArea
	 * @param noDraw
	 */
	private void drawLine(GC gc, LineInfo lineInfo, int swtFlags,
			Rectangle printArea, boolean noDraw) {
		String text = lineInfo.lineOutputed;
		// TODO: ensure width and height have values
		if (lineInfo.outputLineExtent.x == 0 || lineInfo.outputLineExtent.y == 0) {
			lineInfo.outputLineExtent = stringExtent(gc,text);
		}

		int x0;
		if ((swtFlags & SWT.RIGHT) != 0) {
			x0 = printArea.x + printArea.width - lineInfo.outputLineExtent.x;
		} else if ((swtFlags & SWT.CENTER) != 0) {
			x0 = printArea.x + (printArea.width - lineInfo.outputLineExtent.x + 1) / 2;		// generally putting an extra pixel space to the left rather than right looks better
		} else {
			x0 = printArea.x;
		}
		lineInfo.outputLineStartX = x0;

		int y0 = printArea.y;

		int lineInfoRelEndPos = lineInfo.relStartPos
				+ lineInfo.lineOutputed.length();
		int relStartPos = lineInfo.relStartPos;
		int lineStartPos = 0;

		URLInfo urlInfo = null;
		boolean drawURL = hasHitUrl();

		if (drawURL) {
			URLInfo[] hitUrlInfo = getHitUrlInfo();
			int nextHitUrlInfoPos = 0;

			while (drawURL) {
				drawURL = false;
				for (int i = nextHitUrlInfoPos; i < hitUrlInfo.length; i++) {
					urlInfo = hitUrlInfo[i];

					drawURL = (urlInfo.relStartPos < lineInfoRelEndPos)
							&& (urlInfo.relStartPos + urlInfo.titleLength > relStartPos)
							&& (relStartPos >= lineInfo.relStartPos)
							&& (relStartPos < lineInfoRelEndPos);
					if (drawURL) {
						nextHitUrlInfoPos = i + 1;
						break;
					}
				}

				if (!drawURL) {
					break;
				}

				//int numHitUrlsAlready = urlInfo.hitAreas == null ? 0 : urlInfo.hitAreas.size();

				// draw text before url
				int i = lineStartPos + urlInfo.relStartPos - relStartPos;
				//System.out.println("numHitUrlsAlready = " + numHitUrlsAlready + ";i=" + i);
				if (i > 0 && i > lineStartPos && i <= text.length()) {
					String s = text.substring(lineStartPos, i);
					//gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_RED));
					x0 += drawText(gc, s, x0, y0, lineInfo.outputLineExtent.y, null, noDraw, true).x;

					relStartPos += (i - lineStartPos);
					lineStartPos += (i - lineStartPos);
					//System.out.println("|" + s + "|" + textExtent.x);
				}

				// draw url text
				int end = i + urlInfo.titleLength;
				if (i < 0) {
					i = 0;
				}
				//System.out.println("end=" + end + ";" + text.length() + ";titlelen=" + urlInfo.titleLength);
				if (end > text.length()) {
					end = text.length();
				}
				String s = text.substring(i, end);
				relStartPos += (end - i);
				lineStartPos += (end - i);
				Point pt = null;
				//System.out.println("|" + s + "|");
				Color fgColor = null;
				if (!noDraw) {
					fgColor = gc.getForeground();

					/*
					if (urlInfo.dropShadowColor != null) {
						gc.setForeground(urlInfo.dropShadowColor);
						drawText(gc, s, x0 + 1, y0 + 1, lineInfo.outputLineExtent.y, null, noDraw,
								false);
					}
					*/

					if (urlInfo.urlColor != null) {
						gc.setForeground(urlInfo.urlColor);
					} else if (urlColor != null) {
						gc.setForeground(urlColor);
					}
				}
				if (urlInfo.hitAreas == null) {
					urlInfo.hitAreas = new ArrayList<>(1);
				}
				pt = drawText(gc, s, x0, y0, lineInfo.outputLineExtent.y, urlInfo.hitAreas, noDraw,
						true);
				if (!noDraw) {
					if (urlInfo.urlUnderline) {
						gc.drawLine(x0, y0 + pt.y - 1, x0 + pt.x - 1, y0 + pt.y - 1);
					}
					gc.setForeground(fgColor);
				}

				if (urlInfo.hitAreas == null) {
					urlInfo.hitAreas = new ArrayList<>(1);
				}
				//gc.drawRectangle(new Rectangle(x0, y0, pt.x, lineInfo.outputLineExtent.y));

				x0 += pt.x;
			}
		}

		// draw text after url
		if (lineStartPos < text.length()) {
			String s = text.substring(lineStartPos);
			if (!noDraw) {
				drawText(gc, s, x0, y0, lineInfo.outputLineExtent.y, null, noDraw, false);
			}
		}
		printArea.y += lineInfo.outputLineExtent.y;
	}

	private Point drawText(GC gc, String s, int x, int y, int height,
			List<Rectangle> hitAreas, boolean nodraw, boolean calcExtent) {
		Point textExtent;

		if (images != null) {
  		int pctPos = s.indexOf('%');
  		int lastPos = 0;
  		int w = 0;
  		int h = 0;
  		while (pctPos >= 0) {
    		if (pctPos >= 0 && s.length() > pctPos + 1) {
    			int imgIdx = s.charAt(pctPos + 1) - '0';

    			if (imgIdx >= images.length || imgIdx < 0 || images[imgIdx] == null) {
      			String sStart = s.substring(lastPos, pctPos + 1);
    				textExtent = textExtent(gc,sStart);
    				int centerY = y + (height / 2 - textExtent.y / 2);
        		if (hitAreas != null) {
        			hitAreas.add(new Rectangle(x, centerY, textExtent.x, textExtent.y));
        		}
      			if (!nodraw) {
      				drawText(gc,sStart, x, centerY, true);
      			}
      			x += textExtent.x;
      			w += textExtent.x;
      			h = Math.max(h, textExtent.y);

      			lastPos = pctPos + 1;
        		pctPos = s.indexOf('%', pctPos + 1);
    				continue;
    			}

    			String sStart = s.substring(lastPos, pctPos);
    			textExtent = textExtent(gc,sStart);
  				int centerY = y + (height / 2 - textExtent.y / 2);
    			if (!nodraw) {
    				drawText(gc,sStart, x, centerY, true);
    			}
    			x += textExtent.x;
    			w += textExtent.x;
    			h = Math.max(h, textExtent.y);
    			if (hitAreas != null) {
    				hitAreas.add(new Rectangle(x, centerY, textExtent.x, textExtent.y));
    			}

    			//System.out.println("drawimage: " + x + "x" + y + ";idx=" + imgIdx);
    			Rectangle imgBounds = images[imgIdx].getBounds();
    			float scale = 1.0f;
  				if (imageScales != null && imageScales.length > imgIdx) {
  					scale = imageScales[imgIdx];
  				}
  				int scaleImageWidth = (int) (imgBounds.width * scale);
					int scaleImageHeight = (int) (imgBounds.height * scale);


  				centerY = y + (height / 2 - scaleImageHeight / 2);
    			if (hitAreas != null) {
    				hitAreas.add(new Rectangle(x, centerY, scaleImageWidth, scaleImageHeight));
    			}
    			if (!nodraw) {
    				//gc.drawImage(images[imgIdx], x, centerY);
    				gc.drawImage(images[imgIdx], 0, 0, imgBounds.width,
								imgBounds.height, x, centerY, scaleImageWidth, scaleImageHeight);
    			}
    			x += scaleImageWidth;
    			w += scaleImageWidth;

    			h = Math.max(h, scaleImageHeight);
    		}
    		lastPos = pctPos + 2;
    		pctPos = s.indexOf('%', lastPos);
  		}

  		if (s.length() >= lastPos) {
    		String sEnd = s.substring(lastPos);
  			textExtent = textExtent(gc,sEnd);
				int centerY = y + (height / 2 - textExtent.y / 2);
  			if (hitAreas != null) {
  				hitAreas.add(new Rectangle(x, centerY, textExtent.x, textExtent.y));
  			}
  			if (!nodraw) {
  				drawText(gc,sEnd, x, centerY, true);
  			}
  			//x += textExtent.x;
  			w += textExtent.x;
  			h = Math.max(h, textExtent.y);
  		}
  		return new Point(w, h);
		}


		if (!nodraw) {
			drawText(gc,s, x, y, true);
		}
		if (!calcExtent && hitAreas == null) {
			return null;
		}
		textExtent = textExtent(gc,s);
		if (hitAreas != null) {
			hitAreas.add(new Rectangle(x, y, textExtent.x, textExtent.y));
		}
		return textExtent;
	}

	public static Point
	stringExtent(
		GC		gc,
		String	text )
	{
		char[] chars = text.toCharArray();
		
		for ( char c: chars ){
			
			if ( Character.isHighSurrogate(c)){

				TextLayout tl = new TextLayout(gc.getDevice());
				
				tl.setText(text);
				tl.setFont( gc.getFont());
				
				Rectangle bounds = tl.getLineBounds(0);
				
				tl.dispose();
				
				Point temp = gc.stringExtent( text );
				
				return( new Point( bounds.width, Math.max( temp.y, bounds.height )));
			}
		}
		
		return( gc.stringExtent( text ));
	}
	
	private static Point
	textExtent(
		GC			gc,
		String		text )
	{
		char[] chars = text.toCharArray();
		
		for ( char c: chars ){
			
			if ( Character.isHighSurrogate(c)){

				TextLayout tl = new TextLayout(gc.getDevice());
				
				tl.setText(text);
				tl.setFont( gc.getFont());
				
				Rectangle bounds = tl.getBounds();
				
				tl.dispose();
				
				Point temp = gc.textExtent( text );
				
				return( new Point( bounds.width, Math.max( temp.y, bounds.height )));
			}
		}
		
		return( gc.textExtent( text ));
	}
	
	private static void
	drawText(
		GC			gc,
		String		text,
		int			x,
		int			y,
		boolean		transparent )
	{
		char[] chars = text.toCharArray();
		
		for ( char c: chars ){
			
			if ( Character.isHighSurrogate(c)){
				
					// this handles supplemental chars...
				
				TextLayout tl = new TextLayout(gc.getDevice());
				
				tl.setText(text);
				tl.setFont( gc.getFont());
				
				tl.draw( gc,  x,  y );
				
				tl.dispose();
				
				return;
			}
		}
		
		gc.drawText( text, x, y, transparent );
	}
	
	public static void main(String[] args) {

		System.out.println("main start");

		//String s = "this is $1.00";
		//String s2 = "$1";
		//String s3 = s2.replaceAll("\\$", "\\\\\\$");
		//System.out.println(s3);
		//s.replaceAll("h", s3);
		//System.out.println(s);
		//if (true) {
		//	return;
		//}

		final Display display = Display.getDefault();
		final Shell shell = new Shell(display, SWT.SHELL_TRIM);

		try {
			SWTThread.createInstance(null);
		} catch (SWTThreadAlreadyInstanciatedException e) {
			e.printStackTrace();
		}

		ImageLoader imageLoader = ImageLoader.getInstance();

		final Image[] images = {
			imageLoader.getImage("logo32"),
			imageLoader.getImage("logo64"),
			imageLoader.getImage("logo16"),
			imageLoader.getImage("logo128"),
		};

		//final String text = "Opil Wrir, Na Poys Iysk, Yann Only. test of the string printer averlongwordthisisyesindeed";
		final String text = "Apple <A HREF=\"aa\">Banana</a>, Cow <A HREF=\"ss\">Dug Ergo</a>, Flip Only. test of the string printer averlongwordthisisyesindeed " + Constants.INFINITY_STRING;
		//final String text = "Apple, Cow sfjkhsd %1 f, Flip Only. test of %0 the string printer averlongwordthisisyesindeed";

		shell.setSize(500, 600);

		GridLayout gridLayout = new GridLayout(2, false);
		shell.setLayout(gridLayout);

		int initHeight = 67;
		Composite cButtons = new Composite(shell, SWT.NONE);
		GridData gridData = new GridData(SWT.NONE, SWT.FILL, false, true);
		cButtons.setLayoutData(gridData);
		final Canvas cPaint = new Canvas(shell, SWT.DOUBLE_BUFFERED);
		gridData = new GridData(SWT.FILL, SWT.NONE, true, false);
		gridData.heightHint = initHeight;
		cPaint.setLayoutData(gridData);

		cButtons.setLayout(new RowLayout(SWT.VERTICAL));

		Listener l = new Listener() {
			@Override
			public void handleEvent(Event event) {
				cPaint.redraw();
			}
		};

		final Text txtText = new Text(cButtons, SWT.WRAP | SWT.MULTI | SWT.BORDER);
		txtText.setText(text);
		txtText.addListener(SWT.Modify, l);
		txtText.setLayoutData(new RowData(100, 200));
		txtText.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == 'a' && e.stateMask == SWT.CONTROL) {
					txtText.selectAll();
				}
			}
		});

		final Button btnSkipClip = new Button(cButtons, SWT.CHECK);
		btnSkipClip.setText("Skip Clip");
		btnSkipClip.setSelection(true);
		btnSkipClip.addListener(SWT.Selection, l);

		final Button btnFullOnly = new Button(cButtons, SWT.CHECK);
		btnFullOnly.setText("Full Lines Only");
		btnFullOnly.setSelection(true);
		btnFullOnly.addListener(SWT.Selection, l);

		final Combo cboVAlign = new Combo(cButtons, SWT.READ_ONLY);
		cboVAlign.add("Top");
		cboVAlign.add("Bottom");
		cboVAlign.add("None");
		cboVAlign.addListener(SWT.Selection, l);
		cboVAlign.select(0);

		final Combo cboHAlign = new Combo(cButtons, SWT.READ_ONLY);
		cboHAlign.add("Left");
		cboHAlign.add("Center");
		cboHAlign.add("Right");
		cboHAlign.add("None");
		cboHAlign.addListener(SWT.Selection, l);
		cboHAlign.select(0);

		final Button btnWrap = new Button(cButtons, SWT.CHECK);
		btnWrap.setText("Wrap");
		btnWrap.setSelection(true);
		btnWrap.addListener(SWT.Selection, l);

		final Button btnGCAdvanced = new Button(cButtons, SWT.CHECK);
		btnGCAdvanced.setText("gc.Advanced");
		btnGCAdvanced.setSelection(true);
		btnGCAdvanced.addListener(SWT.Selection, l);

		final Spinner spinnerHeight = new Spinner(cButtons, SWT.BORDER);
		spinnerHeight.setSelection(initHeight);
		spinnerHeight.addListener(SWT.Selection, event -> {
			GridData gridData1 = (GridData) cPaint.getLayoutData();
			gridData1.heightHint = spinnerHeight.getSelection();
			cPaint.setLayoutData(gridData1);
			shell.layout();
		});

		final Label lblInfo = new Label(shell, SWT.WRAP);
		lblInfo.setLayoutData(Utils.getWrappableLabelGridData(2, 0));
		lblInfo.setText("Welcome");


		Listener l2 = new Listener() {
			URLInfo lastHitInfo = null;

			@Override
			public void handleEvent(Event event) {
				GC gc = event.gc;
				//System.out.println("HE" + event.type);
				boolean ourGC = gc == null;
				if (ourGC) {
					gc = new GC(cPaint);
				}
				try {
					gc.setAdvanced(btnGCAdvanced.getSelection());
					GCStringPrinter sp = buildSP(gc);
					Color colorURL = gc.getDevice().getSystemColor(SWT.COLOR_RED);
					Color colorURL2 = gc.getDevice().getSystemColor(
							SWT.COLOR_DARK_MAGENTA);

					if (event.type == SWT.MouseMove) {
						Point pt = cPaint.toControl(display.getCursorLocation());
						URLInfo hitUrl = sp.getHitUrl(pt.x, pt.y);
						String url1 = hitUrl == null || hitUrl.url == null ? ""
								: hitUrl.url;
						String url2 = lastHitInfo == null || lastHitInfo.url == null ? ""
								: lastHitInfo.url;

						if (url1.equals(url2)) {
							return;
						}
						cPaint.redraw();
						lastHitInfo = hitUrl;
						return;
					}

					Rectangle bounds = cPaint.getClientArea();

					Color colorBox = gc.getDevice().getSystemColor(SWT.COLOR_YELLOW);
					Color colorText = gc.getDevice().getSystemColor(SWT.COLOR_BLACK);

					gc.setForeground(colorText);

					Point pt = cPaint.toControl(display.getCursorLocation());
					sp.setUrlColor(colorURL);
					URLInfo hitUrl = sp.getHitUrl(pt.x, pt.y);
					if (hitUrl != null) {
						shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
						hitUrl.urlColor = colorURL2;
					} else {
						shell.setCursor(null);
					}
					boolean fit = sp.printString();

					String info = fit ? "fit" : "no fit";
					info += "; Calculated Size=" + sp.getCalculatedSize() + "\nDrawRect "
							+ sp.getCalculatedDrawRect() + "\nOrig Rect " + sp.getPrintArea();
					
					lblInfo.setText(info);

					bounds.width--;
					bounds.height--;

					gc.setForeground(colorBox);
					gc.drawRectangle(bounds);

					bounds.height -= 20;
					bounds.y += 10;
					gc.setLineStyle(SWT.LINE_DOT);
					gc.drawRectangle(bounds);

					//System.out.println("-         " + System.currentTimeMillis());

				} catch (Throwable t) {
					t.printStackTrace();

				} finally {
					if (ourGC) {
						gc.dispose();
					}
				}
			}

			private GCStringPrinter buildSP(GC gc) {
				//gc.setFont(Utils.getFontWithHeight(shell.getFont(), gc, 15));
				//gc.setTextAntialias(SWT.ON);
				Rectangle bounds = cPaint.getClientArea();
				bounds.y += 10;
				bounds.height -= 20;


				int style = btnWrap.getSelection() ? SWT.WRAP : 0;
				if (cboVAlign.getSelectionIndex() == 0) {
					style |= SWT.TOP;
				} else if (cboVAlign.getSelectionIndex() == 1) {
					style |= SWT.BOTTOM;
				}

				if (cboHAlign.getSelectionIndex() == 0) {
					style |= SWT.LEFT;
				} else if (cboHAlign.getSelectionIndex() == 1) {
					style |= SWT.CENTER;
				} else if (cboHAlign.getSelectionIndex() == 2) {
					style |= SWT.RIGHT;
				}

				String text = txtText.getText();
				text = text.replaceAll("\r\n", "\n");
				GCStringPrinter sp = new GCStringPrinter(gc, text, bounds,
						btnSkipClip.getSelection(), btnFullOnly.getSelection(), style);
				sp.setImages(images);
				sp.calculateMetrics();

				return sp;
			}
		};
		cPaint.addListener(SWT.Paint, l2);
		cPaint.addListener(SWT.MouseMove, l2);

		shell.open();
		System.out.println("shell is open " + shell.getClientArea());

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	/**
	 *
	 */
	public GCStringPrinter(GC gc, String string, Rectangle printArea,
			boolean skipClip, boolean fullLinesOnly, int swtFlags) {
		this.gc = gc;
		this.string = string;
		this.printArea = printArea;
		this.swtFlags = swtFlags;

		printFlags = 0;
		if (skipClip) {
			printFlags |= FLAG_SKIPCLIP;
		}
		if (fullLinesOnly) {
			printFlags |= FLAG_FULLLINESONLY;
		}
	}

	public GCStringPrinter(GC gc, String string, Rectangle printArea,
			int printFlags, int swtFlags) {
		this.gc = gc;
		this.string = string;
		this.printArea = printArea;
		this.swtFlags = swtFlags;
		this.printFlags = printFlags;
	}

	public boolean printString() {
		return _printString();
	}

	public boolean printString(int _printFlags) {
		int oldPrintFlags = this.printFlags;
		printFlags |= _printFlags;
		boolean b = _printString();
		this.printFlags = oldPrintFlags;
		return b;
	}

	public void calculateMetrics() {
		int oldPrintFlags = printFlags;
		printFlags |= FLAG_NODRAW;
		_printString();
		printFlags = oldPrintFlags;
	}

	/**
	 * DO NOT REMOVE OR CHANGE RETURN TYPE -- USED BY PLUGINS
	 */
	public void printString(GC gc, Rectangle rectangle, int swtFlags) {
		printString2(gc, rectangle, swtFlags);
	}

	public boolean printString2(GC gc, Rectangle rectangle, int swtFlags) {
		this.gc = gc;
		int printFlags = this.printFlags;
		if (printArea.width == rectangle.width) {
			printFlags |= FLAG_KEEP_URL_INFO;
		}
		printArea = rectangle;
		this.swtFlags = swtFlags;
		return printString(printFlags);
	}

	public Point getCalculatedSize() {
		return size;
	}

	public Point getCalculatedPreferredSize() {
		return preferredSize;
	}

	public Color getUrlColor() {
		return urlColor;
	}

	public void setUrlColor(Color urlColor) {
		this.urlColor = urlColor;
	}

	public URLInfo getHitUrl(int x, int y) {
		if (listUrlInfo == null || listUrlInfo.size() == 0) {
			return null;
		}
		for (URLInfo urlInfo : listUrlInfo) {
			if (urlInfo.hitAreas != null) {
				for (Rectangle r : urlInfo.hitAreas) {
					if (r.contains(x, y)) {
						return urlInfo;
					}
				}
			}
		}
		return null;
	}

	public URLInfo[] getHitUrlInfo() {
		if (listUrlInfo == null) {
			return new URLInfo[0];
		}
		return (URLInfo[]) listUrlInfo.toArray(new URLInfo[0]);
	}

	public boolean hasHitUrl() {
		return listUrlInfo != null && listUrlInfo.size() > 0;
	}

	public boolean isCutoff() {
		return cutoff;
	}

	public boolean isTruncated(){
		return truncated;
	}
	
	public void setImages(Image[] images) {
		this.images = images;
	}

	public float[] getImageScales() {
		return imageScales;
	}

	public void setImageScales(float[] imageScales) {
		this.imageScales = imageScales;
	}

	/**
	 * @return
	 *
	 * @since 4.0.0.1
	 */
	public String getText() {
		return string;
	}

	public boolean isWordCut() {
		return isWordCut;
	}

	/**
	 * Get the area that was drawn to.
	 * Draw Rectangle is a subset of the original printArea that was painted on.
	 * In cases where text was centered vertically, or bottom aligned, y may
	 * be larger than printArea.y. <code>x</code> may be difference if text is
	 * centered or right aligned.  Note: <code>x</code> is only adjusted if text
	 * is drawn.
	 * <p/>
	 * Returned width and height are the same as {@link #getCalculatedSize()}
	 */
	public Rectangle getCalculatedDrawRect() {
		return drawRect;
	}

	public Rectangle getPrintArea() {
		return printArea;
	}

	/*
	private Point stringExtent(GC gc, String s) {
		Matcher m = patOver1000.matcher(s);
		//if (s.length() > MAX_LINE_LEN) {
		if (m.find()) {
			System.out.println(s.length() + "\n" + Debug.getStackTrace(false, false));
			System.out.println(s);
		}
		return gc.stringExtent(s);
	}

	private Point textExtent(GC gc, String s) {
		Matcher m = patOver1000.matcher(s);
		//if (s.length() > MAX_LINE_LEN) {
		if (m.find()) {
			System.out.println(s.length() + "\n" + Debug.getStackTrace(false, false));
			System.out.println(s);
		}
		return gc.textExtent(s);
	}
	*/
}
