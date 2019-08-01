/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.win32;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Constants;
import com.biglybt.platform.win32.access.AEWin32Manager;
import com.biglybt.platform.win32.access.impl.AEWin32AccessInterface;

import com.biglybt.core.drivedetector.DriveDetectedInfo;
import com.biglybt.core.drivedetector.DriveDetector;
import com.biglybt.core.drivedetector.DriveDetectorFactory;

/**
 * @author TuxPaper
 * @created Nov 29, 2006
 *
 * Note: You can safely exclude this class from the build path.
 * All calls to this class use (or at least should use) reflection
 */
public class Win32UIEnhancer
{

	public static final boolean DEBUG = false;

	public static final int SHGFI_ICON = 0x000000100;

	public static final int SHGFI_SMALLICON= 0x1;

	public static final int SHGFI_USEFILEATTRIBUTES = 0x000000010;

	public static final int SHGFI_LARGEICON = 0x2;

	public static final int WM_DEVICECHANGE = 0x219;

	public static final int DBT_DEVICEARRIVAL = 0x8000;

	public static final int DBT_DEVICEREMOVECOMPLETE = 0x8004;

	public static final int DBT_DEVTYP_VOLUME = 0x2;

	public static final int FILE_ATTRIBUTE_NORMAL = 0x00000080;

	private static int messageProcInt;

	private static long messageProcLong;

	private static Object /* Callback */messageCallback;

	private static DriveDetectedInfo loc;

	private static Class<?> claOS;

	private static boolean useLong;

	private static Class<?> claCallback;

	private static Constructor<?> constCallBack;

	private static Method mCallback_getAddress;

	private static Method mSetWindowLongPtr;

	private static int OS_GWLP_WNDPROC;

	private static Method mOS_memmove_byte;

	private static Method mOS_memmove_int;

	private static Class<?> claSHFILEINFO;
	private static Class<?> claSHFILEINFO_Target;

	private static Method mSHGetFileInfo;

	private static Method mImage_win32_new;

	private static Constructor<?> constTCHAR3;

	private static int SHFILEINFO_sizeof;

	private static long oldProc;

	private static Method mGetWindowLongPtr;

	private static Method mCallWindowProc;

