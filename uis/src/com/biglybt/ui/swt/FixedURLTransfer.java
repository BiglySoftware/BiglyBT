/*
 * Created on 01.12.2003
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */
package com.biglybt.ui.swt;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.swt.SWTException;
import org.eclipse.swt.dnd.ByteArrayTransfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.dnd.URLTransfer;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.UrlUtils;

/**
 * URL Transfer type for Drag and Drop of URLs
 * Windows IDs are already functional.
 *
 * Please use Win32TransferTypes to determine the IDs for other OSes!
 *
 * @see com.biglybt.ui.swt.test.Win32TransferTypes
 * @author Rene Leonhardt
 *
 * @author TuxPaper (require incoming string types have an URL prefix)
 * @author TuxPaper (UTF-8, UTF-16, BOM stuff)
 *
 * TuxPaper's Notes:
 * This class is flakey.  It's better to use HTMLTransfer, and then parse
 * the URL from the HTML.  However, IE drag and drops do not support
 * HTMLTransfer, so this class must stay
 *
 * Windows
 * ---
 * TypeIDs seem to be assigned differently on different platform versions
 * (or maybe even different installations!).   Here's some examples
 * 49243: Chrome. HTML. <a href="foo/?id=5&amp;foo=1">bar</a>
 * 49314: Moz/IE 0x01 4-0x00 0x80 lots-of-0x00 "[D]URL" lots-more-0x00
 * 49315: Moz/IE Same as 49315, except unicode
 * 49313: Moz/IE URL in .url format  "[InternetShortcut]\nURL=%1"
 * 49324: Moz/IE URL in text format
 * 49395: Moz Same as 49324, except unicode
 * 49319: Moz Dragged HTML Fragment with position information
 * 49398: Moz Dragged HTML Fragment (NO position information, just HTML), unicode
 * 49396: Moz HTML.  Unknown.
 * 49863: Chrome. URL\r\nTitle
 *
 * There's probably a link to the ID and they type name in the registry, or
 * via a Windows API call.  We don't want to do that, and fortunately,
 * SWT doesn't seem to pay attention to getTypeIds() on Windows, so we check
 * every typeid we get to see if we can parse an URL from it.
 *
 * Also, dragging from the IE URL bar hangs SWT (sometimes for a very long
 * time).  Fortunately, most people willdrag the URL from the actual content
 * window.
 *
 * Dragging an IE bookmark is actually dragging the .url file, and should be
 * handled by the FileTranfer (and then opening it and extracting the URL).
 * Moz Bookmarks are processed as HTML.
 *
 * Linux
 * ---
 * For Linux, this class isn't required.
 * HTMLTransfer will take care of Gecko and Konquerer.
 *
 * Opera
 * ---
 * As of 8.5, Opera still doesn't allow dragging outside of itself (at least on
 * windows)
 *
 */

public class FixedURLTransfer extends ByteArrayTransfer {
	private String[] supportedTypeNames;
	private int[] supportedTypeIDs;

	/** We are in the process of checking a string to see if it's a valid URL */
	private boolean bCheckingString = false;

	private static boolean DEBUG = false;

	private static boolean DISABLED = Constants.isUnix;

	// Opera 7 LINK DRAG & DROP IMPOSSIBLE (just inside Opera)
	private static final String[] ourSupportedTypeNames = new String[] {
			"CF_UNICODETEXT",
			"CF_TEXT",
			"OEM_TEXT"
	};

	private static final int[] ourSupportedTypeIds = new int[] {
			13,
			1,
			17
	};

	private static FixedURLTransfer _instance;

	private static Field field_TransferData_result;

  URLTransfer urlTransfer = URLTransfer.getInstance();

  static {
	  _instance = new FixedURLTransfer();

	  try {
		  field_TransferData_result = TransferData.class.getField("result");
	  } catch (Throwable e) {
		  field_TransferData_result = null;
	  }
  }

  public static FixedURLTransfer getInstance() {
	  return _instance;
	}

	private FixedURLTransfer() {
		try {
			Method m_getTypeIDs = urlTransfer.getClass().getDeclaredMethod("getTypeIds");
			m_getTypeIDs.setAccessible(true);
			int[] superIDs = (int[]) m_getTypeIDs.invoke(urlTransfer);

			supportedTypeIDs = new int[superIDs.length + ourSupportedTypeIds.length];
			System.arraycopy(superIDs, 0, supportedTypeIDs, 0, superIDs.length);
			System.arraycopy(ourSupportedTypeIds, 0, supportedTypeIDs, superIDs.length, ourSupportedTypeIds.length);
		} catch (Throwable e) {
			e.printStackTrace();
			supportedTypeIDs = ourSupportedTypeIds;
		}

		try {
			Method m_getTypeNames = urlTransfer.getClass().getDeclaredMethod("getTypeNames");
			m_getTypeNames.setAccessible(true);
			String[] superNames = (String[]) m_getTypeNames.invoke(urlTransfer);

			supportedTypeNames = new String[superNames.length + ourSupportedTypeNames.length];
			System.arraycopy(superNames, 0, supportedTypeNames, 0, superNames.length);
			System.arraycopy(ourSupportedTypeNames, 0, supportedTypeNames, superNames.length, ourSupportedTypeNames.length);
		} catch (Throwable e) {
			e.printStackTrace();
			supportedTypeNames = ourSupportedTypeNames;
		}

	}

