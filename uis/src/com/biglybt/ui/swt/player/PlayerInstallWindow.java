/*
 * Created on Mar 15, 2010 02:29 PM
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
 */

package com.biglybt.ui.swt.player;

import com.biglybt.ui.skin.SkinPropertiesImpl;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.skin.SWTSkinObjectText;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;

import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.swt.views.skin.VuzeMessageBox;
import com.biglybt.ui.swt.views.skin.VuzeMessageBoxListener;

import com.biglybt.ui.swt.Utils;

/**
 * @author Gudy
 * @created Mar 15, 2010
 *
 */
public class PlayerInstallWindow
	implements PlayerInstallerListener
{
	private final static boolean FAKE_DELAY = Constants.IS_CVS_VERSION;

	private VuzeMessageBox box;

	private ProgressBar progressBar;

	private SWTSkinObjectText soProgressText;

	private String progressText;

	private SWTSkinObjectText soInstallPct;

	private PlayerInstaller installer;

	public PlayerInstallWindow(PlayerInstaller installer) {

		this.installer = installer;
		installer.setListener(this);
	}

	public void open() {

		box = new VuzeMessageBox("","", null, 0);
		box.setSubTitle(MessageText.getString("dlg.player.install.subtitle"));
		box.addResourceBundle(PlayerInstallWindow.class,
				SkinPropertiesImpl.PATH_SKIN_DEFS, "skin3_dlg_register");
		box.setIconResource("image.player.dlg.header");

		this.progressText = MessageText.getString("dlg.player.install.description");

		box.setListener(new VuzeMessageBoxListener() {

			@Override
			public void shellReady(Shell shell, SWTSkinObjectContainer soExtra) {
				SWTSkin skin = soExtra.getSkin();
				skin.createSkinObject("dlg.register.install", "dlg.register.install",
						soExtra);

				SWTSkinObjectContainer soProgressBar = (SWTSkinObjectContainer) skin.getSkinObject("progress-bar");
				if (soProgressBar != null) {
					progressBar = new ProgressBar(soProgressBar.getComposite(),
							SWT.HORIZONTAL);
					progressBar.setMinimum(0);
					progressBar.setMaximum(100);
					progressBar.setLayoutData(Utils.getFilledFormData());
				}

				soInstallPct = (SWTSkinObjectText) skin.getSkinObject("install-pct");

				soProgressText = (SWTSkinObjectText) skin.getSkinObject("progress-text");
				if (soProgressText != null && progressText != null) {
					soProgressText.setText(progressText);
				}
			}
		});

		box.open(new UserPrompterResultListener() {
			@Override
			public void prompterClosed(int result) {
				installer.setListener(null);
				installer.cancel();
			}
		});
	}


	public void failed() {
		if (box != null) {
			box.closeWithButtonVal(0);
		}
	}

	@Override
	public void finished() {
		if (box != null) {
			box.closeWithButtonVal(0);
		}
	}

	@Override
	public void progress(final int percent) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				int pct = percent == 100 ? 99 : percent;
				if (soInstallPct != null) {
					soInstallPct.setText(MessageText.getString("dlg.auth.install.pct",
							new String[] {
								"" + pct
							}));
				}
				if (progressBar != null && !progressBar.isDisposed()) {
					// never reach 100%!
					progressBar.setSelection(pct);
				}
			}
		});

	}
}
