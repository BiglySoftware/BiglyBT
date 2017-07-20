/*
 * Created on Sep 13, 2012
 * Created by Paul Gardner
 *
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


package com.biglybt.ui.swt.shells.main;

import com.biglybt.core.Core;
import com.biglybt.ui.IUIIntializer;
import org.eclipse.swt.widgets.Display;
import com.biglybt.core.util.AERunStateHandler;

public class
MainWindowFactory
{
	private static final boolean
	isImmediate()
	{
		return( !AERunStateHandler.isDelayedUI());
	}

	public static MainWindow
	create(
		Core core,
		Display 				display,
		IUIIntializer uiInitializer )
	{
		if ( isImmediate()){

			return( new MainWindowImpl( core, uiInitializer ));

		}else{

			return( new MainWindowDelayStub( core, display, uiInitializer ));
		}
	}

	public static MainWindowInitStub
	createAsync(
		Display 		display,
		IUIIntializer 	uiInitializer )
	{
		final MainWindow	window;

		if ( isImmediate()){

			window = new MainWindowImpl(uiInitializer );

		}else{

			window = new MainWindowDelayStub( display, uiInitializer );
		}

		return(
			new MainWindowInitStub()
			{
				@Override
				public void
				init(
					Core core )
				{
					window.init( core );
				}

				@Override
				public void dispose() {
					window.disposeOnlyUI();
				}
			});
	}

	public static interface
	MainWindowInitStub
	{
		public void
		init(
			Core core );

		void dispose();
	}
}
