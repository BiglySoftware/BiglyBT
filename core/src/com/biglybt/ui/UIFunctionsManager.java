/*
 * Created on Jul 12, 2006 2:47:29 PM
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
package com.biglybt.ui;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.util.Debug;

/**
 * @author TuxPaper
 * @created Jul 12, 2006
 *
 */
public class UIFunctionsManager
{
	private static UIFunctions instance = null;

	private static List<UIFCallback>	callbacks = null;

	public static void
	execWithUIFunctions(
		UIFCallback		cb )
	{
		UIFunctions current_instance;

		synchronized( UIFunctionsManager.class ){

			current_instance = instance;

			if ( current_instance == null ){

				if ( callbacks == null ){

					callbacks = new ArrayList<>();
				}

				callbacks.add( cb );

				return;
			}
		}

		cb.run( current_instance );
	}

	public static UIFunctions
	getUIFunctions()
	{
		UIFunctions result = instance;

		return( result );
	}

	public static void
	setUIFunctions(
		UIFunctions uiFunctions )
	{
		List<UIFCallback>	pending = null;

		synchronized( UIFunctionsManager.class ){

			instance = uiFunctions;

			if ( callbacks != null ){

				pending = new ArrayList<>(callbacks);

				callbacks = null;
			}
		}

		if ( pending != null ){

			for ( UIFCallback cb: pending ){

				try{
					cb.run( uiFunctions );

				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}
	}

	public interface
	UIFCallback
	{
		public void
		run(
			UIFunctions		functions );
	}
}