/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.biglybt.ui.swt;

import java.lang.reflect.Method;

import org.eclipse.swt.dnd.ByteArrayTransfer;
import org.eclipse.swt.dnd.HTMLTransfer;
import org.eclipse.swt.dnd.TransferData;

/**
 * {@link HTMLTransfer} fixed for (invalid?) parsing of data
 */
public class FixedHTMLTransfer
	extends ByteArrayTransfer
{
	static FixedHTMLTransfer _instance = new FixedHTMLTransfer();
	HTMLTransfer htmlTransfer = HTMLTransfer.getInstance();

	public static FixedHTMLTransfer getInstance() {
		return _instance;
	}

	@Override
	public void javaToNative(Object object, TransferData transferData) {
		htmlTransfer.javaToNative(object, transferData);
	}

	@Override
	public Object nativeToJava(TransferData transferData) {

		Object o = htmlTransfer.nativeToJava(transferData);

		if (!Utils.isGTK3) {
			return o;
		}

		// My theory gtk_selection_data_get_format always returns 8 for a String,
		// independent of charset
		// Wish I could try gtk_selection_data_get_text(selection_data)

		// If SWT's call returns a 1 character string, do our own check
		try {
			if (o instanceof String && ((String) o).length() == 1) {

				Class claTD = TransferData.class;
				Class claOS = Class.forName("org.eclipse.swt.internal.gtk.OS");
				//int size = (transferData.format * transferData.length / 8) / 2 * 2;
				int format = claTD.getField("format").getInt(transferData);
				int length = claTD.getField("length").getInt(transferData);
				int size = (format * length / 8) / 2 * 2;

				//System.out.println("we think format="+ format + "; length=" + length + "; size=" + size);


				Object pValueObject = claTD.getField("pValue").get(transferData);

				if (size > 2) {


					byte[] utf8 = new byte[size];
					//OS.memmove(utf8, transferData.pValue, size);
					if (pValueObject instanceof Long) {
						claOS.getMethod("memmove", byte[].class, long.class,
								long.class).invoke(null, utf8, ((Long) pValueObject).longValue(),
								(long) size);
					} else {
						claOS.getMethod("memmove", byte[].class, int.class,
								int.class).invoke(null, utf8, ((Integer) pValueObject).intValue(),
								size);
					}
					/**
					String s;

					System.out.println("bytes= " + Arrays.toString(utf8));
					s = new String(utf8, "UTF-8");
					System.out.println("UTF-8 = " + s + "/" + s.length());
					s = new String(utf8);
					System.out.println(Charset.defaultCharset() + " = " + s);
					/**/

					if (size > 2) {
						boolean bFirst0 = utf8[0] == 0;
						boolean bSecond0 = utf8[1] == 0;
						if (bFirst0) {
							String string = new String(utf8, "UTF-16BE");
							//System.out.println("UTF-16BE = " + string + "/" + string.length());
							return string;
						} else if (bSecond0) {
							String string = new String(utf8, "UTF-16LE");
							//System.out.println("UTF-16LE = " + string + "/" + string.length());
							return string;
						} else {
							String string = new String(utf8);
							//System.out.println(Charset.defaultCharset() + " = " + string + "/" + string.length());
							return string;
						}
					}
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return o;
	}

	@Override
	public TransferData[] getSupportedTypes() {
		return htmlTransfer.getSupportedTypes();
	}

	@Override
	public boolean isSupportedType(TransferData transferData) {
		return htmlTransfer.isSupportedType(transferData);
	}

	protected int[] getTypeIds() {
		//return htmlTransfer.getTypeIds();
		try {
			Method m_getTypeIDs = htmlTransfer.getClass().getDeclaredMethod("getTypeIds");
			m_getTypeIDs.setAccessible(true);
			return (int[]) m_getTypeIDs.invoke(htmlTransfer);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return new int[] {};
	}

	protected String[] getTypeNames() {
		try {
			Method m_getTypeNames = htmlTransfer.getClass().getDeclaredMethod("getTypeNames");
			m_getTypeNames.setAccessible(true);
			return (String[]) m_getTypeNames.invoke(htmlTransfer);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return new String[] {
		};
	}
}