	static {
		try {
			claOS = Class.forName("org.eclipse.swt.internal.win32.OS");

			boolean alwaysUnicode = SWT.getVersion() >= 4924;	// Removed in 4924 (64-bit) and assumed to always be true
			
			boolean isUnicode;
			
			if ( alwaysUnicode ){
				
				isUnicode = true;	
				
			}else{
				
				isUnicode =  claOS.getDeclaredField("IsUnicode").getBoolean(null);
				
			}
			
			claSHFILEINFO = Class.forName("org.eclipse.swt.internal.win32.SHFILEINFO");

			SHFILEINFO_sizeof = claSHFILEINFO.getField("sizeof").getInt(null);

			if ( isUnicode ){
				
				if ( alwaysUnicode ){
					
					claSHFILEINFO_Target = claSHFILEINFO;
					
				}else{
				
					claSHFILEINFO_Target = Class.forName("org.eclipse.swt.internal.win32.SHFILEINFOW");
				}
			}else{
				
				claSHFILEINFO_Target = Class.forName("org.eclipse.swt.internal.win32.SHFILEINFOA");
			}

			if ( alwaysUnicode ){
				
					//public static long /*int*/ SHGetFileInfo (char[] pszPath, int dwFileAttributes, SHFILEINFO psfi, int cbFileInfo, int uFlags)
				
				mSHGetFileInfo = claOS.getMethod("SHGetFileInfo", new Class<?>[] {
					char[].class,
					int.class,
					claSHFILEINFO,
					int.class,
					int.class,
				});
				
			}else{
				
				Class<?> claTCHAR = Class.forName("org.eclipse.swt.internal.win32.TCHAR");
	
				// public TCHAR (int codePage, String string, boolean terminate) {
				constTCHAR3 = claTCHAR.getConstructor(new Class[] {
					int.class,
					String.class,
					boolean.class
				});
	
				//public static long /*int*/ SHGetFileInfo (TCHAR pszPath, int dwFileAttributes, SHFILEINFO psfi, int cbFileInfo, int uFlags)
				mSHGetFileInfo = claOS.getMethod("SHGetFileInfo", new Class<?>[] {
					claTCHAR,
					int.class,
					claSHFILEINFO,
					int.class,
					int.class,
				});
			}

			// public Callback (Object object, String method, int argCount)
			claCallback = Class.forName("org.eclipse.swt.internal.Callback");
			constCallBack = claCallback.getDeclaredConstructor(new Class[] {
				Object.class,
				String.class,
				int.class
			});
			// public long /*int*/ getAddress ()
			mCallback_getAddress = claCallback.getDeclaredMethod("getAddress",
					new Class[] {});

			try {
				//int /*long*/ SetWindowLongPtr (int /*long*/ hWnd, int nIndex, int /*long*/ dwNewLong) {
				mSetWindowLongPtr = claOS.getMethod("SetWindowLongPtr",
						new Class[] {
							int.class,
							int.class,
							int.class
						});

				mGetWindowLongPtr = claOS.getMethod("GetWindowLongPtr",
						new Class[] {
							int.class,
							int.class
						});

				// int /*long*/ CallWindowProc (int /*long*/ lpPrevWndFunc, int /*long*/ hWnd, int Msg, int /*long*/ wParam, int /*long*/ lParam) {

				mCallWindowProc = claOS.getMethod("CallWindowProc",
						new Class[] {
							int.class,
							int.class,
							int.class,
							int.class,
							int.class
						});

				useLong = false;

				mOS_memmove_byte = claOS.getMethod("memmove", new Class[] {
					byte[].class,
					int.class,
					int.class
				});
				mOS_memmove_int = claOS.getMethod("memmove", new Class[] {
					int[].class,
					int.class,
					int.class
				});

				mImage_win32_new = Image.class.getMethod("win32_new", new Class[] {
					Device.class,
					int.class,
					int.class
				});
			} catch (Exception e) {
				//e.printStackTrace();
				mSetWindowLongPtr = claOS.getMethod("SetWindowLongPtr",
						new Class[] {
							long.class,
							int.class,
							long.class
						});
				mGetWindowLongPtr = claOS.getMethod("GetWindowLongPtr",
						new Class[] {
							long.class,
							int.class
						});

				mCallWindowProc = claOS.getMethod("CallWindowProc",
						new Class[] {
							long.class,
							long.class,
							int.class,
							long.class,
							long.class
						});

				useLong = true;
				mOS_memmove_byte = claOS.getMethod("memmove", new Class[] {
					byte[].class,
					long.class,
					long.class
				});
				mOS_memmove_int = claOS.getMethod("memmove", new Class[] {
					int[].class,
					long.class,
					long.class
				});

				mImage_win32_new = Image.class.getMethod("win32_new", new Class[] {
					Device.class,
					int.class,
					long.class
				});
			}

			//OS.GWLP_WNDPROC
			OS_GWLP_WNDPROC = ((Integer) claOS.getField("GWLP_WNDPROC").get(null)).intValue();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public static Image getFileIcon(File file, boolean big) {
		try {
  		int flags = SHGFI_ICON;
  		flags |= big ? SHGFI_LARGEICON : SHGFI_SMALLICON;
  		if (!file.exists()) {
  			flags |= SHGFI_USEFILEATTRIBUTES;
  		}
  		Object shfi;
  		
  		shfi = claSHFILEINFO_Target.newInstance();
  		
  		String path = file.getAbsolutePath();
  		
  		Object pszPath;
  		
  		if ( constTCHAR3 == null ){
  		
  			char[] temp = path.toCharArray();
  			
  			char[] chars = new char[temp.length+1];
  			
  			System.arraycopy( temp, 0, chars, 0, temp.length );
  			
  			chars[chars.length-1] = 0;
  			
  			pszPath = chars;
  			
		}else{
			
			pszPath = constTCHAR3.newInstance(0, path, true);
		}
		
  		mSHGetFileInfo.invoke(null, new Object[] {
  			pszPath,
  			file.isDirectory() ? 16
  					: FILE_ATTRIBUTE_NORMAL, shfi, SHFILEINFO_sizeof, flags
  		});

  		Field fldHIcon = claSHFILEINFO.getField("hIcon");
  		if (fldHIcon.getLong(shfi) == 0) {
  			return null;
  		}
  		Image image = null;
  		if (useLong) {
  			image = (Image) mImage_win32_new.invoke(null, new Object[] {
  				null,
  				SWT.ICON,
  				fldHIcon.getLong(shfi)
  			});
  		} else {
  			image = (Image) mImage_win32_new.invoke(null, new Object[] {
  				null,
  				SWT.ICON,
  				fldHIcon.getInt(shfi)
  			});
  		}

  		return image;
		} catch (Exception e) {
			return null;
		}
	}

	public static void initMainShell(Shell shell) {
		//Canvas canvas = new Canvas(shell, SWT.NO_BACKGROUND | SWT.NO_TRIM);
		//canvas.setVisible(false);
		Shell subshell = new Shell(shell);

		try {
			messageCallback = constCallBack.newInstance(new Object[] {
				Win32UIEnhancer.class,
				"messageProc2",
				4
			});


			Object oHandle = subshell.getClass().getField("handle").get(subshell);
			oldProc = ((Number) mGetWindowLongPtr.invoke(null,  new Object[] {
				oHandle,
				OS_GWLP_WNDPROC
			})).longValue();

			if (useLong) {
				Number n = (Number) mCallback_getAddress.invoke(messageCallback,
						new Object[] {});
				messageProcLong = n.longValue();
				if (messageProcLong != 0) {
					mSetWindowLongPtr.invoke(null, new Object[] {
						oHandle,
						OS_GWLP_WNDPROC,
						messageProcLong
					});
				}
			} else {
				Number n = (Number) mCallback_getAddress.invoke(messageCallback,
						new Object[] {});
				messageProcInt = n.intValue();
				if (messageProcInt != 0) {
					mSetWindowLongPtr.invoke(null, new Object[] {
						oHandle,
						OS_GWLP_WNDPROC,
						messageProcInt
					});
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		new AEThread2( "Async:USB" )
		{
			@Override
			public void
			run()
			{
				if ( Constants.isWindows7OrHigher ){

					String version = AEWin32Manager.getAccessor( false ).getVersion();

					if ( Constants.compareVersions( "1.21", version ) > 0 ){

							// bug fixed in 1.21 whereby some win7 users got crashes

						return;
					}
				}

		  		Map<File, Map> drives = AEWin32Manager.getAccessor(false).getAllDrives();
		  		if (drives != null) {
		  			for (File file : drives.keySet()) {
		  				Map driveInfo = drives.get(file);
							boolean isWritableUSB = AEWin32Manager.getAccessor(false).isUSBDrive(driveInfo);
							driveInfo.put("isWritableUSB", isWritableUSB);
		  				DriveDetectorFactory.getDeviceDetector().driveDetected(file, driveInfo);
		  			}
		  		}
			}
		}.start();
	}

	static int /*long*/messageProc2(int /*long*/hwnd, int /*long*/msg,
			int /*long*/wParam, int /*long*/lParam) {
		return (int) messageProc2(hwnd, msg, (long) wParam, (long) lParam);
	}

	static long /*int*/messageProc2(long /*int*/hwnd, long /*int*/msg,
			long /*int*/wParam, long /*int*/lParam) {
		try {
			// I'll clean this up soon
			switch ((int) /*64*/msg) {
				case WM_DEVICECHANGE:
					if (wParam == DBT_DEVICEARRIVAL) {
						int[] st = new int[3];
						if (useLong) {
  						mOS_memmove_int.invoke(null, new Object[] {
  							st,
  							lParam,
  							(long) 12
  						});
						} else {
  						mOS_memmove_int.invoke(null, new Object[] {
  							st,
  							(int) lParam,
  							(int) 12
  						});
						}

						if (DEBUG) {
							System.out.println("Arrival: " + st[0] + "/" + st[1] + "/"
									+ st[2]);
						}

						if (st[1] == DBT_DEVTYP_VOLUME) {
							if (DEBUG) {
								System.out.println("NEW VOLUME!");
							}

							byte b[] = new byte[st[0]];

							if (useLong) {
  							mOS_memmove_byte.invoke(null, new Object[] {
  								b,
  								lParam,
  								(int) st[0]
  							});
							} else {
  							mOS_memmove_byte.invoke(null, new Object[] {
  								b,
  								(int) lParam,
  								(int) st[0]
  							});
							}
							long unitMask = (b[12] & 255) + ((b[13] & 255) << 8) + ((b[14] & 255) << 16)
									+ ((b[15] & 3) << 24);
							char letter = '?';
							for (int i = 0; i < 26; i++) {
								if (((1 << i) & unitMask) > 0) {
									letter = (char) ('A' + i);
									if (DEBUG) {
										System.out.println("Drive " + letter + ";mask=" + unitMask);
									}
									Map driveInfo = AEWin32AccessInterface.getDriveInfo(letter);
									boolean isWritableUSB = AEWin32Manager.getAccessor(false).isUSBDrive(driveInfo);
									driveInfo.put("isWritableUSB", isWritableUSB);
									DriveDetector driveDetector = DriveDetectorFactory.getDeviceDetector();
									driveDetector.driveDetected(new File(letter + ":\\"), driveInfo);
								}
							}
						}

					} else if (wParam == DBT_DEVICEREMOVECOMPLETE) {
						int[] st = new int[3];
						if (useLong) {
  						mOS_memmove_int.invoke(null, new Object[] {
  							st,
  							lParam,
  							(long) 12
  						});
						} else {
  						mOS_memmove_int.invoke(null, new Object[] {
  							st,
  							(int) lParam,
  							(int) 12
  						});
						}

						if (DEBUG) {
							System.out.println("Remove: " + st[0] + "/" + st[1] + "/" + st[2]);
						}

						if (st[1] == DBT_DEVTYP_VOLUME) {
							if (DEBUG) {
								System.out.println("REMOVE VOLUME!");
							}

							byte b[] = new byte[st[0]];
							if (useLong) {
  							mOS_memmove_byte.invoke(null, new Object[] {
  								b,
  								lParam,
  								(int) st[0]
  							});
							} else {
  							mOS_memmove_byte.invoke(null, new Object[] {
  								b,
  								(int) lParam,
  								(int) st[0]
  							});
							}
							long unitMask = (b[12] & 255) + ((b[13] & 255) << 8)
									+ ((b[14] & 255) << 16) + ((b[15] & 3) << 24);
							char letter = '?';
							DriveDetector driveDetector = DriveDetectorFactory.getDeviceDetector();
							for (int i = 0; i < 26; i++) {
								if (((1 << i) & unitMask) > 0) {
									letter = (char) ('A' + i);
									if (DEBUG) {
										System.out.println("Drive " + letter + ";mask=" + unitMask);
									}
									driveDetector.driveRemoved(new File(letter + ":\\"));
								}
							}

							Map<File, Map> drives = AEWin32Manager.getAccessor(false).getAllDrives();
				  		if (drives != null) {
  							DriveDetectedInfo[] existingDrives = driveDetector.getDetectedDriveInfo();
  							for (DriveDetectedInfo existingDrive : existingDrives) {
  								File existingDriveFile = existingDrive.getLocation();
  								boolean found = drives.containsKey(existingDriveFile);
  								if (!found) {
  									if (DEBUG) {
  										System.out.println("Fixup: Remove Drive " + existingDriveFile);
  									}
  									driveDetector.driveRemoved(existingDriveFile);
  								}
  							}
				  		}

						}

					}
					if (DEBUG) {
						System.out.println("DEVICE CHANGE" + wParam + "/" + lParam);
					}
					break;
			}

			if (useLong) {
				return (Long)mCallWindowProc.invoke(null, new Object[]{ oldProc, hwnd, (int) msg, wParam, lParam });
			}else{
				return (Integer)mCallWindowProc.invoke(null, new Object[]{ (int)oldProc, (int)hwnd, (int) msg, (int)wParam, (int)lParam });
			}
		} catch (Exception e) {
			e.printStackTrace();

			return( 0 );
		}
	}
}
