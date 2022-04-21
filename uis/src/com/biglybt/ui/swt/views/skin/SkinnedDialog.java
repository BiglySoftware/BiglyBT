/*
 * Created on Dec 23, 2008
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views.skin;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.debug.ObfuscateShell;
import com.biglybt.ui.swt.debug.UIDebugGenerator;

/**
 * Creates a dialog (shell) and fills it with a skinned layout
 *
 * @author TuxPaper
 * @created Dec 23, 2008
 *
 */
public class SkinnedDialog
	implements ObfuscateShell
{
	private final String shellSkinObjectID;

	private Shell shell;

	private SWTSkin skin;

	private List<SkinnedDialogClosedListener> closeListeners = new CopyOnWriteArrayList<>();

	private Shell mainShell;

	protected boolean disposed;

	public SkinnedDialog(String skinFile, String shellSkinObjectID) {
		this(skinFile, shellSkinObjectID, SWT.DIALOG_TRIM | SWT.RESIZE);
	}
	
	public SkinnedDialog(String skinFile, String shellSkinObjectID, int style) {
		this(SkinnedDialog.class.getClassLoader(), skinFile, shellSkinObjectID, style);
	}

	public SkinnedDialog(ClassLoader loader, String skinFile, String shellSkinObjectID, int style) {
		this( loader, "com/biglybt/ui/skin/", skinFile, shellSkinObjectID, style);
	}

	public SkinnedDialog(String skinFile, String shellSkinObjectID, Shell parent, int style) {
		this(SkinnedDialog.class.getClassLoader(), skinFile, shellSkinObjectID, parent, style);
	}

	public SkinnedDialog(ClassLoader loader, String skinFile, String shellSkinObjectID, Shell parent, int style) {
		this( loader, "com/biglybt/ui/skin/", skinFile, shellSkinObjectID, parent, style);
	}
	
	public SkinnedDialog(ClassLoader cla, String skinPath, String skinFile,
			String shellSkinObjectID, int style) {
		this( cla, skinPath, skinFile, shellSkinObjectID, UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell(), style );
	}

	public SkinnedDialog(ClassLoader cla, String skinPath, String skinFile,	String shellSkinObjectID, Shell parent, int style)
	{
		if ( cla == null ){
			cla = SkinnedDialog.class.getClassLoader();
		}
		
		this.shellSkinObjectID = shellSkinObjectID;

		mainShell = UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell();
		shell = ShellFactory.createShell(parent, style);

		shell.setData( "class", this );
		
		Utils.setShellIcon(shell);

		SWTSkin skin = SWTSkinFactory.getNonPersistentInstance(cla, skinPath,
				skinFile + ".properties");

		setSkin(skin);

		skin.initialize(shell, shellSkinObjectID);

		shell.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_ESCAPE) {
					shell.close();
				}
			}
		});

		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				//skin.destroy;
				disposed = true;
				Utils.execSWTThreadLater(0, new AERunnable() {
					@Override
					public void runSupport() {
						for (SkinnedDialogClosedListener l : closeListeners) {
							try {
								l.skinDialogClosed(SkinnedDialog.this);
							} catch (Exception e2) {
								Debug.out(e2);
							}
						}
					}
				});
			}
		});

		disposed = false;
	}

	@Override
	public Image generateObfuscatedImage() {
		return( UIDebugGenerator.generateObfuscatedImage( shell ));
	}
	
	protected void setSkin(SWTSkin _skin) {
		skin = _skin;
	}

	public void open() {
		open(null,true);
	}

	public void openUnadjusted() {
		skin.setAutoSizeOnLayout(false);
		open("none", true);
	}

	public void open(String idShellMetrics, boolean bringToFront ) {
		open( idShellMetrics, bringToFront, null );
	}
	
	public void open(String idShellMetrics, boolean bringToFront, Shell moveBelow ) {
		if (disposed) {
			Debug.out("can't opened disposed skinnedialog");
			return;
		}
		skin.layout();

		if (idShellMetrics != null && !"none".equals(idShellMetrics)) {
			boolean had_metrics = Utils.hasShellMetricsConfig( idShellMetrics );
			Utils.linkShellMetricsToConfig(shell, idShellMetrics);
			if ( !had_metrics ){
				Utils.centerWindowRelativeTo(shell, mainShell);
				Utils.verifyShellRect(shell, true);
			}
		} else if (idShellMetrics == null) {
			Utils.centerWindowRelativeTo(shell, mainShell);
			Utils.verifyShellRect(shell, true);
		}

		shell.setData( "bringToFront", bringToFront );
		
		if ( moveBelow == null ){
			
			shell.open();
			
		}else{

			shell.moveBelow(moveBelow);

			shell.setVisible(true);
		}
	}

	public SWTSkin getSkin() {
		return skin;
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	public void close() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (disposed) {
					return;
				}
				if (shell != null && !shell.isDisposed()) {
					shell.close();
				}
			}
		});
	}

	public void addCloseListener(SkinnedDialogClosedListener l) {
		closeListeners.add(l);
	}

	public interface SkinnedDialogClosedListener
	{
		public void skinDialogClosed(SkinnedDialog dialog);
	}

	/**
	 * @param string
	 *
	 * @since 4.0.0.5
	 */
	public void setTitle(String string) {
		if (!disposed && shell != null && !shell.isDisposed()) {
			shell.setText(string);
		}
	}
	
	/**
	 * @return the shell
	 */
	public Shell getShell() {
		return shell;
	}

	public boolean isDisposed() {
		return disposed || shell == null || shell.isDisposed();
	}
}