	@Override
	public void javaToNative(Object object, TransferData transferData) {
  	try {
			urlTransfer.javaToNative(object, transferData);
	  } catch (SWTException swtException) {
  		if (DISABLED) {
  			throw swtException;
		  }
	  }
  	if (DISABLED) {
  		return;
	  }

		if (DEBUG)
			System.out.println("javaToNative called");

		boolean superDidTheJob = isResultOk(transferData);

		if (superDidTheJob) {
			return;
		}

		setResultField(transferData, false);
		if (object == null || !(object instanceof URLType[]))
			return;

		if (isSupportedType(transferData)) {
			URLType[] myTypes = (URLType[]) object;
			try {
				// write data to a byte array and then ask super to convert to pMedium
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				DataOutputStream writeOut = new DataOutputStream(out);
				for (int i = 0, length = myTypes.length; i < length; i++) {
					writeOut.writeBytes(myTypes[i].linkURL);
					writeOut.writeBytes("\n");
					writeOut.writeBytes(myTypes[i].linkText);
				}
				byte[] buffer = out.toByteArray();
				writeOut.close();

				super.javaToNative(buffer, transferData);

				setResultField(transferData, true);

			} catch (IOException e) {
			}
		}
	}

	@Override
	public Object nativeToJava(TransferData transferData) {
		Object result = urlTransfer.nativeToJava(transferData);
		if (DISABLED || result != null) {
			return result;
		}

		if (DEBUG) System.out.println("nativeToJava called");
		try {
			if (isSupportedType(transferData)) {
				byte [] buffer = (byte[]) super.nativeToJava(transferData);
				return bytebufferToJava(buffer);
			}
		} catch (Exception e) {
			Debug.out(e);
		}

		return null;
	}

