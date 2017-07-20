/*
 * Created on 23-Jun-2004
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.pifimpl.local.torrent;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.category.CategoryManagerListener;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.torrent.TorrentAttributeEvent;
import com.biglybt.pif.utils.StaticUtilities;


public class TorrentAttributeCategoryImpl extends BaseTorrentAttributeImpl {

	protected
	TorrentAttributeCategoryImpl()
	{
		CategoryManager.addCategoryManagerListener(
				new CategoryManagerListener()
				{
					@Override
					public void
					categoryAdded(
						final Category category )
					{
						TorrentAttributeEvent	ev =
							new TorrentAttributeEvent()
							{
								@Override
								public int
								getType()
								{
									return( TorrentAttributeEvent.ET_ATTRIBUTE_VALUE_ADDED );
								}

								@Override
								public TorrentAttribute
								getAttribute()
								{
									return( TorrentAttributeCategoryImpl.this );
								}

								@Override
								public Object
								getData()
								{
									return( category.getName());
								}
							};

							TorrentAttributeCategoryImpl.this.notifyListeners(ev);
					}

					@Override
					public void categoryChanged(Category category) {
					}

					@Override
					public void
					categoryRemoved(
						final Category category )
					{
						TorrentAttributeEvent	ev =
							new TorrentAttributeEvent()
							{
								@Override
								public int
								getType()
								{
									return( TorrentAttributeEvent.ET_ATTRIBUTE_VALUE_REMOVED );
								}

								@Override
								public TorrentAttribute
								getAttribute()
								{
									return( TorrentAttributeCategoryImpl.this );
								}

								@Override
								public Object
								getData()
								{
									return( category.getName());
								}
							};

							TorrentAttributeCategoryImpl.this.notifyListeners(ev);
					}
				});
	}

	@Override
	public String
	getName()
	{
		return( TA_CATEGORY );
	}

	@Override
	public String[]
	getDefinedValues()
	{
		Category[] categories = CategoryManager.getCategories();

		List	v = new ArrayList();

		for (int i=0;i<categories.length;i++){

			Category cat = categories[i];

			if ( cat.getType() == Category.TYPE_USER ){

				v.add( cat.getName());
			}
		}

		String[]	res = new String[v.size()];

		v.toArray( res );

			// make it nice for clients

		Arrays.sort( res, StaticUtilities.getFormatters().getAlphanumericComparator( true ));

		return( res );
	}

	@Override
	public void
	addDefinedValue(
		String		name )
	{
		CategoryManager.createCategory( name );
	}


	@Override
	public void
	removeDefinedValue(
		String		name )
	{
		Category cat = CategoryManager.getCategory( name );

		if ( cat != null ){

			CategoryManager.removeCategory( cat );
		}
	}

}
