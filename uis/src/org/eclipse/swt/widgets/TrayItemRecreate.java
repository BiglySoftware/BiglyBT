package org.eclipse.swt.widgets;

import com.biglybt.core.util.Debug;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * Simple wrapper around {@link TrayItem} to fix item not properly being
 * recreated
 * <p/>
 * Created by TuxPaper on 6/18/2017.
 */
public class TrayItemRecreate
	extends TrayItem
{

	private static Class claOS = null;

	private static boolean isUnicode;

	private static Class NOTIFYICONDATAA = null;

	private static Class NOTIFYICONDATAW = null;

	private static Class claNOTIFYICONDATA = null;

	private static int NOTIFYICONDATA_sizeof;

	private static boolean IS_64;

	static {
		try {
			claOS = Class.forName("org.eclipse.swt.internal.win32.OS");
			if (claOS != null) {

				isUnicode = claOS.getDeclaredField("IsUnicode").getBoolean(null);

				claNOTIFYICONDATA = Class.forName(
						"org.eclipse.swt.internal.win32.NOTIFYICONDATA");

				NOTIFYICONDATA_sizeof = claNOTIFYICONDATA.getField("sizeof").getInt(
						null);

				NOTIFYICONDATAA = Class.forName(
						"org.eclipse.swt.internal.win32.NOTIFYICONDATAA");
				NOTIFYICONDATAW = Class.forName(
						"org.eclipse.swt.internal.win32.NOTIFYICONDATAW");

				IS_64 = Class.forName(
						"org.eclipse.swt.internal.library").getDeclaredField(
								"IS_64").getBoolean(null);
			}
		} catch (Exception e) {
		}
	}

	/**
	 * Constructs a new instance of this class given its parent
	 * (which must be a <code>Tray</code>) and a style value
	 * describing its behavior and appearance. The item is added
	 * to the end of the items maintained by its parent.
	 * <p>
	 * The style value is either one of the style constants defined in
	 * class <code>SWT</code> which is applicable to instances of this
	 * class, or must be built by <em>bitwise OR</em>'ing together
	 * (that is, using the <code>int</code> "|" operator) two or more
	 * of those <code>SWT</code> style constants. The class description
	 * lists the style constants that are applicable to the class.
	 * Style bits are also inherited from superclasses.
	 * </p>
	 *
	 * @param parent a composite control which will be the parent of the new instance (cannot be null)
	 * @param style  the style of control to construct
	 * @throws IllegalArgumentException <ul>
	 *                                  <li>ERROR_NULL_ARGUMENT - if the parent is null</li>
	 *                                  </ul>
	 * @throws SWTException             <ul>
	 *                                  <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the parent</li>
	 *                                  <li>ERROR_INVALID_SUBCLASS - if this class is not an allowed subclass</li>
	 *                                  </ul>
	 * @see SWT
	 * @see Widget#checkSubclass
	 * @see Widget#getStyle
	 */
	public TrayItemRecreate(Tray parent, int style) {
		super(parent, style);
	}

	void recreate() {
		if (claOS == null) {
			return;
		}
		try {
			//NOTIFYICONDATA iconData = isUnicode ? new NOTIFYICONDATAW() : new NOTIFYICONDATAA();
			Object iconData = isUnicode ? NOTIFYICONDATAW.newInstance()
					: NOTIFYICONDATAA.newInstance();

			//iconData.cbSize = NOTIFYICONDATA.sizeof;
			//iconData.uID = id;
			//iconData.hWnd = display.hwndMessage;
			claNOTIFYICONDATA.getDeclaredField("cbSize").set(iconData,
					NOTIFYICONDATA_sizeof);

			Class claTrayItem = TrayItem.class;
			int id = claTrayItem.getDeclaredField("id").getInt(this);
			claNOTIFYICONDATA.getDeclaredField("uID").set(iconData, id);
			if (IS_64) {
				long hwndMessage = display.getClass().getDeclaredField(
						"hwndMessage").getLong(display);
				claNOTIFYICONDATA.getDeclaredField("hWnd").set(iconData, hwndMessage);
			} else {
				int hwndMessage = display.getClass().getDeclaredField(
						"hwndMessage").getInt(display);
				claNOTIFYICONDATA.getDeclaredField("hWnd").set(iconData, hwndMessage);
			}

			//OS.Shell_NotifyIcon(OS.NIM_DELETE, iconData);
			int NIM_DELETE = claOS.getDeclaredField("NIM_DELETE").getInt(null);
			Method meth_shell_notifyIcon = claOS.getDeclaredMethod("Shell_NotifyIcon",
					int.class, claNOTIFYICONDATA);
			meth_shell_notifyIcon.invoke(null, NIM_DELETE, iconData);

			//createUpdateWidget(true);
			claTrayItem.getDeclaredMethod("createUpdateWidget", boolean.class).invoke(
					this, true);

		} catch (Throwable t) {
			Debug.out(t);
		}

		try {
			// OSX doesn't have recreate
			//super.recreate();
			MethodHandle supr = MethodHandles.lookup().findSpecial(TrayItem.class,
					"recreate", MethodType.methodType(void.class),
					TrayItemRecreate.class);
			supr.invoke(this);

		} catch (Throwable t) {
			Debug.out(t);
		}
	}
}