	private URLType bytebufferToJava(byte[] buffer) {
		if (buffer == null) {
			if (DEBUG) System.out.println("buffer null");
			return null;
		}

		if (buffer.length < 2) {
			if (DEBUG) System.out.println("buffer too small");
			return null;
		}

		URLType myData = null;
		try {
			String data;

			if (DEBUG) {
				for (int i = 0; i < buffer.length; i++) {
					if (buffer[i] >= 32)
						System.out.print(((char) buffer[i]));
					else
						System.out.print("#");
				}
				System.out.println();
			}
			boolean bFirst0 = buffer[0] == 0;
			boolean bSecond0 = buffer[1] == 0;
			if (bFirst0 && bSecond0)
				// This is probably UTF-32 Big Endian.
				// Let's hope default constructor can handle it (It can't)
				data = new String(buffer);
			else if (bFirst0)
				data = new String(buffer, "UTF-16BE");
			else if (bSecond0)
				data = new String(buffer, "UTF-16LE");
			else if (buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB
					&& buffer.length > 3 && buffer[2] == (byte) 0xBF)
				data = new String(buffer, 3, buffer.length - 3, "UTF-8");
			else if (buffer[0] == (byte) 0xFF || buffer[0] == (byte) 0xFE)
				data = new String(buffer, "UTF-16");
			else {
				data = new String(buffer);
			}
			int posNull = data.indexOf('\0');
			if (posNull >= 0) {
				data = data.substring(0, posNull);
			}

			int iPos = data.indexOf("\nURL=");
			if (iPos > 0) {
				int iEndPos = data.indexOf("\r", iPos);
				if (iEndPos < 0) {
					iEndPos = data.length();
				}
				myData = new URLType();
				myData.linkURL = data.substring(iPos + 5, iEndPos);
				myData.linkText = "";
			} else {
				String[] split = data.split("[\r\n]+", 2);

				myData = new URLType();
				myData.linkURL = (split.length > 0) ? split[0] : "";
				myData.linkText = (split.length > 1) ? split[1] : "";
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return myData;
	}


	@Override
	protected String[] getTypeNames() {
		return supportedTypeNames;
	}

	@Override
	protected int[] getTypeIds() {
		return supportedTypeIDs;
	}

	/**
	 * @param transferData
	 * @see org.eclipse.swt.dnd.Transfer#isSupportedType(org.eclipse.swt.dnd.TransferData)
	 * @return
	 */
	@Override
	public boolean isSupportedType(TransferData transferData) {
		if (DISABLED) {
			return urlTransfer.isSupportedType(transferData);
		}

		if (bCheckingString)
			return true;

		if (transferData == null)
			return false;

		// TODO: Check if it's a string list of URLs

		// String -- Check if URL, skip to next if not
		URLType url = null;

		if (DEBUG) System.out.println("Checking if type #" + transferData.type + " is URL");

		bCheckingString = true;
		try {
			byte[] buffer = (byte[]) super.nativeToJava(transferData);
			url = bytebufferToJava(buffer);
		} catch (Exception e) {
			Debug.out(e);
		} finally {
			bCheckingString = false;
		}

		if (url == null) {
			if (DEBUG) System.out.println("no, Null URL for type #" + transferData.type);
			return false;
		}

		if (UrlUtils.isURL(url.linkURL, false)) {
			if (DEBUG) System.out.println("Yes, " + url.linkURL + " of type #" + transferData.type);
			return true;
		}

		if (DEBUG) System.out.println("no, " + url.linkURL + " not URL for type #" + transferData.type);
		return false;
	}

	@Override
	public TransferData[] getSupportedTypes() {
		if (DISABLED) {
			return urlTransfer.getSupportedTypes();
		}
		return super.getSupportedTypes();
	}

	/**
	 * Sometimes, CF_Text will be in currentDataType even though CF_UNICODETEXT
	 * is present.  This is a workaround until its fixed properly.
	 * <p>
	 * Place it in <code>dropAccept</code>
	 *
	 * <pre>
	 *if (event.data instanceof URLTransfer.URLType)
	 *	event.currentDataType = URLTransfer.pickBestType(event.dataTypes, event.currentDataType);
	 * </pre>
	 *
	 * @param dataTypes
	 * @param def
	 * @return
	 */
	public static TransferData pickBestType(TransferData[] dataTypes,
			TransferData def) {
		if (Constants.isUnix) {
			return  def;
		}
		int bestTypeIndex = -1;
		for (int j = 0; j < dataTypes.length; j++) {
			try {
        TransferData data = dataTypes[j];
				Object o = _instance.nativeToJava(data);
				if (o instanceof URLType) {
					if (((URLType) o).linkText.length() > 0) {
						return data;
					} else {
						bestTypeIndex = j;
					}
				}
			} catch (Throwable t) {
				Debug.out("Picking Best Type", t);
			}
		}
		if (bestTypeIndex >= 0) {
			return dataTypes[bestTypeIndex];
		}
		return def;
	}

	public static class URLType {
		public String linkURL;

		public String linkText;

		public String toString() {
			return linkURL + "\n" + linkText;
		}
	}

	/**
	 * Test for various UTF Strings
	 * BOM information from http://www.unicode.org/faq/utf_bom.html
	 * @param args
	 */
	public static void main(String[] args) {

		Map map = new LinkedHashMap();
		map.put("UTF-8", new byte[] { (byte) 0xEF, (byte) 0xbb, (byte) 0xbf, 'H',
				'i' });
		map.put("UTF-32BE BOM", new byte[] { 0, 0, (byte) 0xFE, (byte) 0xFF, 'H',
				0, 0, 0, 'i', 0, 0, 0 });
		map.put("UTF-16LE BOM", new byte[] { (byte) 0xFF, (byte) 0xFE, 'H', 0,
				'i', 0 });
		map.put("UTF-16BE BOM", new byte[] { (byte) 0xFE, (byte) 0xFF, 0, 'H', 0,
				'i' });
		map.put("UTF-16LE", new byte[] { 'H', 0, 'i', 0 });
		map.put("UTF-16BE", new byte[] { 0, 'H', 0, 'i' });

		for (Iterator iterator = map.keySet().iterator(); iterator.hasNext();) {
			String element = (String) iterator.next();
			System.out.println(element + ":");
			byte[] buffer = (byte[]) map.get(element);

			try {
				System.out.println("byte array would be " + Arrays.toString("Hi".getBytes(element)));
			} catch (UnsupportedEncodingException e) {
				//e.printStackTrace();
			}
			try {
				System.out.println("toString(UTF8) would be " + new String(buffer, "utf8"));
			} catch (UnsupportedEncodingException e) {
				//e.printStackTrace();
			}


			boolean bFirst0 = buffer[0] == 0;
			boolean bSecond0 = buffer[1] == 0;
			String data = "";
			try {
				if (bFirst0 && bSecond0)
					// This is probably UTF-32 Big Endian.
					// Let's hope default constructor can handle it (It can't)
					data = new String(buffer);
				else if (bFirst0)
					data = new String(buffer, "UTF-16BE");
				else if (bSecond0)
					data = new String(buffer, "UTF-16LE");
				else if (buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB
						&& buffer.length > 3 && buffer[2] == (byte) 0xBF)
					data = new String(buffer, 3, buffer.length - 3, "UTF-8");
				else if (buffer[0] == (byte) 0xFF || buffer[0] == (byte) 0xFE)
					data = new String(buffer, "UTF-16");
				else {
					data = new String(buffer);
				}
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			System.out.println(data);
		}
	}

	private static boolean isResultOk(TransferData transferData) {
		if (field_TransferData_result == null) {
			return true;
		}
		try {
			int val = field_TransferData_result.getInt(transferData);
			return val == (Constants.isWindows ? 0 : 1);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	private static void setResultField(TransferData transferData, boolean ok) {
		if (field_TransferData_result == null) {
			return;
		}
		try {
			int val = ok ? (Constants.isWindows ? 0 : 1)
					: (Constants.isWindows ? -2147467259 : 0);
			field_TransferData_result.setInt(transferData, val);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
