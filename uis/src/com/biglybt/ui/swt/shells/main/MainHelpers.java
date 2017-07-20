/*
 * Created on Sep 14, 2012
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

import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.core.config.COConfigurationManager;

public class
MainHelpers
{
	private static boolean	done_xfer_bar;

	protected static void
	initTransferBar()
	{
		UIFunctionsSWT ui_functions = UIFunctionsManagerSWT.getUIFunctionsSWT();

		if ( ui_functions == null ){

			return;
		}

		synchronized( MainHelpers.class ){

			if ( done_xfer_bar ){

				return;
			}

			done_xfer_bar = true;
		}

		if ( COConfigurationManager.getBooleanParameter("Open Transfer Bar On Start")){

			ui_functions.showGlobalTransferBar();
		}
	}
}
