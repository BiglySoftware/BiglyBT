/*
 * File    : ShareUtils.java
 * Created : 08-Jan-2004
 * By      : parg
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

package com.biglybt.ui.swt.sharing;

/**
 * @author parg
 *
 */

import java.io.File;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;

import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;

public class
ShareUtils
{
	public static void
	shareFile(
		final Shell			shell )
	{
		new AEThread("shareFile")
		{
			@Override
			public void
			runSupport()
			{
				Display display = shell.getDisplay();
				final String[] path = { null };
				final AESemaphore	sem = new AESemaphore("ShareUtils:file");


				display.asyncExec(new AERunnable() {
					@Override
					public void runSupport()
					{
						try{
							FileDialog dialog = new FileDialog(shell, SWT.SYSTEM_MODAL | SWT.OPEN);

							dialog.setFilterPath( TorrentOpener.getFilterPathData() );

							dialog.setText(MessageText.getString("MainWindow.dialog.share.sharefile"));

              path[0] = TorrentOpener.setFilterPathData( dialog.open() );

						}finally{

							sem.release();
						}
					}
				});

				sem.reserve();

				if ( path[0] != null ){

					shareFile( path[0] );
				}
			}
		}.start();
	}

	public static void
	shareDir(
		Shell		shell )
	{
		shareDirSupport( shell, false, false );
	}

	public static void
	shareDirContents(
		Shell		shell,
		boolean		recursive )
	{
		shareDirSupport( shell, true, recursive );
	}

	protected static void
	shareDirSupport(
		final Shell			shell,
		final boolean		contents,
		final boolean		recursive )
	{
		new AEThread("shareDirSupport")
		{
			@Override
			public void
			runSupport()
			{
				Display display = shell.getDisplay();
				final String[] path = { null };
				final AESemaphore	sem = new AESemaphore("ShareUtils:dir");

				display.asyncExec(new AERunnable() {
					@Override
					public void runSupport()
					{
						try{
							DirectoryDialog dialog = new DirectoryDialog(shell, SWT.SYSTEM_MODAL);

							dialog.setFilterPath( TorrentOpener.getFilterPathData() );

							dialog.setText(
										contents?
										MessageText.getString("MainWindow.dialog.share.sharedircontents") +
												(recursive?"("+MessageText.getString("MainWindow.dialog.share.sharedircontents.recursive")+")":""):
										MessageText.getString("MainWindow.dialog.share.sharedir"));

							path[0] = TorrentOpener.setFilterPathData( dialog.open() );

						}finally{

							sem.release();
						}
					}
				});

				sem.reserve();

				if ( path[0] != null ){

					if ( contents ){

						shareDirContents( path[0], recursive );

					}else{

						shareDir( path[0] );
					}
				}
			}
		}.start();
	}

	public static void
	shareFile(
		final String		file_name )
	{
		shareFile( file_name, null );
	}

	public static void
	shareFile(
		final String				file_name,
		final Map<String,String>	properties )
	{
		new AEThread("shareFile")
		{
			@Override
			public void
			runSupport()
			{
				try{
					PluginInitializer.getDefaultInterface().getShareManager().addFile( new File(file_name), properties );

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
		}.start();
	}

	public static void
	shareDir(
		final String		file_name )
	{
		shareDir( file_name, null );
	}

	public static void
	shareDir(
		final String				file_name,
		final Map<String,String>	properties )
	{
		new AEThread("shareDir")
		{
			@Override
			public void
			runSupport()
			{
				try{
					PluginInitializer.getDefaultInterface().getShareManager().addDir(new File(file_name), properties );

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
		}.start();
	}

	public static void
	shareDirContents(
		final String		file_name,
		final boolean		recursive )
	{
		new AEThread("shareDirCntents")
		{
			@Override
			public void
			runSupport()
			{
				try{
					PluginInitializer.getDefaultInterface().getShareManager().addDirContents(new File(file_name), recursive);

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
		}.start();
	}
}
