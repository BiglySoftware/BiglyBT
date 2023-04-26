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

package com.biglybt.ui.swt.columns.subscriptions;

import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.columns.ColumnCheckBox2;

import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionHistory;

/**
 * @author Olivier Chalouhi
 * @created Mar 15, 2009
 *
 */
public class 
ColumnSubscriptionEnabled
	extends ColumnCheckBox2
{
	public static String COLUMN_ID = "enabled";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SETTINGS,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	/** Default Constructor */
	public ColumnSubscriptionEnabled(String sTableID) {
		super( sTableID, COLUMN_ID );
	}

	@Override
	protected Boolean 
	getCheckBoxState(
		Object datasource)
	{
		Subscription sub = (Subscription)datasource;
		
		if (sub != null) {
		
			if ( !( sub.isSearchTemplate() || sub.isSubscriptionTemplate())){
				
				SubscriptionHistory history = sub.getHistory();
				
				if ( history != null ){
					
					return( history.isEnabled());
				}
			}
		}
		return( null );
	}
	
	@Override
	protected void 
	setCheckBoxState(
		Object	datasource, 
		boolean	set)
	{
		Subscription sub = (Subscription)datasource;
	
		try{
			SubscriptionHistory history = sub.getHistory();
					
			history.setEnabled( set );
			
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
}
