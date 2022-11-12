/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views.skin;

import java.util.*;

import com.biglybt.ui.swt.skin.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.UIFunctionsUserPrompter;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.swt.views.skin.SkinnedDialog.SkinnedDialogClosedListener;

public class VuzeMessageBox
	implements UIFunctionsUserPrompter, SkinnedDialogClosedListener
{

	private String title;

	private String text;

	private int result = -1;

	private ArrayList<UserPrompterResultListener> resultListeners = new ArrayList<>(1);

	private VuzeMessageBoxListener vuzeMessageBoxListener;

	private SWTSkinObjectContainer soExtra;

	private SkinnedDialog dlg;

	private String iconResource;

	private String subtitle;

	private java.util.List<rbInfo> listRBs = new ArrayList<>();

	private SWTSkin skin;

	private String textIconResource;

	private boolean closed;

	private boolean opened;

	private StandardButtonsArea buttonsArea;

	private String dialogTempate = "skin3_dlg_generic";

	public VuzeMessageBox(final String title, final String text,
			final String[] buttons, final int defaultOption) {
		this.title = title;
		this.text = text;
		buttonsArea = new StandardButtonsArea() {
			// @see StandardButtonsArea#clicked(int)
			@Override
			protected void clicked(int buttonValue) {
				closeWithButtonVal(buttonValue);
			}
		};
		buttonsArea.setButtonIDs(buttons);
		buttonsArea.setDefaultButtonPos(defaultOption);
	}

	public void setButtonEnabled(final int buttonVal, final boolean enable) {
		buttonsArea.setButtonEnabled(buttonVal, enable);
	}

	public void setButtonVals(Integer[] buttonVals) {
		buttonsArea.setButtonVals(buttonVals);
	}


	public void setSubTitle(String s) {
		subtitle = s;
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#getAutoCloseInMS()
	 */
	@Override
	public int getAutoCloseInMS() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#getHtml()
	 */
	@Override
	public String getHtml() {
		return null;
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#getRememberID()
	 */
	@Override
	public String getRememberID() {
		return null;
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#getRememberText()
	 */
	@Override
	public String getRememberText() {
		return null;
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#isAutoClosed()
	 */
	@Override
	public boolean isAutoClosed() {
		return false;
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#open(UserPrompterResultListener)
	 */
	@Override
	public void open(final UserPrompterResultListener l) {
		opened = true;
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				// catch someone calling close() while we are opening
				if (closed) {
					return;
				}
				synchronized (VuzeMessageBox.this) {
					_open(l);
				}
			}
		});
	}

	public void setSkinnedDialagTemplate(String dialogTempate) {
		this.dialogTempate = dialogTempate;
	}

	protected void _open(UserPrompterResultListener l) {
		if (l != null) {
  		synchronized (resultListeners) {
  			resultListeners.add(l);
  		}
		}
		dlg = new SkinnedDialog(dialogTempate, "shell", SWT.DIALOG_TRIM) {
			@Override
			protected void setSkin(SWTSkin skin) {
				super.setSkin(skin);

				//skin.DEBUGLAYOUT = true;

				VuzeMessageBox.this.skin = skin;
				synchronized (listRBs) {
					for (rbInfo rb : listRBs) {
						addResourceBundle(rb.cla, rb.path, rb.name);
					}
					listRBs.clear();
				}

			}
		};

		dlg.setTitle(title);
		dlg.addCloseListener(this);

		SWTSkinObjectText soTopTitle = (SWTSkinObjectText) skin.getSkinObject("top-title");
		if (soTopTitle != null) {
			soTopTitle.setText(subtitle == null ? title : subtitle);
		}

		SWTSkinObjectText soText = (SWTSkinObjectText) skin.getSkinObject("middle-title");
		if (soText != null) {
			soText.setText(text);
		}

		if (iconResource != null) {
  		SWTSkinObjectImage soTopLogo = (SWTSkinObjectImage) dlg.getSkin().getSkinObject("top-logo");
  		if (soTopLogo != null) {
  			soTopLogo.setImageByID(iconResource, null);
  		}
		}

		if (textIconResource != null) {
  		SWTSkinObjectImage soIcon = (SWTSkinObjectImage) dlg.getSkin().getSkinObject("text-icon");
  		if (soIcon != null) {
  			soIcon.setImageByID(textIconResource, null);
  		}
		}

		if (iconResource == null && textIconResource == null && soTopTitle != null && soText != null) {
			soTopTitle.setStyle(soText.getStyle() & ~(SWT.RIGHT | SWT.CENTER));
		}

		SWTSkinObjectContainer soBottomArea = (SWTSkinObjectContainer) skin.getSkinObject("bottom-area");
		if (soBottomArea != null) {
			if (buttonsArea.getButtonCount() == 0) {
				soBottomArea.setVisible(false);
			} else {
				buttonsArea.swt_createButtons(soBottomArea.getComposite());
			}
		}

		if (vuzeMessageBoxListener != null) {
			soExtra = (SWTSkinObjectContainer) skin.getSkinObject("middle-extra");
			try {
				vuzeMessageBoxListener.shellReady(dlg.getShell(), soExtra);
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		if (closed) {
			return;
		}
		dlg.open();
	}

	public Button[] getButtons() {
		return buttonsArea.getButtons();
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#setAutoCloseInMS(int)
	 */
	@Override
	public void setAutoCloseInMS(int autoCloseInMS) {
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#setHtml(java.lang.String)
	 */
	@Override
	public void setHtml(String html) {
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#setIconResource(java.lang.String)
	 */
	@Override
	public void setIconResource(String resource) {
		this.iconResource = resource;
		if (dlg != null) {
  		SWTSkinObjectImage soTopLogo = (SWTSkinObjectImage) dlg.getSkin().getSkinObject("top-logo");
  		if (soTopLogo != null) {
  			soTopLogo.setImageByID(iconResource, null);
  		}
		}
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#setRelatedObject(java.lang.Object)
	 */
	@Override
	public void setRelatedObject(Object relatedObject) {
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#setRelatedObjects(java.lang.Object[])
	 */
	@Override
	public void setRelatedObjects(Object[] relatedObjects) {
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#setRemember(java.lang.String, boolean, java.lang.String)
	 */
	@Override
	public void setRemember(String rememberID, boolean rememberByDefault,
	                        String rememberText) {
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#setRememberText(java.lang.String)
	 */
	@Override
	public void setRememberText(String rememberText) {
	}

	@Override
	public void setRememberOnlyIfButton(int button) {
		// TODO Auto-generated method stub

	}
	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#setUrl(java.lang.String)
	 */
	@Override
	public void setUrl(String url) {
	}

	/* (non-Javadoc)
	 * @see UIFunctionsUserPrompter#waitUntilClosed()
	 */
	@Override
	public int waitUntilClosed() {
		if (opened) {
			final AESemaphore sem = new AESemaphore("waitUntilClosed");
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					if (dlg == null) {
						sem.release();
						return;
					}
					if (!opened) {
						dlg.open();
					}
					Shell shell = dlg.getShell();
					if (shell == null || shell.isDisposed()) {
						sem.release();
						return;
					}

					shell.addDisposeListener(new DisposeListener() {
						@Override
						public void widgetDisposed(DisposeEvent e) {
							sem.release();
						}
					});
				}
			});

			if (Utils.isThisThreadSWT()) {
				// on swt thread, so execSWTThread just ran and we should have a shell
				if (dlg != null) {
					Shell shell = dlg.getShell();
					if (shell != null) {
						Utils.readAndDispatchLoop( shell );
					}
					skinDialogClosed(dlg);
					return buttonsArea.getButtonVal(result);
				}
			}
			sem.reserve();
		}

		skinDialogClosed(dlg);
		return buttonsArea.getButtonVal(result);
	}

	/* (non-Javadoc)
	 * @see SkinnedDialog.SkinnedDialogClosedListener#skinDialogClosed(SkinnedDialog)
	 */
	@Override
	public void skinDialogClosed(SkinnedDialog dialog) {
		synchronized (resultListeners) {
			int realResult = buttonsArea.getButtonVal(result);
			for (UserPrompterResultListener l : resultListeners) {
				try {
					l.prompterClosed(realResult);
				} catch (Exception e) {
					Debug.out(e);
				}
			}
			resultListeners.clear();
		}
	}

	public void setListener(VuzeMessageBoxListener l) {
		this.vuzeMessageBoxListener = l;
	}

	public void closeWithButtonVal(int buttonVal) {
		synchronized (VuzeMessageBox.this) {
  		this.closed = true;
  		this.result = buttonsArea.getButtonPosFromVal(buttonVal);
  		if (dlg != null) {
  			dlg.close();
  		}
		}
	}

	public void addResourceBundle(Class<?> cla, String path, String name) {

		synchronized (listRBs) {
			if (skin == null) {
				listRBs.add(new rbInfo(cla, path, name));
				return;
			}
		}

		String sFile = path + name;
		ClassLoader loader = cla.getClassLoader();
		ResourceBundle subBundle = ResourceBundle.getBundle(sFile,
				Locale.getDefault(), loader);


		SWTSkinProperties skinProperties = skin.getSkinProperties();
		skinProperties.addResourceBundle(subBundle, path, loader);
	}

	public void setTextIconResource(String resource) {
		this.textIconResource = resource;
		if (dlg != null) {
  		SWTSkinObjectImage soIcon = (SWTSkinObjectImage) dlg.getSkin().getSkinObject("text-icon");
  		if (soIcon != null) {
  			soIcon.setImageByID(textIconResource, null);
  		}
		}
	}

	public void addListener(UserPrompterResultListener l) {
		if (l == null) {
			return;
		}
		synchronized (resultListeners) {
			resultListeners.add(l);
		}
	}

	public void setDefaultButtonByPos(int pos) {
		if (dlg == null) {
			buttonsArea.setDefaultButtonPos(pos);
		}
	}


	private static class rbInfo {
		public rbInfo(Class<?> cla, String path, String name) {
			super();
			this.cla = cla;
			this.path = path;
			this.name = name;
		}
		Class<?> cla;
		String path;
		String name;
	}


	// @see UIFunctionsUserPrompter#setOneInstanceOf(java.lang.String)
	@Override
	public void setOneInstanceOf(String instanceID) {
		// TODO Auto-generated method stub
	}
}
